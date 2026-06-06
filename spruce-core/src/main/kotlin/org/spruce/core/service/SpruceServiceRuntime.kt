package org.spruce.core.service

import org.spruce.api.event.GatewayEvent
import org.spruce.api.event.GatewayEventEnvelope
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.params.XAddParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.resps.StreamEntry
import java.io.File
import java.util.*
import java.util.concurrent.*
import java.util.logging.Logger

class SpruceServiceRuntime(
    private val serviceName: String,
    redisUrl: String = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379")
) : AbstractSpruceService(redisUrl, Logger.getLogger(serviceName)) {

    private val serviceGroup = "$SERVICE_GROUP:$serviceName"
    private val consumerName = "$serviceName-${UUID.randomUUID().toString().substring(0, 8)}"

    private val configDirectory: File = File(
        System.getenv().getOrDefault("CONFIG_DIR", "configs/$serviceName")
    )

    private val handlers = ConcurrentHashMap<String, ServiceRequestHandler>()
    private val eventHandlers = ConcurrentHashMap<String, MutableList<ServiceEventHandler>>()

    private val shutdownLatch = CountDownLatch(1)

    private val eventListenerExecutor = Executors.newSingleThreadExecutor {
        Thread(it, "$serviceName-events").apply { isDaemon = false }
    }

    private val retryExecutor = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "$serviceName-retry").apply { isDaemon = false }
    }

    private val schedulerExecutor = Executors.newScheduledThreadPool(
        System.getenv().getOrDefault("SCHEDULER_THREADS", "2").toInt()
    )

    init {
        require(serviceName.isNotBlank()) { "Service name must not be blank" }
        createGroup(REQUEST_STREAM, getServiceGroup())
    }

    fun getServiceName(): String = serviceName

    fun getConfigDirectory(): File = configDirectory

    public override fun resolveEventType(clazz: Class<out GatewayEvent>): String {
        return super.resolveEventType(clazz)
    }

    fun registerHandler(action: String, handler: ServiceRequestHandler) {
        require(action.isNotBlank()) { "Service action must not be blank" }

        val previous = handlers.putIfAbsent(action, handler)
        check(previous == null) { "Duplicate service action: $action" }

        logger.info("Registered service action: $serviceName.$action")
    }

    fun hasHandler(action: String): Boolean {
        return handlers.containsKey(action)
    }

    fun <T> readPayload(payloadJson: String, type: Class<T>): T {
        try {
            return mapper.readValue(payloadJson, type)
        } catch (e: Exception) {
            throw RuntimeException("Failed to deserialize service payload as ${type.name}", e)
        }
    }

    fun writeResponse(response: Any?): String {
        try {
            return mapper.writeValueAsString(response)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize service response", e)
        }
    }

    fun schedule(task: Runnable, delayMillis: Long): ScheduledFuture<*> {
        return schedulerExecutor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
    }

    fun scheduleAtFixedRate(
        task: Runnable,
        delayMillis: Long,
        periodMillis: Long
    ): ScheduledFuture<*> {
        return schedulerExecutor.scheduleAtFixedRate(
            task,
            delayMillis,
            periodMillis,
            TimeUnit.MILLISECONDS
        )
    }

    fun registerEventHandler(type: String, handler: ServiceEventHandler) {
        require(type.isNotBlank()) { "Event type must not be blank" }

        eventHandlers
            .computeIfAbsent(type) { CopyOnWriteArrayList() }
            .add(handler)

        logger.info("Registered global event handler: $type")
    }

    private fun startEventListener() {
        if (eventHandlers.isEmpty()) return
        subscribeEvents()
    }

    private fun subscribeEvents() {
        if (Thread.currentThread().isInterrupted) return

        eventListenerExecutor.submit {
            try {
                logger.info("Subscribing to Redis Pub/Sub channel '$EVENT_CHANNEL'...")

                subRedis.subscribe(object : JedisPubSub() {
                    override fun onMessage(channel: String?, message: String?) {
                        if (message == null) return

                        try {
                            val envelope = mapper.readValue(
                                message,
                                GatewayEventEnvelope::class.java
                            )

                            dispatchEvent(envelope.type(), envelope.payload())
                        } catch (e: Exception) {
                            logger.warning("Failed to handle event message: ${e.message}")
                        }
                    }

                    override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                        logger.info("Subscribed to channel: $channel")
                    }

                    override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                        logger.info("Unsubscribed from channel: $channel")
                    }
                }, EVENT_CHANNEL)

                logger.warning("Redis Pub/Sub subscription ended unexpectedly. Will retry.")
                retryExecutor.schedule(::subscribeEvents, 1, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warning("Error in Redis Pub/Sub subscriber: ${e.message}")
                retryExecutor.schedule(::subscribeEvents, 1, TimeUnit.SECONDS)
            }
        }
    }

    private fun dispatchEvent(type: String, payload: String) {
        val handlers = eventHandlers[type]
        if (handlers.isNullOrEmpty()) return

        for (handler in handlers) {
            try {
                handler.handle(payload).exceptionally { error ->
                    logger.warning("Global event handler failed [$type]: ${unwrap(error).message}")
                    null
                }
            } catch (e: Exception) {
                logger.warning("Global event handler failed [$type]: ${e.message}")
            }
        }
    }

    override fun start() {
        logger.info("Starting service runtime: $serviceName")
        startEventListener()
        super.start()
    }

    override fun shutdown() {
        logger.info("Stopping service runtime: $serviceName")
        super.shutdown()
        schedulerExecutor.shutdownNow()
        eventListenerExecutor.shutdownNow()
        retryExecutor.shutdownNow()
        shutdownLatch.countDown()
    }

    fun awaitShutdown() {
        shutdownLatch.await()
    }

    override fun handleEntry(entry: StreamEntry): CompletableFuture<Void?> {
        val fields = entry.fields

        if (fields["service"] != serviceName) {
            return CompletableFuture.completedFuture(null)
        }

        val requestId = fields["requestId"]
        val action = fields["action"]
        val payload = fields["payload"]
        val gatewayId = fields["gatewayId"]

        if (requestId == null || action == null || gatewayId == null) {
            logger.warning("Invalid service message: missing fields: $fields")
            return CompletableFuture.completedFuture(null)
        }

        return handleRequest(action, payload)
            .exceptionally { error ->
                logger.warning("Failed to handle request [$action]: ${unwrap(error).message}")
                error(unwrap(error).message)
            }
            .thenAccept { responseJson ->
                try {
                    redis.xadd(
                        getResponseStream(gatewayId),
                        XAddParams.xAddParams(),
                        mapOf(
                            "requestId" to requestId,
                            "response" to responseJson
                        )
                    )
                } catch (e: Exception) {
                    logger.warning("Failed to publish response for request $requestId: ${e.message}")
                }
            }
    }

    private fun handleRequest(
        action: String,
        payloadJson: String?
    ): CompletableFuture<String> {
        val handler = handlers[action]
            ?: return CompletableFuture.completedFuture(error("Unknown action: $action"))

        try {
            val future = handler.handle(payloadJson ?: "")
            return future
        } catch (e: Throwable) {
            return CompletableFuture.completedFuture(error(e.message))
        }
    }

    private fun error(message: String?): String {
        return try {
            mapper.writeValueAsString(
                mapOf(
                    "status" to "ERROR",
                    "message" to (message ?: "Unknown error")
                )
            )
        } catch (e: Exception) {
            logger.warning("Failed to serialize error: ${e.message}")
            """{"status":"ERROR","message":"${escapeJson(message)}"}"""
        }
    }

    private fun escapeJson(value: String?): String {
        return (value ?: "Unknown error")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun unwrap(error: Throwable): Throwable {
        return when {
            error is CompletionException && error.cause != null -> error.cause!!
            error is ExecutionException && error.cause != null -> error.cause!!
            else -> error
        }
    }

    override fun pollStream(): List<Map.Entry<String, List<StreamEntry>>>? {
        return redis.xreadGroup(
            getServiceGroup(),
            consumerName,
            XReadGroupParams.xReadGroupParams().block(5000).count(10),
            mapOf(REQUEST_STREAM to StreamEntryID.UNRECEIVED_ENTRY)
        )
    }

    override fun getServiceGroup(): String = serviceGroup

    companion object {
        @JvmStatic
        fun run(runtime: SpruceServiceRuntime) {
            try {
                runtime.start()
                Runtime.getRuntime().addShutdownHook(Thread(runtime::shutdown))
                runtime.awaitShutdown()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}