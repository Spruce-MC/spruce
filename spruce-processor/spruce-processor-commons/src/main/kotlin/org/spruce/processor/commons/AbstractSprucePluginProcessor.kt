package org.spruce.processor.commons

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.impl.*
import java.io.OutputStreamWriter

abstract class AbstractSprucePluginProcessor(
    protected val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val plugins = resolver.getSymbolsWithAnnotation("org.spruce.api.plugin.SprucePlugin")
            .filterIsInstance<KSClassDeclaration>()

        val pluginClassNames = mutableListOf<String>()
        for (plugin in plugins) {
            val className = plugin.qualifiedName?.asString() ?: continue
            pluginClassNames.add(className)

            processInjector(plugin, environment)
            processBeans(plugin, environment)
            processCommandRegistry(plugin, environment)
            processEventListenerRegistry(plugin, environment)
            processScheduledTaskRegistry(plugin, environment)
            processFileConfigLoader(plugin, environment)
            processGlobalEventListenerRegistry(plugin, environment)
        }

        val configClassNames = mutableListOf<String>()
        val configs = resolver.getSymbolsWithAnnotation("org.spruce.api.plugin.Configuration")
            .filterIsInstance<KSClassDeclaration>()

        for (config in configs) {
            if (processBeans(config, environment)) {
                val className = config.qualifiedName?.asString() ?: continue
                configClassNames.add(className)
            }
        }

        val components = resolver.getSymbolsWithAnnotation("org.spruce.api.plugin.Component")
            .filterIsInstance<KSClassDeclaration>()

        for (component in components) {
            processInjector(component, environment)
            processCommandRegistry(component, environment)
            processEventListenerRegistry(component, environment)
            processScheduledTaskRegistry(component, environment)
            processFileConfigLoader(component, environment)
            processGlobalEventListenerRegistry(component, environment)
        }

        if (pluginClassNames.isNotEmpty()) {
            environment.writeToFile("spruce.plugins", pluginClassNames)
        }

        if (configClassNames.isNotEmpty()) {
            environment.writeToFile("spruce.configurations", configClassNames)
        }

        return emptyList()
    }

    open fun processInjector(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        InjectorGenerator.process(component, environment)
    }

    open fun processBeans(component: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        return BeanRegistryGenerator.process(component, environment)
    }

    open fun processGlobalEventListenerRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        GlobalEventListenerRegistryGenerator.process(component, environment)
    }

    open fun processServiceModelProxy(component: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        return ServiceModelProxyGenerator.process(component, environment)
    }

    abstract fun processCommandRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment)
    abstract fun processEventListenerRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment)
    abstract fun processScheduledTaskRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment)
    abstract fun processFileConfigLoader(component: KSClassDeclaration, environment: SymbolProcessorEnvironment)
}

fun SymbolProcessorEnvironment.writeToFile(fileName: String, lines: List<String>) {
    val file = this.codeGenerator.createNewFile(
        Dependencies(false), "META-INF", fileName, "txt"
    )
    OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
        for (line in lines) writer.write("$line\n")
    }
}