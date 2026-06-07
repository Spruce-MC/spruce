package org.spruce.api.annotation;

import java.lang.annotation.*;

/**
 * Ensures that only a single execution of the annotated method
 * can occur simultaneously across the entire Spruce network
 * for the resolved lock key.
 * <p>
 * Before invoking the method, Spruce attempts to acquire
 * a distributed lock from the gateway. If acquisition succeeds,
 * the method is executed and the lock is automatically released
 * afterwards.
 * <p>
 * Lock keys may reference method parameters using placeholder syntax:
 *
 * <pre>{@code
 * @WithDistributedLock(
 *     value = "guild:{guildId}",
 *     ttl = "5s"
 * )
 * public void upgradeGuild(long guildId) {
 *     ...
 * }
 * }</pre>
 *
 * The example above resolves the lock key to:
 *
 * <pre>{@code
 * guild:123
 * }</pre>
 *
 * when the method is called with {@code guildId = 123}.
 * <p>
 * For methods returning {@link java.util.concurrent.CompletableFuture},
 * Spruce automatically uses the asynchronous lock API.
 * For all other methods, the synchronous lock API is used.
 * <p>
 * This annotation is intended for protecting critical operations
 * such as guild upgrades, economy transfers, reward claiming,
 * auction purchases, and other actions that must not run concurrently
 * on multiple servers.
 * <p>
 * Distributed locks prevent race conditions between servers,
 * but do not replace database transactions.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface WithDistributedLock {

    /**
     * Lock key template.
     * <p>
     * Method parameters may be referenced using
     * {@code {parameterName}} placeholders.
     *
     * @return lock key template
     */
    String value();

    /**
     * Lock time-to-live.
     * <p>
     * Supported formats:
     *
     * <pre>{@code
     * 500ms
     * 5s
     * 1m
     * 1h
     * }</pre>
     *
     * @return lock TTL
     */
    String ttl();

    /**
     * Maximum time to wait for lock acquisition.
     * <p>
     * If the lock cannot be acquired within this period,
     * a {@link org.spruce.api.lock.LockAcquireException}
     * is thrown.
     *
     * @return acquisition timeout
     */
    String acquireTimeout() default "0s";
}