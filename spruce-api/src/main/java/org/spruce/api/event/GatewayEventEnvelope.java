package org.spruce.api.event;

/**
 * Wrapper for serialized events published to the Redis event channel.
 */
public record GatewayEventEnvelope(String type, String payload) {}