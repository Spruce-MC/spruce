package org.spruce.processor.velocity.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object ScheduledTaskRegistryVelocityGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val methods = clazz.getAllFunctions()
            .withIndex()
            .filter { (_, fn) ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Scheduled"
                }
            }

        if (methods.none()) return false

        val packageName = clazz.containingFile?.packageName?.asString()?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__Scheduled"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import com.velocitypowered.api.scheduler.Scheduler\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import org.spruce.api.plugin.SpruceLoaderPlugin\n")
            writer.write("import ${clazz.qualifiedName!!.asString()}\n")
            writer.write("import java.time.Duration\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val scheduler = ctx.get(Scheduler::class.java)!!\n")
            writer.write("        val plugin = ctx.get(SpruceLoaderPlugin::class.java)!!\n\n")

            for ((index, method) in methods) {
                val annotation = method.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Scheduled"
                }

                val delay = readLongValue("delay", annotation)
                val period = readLongValue("period", annotation)
                val methodName = method.simpleName.asString()

                val runnableName = "runnable_$index"

                writer.write("        val $runnableName = Runnable {\n")
                writer.write("            try {\n")
                writer.write("                instance.$methodName()\n") // no Task in Velocity
                writer.write("            } catch (e: Exception) {\n")
                writer.write("                println(\"Scheduled task '$simpleName.$methodName' threw: \${e.message}\")\n")
                writer.write("            }\n")
                writer.write("        }\n")

                writer.write("        val task = scheduler.buildTask(plugin, $runnableName)\n")
                writer.write("            .delay(Duration.ofMillis($delay))\n")

                if (period > 0) {
                    writer.write("            .repeat(Duration.ofMillis($period))\n")
                }

                writer.write("            .schedule()\n\n")
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
