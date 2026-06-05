package org.spruce.api.lock;

/**
 * Thrown when a distributed lock cannot be acquired.
 */
public class LockAcquireException extends RuntimeException {

    private final String key;

    /**
     * Creates a new exception instance.
     *
     * @param key lock key
     */
    public LockAcquireException(String key) {
        super("Failed to acquire distributed lock: " + key);
        this.key = key;
    }

    /**
     * Returns the lock key that failed to be acquired.
     *
     * @return lock key
     */
    public String getKey() {
        return key;
    }
}