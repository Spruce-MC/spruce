package org.spruce.loader.commons.gateway

import org.spruce.api.gateway.SpruceGatewayClient
import org.spruce.api.plugin.SpruceContext
import org.spruce.core.SpruceGatewayClientImpl
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class SpruceGatewayManager(
    private val logger: Logger,
    context: SpruceContext,
    private val config: GatewayConfig
) {
    private val scheduler = Executors.newScheduledThreadPool(1)
    private var gatewayClient = SpruceGatewayClientImpl(logger, config.host, config.port, config.serverId)

    init {
        context.register(SpruceGatewayClient::class.java, gatewayClient)
    }

    fun start() {
        if (config.enabled) {
            scheduler.run { connectWithRetry() }
        }
    }

    private fun connectWithRetry() {
        try {
            gatewayClient.connect()
            logger.info("Connected to Gateway successfully!")
        } catch (e: Exception) {
            scheduler.schedule({ connectWithRetry() }, 5, TimeUnit.SECONDS)
        }
    }

    fun stop() {
        logger.info("Stopping Gateway Manager...")
        gatewayClient.disconnect()
        scheduler.shutdownNow()
    }
}
