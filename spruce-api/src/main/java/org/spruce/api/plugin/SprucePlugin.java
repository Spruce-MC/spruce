package org.spruce.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the entry point of a Spruce plugin.
 * Used by the Spruce loader to detect and initialize plugins.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SprucePlugin {
    String name() default "";
    String version() default "";
    String author() default "";
}
