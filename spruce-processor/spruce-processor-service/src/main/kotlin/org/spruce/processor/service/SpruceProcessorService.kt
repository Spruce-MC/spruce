package org.spruce.processor.service

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.impl.BeanRegistryGenerator
import org.spruce.processor.commons.generator.impl.InjectorGenerator
import org.spruce.processor.commons.generator.writeToFile
import org.spruce.processor.service.generator.impl.ServiceActionRegistryGenerator
import org.spruce.processor.service.generator.impl.ServiceFileConfigLoaderGenerator
import org.spruce.processor.service.generator.impl.ServiceGlobalEventListenerRegistryGenerator
import org.spruce.processor.service.generator.impl.ServiceScheduledTaskRegistryGenerator

class SpruceProcessorService(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val serviceClassNames = mutableListOf<String>()

        val services = resolver.getSymbolsWithAnnotation("org.spruce.api.service.SpruceService")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        for (service in services) {
            val className = service.qualifiedName?.asString() ?: continue
            serviceClassNames.add(className)

            processInjector(service)
            processBeans(service)
            processServiceActions(service)
            processScheduledTasks(service)
            processFileConfigLoader(service)
            processGlobalEventListeners(service)
        }

        val configClassNames = mutableListOf<String>()

        val configs = resolver.getSymbolsWithAnnotation("org.spruce.api.annotation.Configuration")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        for (config in configs) {
            if (processBeans(config)) {
                val className = config.qualifiedName?.asString() ?: continue
                configClassNames.add(className)
            }
        }

        val components = resolver.getSymbolsWithAnnotation("org.spruce.api.annotation.Component")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        for (component in components) {
            processInjector(component)
            processBeans(component)
            processServiceActions(component)
            processScheduledTasks(component)
            processFileConfigLoader(component)
            processGlobalEventListeners(component)
        }

        if (serviceClassNames.isNotEmpty()) {
            environment.writeToFile("spruce.services", serviceClassNames)
        }

        if (configClassNames.isNotEmpty()) {
            environment.writeToFile("spruce.configurations", configClassNames)
        }

        return emptyList()
    }

    private fun processInjector(clazz: KSClassDeclaration) {
        InjectorGenerator.process(clazz, environment)
    }

    private fun processBeans(clazz: KSClassDeclaration): Boolean {
        return BeanRegistryGenerator.process(clazz, environment)
    }

    private fun processServiceActions(clazz: KSClassDeclaration) {
        ServiceActionRegistryGenerator.process(clazz, environment)
    }

    private fun processScheduledTasks(clazz: KSClassDeclaration) {
        ServiceScheduledTaskRegistryGenerator.process(clazz, environment)
    }

    private fun processFileConfigLoader(clazz: KSClassDeclaration) {
        ServiceFileConfigLoaderGenerator.process(clazz, environment)
    }

    private fun processGlobalEventListeners(clazz: KSClassDeclaration) {
        ServiceGlobalEventListenerRegistryGenerator.process(clazz, environment)
    }
}