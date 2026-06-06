package org.spruce.loader.commons

import org.spruce.api.gateway.SpruceGatewayClient
import org.spruce.api.lock.DistributedLockManager
import org.spruce.api.plugin.SpruceContext
import org.spruce.api.plugin.SprucePlugin
import org.spruce.core.SpruceContextImpl
import java.io.File
import java.net.URLClassLoader
import java.util.Locale
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

        val jars = pluginDir.listFiles { f -> f.extension == "jar" }?.toList().orEmpty()

        val descriptors = scanPlugins(jars)
        val sortedPlugins = sortPlugins(descriptors)

        val initializedJars = mutableSetOf<File>()

        for (descriptor in sortedPlugins) {
            try {
                if (initializedJars.add(descriptor.jar)) {
                    loadJarInfrastructure(descriptor.jar, descriptor.classLoader)
                }

                val instance = descriptor.clazz.getDeclaredConstructor().newInstance()
                components += instance

                invokeAllGenerators(descriptor.classLoader, descriptor.clazz, instance)

                logger.info(
                    "Loading plugin ${descriptor.name} (${descriptor.version}) by ${descriptor.author}"
                )
            } catch (e: Exception) {
                logger.warning("Failed to load ${descriptor.className}: ${e.message}")
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

    private fun scanPlugins(jars: List<File>): List<PluginDescriptor> {
        val descriptors = mutableListOf<PluginDescriptor>()

        for (jar in jars) {
            try {
                val jarFile = JarFile(jar)
                val classLoader = URLClassLoader(arrayOf(jar.toURI().toURL()), this.javaClass.classLoader)

                val pluginEntry = jarFile.getEntry("META-INF/spruce.plugins.txt")
                    ?: throw IllegalStateException("No META-INF/spruce.plugins.txt in ${jar.name}")

                jarFile.getInputStream(pluginEntry).bufferedReader().readLines().forEach { className ->
                    val clazz = classLoader.loadClass(className)
                    val annotation = clazz.getAnnotation(SprucePlugin::class.java)
                        ?: throw IllegalStateException("$className is missing @SprucePlugin")

                    val name = annotation.name.ifBlank { clazz.simpleName }

                    descriptors += PluginDescriptor(
                        jar = jar,
                        classLoader = classLoader,
                        clazz = clazz,
                        className = className,
                        name = name,
                        key = normalize(name),
                        version = annotation.version,
                        author = annotation.author,
                        depend = annotation.depend.map(::normalize),
                        softDepend = annotation.softDepend.map(::normalize)
                    )
                }
            } catch (e: Exception) {
                logger.warning("Failed to scan ${jar.name}: ${e.message}")
                e.printStackTrace()
            }
        }

        val duplicates = descriptors.groupBy { it.key }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw IllegalStateException(
                "Duplicate Spruce plugin names: " +
                        duplicates.values.flatten().joinToString { it.name }
            )
        }

        return descriptors
    }

    private fun sortPlugins(descriptors: List<PluginDescriptor>): List<PluginDescriptor> {
        val byName = descriptors.associateBy { it.key }
        val result = mutableListOf<PluginDescriptor>()
        val state = mutableMapOf<String, VisitState>()
        val skipped = mutableSetOf<String>()

        fun visit(plugin: PluginDescriptor, stack: List<String> = emptyList()): Boolean {
            when (state[plugin.key]) {
                VisitState.VISITING -> {
                    val cycle = (stack + plugin.key).joinToString(" -> ")
                    throw IllegalStateException("Circular Spruce plugin dependency detected: $cycle")
                }

                VisitState.VISITED -> return plugin.key !in skipped
                null -> {}
            }

            state[plugin.key] = VisitState.VISITING

            for (dependencyName in plugin.depend) {
                val dependency = byName[dependencyName]

                if (dependency == null) {
                    logger.warning(
                        "Skipping plugin ${plugin.name}: missing required dependency '$dependencyName'"
                    )
                    skipped += plugin.key
                    state[plugin.key] = VisitState.VISITED
                    return false
                }

                if (!visit(dependency, stack + plugin.key)) {
                    logger.warning(
                        "Skipping plugin ${plugin.name}: dependency '${dependency.name}' was not loaded"
                    )
                    skipped += plugin.key
                    state[plugin.key] = VisitState.VISITED
                    return false
                }
            }

            for (dependencyName in plugin.softDepend) {
                val dependency = byName[dependencyName] ?: continue
                visit(dependency, stack + plugin.key)
            }

            state[plugin.key] = VisitState.VISITED

            if (plugin.key !in skipped && plugin !in result) {
                result += plugin
            }

            return plugin.key !in skipped
        }

        descriptors.forEach { visit(it) }

        return result
    }

    private fun loadJarInfrastructure(jar: File, classLoader: ClassLoader) {
        val jarFile = JarFile(jar)

        jarFile.getEntry("META-INF/spruce.configurations.txt")?.let { entry ->
            jarFile.getInputStream(entry).bufferedReader().readLines().forEach { className ->
                invokeGenerated(
                    classLoader,
                    "${className}__Beans",
                    "register",
                    arrayOf(SpruceContext::class.java),
                    arrayOf(context),
                    "config $className"
                )
            }
        }

        val gatewayClient = context.get(SpruceGatewayClient::class.java)
        val lockManager = context.get(DistributedLockManager::class.java)

        jarFile.getEntry("META-INF/spruce.models.txt")?.let { entry ->
            jarFile.getInputStream(entry).bufferedReader().readLines().forEach { interfaceName ->
                try {
                    val interfaceClass = classLoader.loadClass(interfaceName)
                    val proxyClass = classLoader.loadClass("${interfaceName}__Proxy")

                    val constructor = proxyClass.getConstructor(
                        SpruceGatewayClient::class.java,
                        DistributedLockManager::class.java
                    )

                    val proxyInstance = constructor.newInstance(
                        gatewayClient,
                        lockManager
                    )

                    @Suppress("UNCHECKED_CAST")
                    context.register(interfaceClass as Class<Any>, proxyInstance as Any)

                    logger.info("Loaded model proxy for $interfaceName")
                } catch (e: Exception) {
                    logger.warning("Failed to load proxy for $interfaceName: ${e.message}")
                }
            }
        }
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
            if (warn && logPrefix != null) {
                logger.warning("Failed to load $logPrefix: ${e.message}")
            }
        }
    }

    private fun normalize(name: String): String {
        return name.trim().lowercase(Locale.ROOT)
    }

    private data class PluginDescriptor(
        val jar: File,
        val classLoader: ClassLoader,
        val clazz: Class<*>,
        val className: String,
        val name: String,
        val key: String,
        val version: String,
        val author: String,
        val depend: List<String>,
        val softDepend: List<String>
    )

    private enum class VisitState {
        VISITING,
        VISITED
    }
}