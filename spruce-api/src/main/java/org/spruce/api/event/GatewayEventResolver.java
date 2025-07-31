package org.spruce.api.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves event types and mappings between event classes and their string identifiers.
 * Used for serializing and deserializing events in a generic way across the system.
 * <p>
 * Typically extended by core classes.
 */
public class GatewayEventResolver {

    private final Map<Class<?>, String> EVENT_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolves the event type string for a given event class.
     * Must be consistent with what consumers expect.
     */
    protected String resolveEventType(Class<? extends GatewayEvent> clazz) {
        return EVENT_TYPE_CACHE.computeIfAbsent(clazz, c -> GatewayEvent.resolveType(clazz));
    }
}
