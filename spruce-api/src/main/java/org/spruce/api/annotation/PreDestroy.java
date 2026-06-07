package org.spruce.api.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to be called before the plugin is destroyed.
 * Useful for releasing resources.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreDestroy {
}
