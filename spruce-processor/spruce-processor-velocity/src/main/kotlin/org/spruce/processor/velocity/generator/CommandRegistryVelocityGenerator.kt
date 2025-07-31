package org.spruce.processor.velocity.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object CommandRegistryVelocityGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val commands = clazz.getAllFunctions()
            .filter { fn ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Command"
                }
            }

        if (commands.none()) return false

        val packageName = clazz.containingFile?.packageName?.asString()?.takeIf { it.isNotBlank() }?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__Commands"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import com.velocitypowered.api.command.SimpleCommand\n")
            writer.write("import com.velocitypowered.api.command.CommandManager\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import org.spruce.api.plugin.SpruceLoaderPlugin\n")
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val commandManager = ctx.get(CommandManager::class.java)!!\n")
            writer.write("        val plugin = ctx.get(SpruceLoaderPlugin::class.java)!!\n\n")

            for (cmd in commands) {
                val annotation = cmd.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Command"
                }

                val name = annotation.arguments.find { it.name?.asString() == "value" }?.value as? String ?: continue
                val aliases = annotation.arguments.find { it.name?.asString() == "aliases" }?.value as? List<*> ?: emptyList<String>()

                val methodName = cmd.simpleName.asString()
                val params = cmd.parameters
                val expectsArgs = params.size == 2

                val aliasesString = aliases.joinToString(", ") { "\"$it\"" }.let {
                    if (it.isEmpty()) {
                        ""
                    } else {
                        ".aliases($it)"
                    }
                }

                val className = "${simpleName}_${methodName}_Command"

                writer.write("        class $className(private val instance: $simpleName) : SimpleCommand {\n")
                writer.write("            override fun execute(invocation: SimpleCommand.Invocation) {\n")
                writer.write("                val sender = invocation.source()\n")
                if (expectsArgs) {
                    writer.write("                val args = invocation.arguments()\n")
                    writer.write("                instance.$methodName(sender, args)\n")
                } else {
                    writer.write("                instance.$methodName(sender)\n")
                }
                writer.write("            }\n")
                writer.write("        }\n\n")

                writer.write("        commandManager.register(commandManager.metaBuilder(\"${name}\")$aliasesString.plugin(plugin).build(), $className(instance))\n\n")
            }

            writer.write("    }\n}\n")
        }

        return true
    }
}
