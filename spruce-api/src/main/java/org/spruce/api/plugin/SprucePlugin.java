package org.spruce.api.plugin;

import java.lang.annotation.*;

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

    /**
     * Required plugin dependencies.
     * <p>
     * If any dependency is missing, this plugin will not be loaded.
     *
     * @return required plugin names
     */
    String[] depend() default {};

    /**
     * Optional plugin dependencies.
     * <p>
     * If a soft dependency is present, this plugin will be loaded after it.
     * If it is missing, this plugin can still be loaded.
     *
     * @return optional plugin names
     */
    String[] softDepend() default {};
}