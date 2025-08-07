package org.spruce.api.service;

import redis.clients.jedis.*;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Base abstract service class for all Spruce microservices.
 * <p>
 * It:
 * - Connects to Redis Stream and Pub/Sub
 * - Handles incoming actions via @Action-annotated methods
 * - Sends responses back to gateway
 * - Emits events to all interested listeners
 * <p>
 * Extend this class to implement custom services (e.g. friends, stats, etc.).
 */
public abstract class SpruceServiceBase extends AbstractSpruceService {

    protected final String serviceName;
    protected final String consumerName;

    protected final Map<String, Method> handlers = new ConcurrentHashMap<>();

    protected final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public SpruceServiceBase(String serviceName) {
        this(serviceName, System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379"));
    }

    public SpruceServiceBase(String serviceName, String redisUrl) {
        super(redisUrl, Logger.getLogger(serviceName));
        this.serviceName = serviceName;
        this.consumerName = serviceName + "-" + UUID.randomUUID().toString().substring(0, 8);

        registerActions();
        createGroup(REQUEST_STREAM, SERVICE_GROUP);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        shutdownLatch.countDown();
    }

    /**
     * Starts the service processing:
     * - Consumes Redis Stream entries
     * - Handles each entry in a worker thread
     * - Acknowledges processed entries in a separate thread
     */
    @Override
    public void start() {
        logger.info("Starting service: " + serviceName);
        super.start();
    }

    /**
     * Handles a single stream entry:
     * - Parses fields
     * - Invokes the appropriate @Action method
     * - Sends response to the gateway response stream
     * - Queues the entry ID for XACK
     */
    @Override
    public void handleEntry(StreamEntry entry) {
        var fields = entry.getFields();
        if (!serviceName.equals(fields.get("service"))) return;

        String requestId = fields.get("requestId");
        String action = fields.get("action");
        String payload = fields.get("payload");
        String gatewayId = fields.get("gatewayId");

        if (requestId == null || action == null || gatewayId == null) {
            logger.warning("Invalid message: missing fields: " + fields);
            return;
        }

        String response = handleRequest(action, payload);

        streamRedis.xadd(
                getResponseStream(gatewayId),
                XAddParams.xAddParams(),
                Map.of(
                        "requestId", requestId,
                        "response", response
                )
        );
    }

    @Override
    protected List<Map.Entry<String, List<StreamEntry>>> pollStream() {
        return streamRedis.xreadGroup(
                SERVICE_GROUP,
                consumerName,
                XReadGroupParams.xReadGroupParams().block(5000).count(10),
                Map.of(REQUEST_STREAM, StreamEntryID.UNRECEIVED_ENTRY)
        );
    }

    protected String handleRequest(String action, String payloadJson) {
        try {
            Method method = handlers.get(action);
            if (method == null) {
                return error("Unknown action: " + action);
            }

            Class<?>[] params = method.getParameterTypes();
            Object result;
            if (params.length == 0) {
                result = method.invoke(this);
            } else {
                Object param = mapper.readValue(payloadJson, params[0]);
                result = method.invoke(this, param);
            }

            if (result instanceof CompletableFuture<?> future) {
                result = future.join();
            }

            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.warning("Failed to handle request [" + action + "]: " + e.getMessage());
            return error(e.getMessage());
        }
    }

    private String error(String message) {
        try {
            return mapper.writeValueAsString(Map.of("status", "ERROR", "message", message));
        } catch (Exception e) {
            logger.warning("Failed to serialize error: " + e.getMessage());
            return "ERROR";
        }
    }

    private void registerActions() {
        for (Method method : getClass().getDeclaredMethods()) {
            Action annotation = method.getAnnotation(Action.class);
            if (annotation != null) {
                if (handlers.containsKey(annotation.value())) {
                    throw new IllegalStateException("Duplicate action: " + annotation.value());
                }

                handlers.put(annotation.value(), method);
                logger.info("Registered action: " + annotation.value() + " → " + method.getName());
            }
        }

        for (Class<?> iface : getClass().getInterfaces()) {
            if (!iface.isAnnotationPresent(ServiceModel.class)) continue;

            for (Method ifaceMethod : iface.getDeclaredMethods()) {
                String action = ifaceMethod.getName();

                ServiceCall call = ifaceMethod.getAnnotation(ServiceCall.class);
                if (call != null) action = call.value();

                try {
                    Method implMethod = getClass().getMethod(ifaceMethod.getName(), ifaceMethod.getParameterTypes());
                    if (!handlers.containsKey(action)) {
                        handlers.put(action, implMethod);
                        logger.info("Registered service model action: " + action + " → " + implMethod.getName());
                    }
                } catch (NoSuchMethodException e) {
                    logger.warning("Missing implementation for service model method: " + ifaceMethod.getName());
                }
            }
        }
    }

    /**
     * Starts the service and waits for shutdown.
     * <p>
     * This is the recommended entrypoint for any service main() method.
     */
    public static void run(SpruceServiceBase service) {
        try {
            service.start();
            Runtime.getRuntime().addShutdownHook(new Thread(service::shutdown));
            service.shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Marks a method as a handler for a specific gateway action.
     * <p>
     * The method name does not matter; it is bound to the given action name.
     * Must be declared in subclasses of SpruceService.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Action {
        /**
         * Action name to bind this method to.
         */
        String value();
    }

}
