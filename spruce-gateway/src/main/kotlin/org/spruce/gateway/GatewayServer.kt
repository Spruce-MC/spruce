package org.spruce.gateway

import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object GatewayServer {

    private val logger = Logger.getLogger("SpruceGateway")

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val id = System.getenv("GATEWAY_ID") ?: "default"
        val port = System.getenv("GATEWAY_PORT")?.toIntOrNull() ?: 6565
        val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"

        val redis = GatewayRedisBridge(redisUrl, id, logger)
        val service = GatewayServiceImpl(redis)

        val server: Server = ServerBuilder.forPort(port)
            .addService(service)
            .build()

        logger.info("Starting SpruceGateway on port $port...")
        server.start()
        logger.info("SpruceGateway started successfully!")

        // Start Redis listeners
        redis.startEventListener { eventType, payload ->
            service.broadcastEvent(eventType, payload)
        }
        redis.startResponseListener()

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown requested. Closing resources...")
            redis.shutdown()
            server.shutdown()
            server.awaitTermination(5, TimeUnit.SECONDS)
            logger.info("SpruceGateway stopped.")
        })

        server.awaitTermination()
    }
}
