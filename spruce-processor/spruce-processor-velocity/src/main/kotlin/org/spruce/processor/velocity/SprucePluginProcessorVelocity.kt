package org.spruce.processor.velocity

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.AbstractSprucePluginProcessor
import org.spruce.processor.velocity.generator.CommandRegistryVelocityGenerator
import org.spruce.processor.velocity.generator.EventListenerRegistryVelocityGenerator
import org.spruce.processor.velocity.generator.FileConfigLoaderVelocityGenerator
import org.spruce.processor.velocity.generator.ScheduledTaskRegistryVelocityGenerator

class SprucePluginProcessorVelocity(environment: SymbolProcessorEnvironment) : AbstractSprucePluginProcessor(environment) {

    override fun processCommandRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        CommandRegistryVelocityGenerator.process(component, environment)
    }

    override fun processEventListenerRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        EventListenerRegistryVelocityGenerator.process(component, environment)
    }

    override fun processScheduledTaskRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        ScheduledTaskRegistryVelocityGenerator.process(component, environment)
    }

    override fun processFileConfigLoader(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        FileConfigLoaderVelocityGenerator.process(component, environment)
    }
}