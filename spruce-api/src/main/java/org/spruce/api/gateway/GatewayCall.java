package org.spruce.api.gateway;

/**
 * Represents a call to a remote service via the Spruce Gateway.
 * Contains service name, method name, request payload and expected response class.
 */
public record GatewayCall<T>(String service, String action, Object payload, Class<T> responseType) {

    public static <T> GatewayCall<T> of(String service, String action, Object payload, Class<T> responseType) {
        return new GatewayCall<>(service, action, payload, responseType);
    }
}
