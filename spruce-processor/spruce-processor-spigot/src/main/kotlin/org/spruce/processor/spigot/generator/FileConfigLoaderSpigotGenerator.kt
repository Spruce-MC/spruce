package org.spruce.processor.spigot.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object FileConfigLoaderSpigotGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.FileConfig"
        } ?: return false

        val filePath = annotation.arguments.find { it.name?.asString() == "value" }?.value as? String ?: return false

        val packageName = clazz.packageName.asString()
        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName!!.asString()

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, "${simpleName}__Loader"
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import java.io.File\n")
            writer.write("import java.util.jar.JarFile\n")
            writer.write("import org.bukkit.plugin.java.JavaPlugin\n")
            writer.write("import org.bukkit.configuration.file.YamlConfiguration\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import $qualifiedName\n\n")

            writer.write("object ${simpleName}__Loader {\n")
            writer.write("    fun register(ctx: SpruceContext, instance: $simpleName) {\n")
            writer.write("        val codeSource = instance::class.java.protectionDomain.codeSource\n")
            writer.write("            ?: throw IllegalStateException(\"Cannot determine JAR for \${instance::class.java.name}\")\n")
            writer.write("        val jarFile = File(codeSource.location.toURI())\n")
            writer.write("        val pluginName = jarFile.name.removeSuffix(\".jar\")\n")
            writer.write("        val file = File(File(jarFile.parentFile, \"configs/\$pluginName\"), \"$filePath\")\n")
            writer.write("        if (!file.exists()) {\n")
            writer.write("            JarFile(jarFile).use { jar ->\n")
            writer.write("                val entry = jar.getEntry(\"$filePath\")\n")
            writer.write("                    ?: throw IllegalStateException(\"Missing config file: $filePath and no fallback in JAR\")\n")
            writer.write("                jar.getInputStream(entry).use { input ->\n")
            writer.write("                    file.parentFile.mkdirs()\n")
            writer.write("                    file.outputStream().use { output -> input.copyTo(output) }\n")
            writer.write("                }\n")
            writer.write("            }\n")
            writer.write("        }\n")
            writer.write("        val config = YamlConfiguration.loadConfiguration(file)\n")
            generateFieldAssignments(writer, "instance", clazz, "config", "")
            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }

    private fun generateFieldAssignments(
        writer: OutputStreamWriter,
        instanceName: String,
        clazz: KSClassDeclaration,
        configVar: String,
        pathPrefix: String
    ) {
        for (field in clazz.getAllProperties()) {
            if (field.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.plugin.Inject"
                }) continue

            val name = field.simpleName.asString()
            val fullPath = if (pathPrefix.isEmpty()) name else "$pathPrefix.$name"
            val type = field.type.resolve()
            val typeName = type.declaration.qualifiedName?.asString()

            val fieldType = when (typeName) {
                "java.lang.String", "kotlin.String" -> "getString"
                "int", "java.lang.Integer", "kotlin.Int" -> "getInt"
                "boolean", "java.lang.Boolean", "kotlin.Boolean" -> "getBoolean"
                "double", "java.lang.Double", "kotlin.Double" -> "getDouble"
                "java.util.List", "kotlin.collections.List" -> "getStringList"
                else -> null
            }

            if (fieldType != null) {
                writer.write("        $instanceName.$name = $configVar.$fieldType(\"$fullPath\")\n")
            } else if (type.declaration is KSClassDeclaration) {
                val subClass = type.declaration as KSClassDeclaration
                val subVar = "${name}_cfg"
                writer.write("        val $subVar = ${subClass.qualifiedName!!.asString()}()\n")
                generateFieldAssignments(writer, subVar, subClass, configVar, fullPath)
                writer.write("        $instanceName.$name = $subVar\n")
            }
        }
    }
}
