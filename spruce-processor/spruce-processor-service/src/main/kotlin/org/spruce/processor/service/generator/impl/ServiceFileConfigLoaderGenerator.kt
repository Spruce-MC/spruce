package org.spruce.processor.service.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter

object ServiceFileConfigLoaderGenerator : CodeGenerator {

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    "org.spruce.api.annotation.FileConfig"
        } ?: return false

        val filePath = annotation.arguments
            .find { it.name?.asString() == "value" }
            ?.value as? String ?: return false

        val packageName = clazz.packageName.asString()
        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName?.asString() ?: return false
        val fileName = "${simpleName}__Loader"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import java.io.File\n")
            writer.write("import com.fasterxml.jackson.databind.ObjectMapper\n")
            writer.write("import com.fasterxml.jackson.dataformat.yaml.YAMLFactory\n")
            writer.write("import com.fasterxml.jackson.module.kotlin.registerKotlinModule\n")
            writer.write("import org.spruce.api.context.SpruceContext\n")
            writer.write("import org.spruce.service.SpruceServiceRuntime\n")
            writer.write("import $qualifiedName\n\n")

            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, runtime: SpruceServiceRuntime, instance: $simpleName) {\n")
            writer.write("        val file = File(runtime.getConfigDirectory(), \"$filePath\")\n")
            writer.write("        if (!file.exists()) {\n")
            writer.write("            file.parentFile.mkdirs()\n")
            writer.write("            val resource = instance::class.java.classLoader.getResourceAsStream(\"$filePath\")\n")
            writer.write("                ?: throw IllegalStateException(\"Missing config file: $filePath and no fallback resource\")\n")
            writer.write("            resource.use { input ->\n")
            writer.write("                file.outputStream().use { output -> input.copyTo(output) }\n")
            writer.write("            }\n")
            writer.write("        }\n\n")

            writer.write("        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()\n")
            writer.write("        val loaded = mapper.readValue(file, $simpleName::class.java)\n")
            writer.write("        copyFields(loaded, instance)\n")
            writer.write("    }\n\n")

            writer.write("    private fun copyFields(from: $simpleName, to: $simpleName) {\n")
            for (field in clazz.getAllProperties()) {
                val name = field.simpleName.asString()
                writer.write("        to.$name = from.$name\n")
            }
            writer.write("    }\n")

            writer.write("}\n")
        }

        return true
    }
}