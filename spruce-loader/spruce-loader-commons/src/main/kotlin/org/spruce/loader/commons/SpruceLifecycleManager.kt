package org.spruce.loader.commons

import org.spruce.api.plugin.PostConstruct
import org.spruce.api.plugin.PreDestroy
import java.util.logging.Logger

class SpruceLifecycleManager(
    private val logger: Logger
) {

    private val instances = mutableSetOf<Any>()

    fun initialize(targets: Set<Any>) {
        instances.addAll(targets)
        targets.forEach { callAnnotatedMethod(it, PostConstruct::class.java, "PostConstruct") }
    }

    fun shutdown() {
        instances.forEach { callAnnotatedMethod(it, PreDestroy::class.java, "PreDestroy") }
    }

    private fun callAnnotatedMethod(instance: Any, annotation: Class<out Annotation>, label: String) {
        instance.javaClass.methods.forEach { method ->
            if (method.getAnnotation(annotation) != null && method.parameterCount == 0) {
                try {
                    method.invoke(instance)
                    logger.info("Called @$label on ${instance.javaClass.name}#${method.name}")
                } catch (e: Exception) {
                    logger.warning("Failed @$label on ${instance.javaClass.name}#${method.name}: ${e.message}")
                }
            }
        }
    }
}
