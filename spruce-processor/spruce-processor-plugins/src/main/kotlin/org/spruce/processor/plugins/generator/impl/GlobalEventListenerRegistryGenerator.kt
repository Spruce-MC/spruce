package org.spruce.processor.plugins.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object GlobalEventListenerRegistryGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val listeners = clazz.getAllFunctions()
            .filter { fn ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.event.GlobalEventListener"
                }
            }
            .toList()

        if (listeners.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__GlobalEvents"
        val hasLocks = listeners.any { LockInvocationGenerator.hasLock(it) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import org.spruce.api.context.SpruceContext\n")
            writer.write("import org.spruce.api.gateway.SpruceGatewayClient\n")
            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val client = ctx.get(SpruceGatewayClient::class.java)!!\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for (fn in listeners) {
                val param = fn.parameters.firstOrNull() ?: continue
                val paramName = param.name?.asString() ?: "event"
                val paramType = param.type.resolve().declaration.qualifiedName?.asString() ?: continue
                val methodName = fn.simpleName.asString()

                writer.write("        client.on($paramType::class.java) { $paramName ->\n")

                val call = "instance.$methodName($paramName)"

                if (LockInvocationGenerator.hasLock(fn)) {
                    val wrapped = LockInvocationGenerator.wrapCall(fn, call)
                        .prependIndent("            ")

                    writer.write("$wrapped\n")
                } else {
                    writer.write("            $call\n")
                }

                writer.write("        }\n")
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }
}