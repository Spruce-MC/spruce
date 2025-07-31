package org.spruce.api.plugin;

import java.lang.annotation.*;

/**
 * Marks a method as a command handler for a plugin.
 * Will be automatically registered by the plugin loader.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Command {
    String value();
    String[] aliases() default {};
    String description() default "";
}
