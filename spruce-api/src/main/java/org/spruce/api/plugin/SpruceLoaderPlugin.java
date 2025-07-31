package org.spruce.api.plugin;

/**
 * Marker interface for the main class of a Spruce Loader plugin.
 * <p>
 * This is used to register the plugin instance in the context,
 * allowing scheduled tasks, event listeners, and other systems
 * to access the plugin for task scheduling, command registration, etc.
 */
public interface SpruceLoaderPlugin {
}
