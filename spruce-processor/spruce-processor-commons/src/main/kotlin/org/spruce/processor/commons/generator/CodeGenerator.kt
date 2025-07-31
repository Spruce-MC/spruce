package org.spruce.processor.commons.generator

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration

interface CodeGenerator {

    fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean
}