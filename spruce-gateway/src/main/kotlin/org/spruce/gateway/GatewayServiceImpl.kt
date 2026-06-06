package org.spruce.gateway

import com.google.protobuf.Empty
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import org.spruce.api.lock.DistributedLock
import org.spruce.core.lock.redis.RedisDistributedLockManager
import org.spruce.proto.*
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class GatewayServiceImpl(
    private val redis: GatewayRedisBridge,
    private val lockManager: RedisDistributedLockManager
) : GatewayGrpc.GatewayImplBase() {

    private val logger = Logger.getLogger("GatewayService")
    private val eventStreams = CopyOnWriteArrayList<ServerCallStreamObserver<EventStreamResponse>>()

    private val activeLocks = ConcurrentHashMap<String, DistributedLock>()

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

        redis.sendRequest(
            requestId,
            request.service,
            request.action,
            request.payload,
            { response ->
                if (!cancelled.get()) {
                    responseObserver.onNext(
                        CallServiceResponse.newBuilder()
                            .setResult(response)
                            .build()
                    )
                    responseObserver.onCompleted()
                }
            },
            { error ->
                if (!cancelled.get()) {
                    responseObserver.onError(error)
                }
            }
        )
    }

    override fun eventStream(
        request: EventStreamRequest,
        responseObserver: StreamObserver<EventStreamResponse>
    ) {
        val serverObserver = responseObserver as ServerCallStreamObserver<EventStreamResponse>
        eventStreams.add(serverObserver)

        logger.info(
            "Client subscribed to event stream (serverId=${request.serverId}), total=${eventStreams.size}"
        )

        serverObserver.setOnCancelHandler {
            eventStreams.remove(serverObserver)
            logger.info(
                "Client disconnected from event stream (serverId=${request.serverId}), remaining=${eventStreams.size}"
            )
        }
    }

    override fun acquireLock(
        request: AcquireLockRequest,
        responseObserver: StreamObserver<AcquireLockResponse>
    ) {
        try {
            val lock = lockManager.acquire(
                request.key,
                Duration.ofMillis(request.ttlMillis),
                Duration.ofMillis(request.waitMillis)
            )

            if (lock != null) {
                activeLocks[lock.token] = lock
            }

            responseObserver.onNext(
                AcquireLockResponse.newBuilder()
                    .setAcquired(lock != null)
                    .setToken(lock?.token ?: "")
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun releaseLock(
        request: ReleaseLockRequest,
        responseObserver: StreamObserver<ReleaseLockResponse>
    ) {
        try {
            val lock = activeLocks.remove(request.token)
            val released = lock?.release() ?: false

            responseObserver.onNext(
                ReleaseLockResponse.newBuilder()
                    .setReleased(released)
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
        }
    }

    override fun refreshLock(
        request: RefreshLockRequest,
        responseObserver: StreamObserver<RefreshLockResponse>
    ) {
        try {
            val lock = activeLocks[request.token]
            val refreshed = lock?.refresh() ?: false

            if (!refreshed) {
                activeLocks.remove(request.token)
            }

            responseObserver.onNext(
                RefreshLockResponse.newBuilder()
                    .setRefreshed(refreshed)
                    .build()
            )
            responseObserver.onCompleted()
        } catch (e: Exception) {
            responseObserver.onError(e)
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

    fun shutdown() {
        activeLocks.values.forEach { lock ->
            try {
                lock.release()
            } catch (e: Exception) {
                logger.warning("Failed to release lock ${lock.key}: ${e.message}")
            }
        }

        activeLocks.clear()
    }
}