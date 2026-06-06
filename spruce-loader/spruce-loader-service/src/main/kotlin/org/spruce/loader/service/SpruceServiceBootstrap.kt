package org.spruce.loader.service

import org.spruce.api.lock.DistributedLockManager
import org.spruce.api.plugin.SpruceContext
import org.spruce.api.service.SpruceService
import org.spruce.core.SpruceContextImpl
import org.spruce.core.lock.redis.RedisDistributedLockManager
import org.spruce.loader.commons.SpruceLifecycleManager
import org.spruce.service.SpruceServiceRuntime
import java.util.logging.Logger

class SpruceServiceBootstrap(
    private val logger: Logger = Logger.getLogger("SpruceService"),
    private val context: SpruceContextImpl = SpruceContextImpl()
) {

    private lateinit var runtime: SpruceServiceRuntime
    private lateinit var lockManager: RedisDistributedLockManager
    private lateinit var lifecycle: SpruceLifecycleManager

    fun enable(serviceClass: Class<*>) {
        val annotation = serviceClass.getAnnotation(SpruceService::class.java)
            ?: throw IllegalStateException("${serviceClass.name} is missing @SpruceService")

        val serviceName = annotation.value
        require(serviceName.isNotBlank()) { "@SpruceService value must not be blank" }

        logger.info("Starting Spruce service '$serviceName'...")

        runtime = SpruceServiceRuntime(serviceName)
        lockManager = RedisDistributedLockManager(runtime.redis)
        lifecycle = SpruceLifecycleManager(logger)

        context.register(SpruceServiceRuntime::class.java, runtime)
        context.register(DistributedLockManager::class.java, lockManager)
        context.register(RedisDistributedLockManager::class.java, lockManager)

        loadConfigurations(serviceClass.classLoader)

        val instance = serviceClass.getDeclaredConstructor().newInstance()
        @Suppress("UNCHECKED_CAST")
        context.register(serviceClass as Class<Any>, instance as Any)

        val allInstances = context.getAll().asSequence().map { it.second }.toSet()

        for (component in allInstances) {
            invokeAllGenerators(component.javaClass.classLoader, component.javaClass, component)
        }

        lifecycle.initialize(allInstances)

        Runtime.getRuntime().addShutdownHook(Thread {
            disable()
        })

        runtime.start()
        runtime.awaitShutdown()
    }

    fun disable() {
        logger.info("Stopping Spruce service...")

        if (::lifecycle.isInitialized) {
            lifecycle.shutdown()
        }

        if (::lockManager.isInitialized) {
            lockManager.shutdown()
        }

        if (::runtime.isInitialized) {
            runtime.shutdown()
        }
    }

    private fun loadConfigurations(classLoader: ClassLoader) {
        val resource = classLoader.getResource("META-INF/spruce.configurations.txt") ?: return

        resource.openStream().bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { className ->
                    invokeGenerated(
                        classLoader = classLoader,
                        className = "${className}__Beans",
                        methodName = "register",
                        paramTypes = arrayOf(SpruceContext::class.java),
                        args = arrayOf(context),
                        warn = true
                    )
                }
        }
    }

    private fun invokeAllGenerators(
        classLoader: ClassLoader,
        clazz: Class<*>,
        instance: Any
    ) {
        invokeGenerated(
            classLoader,
            "${clazz.name}__Loader",
            "register",
            arrayOf(SpruceContext::class.java, SpruceServiceRuntime::class.java, clazz),
            arrayOf(context, runtime, instance),
            warn = false
        )

        invokeGenerated(
            classLoader,
            "${clazz.name}__Injector",
            "register",
            arrayOf(SpruceContext::class.java, clazz),
            arrayOf(context, instance),
            warn = false
        )

        listOf("__Actions", "__Scheduled", "__GlobalEvents").forEach { suffix ->
            invokeGenerated(
                classLoader,
                "${clazz.name}$suffix",
                "register",
                arrayOf(SpruceContext::class.java, SpruceServiceRuntime::class.java, clazz),
                arrayOf(context, runtime, instance),
                warn = false
            )
        }
    }

    private fun invokeGenerated(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        paramTypes: Array<Class<*>>,
        args: Array<Any>,
        warn: Boolean
    ) {
        try {
            val generatedClass = classLoader.loadClass(className)
            val generatedInstance = generatedClass.getField("INSTANCE").get(null)
            val method = generatedClass.getMethod(methodName, *paramTypes)
            method.invoke(generatedInstance, *args)
        } catch (_: ClassNotFoundException) {
            if (warn) logger.warning("Generated class not found: $className")
        } catch (e: Exception) {
            logger.warning("Failed to invoke $className#$methodName: ${e.message}")
            e.printStackTrace()
        }
    }
}