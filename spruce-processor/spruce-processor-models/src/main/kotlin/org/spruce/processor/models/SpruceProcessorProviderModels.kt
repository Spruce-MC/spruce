package org.spruce.processor.models

import com.google.devtools.ksp.processing.*

class SpruceProcessorProviderModels : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SpruceProcessorModels(environment)
    }
}
