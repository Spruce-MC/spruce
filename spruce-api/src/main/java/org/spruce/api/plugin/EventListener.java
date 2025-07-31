package org.spruce.api.plugin;

import java.lang.annotation.*;

/**
 * Marks a method as an event listener for Bukkit/Velocity events.
 * The method must accept one event parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventListener {
}
