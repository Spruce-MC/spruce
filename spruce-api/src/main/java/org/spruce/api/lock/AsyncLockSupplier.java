package org.spruce.api.lock;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an asynchronous action executed while holding a distributed lock.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface AsyncLockSupplier<T> {

    /**
     * Executes the asynchronous action.
     *
     * @return future completed with the action result
     */
    CompletableFuture<T> get();
}