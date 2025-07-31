package org.spruce.api.plugin;

import java.lang.annotation.*;

/**
 * Marks a method to be called after the bean is constructed and injected.
 * Similar to Spring's @PostConstruct.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostConstruct {
}
