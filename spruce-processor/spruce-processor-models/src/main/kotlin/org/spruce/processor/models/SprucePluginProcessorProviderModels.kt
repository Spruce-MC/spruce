package org.spruce.processor.models

import com.google.devtools.ksp.processing.*

class SprucePluginProcessorProviderModels : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SprucePluginProcessorModels(environment)
    }
}
