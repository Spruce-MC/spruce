package org.spruce.api.service;

import java.lang.annotation.*;

/**
 * Marks the entry point of a Spruce service.
 * <p>
 * Service classes are discovered by the Spruce service processor
 * and started by the Spruce service loader.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpruceService {

    /**
     * Service name used by the gateway routing layer.
     *
     * @return service name
     */
    String value();
}