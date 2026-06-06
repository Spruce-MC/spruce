package org.spruce.core.lock.redis

import org.spruce.api.lock.DistributedLock
import org.spruce.api.lock.DistributedLockManager
import redis.clients.jedis.JedisPooled
import redis.clients.jedis.params.SetParams
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RedisDistributedLockManager(
    private val redis: JedisPooled,
    private val namespace: String = "spruce:lock",
    private val retryPolicy: LockRetryPolicy = LockRetryPolicy(),
    private val executor: ExecutorService = Executors.newCachedThreadPool()
) : DistributedLockManager {

    override fun acquire(
        key: String,
        ttl: Duration,
        acquireTimeout: Duration
    ): DistributedLock? {
        require(key.isNotBlank()) { "Lock key must not be blank" }
        require(!ttl.isZero && !ttl.isNegative) { "Lock ttl must be positive" }

        val redisKey = "$namespace:$key"
        val token = UUID.randomUUID().toString()

        val wait = !acquireTimeout.isZero && !acquireTimeout.isNegative
        val deadline = if (wait) {
            System.nanoTime() + acquireTimeout.toNanos()
        } else {
            0L
        }

        var attempt = 0

        while (true) {
            val result = redis.set(
                redisKey,
                token,
                SetParams.setParams()
                    .nx()
                    .px(ttl.toMillis())
            )

            if (result == "OK") {
                return RedisDistributedLock(
                    redis = redis,
                    key = redisKey,
                    token = token,
                    ttl = ttl,
                    executor = executor
                )
            }

            if (!wait) {
                return null
            }

            val now = System.nanoTime()
            if (now >= deadline) {
                return null
            }

            val delayMillis = retryPolicy
                .nextDelay(attempt++)
                .toMillis()
                .coerceAtMost(Duration.ofNanos(deadline - now).toMillis())
                .coerceAtLeast(1)

            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
    }

    override fun acquireAsync(
        key: String,
        ttl: Duration,
        acquireTimeout: Duration
    ): CompletableFuture<DistributedLock?> {
        return CompletableFuture.supplyAsync(
            { acquire(key, ttl, acquireTimeout) },
            executor
        )
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}