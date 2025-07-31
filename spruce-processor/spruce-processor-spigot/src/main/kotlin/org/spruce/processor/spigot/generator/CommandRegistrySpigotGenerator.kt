package org.spruce.processor.spigot.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object CommandRegistrySpigotGenerator : CodeGenerator {

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
            writer.write("import org.bukkit.Bukkit\n")
            writer.write("import org.bukkit.command.Command\n")
            writer.write("import org.bukkit.command.CommandSender\n")
            writer.write("import org.bukkit.command.CommandMap\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val commandMap = Bukkit.getServer().javaClass\n")
            writer.write("            .getDeclaredMethod(\"getCommandMap\")\n")
            writer.write("            .apply { isAccessible = true }\n")
            writer.write("            .invoke(Bukkit.getServer()) as CommandMap\n\n")

            for (cmd in commands) {
                val annotation = cmd.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Command"
                }

                val name = annotation.arguments.find { it.name?.asString() == "value" }?.value as? String ?: continue
                val aliases = annotation.arguments.find { it.name?.asString() == "aliases" }?.value as? List<*> ?: emptyList<String>()
                val description = annotation.arguments.find { it.name?.asString() == "description" }?.value as? String ?: ""

                val methodName = cmd.simpleName.asString()
                val params = cmd.parameters
                val expectsArgs = params.size == 2

                val aliasesString = aliases.joinToString(", ") { "\"$it\"" }

                writer.write("        commandMap.register(\"spruce\", object : Command(\"$name\", \"$description\", \"/$name\", listOf($aliasesString)) {\n")
                writer.write("            override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {\n")
                writer.write("                try {\n")
                if (expectsArgs) {
                    writer.write("                    instance.$methodName(sender, args)\n")
                } else {
                    writer.write("                    instance.$methodName(sender)\n")
                }
                writer.write("                } catch (e: Exception) {\n")
                writer.write("                    sender.sendMessage(\"§cCommand error: \${e.message}\")\n")
                writer.write("                }\n")
                writer.write("                return true\n")
                writer.write("            }\n")
                writer.write("        })\n")
            }

            writer.write("    }\n}\n")
        }

        return true
    }
}