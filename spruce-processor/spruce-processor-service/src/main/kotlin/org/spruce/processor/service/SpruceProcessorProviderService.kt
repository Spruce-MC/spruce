package org.spruce.processor.service

import com.google.devtools.ksp.processing.*

class SpruceProcessorProviderService : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SpruceProcessorService(environment)
    }
}
