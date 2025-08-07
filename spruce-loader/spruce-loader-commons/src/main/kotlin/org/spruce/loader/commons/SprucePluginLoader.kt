package org.spruce.loader.commons

import org.spruce.api.gateway.SpruceGatewayClient
import org.spruce.api.plugin.SpruceContext
import org.spruce.api.plugin.SprucePlugin
import org.spruce.core.SpruceContextImpl
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.util.logging.Logger

class SprucePluginLoader(
    private val context: SpruceContextImpl,
    private val logger: Logger,
    private val dataFolder: File
) {

    fun load(): Set<Any> {
        val components = mutableSetOf<Any>()

        val pluginDir = File(dataFolder.parentFile, "spruce-plugins")
        if (!pluginDir.exists()) pluginDir.mkdirs()

        pluginDir.listFiles { f -> f.extension == "jar" }?.forEach { jar ->
            try {
                components.addAll(loadPluginJar(jar))
            } catch (e: Exception) {
                logger.warning("Failed to load ${jar.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        context.getAll().forEach { (clazz, instance) ->
            try {
                val loader = instance::class.java.classLoader
                invokeAllGenerators(loader, clazz, instance)
                components += instance
            } catch (e: Exception) {
                logger.warning("Failed to invoke generators for component ${clazz.name}: ${e.message}")
            }
        }

        return components
    }

    private fun loadPluginJar(jar: File): List<Any> {
        val instances = mutableSetOf<Any>()

        val jarFile = JarFile(jar)
        val classLoader = URLClassLoader(arrayOf(jar.toURI().toURL()), this.javaClass.classLoader)

        // Configs
        jarFile.getEntry("META-INF/spruce.configurations.txt")?.let { entry ->
            jarFile.getInputStream(entry).bufferedReader().readLines().forEach { className ->
                invokeGenerated(classLoader, "${className}__Beans", "register", arrayOf(SpruceContext::class.java), arrayOf(context), "config $className")
            }
        }

        // Models
        val gatewayClient = context.get(SpruceGatewayClient::class.java)
        jarFile.getEntry("META-INF/spruce.models.txt")?.let { entry ->
            jarFile.getInputStream(entry).bufferedReader().readLines().forEach { interfaceName ->
                try {
                    val interfaceClass = classLoader.loadClass(interfaceName)
                    val proxyClass = classLoader.loadClass("${interfaceName}__Proxy")
                    val constructor = proxyClass.getConstructor(Class.forName("org.spruce.api.gateway.SpruceGatewayClient"))
                    val proxyInstance = constructor.newInstance(gatewayClient)

                    @Suppress("UNCHECKED_CAST")
                    context.register(interfaceClass as Class<Any>, proxyInstance as Any)
                    logger.info("Loaded model proxy for $interfaceName")
                } catch (e: Exception) {
                    logger.warning("Failed to load proxy for $interfaceName: ${e.message}")
                }
            }
        }

        // Plugins
        val pluginEntry = jarFile.getEntry("META-INF/spruce.plugins.txt")
            ?: throw IllegalStateException("No META-INF/spruce.plugins in ${jar.name}")

        val plugins = jarFile.getInputStream(pluginEntry).bufferedReader().readLines().map { className ->
            val clazz = classLoader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            instances += instance

            invokeAllGenerators(classLoader, clazz, instance)

            val annotation = clazz.getAnnotation(SprucePlugin::class.java)
            if (annotation !== null) {
                logger.info("Loading plugin ${annotation.name} (${annotation.version}) by ${annotation.author}")
            }

            instance
        }

        return plugins
    }

    private fun invokeAllGenerators(classLoader: ClassLoader, clazz: Class<*>, instance: Any) {
        val commonParams = arrayOf(SpruceContext::class.java, clazz)
        val commonArgs = arrayOf(context, instance)

        listOf("__Loader", "__Injector", "__Commands", "__Events", "__Scheduled", "__GlobalEvents").forEach { suffix ->
            invokeGenerated(classLoader, "${clazz.name}$suffix", "register", commonParams, commonArgs, null, warn = false)
        }
    }

    private fun invokeGenerated(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        args: Array<Any>,
        logPrefix: String? = null,
        warn: Boolean = true
    ) {
        try {
            val clazz = classLoader.loadClass(className)
            val instance = clazz.getField("INSTANCE").get(null)
            val method = clazz.getMethod(methodName, *paramTypes)
            method.invoke(instance, *args)
            if (logPrefix != null) logger.info("Loaded $logPrefix")
        } catch (e: Exception) {
            if (warn && logPrefix != null)
                logger.warning("Failed to load $logPrefix: ${e.message}")
        }
    }
}
