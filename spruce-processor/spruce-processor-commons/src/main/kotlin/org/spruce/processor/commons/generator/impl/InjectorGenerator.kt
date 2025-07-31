package org.spruce.processor.commons.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object InjectorGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val packageName = clazz.containingFile?.packageName?.asString()?.takeIf { it.isNotBlank() }?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val fields = clazz.getAllProperties().filter { field ->
            field.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Inject"
            }
        }

        if (fields.none()) return false

        val simpleName = clazz.simpleName.asString()
        val injectorName = "${simpleName}__Injector"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, injectorName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")
            writer.write("object $injectorName {\n")
            writer.write("    fun register(ctx: SpruceContext, target: $simpleName) {\n")

            for (field in fields) {
                val fieldName = field.simpleName.asString()
                val type = field.type.resolve().declaration.qualifiedName?.asString() ?: continue
                writer.write("        target.$fieldName = ctx.get($type::class.java)!!\n")
            }

            writer.write("    }\n}")
        }

        return true
    }
}