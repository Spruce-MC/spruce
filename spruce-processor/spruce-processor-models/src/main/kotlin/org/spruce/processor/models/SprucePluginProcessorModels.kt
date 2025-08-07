package org.spruce.processor.models

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.AbstractSprucePluginProcessor
import org.spruce.processor.commons.writeToFile

class SprucePluginProcessorModels(environment: SymbolProcessorEnvironment) : AbstractSprucePluginProcessor(environment) {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val modelClassNames = mutableListOf<String>()
        val serviceModels = resolver.getSymbolsWithAnnotation("org.spruce.api.service.ServiceModel")
            .filterIsInstance<KSClassDeclaration>()

        for (model in serviceModels) {
            if (processServiceModelProxy(model, environment)) {
                val className = model.qualifiedName?.asString() ?: continue
                modelClassNames.add(className)
            }
        }

        if (modelClassNames.isNotEmpty()) {
            environment.writeToFile("spruce.models", modelClassNames)
        }

        return emptyList()
    }

    override fun processCommandRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {}
    override fun processEventListenerRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {}
    override fun processScheduledTaskRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {}
    override fun processFileConfigLoader(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {}
}