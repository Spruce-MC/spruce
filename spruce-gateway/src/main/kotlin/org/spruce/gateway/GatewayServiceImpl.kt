package org.spruce.gateway

import com.google.protobuf.Empty
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import org.spruce.proto.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class GatewayServiceImpl(
    private val redis: GatewayRedisBridge
) : GatewayGrpc.GatewayImplBase() {

    private val logger = Logger.getLogger("GatewayService")
    private val eventStreams = CopyOnWriteArrayList<ServerCallStreamObserver<EventStreamResponse>>()

    override fun emitEvent(
        request: EmitEventRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        redis.emit(request.type, request.payload)
        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun callService(
        request: CallServiceRequest,
        responseObserver: StreamObserver<CallServiceResponse>
    ) {
        val requestId = UUID.randomUUID().toString()
        val serverObserver = responseObserver as? ServerCallStreamObserver<CallServiceResponse>
        val cancelled = AtomicBoolean(false)

        serverObserver?.setOnCancelHandler {
            cancelled.set(true)
        }

        redis.sendRequest(requestId, request.service, request.action, request.payload, { response ->
            if (!cancelled.get()) {
                responseObserver.onNext(
                    CallServiceResponse.newBuilder().setResult(response).build()
                )
                responseObserver.onCompleted()
            }
        }, { error ->
            if (!cancelled.get()) {
                responseObserver.onError(error)
            }
        })
    }

    override fun eventStream(
        request: EventStreamRequest,
        responseObserver: StreamObserver<EventStreamResponse>
    ) {
        val serverObserver = responseObserver as ServerCallStreamObserver<EventStreamResponse>
        eventStreams.add(serverObserver)
        logger.info("Client subscribed to event stream (serverId=${request.serverId}), total=${eventStreams.size}")

        serverObserver.setOnCancelHandler {
            eventStreams.remove(serverObserver)
            logger.info("Client disconnected from event stream (serverId=${request.serverId}), remaining=${eventStreams.size}")
        }
    }

    fun broadcastEvent(type: String, payload: String) {
        val response = EventStreamResponse.newBuilder()
            .setType(type)
            .setPayload(payload)
            .build()

        eventStreams.removeIf { observer ->
            try {
                observer.onNext(response)
                false
            } catch (e: Exception) {
                logger.warning("Failed to send event, removing observer: ${e.message}")
                true
            }
        }
    }
}
