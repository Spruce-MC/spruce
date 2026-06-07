package org.spruce.processor.service.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object ServiceGlobalEventListenerRegistryGenerator : CodeGenerator {

    private const val GLOBAL_EVENT_LISTENER = "org.spruce.api.event.GlobalEventListener"

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val listeners = clazz.getAllFunctions()
            .filter { fn ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            GLOBAL_EVENT_LISTENER
                }
            }
            .toList()

        if (listeners.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName?.asString() ?: return false
        val fileName = "${simpleName}__GlobalEvents"
        val hasLocks = listeners.any { LockInvocationGenerator.hasLock(it) }

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
            writer.write("import java.util.concurrent.CompletableFuture\n")

            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }

            writer.write("\n")
            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, runtime: SpruceServiceRuntime, instance: $simpleName) {\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for (fn in listeners) {
                val param = fn.parameters.firstOrNull() ?: continue
                val paramName = param.name?.asString() ?: "event"
                val paramType = param.type.resolve().declaration.qualifiedName?.asString() ?: continue
                val methodName = fn.simpleName.asString()

                if (fn.parameters.size != 1) {
                    environment.logger.error("@GlobalEventListener method must have exactly one parameter: $methodName", fn)
                    continue
                }

                val eventType = "runtime.resolveEventType($paramType::class.java)"
                val call = "instance.$methodName($paramName)"

                writer.write("        runtime.registerEventHandler($eventType) { payload ->\n")
                writer.write("            val $paramName = runtime.readPayload(payload, $paramType::class.java)\n")

                if (LockInvocationGenerator.hasLock(fn)) {
                    val wrapped = LockInvocationGenerator.wrapCall(fn, call)

                    if (LockInvocationGenerator.isAsync(fn)) {
                        writer.write("            $wrapped.thenApply<Void?> { null }\n")
                    } else {
                        writer.write("            $wrapped\n")
                        writer.write("            CompletableFuture.completedFuture(null)\n")
                    }
                } else {
                    if (LockInvocationGenerator.isAsync(fn)) {
                        writer.write("            $call.thenApply<Void?> { null }\n")
                    } else {
                        writer.write("            $call\n")
                        writer.write("            CompletableFuture.completedFuture(null)\n")
                    }
                }

                writer.write("        }\n\n")
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }
}