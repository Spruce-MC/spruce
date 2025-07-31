package org.spruce.api.plugin;

import java.lang.annotation.*;

/**
 * Marks a class as a Bean (managed object) to be instantiated and injected by the plugin framework.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Bean {
}
