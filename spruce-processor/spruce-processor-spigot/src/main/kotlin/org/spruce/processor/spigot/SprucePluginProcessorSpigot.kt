package org.spruce.processor.spigot

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.AbstractSprucePluginProcessor
import org.spruce.processor.spigot.generator.CommandRegistrySpigotGenerator
import org.spruce.processor.spigot.generator.EventListenerRegistrySpigotGenerator
import org.spruce.processor.spigot.generator.FileConfigLoaderSpigotGenerator
import org.spruce.processor.spigot.generator.ScheduledTaskRegistrySpigotGenerator

class SprucePluginProcessorSpigot(environment: SymbolProcessorEnvironment) : AbstractSprucePluginProcessor(environment) {

    override fun processCommandRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        CommandRegistrySpigotGenerator.process(component, environment)
    }

    override fun processEventListenerRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        EventListenerRegistrySpigotGenerator.process(component, environment)
    }

    override fun processScheduledTaskRegistry(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        ScheduledTaskRegistrySpigotGenerator.process(component, environment)
    }

    override fun processFileConfigLoader(component: KSClassDeclaration, environment: SymbolProcessorEnvironment) {
        FileConfigLoaderSpigotGenerator.process(component, environment)
    }
}