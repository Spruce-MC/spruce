package org.spruce.loader.velocity

import com.google.inject.Inject
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.Scheduler
import org.spruce.api.plugin.SpruceLoaderPlugin
import org.spruce.core.SpruceContextImpl
import org.spruce.loader.commons.SpruceLoaderBootstrap
import java.nio.file.Path
import java.util.logging.Logger

@Plugin(
    id = "spruce",
    name = "Spruce",
    version = "1.0.1",
    authors = ["Spruce"]
)
class SpruceLoaderVelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) : SpruceLoaderPlugin {

    private lateinit var bootstrap: SpruceLoaderBootstrap

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        val context = SpruceContextImpl().apply {
            register(ProxyServer::class.java, server)
            register(Logger::class.java, logger)
            register(SpruceLoaderPlugin::class.java, this@SpruceLoaderVelocityPlugin)
            register(Scheduler::class.java, server.scheduler)
            register(CommandManager::class.java, server.commandManager)
            register(EventManager::class.java, server.eventManager)
        }

        bootstrap = SpruceLoaderBootstrap(logger, dataDirectory.toFile(), context)
        bootstrap.enable()

        server.eventManager.register(this, ProxyShutdownEvent::class.java) {
            bootstrap.disable()
        }
    }
}