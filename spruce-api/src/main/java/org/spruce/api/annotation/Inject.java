package org.spruce.api.annotation;

import java.lang.annotation.*;

/**
 * Marks a field for automatic dependency injection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Inject {
}
