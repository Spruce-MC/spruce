package org.spruce.api.event;

/**
 * Marker interface for all gateway events that can be published or consumed.
 * All events must implement this interface to be emitted.
 */
public interface GatewayEvent {

    static String resolveType(Class<? extends GatewayEvent> clazz) {
        GatewayEventType annotation = clazz.getAnnotation(GatewayEventType.class);
        if (annotation != null) {
            String type = annotation.value();
            if (!type.isEmpty()) {
                return type;
            }
        }

        return clazz.getSimpleName();
    }

    static String resolveType(GatewayEvent event) {
        return resolveType(event.getClass());
    }
}
