package org.spruce.core.lock.redis

import org.spruce.api.lock.DistributedLock
import redis.clients.jedis.JedisPooled
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class RedisDistributedLock(
    private val redis: JedisPooled,
    private val key: String,
    private val token: String,
    private val ttl: Duration,
    private val executor: ExecutorService
) : DistributedLock {

    override fun getKey(): String = key

    override fun getToken(): String = token

    override fun release(): Boolean {
        val result = redis.eval(
            RELEASE_SCRIPT,
            listOf(key),
            listOf(token)
        )

        return result == 1L
    }

    override fun releaseAsync(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({ release() }, executor)
    }

    override fun refresh(): Boolean {
        val result = redis.eval(
            REFRESH_SCRIPT,
            listOf(key),
            listOf(token, ttl.toMillis().toString())
        )

        return result == 1L
    }

    override fun refreshAsync(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({ refresh() }, executor)
    }

    companion object {
        private const val RELEASE_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
        """

        private const val REFRESH_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("pexpire", KEYS[1], ARGV[2])
            else
                return 0
            end
        """
    }
}