package org.spruce.core.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.spruce.api.event.GatewayEvent
import org.spruce.api.event.GatewayEventEnvelope
import org.spruce.api.event.GatewayEventResolver
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.resps.StreamEntry
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger

abstract class AbstractSpruceService(
    redisUrl: String,
    protected val logger: Logger
) : GatewayEventResolver() {

    companion object {
        const val GATEWAY_PREFIX = "gateway:"
        const val REQUEST_STREAM = "${GATEWAY_PREFIX}requests"
        const val RESPONSE_STREAM = "${GATEWAY_PREFIX}responses"
        const val EVENT_CHANNEL = "${GATEWAY_PREFIX}events"
        const val SERVICE_GROUP = "service-group"
    }

    val redis = JedisPooled(redisUrl)
    val subRedis = Jedis(redisUrl)

    protected val mapper: ObjectMapper =
        ObjectMapper().registerModule(KotlinModule.Builder().build())

    private val ackQueue = LinkedBlockingQueue<StreamEntryID>()

    private val ackExecutor = Executors.newSingleThreadExecutor {
        Thread(it, "spruce-service-ack").apply { isDaemon = false }
    }

    private val streamExecutor = Executors.newSingleThreadExecutor {
        Thread(it, "spruce-service-stream").apply { isDaemon = false }
    }

    protected val workerPool: ExecutorService = Executors.newFixedThreadPool(
        System.getenv().getOrDefault("WORKER_THREADS", "8").toInt()
    )

    protected abstract fun handleEntry(entry: StreamEntry): CompletableFuture<Void?>

    protected abstract fun pollStream(): List<Map.Entry<String, List<StreamEntry>>>?

    open fun start() {
        ackExecutor.submit(::ackLoop)
        streamExecutor.submit(::consumeLoop)
    }

    protected fun dispatchStream(stream: Map.Entry<String, List<StreamEntry>>) {
        for (entry in stream.value) {
            try {
                workerPool.submit { safeHandle(entry) }
            } catch (e: RejectedExecutionException) {
                logger.warning("Worker pool overloaded. Entry dropped: ${entry.id}")
            }
        }
    }

    private fun safeHandle(entry: StreamEntry) {
        try {
            handleEntry(entry).whenComplete { _, error ->
                if (error != null) {
                    logger.log(Level.WARNING, "Error in worker", unwrap(error))
                    return@whenComplete
                }

                ackQueue.add(entry.id)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error in worker", e)
        }
    }

    private fun ackLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val id = ackQueue.poll(1, TimeUnit.SECONDS)
                if (id != null) {
                    redis.xack(getAckStream(), getAckGroup(), id)
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Ack failed", e)
            }
        }
    }

    private fun consumeLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                pollStream()?.forEach(::dispatchStream)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error in consumeLoop", e)

                if (e.message?.contains("NOGROUP") == true) {
                    createGroup(REQUEST_STREAM, getServiceGroup())
                }
            }
        }
    }

    fun getResponseStream(id: String): String {
        return "$RESPONSE_STREAM:$id"
    }

    fun createGroup(stream: String, group: String) {
        try {
            redis.xgroupCreate(stream, group, StreamEntryID.LAST_ENTRY, true)
            logger.info("Created group $group for $stream")
        } catch (e: Exception) {
            if (e.message?.contains("BUSYGROUP") != true) {
                logger.warning("Group create error: ${e.message}")
            }
        }
    }

    fun emit(event: GatewayEvent) {
        try {
            val type = resolveEventType(event.javaClass)
            val payload = mapper.writeValueAsString(event)
            emit(type, payload)
        } catch (e: Exception) {
            logger.warning("Failed to emit event: ${e.message}")
        }
    }

    fun emit(type: String, payload: String) {
        try {
            redis.publish(
                EVENT_CHANNEL,
                mapper.writeValueAsString(GatewayEventEnvelope(type, payload))
            )
        } catch (e: Exception) {
            logger.warning("Failed to emit event: ${e.message}")
        }
    }

    open fun shutdown() {
        redis.close()
        subRedis.close()
        workerPool.shutdownNow()
        ackExecutor.shutdownNow()
        streamExecutor.shutdownNow()
    }

    protected open fun getAckStream(): String = REQUEST_STREAM

    protected open fun getAckGroup(): String = getServiceGroup()

    protected open fun getServiceGroup(): String = SERVICE_GROUP

    private fun unwrap(error: Throwable): Throwable {
        return when {
            error is CompletionException && error.cause != null -> error.cause!!
            error is ExecutionException && error.cause != null -> error.cause!!
            else -> error
        }
    }
}