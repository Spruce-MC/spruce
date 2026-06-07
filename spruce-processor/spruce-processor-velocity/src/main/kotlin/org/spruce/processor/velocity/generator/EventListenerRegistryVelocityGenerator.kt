package org.spruce.processor.velocity.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object EventListenerRegistryVelocityGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val listeners = clazz.getAllFunctions()
            .filter { fn ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.plugin.EventListener"
                }
            }
            .toList()

        if (listeners.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__Events"
        val hasLocks = listeners.any { LockInvocationGenerator.hasLock(it) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import com.velocitypowered.api.event.Subscribe\n")
            writer.write("import com.velocitypowered.api.event.EventManager\n")
            writer.write("import com.velocitypowered.api.event.EventTask\n")
            writer.write("import com.velocitypowered.api.event.Continuation\n")
            writer.write("import org.spruce.api.plugin.SpruceLoaderPlugin\n")
            writer.write("import org.spruce.api.context.SpruceContext\n")
            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
                writer.write("import org.spruce.api.lock.LockAcquireException\n")
            }
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val plugin = ctx.get(SpruceLoaderPlugin::class.java)!!\n")
            writer.write("        val eventManager = ctx.get(EventManager::class.java)!!\n\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            writer.write("        eventManager.register(plugin, object {\n")

            for (fn in listeners) {
                val param = fn.parameters.firstOrNull() ?: continue
                val paramName = param.name?.asString() ?: "event"
                val paramType = param.type.resolve().declaration.qualifiedName?.asString() ?: continue
                val methodName = fn.simpleName.asString()

                val async = LockInvocationGenerator.isAsync(fn)
                val eventTask = isEventTask(fn)
                val returnsTask = async || eventTask

                writer.write("            @Subscribe\n")
                writer.write("            fun ${methodName}_handler($paramName: $paramType)${if (returnsTask) ": EventTask" else ""} {\n")

                val call = "instance.$methodName($paramName)"

                if (!LockInvocationGenerator.hasLock(fn)) {
                    writer.write("                ${if (returnsTask) "return " else ""}$call\n")
                } else {
                    when {
                        async -> {
                            val wrapped = LockInvocationGenerator.wrapAsyncCall(fn, call)
                            writer.write("                return EventTask.resumeWhenComplete(\n")
                            writer.write(wrapped.prependIndent("                    "))
                            writer.write("\n")
                            writer.write("                )\n")
                        }

                        eventTask -> {
                            writeLockedEventTaskInvocation(writer, fn, call)
                        }

                        else -> {
                            val wrapped = LockInvocationGenerator.wrapSyncCall(fn, call)
                                .prependIndent("                ")
                            writer.write("$wrapped\n")
                        }
                    }
                }

                writer.write("            }\n")
            }

            writer.write("        })\n")
            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }

    private fun writeLockedEventTaskInvocation(
        writer: OutputStreamWriter,
        fn: KSFunctionDeclaration,
        call: String
    ) {
        val data = LockInvocationGenerator.read(fn)

        writer.write("                return EventTask.withContinuation { continuation ->\n")
        writer.write("                    lockManager.acquireAsync(\n")
        writer.write("                        ${data.keyExpression},\n")
        writer.write("                        Duration.ofMillis(${data.ttlMillis}L),\n")
        writer.write("                        Duration.ofMillis(${data.acquireTimeoutMillis}L)\n")
        writer.write("                    ).thenAccept { lock ->\n")
        writer.write("                        if (lock == null) {\n")
        writer.write("                            continuation.resumeWithException(LockAcquireException(${data.keyExpression}))\n")
        writer.write("                            return@thenAccept\n")
        writer.write("                        }\n")
        writer.write("\n")
        writer.write("                        try {\n")
        writer.write("                            val task = $call\n")
        writer.write("                            task.execute(object : Continuation {\n")
        writer.write("                                override fun resume() {\n")
        writer.write("                                    lock.releaseAsync().whenComplete { _, releaseError ->\n")
        writer.write("                                        if (releaseError != null) continuation.resumeWithException(releaseError)\n")
        writer.write("                                        else continuation.resume()\n")
        writer.write("                                    }\n")
        writer.write("                                }\n")
        writer.write("\n")
        writer.write("                                override fun resumeWithException(exception: Throwable) {\n")
        writer.write("                                    lock.releaseAsync().whenComplete { _, releaseError ->\n")
        writer.write("                                        if (releaseError != null) exception.addSuppressed(releaseError)\n")
        writer.write("                                        continuation.resumeWithException(exception)\n")
        writer.write("                                    }\n")
        writer.write("                                }\n")
        writer.write("                            })\n")
        writer.write("                        } catch (e: Throwable) {\n")
        writer.write("                            lock.releaseAsync().whenComplete { _, releaseError ->\n")
        writer.write("                                if (releaseError != null) e.addSuppressed(releaseError)\n")
        writer.write("                                continuation.resumeWithException(e)\n")
        writer.write("                            }\n")
        writer.write("                        }\n")
        writer.write("                    }.exceptionally { e ->\n")
        writer.write("                        continuation.resumeWithException(e)\n")
        writer.write("                        null\n")
        writer.write("                    }\n")
        writer.write("                }\n")
    }

    private fun isEventTask(fn: KSFunctionDeclaration): Boolean {
        val returnType = fn.returnType?.resolve() ?: return false
        return returnType.declaration.qualifiedName?.asString() ==
                "com.velocitypowered.api.event.EventTask"
    }
}