package org.spruce.api.gateway;

import org.spruce.api.event.GatewayEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface used by plugins to call backend services through the Spruce Gateway.
 * Typically injected into plugins using DI.
 */
public interface SpruceGatewayClient {

    void connect();

    void disconnect();

    boolean isConnected();

    <T> CompletableFuture<T> call(GatewayCall<T> call);

    void registerEventType(Class<? extends GatewayEvent> clazz);

    <T extends GatewayEvent> void on(Class<T> eventClass, Consumer<T> handler);

    void emitGlobal(GatewayEvent event);
}
