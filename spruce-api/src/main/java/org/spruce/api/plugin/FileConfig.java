package org.spruce.api.plugin;

import java.lang.annotation.*;

/**
 * Marks a class to be loaded from a YAML configuration file.
 * The configuration file is specified by the annotation value.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FileConfig {
    String value();
}
