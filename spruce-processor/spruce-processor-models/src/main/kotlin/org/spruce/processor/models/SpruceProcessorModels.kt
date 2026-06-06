package org.spruce.processor.models

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.models.generator.impl.ServiceModelProxyGenerator
import org.spruce.processor.commons.generator.writeToFile

class SpruceProcessorModels(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

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

    fun processServiceModelProxy(component: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        return ServiceModelProxyGenerator.process(component, environment)
    }
}