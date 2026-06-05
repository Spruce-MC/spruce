package org.spruce.processor.velocity.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter

object CommandRegistryVelocityGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val commands = clazz.getAllFunctions()
            .filter { fn ->
                fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.plugin.Command"
                }
            }
            .toList()

        if (commands.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val fileName = "${simpleName}__Commands"
        val hasLocks = commands.any { LockInvocationGenerator.hasLock(it) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import com.velocitypowered.api.command.SimpleCommand\n")
            writer.write("import com.velocitypowered.api.command.CommandManager\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import org.spruce.api.plugin.SpruceLoaderPlugin\n")
            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }
            writer.write("import ${clazz.qualifiedName!!.asString()}\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val commandManager = ctx.get(CommandManager::class.java)!!\n")
            writer.write("        val plugin = ctx.get(SpruceLoaderPlugin::class.java)!!\n\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for (cmd in commands) {
                val annotation = cmd.annotations.first {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                            "org.spruce.api.plugin.Command"
                }

                val name = annotation.arguments
                    .find { it.name?.asString() == "value" }
                    ?.value as? String ?: continue

                val aliases = annotation.arguments
                    .find { it.name?.asString() == "aliases" }
                    ?.value as? List<*> ?: emptyList<String>()

                val methodName = cmd.simpleName.asString()
                val params = cmd.parameters

                val senderParamName = params.getOrNull(0)?.name?.asString() ?: "sender"
                val argsParamName = params.getOrNull(1)?.name?.asString() ?: "args"
                val expectsArgs = params.size == 2

                val aliasesString = aliases.joinToString(", ") { "\"$it\"" }.let {
                    if (it.isEmpty()) {
                        ""
                    } else {
                        ".aliases($it)"
                    }
                }

                val className = "${simpleName}_${methodName}_Command"

                val call = if (expectsArgs) {
                    "instance.$methodName($senderParamName, $argsParamName)"
                } else {
                    "instance.$methodName($senderParamName)"
                }

                writer.write("        class $className(private val instance: $simpleName) : SimpleCommand {\n")
                writer.write("            override fun execute(invocation: SimpleCommand.Invocation) {\n")
                writer.write("                val $senderParamName = invocation.source()\n")

                if (expectsArgs) {
                    writer.write("                val $argsParamName = invocation.arguments()\n")
                }

                if (LockInvocationGenerator.hasLock(cmd)) {
                    val wrapped = LockInvocationGenerator.wrapCall(cmd, call)
                        .prependIndent("                ")

                    writer.write("$wrapped\n")
                } else {
                    writer.write("                $call\n")
                }

                writer.write("            }\n")
                writer.write("        }\n\n")

                writer.write("        commandManager.register(commandManager.metaBuilder(\"$name\")$aliasesString.plugin(plugin).build(), $className(instance))\n\n")
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }
}