package org.spruce.api.lock;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Provides access to distributed locks managed by the Spruce Gateway.
 * <p>
 * Implementations coordinate lock ownership across all servers and services
 * connected to the network.
 * <p>
 * Distributed lock operations may perform network I/O. Avoid calling blocking
 * methods on performance-critical threads. Use asynchronous methods when
 * working from a Minecraft main thread or another latency-sensitive context.
 */
public interface DistributedLockManager {

    /**
     * Attempts to acquire a distributed lock.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param acquireTimeout maximum time to wait for acquisition
     * @return acquired lock or {@code null} if acquisition failed
     */
    DistributedLock acquire(String key, Duration ttl, Duration acquireTimeout);

    /**
     * Attempts to acquire a distributed lock without waiting.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @return acquired lock or {@code null} if acquisition failed
     */
    default DistributedLock acquire(String key, Duration ttl) {
        return acquire(key, ttl, Duration.ZERO);
    }

    /**
     * Attempts to acquire a distributed lock asynchronously.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param acquireTimeout maximum time to wait for acquisition
     * @return future containing acquired lock or {@code null} if acquisition failed
     */
    CompletableFuture<DistributedLock> acquireAsync(String key, Duration ttl, Duration acquireTimeout);

    /**
     * Attempts to acquire a distributed lock asynchronously without waiting.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @return future containing acquired lock or {@code null} if acquisition failed
     */
    default CompletableFuture<DistributedLock> acquireAsync(String key, Duration ttl) {
        return acquireAsync(key, ttl, Duration.ZERO);
    }

    /**
     * Executes the supplied action while holding a distributed lock.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param acquireTimeout maximum time to wait for acquisition
     * @param supplier action to execute
     * @return supplier result
     * @throws LockAcquireException if the lock cannot be acquired
     */
    default <T> T withLock(
            String key,
            Duration ttl,
            Duration acquireTimeout,
            LockSupplier<T> supplier
    ) {
        DistributedLock lock = acquire(key, ttl, acquireTimeout);

        if (lock == null) {
            throw new LockAcquireException(key);
        }

        try (lock) {
            return supplier.get();
        }
    }

    /**
     * Executes the supplied action while holding a distributed lock without waiting.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param supplier action to execute
     * @return supplier result
     * @throws LockAcquireException if the lock cannot be acquired
     */
    default <T> T withLock(String key, Duration ttl, LockSupplier<T> supplier) {
        return withLock(key, ttl, Duration.ZERO, supplier);
    }

    /**
     * Executes the supplied asynchronous action while holding a distributed lock.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param acquireTimeout maximum time to wait for acquisition
     * @param supplier asynchronous action to execute
     * @return future completed with the action result
     */
    default <T> CompletableFuture<T> withLockAsync(
            String key,
            Duration ttl,
            Duration acquireTimeout,
            AsyncLockSupplier<T> supplier
    ) {
        return acquireAsync(key, ttl, acquireTimeout).thenCompose(lock -> {
            if (lock == null) {
                return CompletableFuture.failedFuture(new LockAcquireException(key));
            }

            CompletableFuture<T> actionFuture;

            try {
                actionFuture = supplier.get();
            } catch (Throwable throwable) {
                return lock.releaseAsync()
                        .handle((ignored, releaseError) -> null)
                        .thenCompose(ignored -> CompletableFuture.failedFuture(throwable));
            }

            return actionFuture.handle((result, actionError) ->
                    lock.releaseAsync().handle((released, releaseError) -> {
                        if (actionError != null) {
                            if (releaseError != null) {
                                actionError.addSuppressed(releaseError);
                            }

                            throw new CompletionException(actionError);
                        }

                        if (releaseError != null) {
                            throw new CompletionException(releaseError);
                        }

                        return result;
                    })
            ).thenCompose(future -> future);
        });
    }

    /**
     * Executes the supplied asynchronous action while holding a distributed lock without waiting.
     *
     * @param key lock key
     * @param ttl lock lifetime
     * @param supplier asynchronous action to execute
     * @return future completed with the action result
     */
    default <T> CompletableFuture<T> withLockAsync(
            String key,
            Duration ttl,
            AsyncLockSupplier<T> supplier
    ) {
        return withLockAsync(key, ttl, Duration.ZERO, supplier);
    }
}