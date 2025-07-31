package org.spruce.processor.commons.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object BeanRegistryGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        if (!clazz.annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Configuration" })
            return false

        val beans = clazz.getAllFunctions().filter { fn ->
            fn.annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Bean" }
        }

        if (beans.none()) return false

        val packageName = clazz.containingFile?.packageName?.asString() ?: return false
        val simpleName = clazz.simpleName.asString()
        val className = clazz.qualifiedName!!.asString()
        val genName = "${simpleName}__Beans"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, genName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import $className\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n\n")
            writer.write("object $genName {\n")
            writer.write("    fun register(ctx: SpruceContext) {\n")
            writer.write("        val config = $simpleName()\n")

            beans.forEach { fn ->
                val name = fn.simpleName.asString()
                val returnType = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
                    ?: return@forEach

                writer.write("        val $name = config.$name()\n")
                writer.write("        ctx.register($returnType::class.java, $name)\n")
            }

            writer.write("    }\n}\n")
        }

        return true
    }
}