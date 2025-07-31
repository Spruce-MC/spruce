package org.spruce.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be run on a schedule.
 * Can be used to define repeated tasks within the plugin lifecycle.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {
    long delay() default 0;
    long period() default -1;
    boolean async() default false;
}
