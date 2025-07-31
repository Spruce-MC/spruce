package org.spruce.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.spruce.api.event.GatewayEvent;
import org.spruce.api.event.GatewayEventEnvelope;
import org.spruce.api.event.GatewayEventResolver;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.resps.StreamEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class providing Redis connectivity and event emission logic.
 * Extended by SpruceService to support core infrastructure functionality.
 */
public abstract class AbstractSpruceService extends GatewayEventResolver {

    public static final String GATEWAY_PREFIX = "gateway:";

    public static final String REQUEST_STREAM = GATEWAY_PREFIX + "requests";
    public static final String RESPONSE_STREAM = GATEWAY_PREFIX + "responses";

    public static final String EVENT_CHANNEL = GATEWAY_PREFIX + "events";

    public static final String SERVICE_GROUP = "service-group";


    protected final JedisPooled streamRedis;
    protected final Jedis pubSubRedis;
    protected final Logger logger;
    protected final ObjectMapper mapper;

    protected final BlockingQueue<StreamEntryID> ackQueue = new LinkedBlockingQueue<>();
    protected final ExecutorService ackExecutor = Executors.newSingleThreadExecutor();
    protected final ExecutorService workerPool = Executors.newFixedThreadPool(
            Integer.parseInt(System.getenv().getOrDefault("WORKER_THREADS", "8"))
    );

    public AbstractSpruceService(String redisUrl, Logger logger) {
        this.streamRedis = new JedisPooled(redisUrl);
        this.pubSubRedis = new Jedis(redisUrl);
        this.logger = logger;
        this.mapper = new ObjectMapper().registerModule(new KotlinModule.Builder().build());
    }

    /**
     * Must be implemented by subclass.
     * Called for every Redis Stream entry received.
     * Add entry ID to ackQueue manually when done.
     */
    protected abstract void handleEntry(StreamEntry entry);

    /**
     * Starts the acknowledgment loop in a separate thread.
     * Continuously pulls entry IDs from ackQueue and sends XACK to Redis.
     */
    public void start() {
        ackExecutor.submit(this::ackLoop);
    }

    /**
     * Submits all entries from a Redis stream to the worker thread pool.
     * If the pool is overloaded, the entry is dropped and a warning is logged.
     */
    protected void dispatchStream(Map.Entry<String, List<StreamEntry>> stream) {
        for (StreamEntry entry : stream.getValue()) {
            try {
                workerPool.submit(() -> safeHandle(entry));
            } catch (RejectedExecutionException e) {
                logger.warning("Worker pool overloaded. Entry dropped: " + entry.getID());
            }
        }
    }

    /**
     * Safely handles a single entry by delegating to handleEntry().
     * Any exception is caught and logged without crashing the worker.
     */
    protected void safeHandle(StreamEntry entry) {
        try {
            handleEntry(entry);
            ackQueue.add(entry.getID());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in worker", e);
        }
    }

    /**
     * Separate loop for acknowledging processed entries (XACK).
     * Pulls entry IDs from the ack queue and confirms them in Redis.
     */
    private void ackLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StreamEntryID id = ackQueue.poll(1, TimeUnit.SECONDS);
                if (id != null) {
                    streamRedis.xack(getAckStream(), getAckGroup(), id);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ack failed", e);
            }
        }
    }

    /**
     * Returns the Redis stream name for the response channel scoped to a specific gateway ID.
     */
    public String getResponseStream(String id) {
        return SpruceServiceBase.RESPONSE_STREAM + ":" + id;
    }

    /**
     * Ensures the Redis consumer group exists for the given stream.
     * If the group already exists (BUSYGROUP), it will be silently ignored.
     */
    public void createGroup(String stream, String group) {
        try {
            streamRedis.xgroupCreate(stream, group, StreamEntryID.LAST_ENTRY, true);
            logger.info("Created group " + group + " for " + stream);
        } catch (Exception e) {
            if (!e.getMessage().contains("BUSYGROUP")) {
                logger.warning("Group create error: " + e.getMessage());
            }
        }
    }

    /**
     * Publishes a GatewayEvent to the Redis Pub/Sub channel as a JSON envelope.
     * Uses reflection to resolve the event type string.
     */
    public void emit(GatewayEvent event) {
        try {
            String type = resolveEventType(event.getClass());
            String payload = mapper.writeValueAsString(event);
            emit(type, payload);
        } catch (Exception e) {
            logger.warning("Failed to emit event: " + e.getMessage());
        }
    }

    /**
     * Publishes a raw event with custom type and JSON payload to the event channel.
     */
    public void emit(String type, String payload) {
        try {
            streamRedis.publish(
                    SpruceServiceBase.EVENT_CHANNEL,
                    mapper.writeValueAsString(new GatewayEventEnvelope(type, payload))
            );
        } catch (Exception e) {
            logger.warning("Failed to emit event: " + e.getMessage());
        }
    }

    /**
     * Closes Redis connections and pools gracefully.
     */
    public void shutdown() {
        streamRedis.close();
        pubSubRedis.close();
        workerPool.shutdownNow();
        ackExecutor.shutdownNow();
    }

    protected String getAckStream() {
        return REQUEST_STREAM;
    }

    protected String getAckGroup() {
        return SERVICE_GROUP;
    }

    public JedisPooled getStreamRedis() {
        return streamRedis;
    }

    public Jedis getPubSubRedis() {
        return pubSubRedis;
    }
}

