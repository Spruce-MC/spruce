package org.spruce.processor.spigot.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object ScheduledTaskRegistrySpigotGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val methods = clazz.getAllFunctions()
            .withIndex()
            .filter { (_, fn) ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.plugin.Scheduled"
                }
            }
            .toList()

        if (methods.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__Scheduled"
        val hasLocks = methods.any { (_, fn) -> LockInvocationGenerator.hasLock(fn) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import org.bukkit.Bukkit\n")
            writer.write("import org.bukkit.plugin.java.JavaPlugin\n")
            writer.write("import org.bukkit.scheduler.BukkitTask\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val plugin = ctx.get(JavaPlugin::class.java)!!\n\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for ((index, method) in methods) {
                val annotation = method.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.plugin.Scheduled"
                }

                val delay = readLongValue("delay", annotation)
                val period = readLongValue("period", annotation)
                val async = readBooleanValue("async", annotation)
                val methodName = method.simpleName.asString()

                val expectsTask = method.parameters.size == 1
                val taskParamName = method.parameters.firstOrNull()?.name?.asString() ?: "task"

                val runnableName = "runnable_$index"
                val taskName = "task_$index"

                val call = if (expectsTask) {
                    "instance.$methodName($taskParamName)"
                } else {
                    "instance.$methodName()"
                }

                if (expectsTask) {
                    writer.write("        val ${taskName}_ref = arrayOfNulls<BukkitTask>(1)\n")
                    writer.write("        val $runnableName = Runnable {\n")
                    writer.write("            val $taskParamName = ${taskName}_ref[0]!!\n")

                    if (LockInvocationGenerator.hasLock(method)) {
                        val wrapped = LockInvocationGenerator.wrapCall(method, call)
                            .prependIndent("            ")
                        writer.write("$wrapped\n")
                    } else {
                        writer.write("            $call\n")
                    }

                    writer.write("        }\n")
                } else {
                    writer.write("        val $runnableName = Runnable {\n")

                    if (LockInvocationGenerator.hasLock(method)) {
                        val wrapped = LockInvocationGenerator.wrapCall(method, call)
                            .prependIndent("            ")
                        writer.write("$wrapped\n")
                    } else {
                        writer.write("            $call\n")
                    }

                    writer.write("        }\n")
                }

                val schedulerCall = when {
                    period > 0 -> if (async)
                        "Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, $runnableName, $delay, $period)"
                    else
                        "Bukkit.getScheduler().runTaskTimer(plugin, $runnableName, $delay, $period)"

                    else -> if (async)
                        "Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, $runnableName, $delay)"
                    else
                        "Bukkit.getScheduler().runTaskLater(plugin, $runnableName, $delay)"
                }

                if (expectsTask) {
                    writer.write("        val $taskName = $schedulerCall\n")
                    writer.write("        ${taskName}_ref[0] = $taskName\n\n")
                } else {
                    writer.write("        $schedulerCall\n\n")
                }
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }

    fun readLongValue(argName: String, annotation: KSAnnotation): Long {
        val value = annotation.arguments.find { it.name?.asString() == argName }?.value
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> 0L
        }
    }

    fun readBooleanValue(argName: String, annotation: KSAnnotation): Boolean {
        val value = annotation.arguments.find { it.name?.asString() == argName }?.value
        return value as? Boolean ?: false
    }
}