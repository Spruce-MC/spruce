package org.spruce.core.lock

import org.spruce.api.lock.DistributedLock
import org.spruce.api.lock.DistributedLockManager
import org.spruce.core.SpruceGatewayClientImpl
import org.spruce.core.utils.GrpcFutureUtils
import org.spruce.proto.AcquireLockRequest
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

class GatewayDistributedLockManager(
    private val gatewayClient: SpruceGatewayClientImpl,
    private val ownerId: String = UUID.randomUUID().toString()
) : DistributedLockManager {

    override fun acquire(
        key: String,
        ttl: Duration,
        acquireTimeout: Duration
    ): DistributedLock? {
        validate(key, ttl, acquireTimeout)

        val response = gatewayClient.blockingStub.acquireLock(
            AcquireLockRequest.newBuilder()
                .setKey(key)
                .setOwnerId(ownerId)
                .setTtlMillis(ttl.toMillis())
                .setWaitMillis(acquireTimeout.toMillis())
                .build()
        )

        if (!response.acquired) {
            return null
        }

        return GatewayDistributedLock(
            gatewayClient = gatewayClient,
            key = key,
            token = response.token,
            ttl = ttl
        )
    }

    override fun acquireAsync(
        key: String,
        ttl: Duration,
        acquireTimeout: Duration
    ): CompletableFuture<DistributedLock> {
        validate(key, ttl, acquireTimeout)

        val request = AcquireLockRequest.newBuilder()
            .setKey(key)
            .setOwnerId(ownerId)
            .setTtlMillis(ttl.toMillis())
            .setWaitMillis(acquireTimeout.toMillis())
            .build()

        return GrpcFutureUtils.toCompletableFuture(gatewayClient.futureStub.acquireLock(request))
            .thenApply { response ->
                if (!response.acquired) {
                    null
                } else {
                    GatewayDistributedLock(
                        gatewayClient = gatewayClient,
                        key = key,
                        token = response.token,
                        ttl = ttl
                    )
                }
            }
    }

    private fun validate(
        key: String,
        ttl: Duration,
        acquireTimeout: Duration
    ) {
        require(key.isNotBlank()) {
            "Lock key must not be blank"
        }

        require(!ttl.isZero && !ttl.isNegative) {
            "Lock ttl must be positive"
        }

        require(!acquireTimeout.isNegative) {
            "Lock acquire timeout must not be negative"
        }
    }
}