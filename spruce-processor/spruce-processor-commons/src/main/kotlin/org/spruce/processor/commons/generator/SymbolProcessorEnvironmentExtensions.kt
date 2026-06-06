package org.spruce.processor.commons.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import java.io.OutputStreamWriter

fun SymbolProcessorEnvironment.writeToFile(fileName: String, lines: List<String>) {
    val file = this.codeGenerator.createNewFile(
        Dependencies(false), "META-INF", fileName, "txt"
    )
    OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
        for (line in lines) writer.write("$line\n")
    }
}