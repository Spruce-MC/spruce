package org.spruce.api.lock;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an acquired distributed lock.
 * <p>
 * A distributed lock guarantees that only a single node in the network
 * can hold ownership of the same lock key at a given time.
 * <p>
 * Instances are typically obtained through {@link DistributedLockManager}.
 */
public interface DistributedLock extends AutoCloseable {

    /**
     * Returns the lock key.
     *
     * @return lock key
     */
    String getKey();

    /**
     * Returns the unique lock token assigned by the gateway.
     *
     * @return lock token
     */
    String getToken();

    /**
     * Releases this lock.
     *
     * @return {@code true} if the lock was successfully released
     */
    boolean release();

    /**
     * Releases this lock asynchronously.
     *
     * @return future containing {@code true} if the lock was successfully released
     */
    CompletableFuture<Boolean> releaseAsync();

    /**
     * Extends the lock lifetime using the original TTL.
     *
     * @return {@code true} if the lock was successfully refreshed
     */
    boolean refresh();

    /**
     * Extends the lock lifetime asynchronously using the original TTL.
     *
     * @return future containing {@code true} if the lock was successfully refreshed
     */
    CompletableFuture<Boolean> refreshAsync();

    /**
     * Equivalent to {@link #release()}.
     */
    @Override
    default void close() {
        release();
    }
}