package org.spruce.core.lock.gateway

import org.spruce.api.lock.DistributedLock
import org.spruce.core.SpruceGatewayClientImpl
import org.spruce.core.utils.GrpcFutureUtils
import org.spruce.proto.RefreshLockRequest
import org.spruce.proto.ReleaseLockRequest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class GatewayDistributedLock(
    private val gatewayClient: SpruceGatewayClientImpl,
    private val key: String,
    private val token: String,
    private val ttl: Duration
) : DistributedLock {

    private val released = AtomicBoolean(false)

    override fun getKey() = key
    override fun getToken() = token

    override fun release(): Boolean {
        if (!released.compareAndSet(false, true)) {
            return false
        }

        return gatewayClient.blockingStub.releaseLock(
            ReleaseLockRequest.newBuilder()
                .setKey(key)
                .setToken(token)
                .build()
        ).released
    }

    override fun releaseAsync(): CompletableFuture<Boolean> {
        if (!released.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false)
        }

        val request = ReleaseLockRequest.newBuilder()
            .setKey(key)
            .setToken(token)
            .build()

        return GrpcFutureUtils.toCompletableFuture(gatewayClient.futureStub.releaseLock(request))
            .thenApply { it.released }
    }

    override fun refresh(): Boolean {
        if (released.get()) {
            return false
        }

        return gatewayClient.blockingStub.refreshLock(
            RefreshLockRequest.newBuilder()
                .setKey(key)
                .setToken(token)
                .setTtlMillis(ttl.toMillis())
                .build()
        ).refreshed
    }

    override fun refreshAsync(): CompletableFuture<Boolean> {
        if (released.get()) {
            return CompletableFuture.completedFuture(false)
        }

        val request = RefreshLockRequest.newBuilder()
            .setKey(key)
            .setToken(token)
            .setTtlMillis(ttl.toMillis())
            .build()

        return GrpcFutureUtils.toCompletableFuture(gatewayClient.futureStub.refreshLock(request))
            .thenApply { it.refreshed }
    }
}