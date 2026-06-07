package org.spruce.api.annotation;

import java.lang.annotation.*;

/**
 * Marks a class that provides bean definitions.
 * Similar to Spring's @Configuration annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Configuration {
}