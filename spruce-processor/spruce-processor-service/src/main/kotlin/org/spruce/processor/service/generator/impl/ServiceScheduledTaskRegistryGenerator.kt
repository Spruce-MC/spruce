package org.spruce.processor.service.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object ServiceScheduledTaskRegistryGenerator : CodeGenerator {

    private const val SCHEDULED = "org.spruce.api.annotation.Scheduled"

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val methods = clazz.getAllFunctions()
            .withIndex()
            .filter { (_, fn) ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == SCHEDULED
                }
            }
            .toList()

        if (methods.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName?.asString() ?: return false
        val fileName = "${simpleName}__Scheduled"
        val hasLocks = methods.any { (_, method) -> LockInvocationGenerator.hasLock(method) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import $qualifiedName\n")
            writer.write("import org.spruce.api.context.SpruceContext\n")
            writer.write("import org.spruce.service.SpruceServiceRuntime\n")

            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }

            writer.write("\n")
            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, runtime: SpruceServiceRuntime, instance: $simpleName) {\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for ((index, method) in methods) {
                val annotation = method.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == SCHEDULED
                }

                val delay = readLongValue("delay", annotation)
                val period = readLongValue("period", annotation)
                val methodName = method.simpleName.asString()
                val runnableName = "runnable_$index"

                if (method.parameters.isNotEmpty()) {
                    environment.logger.error("@Scheduled service method must have no parameters: $methodName", method)
                    continue
                }

                val call = "instance.$methodName()"

                writer.write("\n")
                writer.write("        val $runnableName = Runnable {\n")

                if (LockInvocationGenerator.hasLock(method)) {
                    val wrapped = LockInvocationGenerator.wrapCall(method, call)
                        .prependIndent("            ")
                    writer.write("$wrapped\n")
                } else {
                    writer.write("            $call\n")
                }

                writer.write("        }\n")

                if (period > 0) {
                    writer.write("        runtime.scheduleAtFixedRate($runnableName, $delay, $period)\n")
                } else {
                    writer.write("        runtime.schedule($runnableName, $delay)\n")
                }
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }

    private fun readLongValue(argName: String, annotation: KSAnnotation): Long {
        val value = annotation.arguments.find { it.name?.asString() == argName }?.value
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> 0L
        }
    }
}