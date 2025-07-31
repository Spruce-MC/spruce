package org.spruce.api.plugin;

/**
 * Context object passed to plugins on load.
 * Used to access the server, plugin instance, gateway client, and other services.
 */
public interface SpruceContext {

    <T> T get(Class<T> type);

    <T> void register(Class<T> type, T instance);
}
