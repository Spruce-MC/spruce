package org.spruce.api.lock;

/**
 * Represents an action executed while holding a distributed lock.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface LockSupplier<T> {

    /**
     * Executes the action.
     *
     * @return action result
     */
    T get();
}