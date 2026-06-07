package org.spruce.loader.plugins.gateway

import org.spruce.api.gateway.SpruceGatewayClient
import org.spruce.api.lock.DistributedLockManager
import org.spruce.api.context.SpruceContext
import org.spruce.core.SpruceGatewayClientImpl
import org.spruce.core.lock.gateway.GatewayDistributedLockManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class SpruceGatewayManager(
    private val logger: Logger,
    context: SpruceContext,
    private val config: GatewayConfig
) {
    private val scheduler = Executors.newScheduledThreadPool(1)

    private val gatewayClient = SpruceGatewayClientImpl(logger, config.host, config.port, config.serverId)
    private val lockManager = GatewayDistributedLockManager(gatewayClient)

    init {
        context.register(SpruceGatewayClient::class.java, gatewayClient)
        context.register(DistributedLockManager::class.java, lockManager)
    }

    fun start() {
        if (config.enabled) {
            scheduler.execute {
                connectWithRetry()
            }
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