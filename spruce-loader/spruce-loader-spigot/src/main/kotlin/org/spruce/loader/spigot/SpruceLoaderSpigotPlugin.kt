package org.spruce.loader.spigot

import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.spruce.api.plugin.SpruceLoaderPlugin
import org.spruce.core.SpruceContextImpl
import org.spruce.loader.commons.SpruceLoaderBootstrap

class SpruceLoaderSpigotPlugin : JavaPlugin(), SpruceLoaderPlugin {

    private lateinit var bootstrap: SpruceLoaderBootstrap

    override fun onEnable() {
        val context = SpruceContextImpl().apply {
            register(Server::class.java, server)
            register(JavaPlugin::class.java, this@SpruceLoaderSpigotPlugin)
            register(SpruceLoaderPlugin::class.java, this@SpruceLoaderSpigotPlugin)
        }

        bootstrap = SpruceLoaderBootstrap(logger, dataFolder, context)
        bootstrap.enable()
    }

    override fun onDisable() {
        bootstrap.disable()
    }
}
