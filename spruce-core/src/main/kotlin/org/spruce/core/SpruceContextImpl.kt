package org.spruce.core

import org.spruce.api.plugin.Inject
import org.spruce.api.plugin.SpruceContext
import java.util.concurrent.ConcurrentHashMap

class SpruceContextImpl : SpruceContext {
    private val beans = ConcurrentHashMap<Class<*>, Any>()

    override fun <T : Any> get(type: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return beans[type] as? T
    }

    override fun <T : Any> register(type: Class<T>, instance: T) {
        beans[type] = instance
    }

    fun inject(target: Any) {
        val fields = target::class.java.declaredFields
        for (field in fields) {
            if (field.isAnnotationPresent(Inject::class.java)) {
                val bean = get(field.type)
                requireNotNull(bean) { "No bean of type ${field.type}" }
                field.isAccessible = true
                field.set(target, bean)
            }
        }
    }

    fun getAll(): List<Pair<Class<*>, Any>> = beans.entries.map { (clazz, instance) -> clazz to instance }
}
