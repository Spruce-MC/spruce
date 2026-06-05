package org.spruce.gateway

import org.spruce.api.event.GatewayEventEnvelope
import org.spruce.api.service.AbstractSpruceService
import org.spruce.api.service.SpruceServiceBase
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.resps.StreamEntry
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class GatewayRedisBridge(
    redisUrl: String,
    private val gatewayId: String,
    logger: Logger
): AbstractSpruceService(
    redisUrl,
    logger
) {
    private val consumerName = "gateway-${System.getenv("GATEWAY_ID") ?: UUID.randomUUID().toString().take(8)}"

    private val pendingResponses = ConcurrentHashMap<String, (String) -> Unit>()
    private val responseStream: String = getResponseStream(gatewayId)

    private val eventListenerExecutor = Executors.newSingleThreadExecutor()
    private val requestTimeoutExecutor = Executors.newSingleThreadScheduledExecutor()

    init {
        createGroup(getResponseStream(gatewayId), serviceGroup)
        start()
    }

    override fun handleEntry(entry: StreamEntry) {
        val fields = entry.fields
        val requestId = fields["requestId"]
        val response = fields["response"]

        if (requestId == null || response == null) {
            logger.warning("Malformed stream entry: missing requestId or response")
            return
        }

        val callback = pendingResponses.remove(requestId)
        if (callback != null) {
            callback(response)
        } else {
            logger.fine("Late response for $requestId (already timed out?)")
        }
    }

    override fun pollStream(): MutableList<MutableMap.MutableEntry<String, MutableList<StreamEntry>>>? =
        streamRedis.xreadGroup(
            serviceGroup,
            consumerName,
            XReadGroupParams.xReadGroupParams().block(5000).count(10),
            mapOf(responseStream to StreamEntryID.UNRECEIVED_ENTRY)
        )

    override fun shutdown() {
        super.shutdown()
        eventListenerExecutor.shutdownNow()
        requestTimeoutExecutor.shutdownNow()
    }

    override fun getAckStream() = responseStream

    override fun getServiceGroup() = gatewayId

    /** ===================== Streams ===================== */

    fun sendRequest(
        requestId: String = UUID.randomUUID().toString(),
        service: String,
        action: String,
        payload: String,
        callback: (String) -> Unit,
        onError: (RuntimeException) -> Unit
    ) {
        val timeoutFuture = requestTimeoutExecutor.schedule({
            logger.warning("Request $requestId timed out after 10 seconds")
            pendingResponses.remove(requestId) ?: return@schedule
            onError(RequestTimeoutException(requestId))
        }, 10, TimeUnit.SECONDS)

        pendingResponses[requestId] = { response ->
            timeoutFuture.cancel(true)
            callback(response)
        }

        streamRedis.xadd(
            SpruceServiceBase.REQUEST_STREAM,
            XAddParams.xAddParams().approximateTrimming().maxLen(10000),
            mapOf(
                "requestId" to requestId,
                "service" to service,
                "action" to action,
                "payload" to payload,
                "gatewayId" to gatewayId
            )
        )
    }

    /** ===================== Events via Pub/Sub ===================== */

    fun startEventListener(onEvent: (String, String) -> Unit) {
        fun subscribe() {
            if (Thread.currentThread().isInterrupted) return

            eventListenerExecutor.submit {
                try {
                    logger.info("Subscribing to Redis Pub/Sub channel '${SpruceServiceBase.EVENT_CHANNEL}'...")

                    pubSubRedis.subscribe(object : redis.clients.jedis.JedisPubSub() {
                        override fun onMessage(channel: String?, message: String?) {
                            if (message != null) {
                                try {
                                    val envelope = mapper.readValue(message, GatewayEventEnvelope::class.java)
                                    onEvent(envelope.type, envelope.payload)
                                } catch (e: Exception) {
                                    logger.warning("Failed to handle event message: ${e.message}")
                                }
                            }
                        }

                        override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                            logger.info("Subscribed to channel: $channel ($subscribedChannels total)")
                        }

                        override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                            logger.info("Unsubscribed from channel: $channel")
                        }
                    }, SpruceServiceBase.EVENT_CHANNEL)

                    logger.warning("Redis Pub/Sub subscription ended unexpectedly. Will retry.")
                    requestTimeoutExecutor.schedule({ subscribe() }, 1, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    logger.warning("Error in Redis Pub/Sub subscriber: ${e.message}")
                    requestTimeoutExecutor.schedule({ subscribe() }, 1, TimeUnit.SECONDS)
                }
            }
        }

        subscribe()
    }

    /** ===================== Distributed Locks ===================== */

    private val lockPrefix = "spruce:lock:"

    private val releaseLockScript = """
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("DEL", KEYS[1])
        else
            return 0
        end
    """.trimIndent()

    private val refreshLockScript = """
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("PEXPIRE", KEYS[1], ARGV[2])
        else
            return 0
        end
    """.trimIndent()

    fun acquireLock(
        key: String,
        ownerId: String,
        token: String,
        ttlMillis: Long,
        waitMillis: Long
    ): Boolean {
        require(key.isNotBlank()) { "Lock key must not be blank" }
        require(token.isNotBlank()) { "Lock token must not be blank" }
        require(ttlMillis > 0) { "Lock ttl must be positive" }
        require(waitMillis >= 0) { "Lock acquire timeout must not be negative" }

        val redisKey = lockKey(key)

        if (waitMillis == 0L) {
            val result = streamRedis.set(
                redisKey,
                token,
                redis.clients.jedis.params.SetParams.setParams()
                    .nx()
                    .px(ttlMillis)
            )

            return result == "OK"
        }

        val deadline = System.currentTimeMillis() + waitMillis

        var backoff = 10L
        val maxBackoff = 100L

        while (System.currentTimeMillis() < deadline) {
            val result = streamRedis.set(
                redisKey,
                token,
                redis.clients.jedis.params.SetParams.setParams()
                    .nx()
                    .px(ttlMillis)
            )

            if (result == "OK") {
                logger.fine(
                    "Lock acquired: key=$key ownerId=$ownerId ttl=${ttlMillis}ms"
                )
                return true
            }

            val jitter = ThreadLocalRandom.current()
                .nextLong(backoff / 2, backoff + 1)

            val remaining = deadline - System.currentTimeMillis()

            if (remaining <= 0) {
                break
            }

            Thread.sleep(minOf(jitter, remaining))
            backoff = minOf(backoff * 2, maxBackoff)
        }

        return false
    }

    fun releaseLock(
        key: String,
        token: String
    ): Boolean {
        require(key.isNotBlank()) { "Lock key must not be blank" }
        require(token.isNotBlank()) { "Lock token must not be blank" }

        val result = streamRedis.eval(
            releaseLockScript,
            listOf(lockKey(key)),
            listOf(token)
        )

        return result == 1L
    }

    fun refreshLock(
        key: String,
        token: String,
        ttlMillis: Long
    ): Boolean {
        require(key.isNotBlank()) { "Lock key must not be blank" }
        require(token.isNotBlank()) { "Lock token must not be blank" }
        require(ttlMillis > 0) { "Lock ttl must be positive" }

        val result = streamRedis.eval(
            refreshLockScript,
            listOf(lockKey(key)),
            listOf(token, ttlMillis.toString())
        )

        return result == 1L
    }

    private fun lockKey(key: String): String {
        return lockPrefix + key
    }

    class RequestTimeoutException(requestId: String) : RuntimeException("Request timed out: $requestId")
}
