package org.spruce.processor.velocity

import com.google.devtools.ksp.processing.*

class SprucePluginProcessorProviderVelocity : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SprucePluginProcessorVelocity(environment)
    }
}
