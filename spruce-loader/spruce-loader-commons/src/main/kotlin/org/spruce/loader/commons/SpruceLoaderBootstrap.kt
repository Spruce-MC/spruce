package org.spruce.loader.commons

import org.spruce.core.SpruceContextImpl
import org.spruce.loader.commons.gateway.GatewayConfig
import org.spruce.loader.commons.gateway.SpruceGatewayManager
import java.io.File
import java.util.logging.Logger

class SpruceLoaderBootstrap(
    private val logger: Logger,
    private val dataFolder: File,
    private val context: SpruceContextImpl,
    private val platformConfigFile: String = "config.yml"
) {
    private lateinit var gatewayManager: SpruceGatewayManager
    private lateinit var pluginLoader: SprucePluginLoader
    private lateinit var lifecycle: SpruceLifecycleManager

    fun enable() {
        logger.info("Spruce booting...")

        if (!dataFolder.exists()) dataFolder.mkdirs()
        val configFile = File(dataFolder, platformConfigFile)

        if (!configFile.exists()) {
            val resource = javaClass.classLoader.getResourceAsStream(platformConfigFile)
            if (resource != null) {
                configFile.outputStream().use { out -> resource.copyTo(out) }
                logger.info("Default config copied to ${configFile.absolutePath}")
            } else {
                logger.warning("Default config '$platformConfigFile' not found in resources.")
            }
        }

        val config = GatewayConfig.load(configFile)

        gatewayManager = SpruceGatewayManager(logger, context, config)
        pluginLoader = SprucePluginLoader(context, logger, dataFolder)
        lifecycle = SpruceLifecycleManager(logger)

        val allInstances = pluginLoader.load()
        lifecycle.initialize(allInstances)
        gatewayManager.start()

        logger.info("Spruce ready.")
    }

    fun disable() {
        logger.info("Spruce shutting down...")
        lifecycle.shutdown()
        gatewayManager.stop()
    }
}
