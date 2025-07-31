package org.spruce.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import org.spruce.api.gateway.GatewayCall
import org.spruce.api.event.GatewayEvent
import org.spruce.api.event.GatewayEventResolver
import org.spruce.api.gateway.SpruceGatewayClient
import org.spruce.proto.CallServiceRequest
import org.spruce.proto.CallServiceResponse
import org.spruce.proto.EmitEventRequest
import org.spruce.proto.EventStreamRequest
import org.spruce.proto.EventStreamResponse
import org.spruce.proto.GatewayGrpc
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.logging.Logger

class SpruceGatewayClientImpl(
    private val logger: Logger,
    private val host: String,
    private val port: Int,
    private val serverId: String
): GatewayEventResolver(), SpruceGatewayClient {

    private lateinit var channel: ManagedChannel
    private lateinit var stub: GatewayGrpc.GatewayStub
    private val connected = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val mapper = ObjectMapper().registerKotlinModule()

    private val eventTypeRegistry = ConcurrentHashMap<String, Class<out GatewayEvent>>()
    private val handlers = ConcurrentHashMap<Class<out GatewayEvent>, MutableList<Consumer<GatewayEvent>>>()

    @Synchronized
    fun reconnect() {
        try {
            if (connected.get()) {
                channel.shutdownNow()
                connected.set(false)
            }
            logger.info("Reconnecting to Spruce Gateway...")
            channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()
            stub = GatewayGrpc.newStub(channel)
            connected.set(true)
            startEventStream()
            logger.info("Reconnected to Spruce Gateway!")
        } catch (e: Exception) {
            logger.warning("Reconnect failed: ${e.message}")
        }
    }

    override fun connect() {
        if (connected.get()) return

        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()

        stub = GatewayGrpc.newStub(channel)
        connected.set(true)
        logger.info("Connected to Spruce Gateway at $host:$port")

        startEventStream()
    }

    override fun disconnect() {
        if (!connected.get()) return
        logger.info("Disconnecting from Gateway...")
        connected.set(false)
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        executor.shutdownNow()
    }

    override fun isConnected(): Boolean = connected.get()

    override fun <T> call(call: GatewayCall<T>): CompletableFuture<T> {
        val payloadJson = mapper.writeValueAsString(call.payload)
        val request = CallServiceRequest.newBuilder()
            .setService(call.service)
            .setAction(call.action)
            .setPayload(payloadJson)
            .build()

        val future = CompletableFuture<T>()
        stub.callService(request, object : StreamObserver<CallServiceResponse> {
            override fun onNext(value: CallServiceResponse) {
                try {
                    val result = mapper.readValue(value.result, call.responseType)
                    future.complete(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }

            override fun onError(t: Throwable) {
                future.completeExceptionally(t)
            }

            override fun onCompleted() {}
        })
        return future
    }


    override fun registerEventType(clazz: Class<out GatewayEvent>) {
        val type = resolveEventType(clazz)
        eventTypeRegistry[type] = clazz
        logger.info("Registered event type: $type -> ${clazz.simpleName}")
    }

    override fun <T : GatewayEvent> on(eventClass: Class<T>, handler: Consumer<T>) {
        if (!eventTypeRegistry.containsValue(eventClass)) {
            registerEventType(eventClass)
        }

        handlers.computeIfAbsent(eventClass) { mutableListOf() }
            .add { e -> handler.accept(eventClass.cast(e)) }
        logger.info("Subscribed handler for ${eventClass.simpleName}")
    }

    override fun emitGlobal(event: GatewayEvent) {
        val request = EmitEventRequest.newBuilder()
            .setType(resolveEventType(event.javaClass))
            .setPayload(mapper.writeValueAsString(event))
            .build()

        try {
            stub.emitEvent(request, object : StreamObserver<Empty> {
                override fun onNext(value: Empty) {}
                override fun onError(t: Throwable) {
                    logger.warning("Failed to emit global event: ${t.message}")
                }
                override fun onCompleted() {}
            })
        } catch (e: Exception) {
            logger.warning("Failed to emit global event: ${e.message}")
        }
    }

    private fun startEventStream() {
        val request = EventStreamRequest.newBuilder()
            .setServerId(serverId)
            .build()

        stub.eventStream(request, object : StreamObserver<EventStreamResponse> {
            override fun onNext(value: EventStreamResponse) {
                try {
                    val type = value.type
                    val clazz = eventTypeRegistry[type]
                    if (clazz != null) {
                        val event = mapper.readValue(value.payload, clazz)
                        dispatchEvent(event)
                    }
                } catch (e: Exception) {
                    logger.severe("Failed to process event: ${e.message}")
                }
            }

            override fun onError(t: Throwable) {
                logger.severe("Event stream error: ${t.message}")
                if (connected.get() && !executor.isShutdown) {
                    executor.schedule({
                        reconnect()
                    }, 2, TimeUnit.SECONDS)
                }
            }

            override fun onCompleted() {
                logger.info("Event stream closed by server.")
            }
        })
    }

    private fun dispatchEvent(event: GatewayEvent) {
        handlers[event::class.java]?.forEach { handler ->
            handler.accept(event)
        }
    }
}