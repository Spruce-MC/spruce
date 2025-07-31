package org.spruce.processor.spigot

import com.google.devtools.ksp.processing.*

class SprucePluginProcessorProviderSpigot : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SprucePluginProcessorSpigot(environment)
    }
}
