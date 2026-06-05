package org.spruce.processor.commons.generator.lock

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.Writer

object LockInvocationGenerator {

    private const val LOCK_ANNOTATION = "org.spruce.api.plugin.WithDistributedLock"

    fun hasLock(fn: KSFunctionDeclaration): Boolean {
        return fn.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == LOCK_ANNOTATION
        }
    }

    fun isAsync(fn: KSFunctionDeclaration): Boolean {
        val returnType = fn.returnType?.resolve() ?: return false
        return returnType.declaration.qualifiedName?.asString() == "java.util.concurrent.CompletableFuture"
    }

    fun writeImports(writer: Writer) {
        writer.write("import java.time.Duration\n")
        writer.write("import org.spruce.api.lock.DistributedLockManager\n")
    }

    fun writeLockManager(writer: Writer) {
        writer.write("        val lockManager = ctx.get(DistributedLockManager::class.java)!!\n")
    }

    fun wrapCall(fn: KSFunctionDeclaration, call: String): String {
        return if (isAsync(fn)) {
            wrapAsyncCall(fn, call)
        } else {
            wrapSyncCall(fn, call)
        }
    }

    fun wrapSyncCall(fn: KSFunctionDeclaration, call: String): String {
        val data = read(fn)

        return """
            lockManager.withLock(
                ${data.keyExpression},
                Duration.ofMillis(${data.ttlMillis}L),
                Duration.ofMillis(${data.acquireTimeoutMillis}L)
            ) {
                $call
                null
            }
        """.trimIndent()
    }

    fun wrapAsyncCall(fn: KSFunctionDeclaration, call: String): String {
        val data = read(fn)

        return """
            lockManager.withLockAsync(
                ${data.keyExpression},
                Duration.ofMillis(${data.ttlMillis}L),
                Duration.ofMillis(${data.acquireTimeoutMillis}L)
            ) {
                $call
            }
        """.trimIndent()
    }

    fun read(fn: KSFunctionDeclaration): LockData {
        val annotation = fn.annotations.first {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == LOCK_ANNOTATION
        }

        val keyTemplate = annotation.arguments
            .firstOrNull { it.name?.asString() == "value" }
            ?.value as? String
            ?: error("@WithDistributedLock requires value")

        val ttl = annotation.arguments
            .firstOrNull { it.name?.asString() == "ttl" }
            ?.value as? String
            ?: error("@WithDistributedLock requires ttl")

        val acquireTimeout = annotation.arguments
            .firstOrNull { it.name?.asString() == "acquireTimeout" }
            ?.value as? String
            ?: "0s"

        return LockData(
            keyExpression = buildKeyExpression(keyTemplate, fn.parameters),
            ttlMillis = parseDurationMillis(ttl),
            acquireTimeoutMillis = parseDurationMillis(acquireTimeout)
        )
    }

    private fun buildKeyExpression(
        template: String,
        parameters: List<KSValueParameter>
    ): String {
        val parameterNames = parameters
            .mapNotNull { it.name?.asString() }
            .toSet()

        val regex = Regex("\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)}")

        val builder = StringBuilder("\"")
        var lastIndex = 0

        for (match in regex.findAll(template)) {
            val expression = match.groupValues[1]
            val rootName = expression.substringBefore('.')

            if (rootName !in parameterNames) {
                error("Unknown @WithDistributedLock key parameter: {$rootName}")
            }

            builder.append(template.substring(lastIndex, match.range.first))
            builder.append("\${")
            builder.append(expression)
            builder.append("}")

            lastIndex = match.range.last + 1
        }

        builder.append(template.substring(lastIndex))
        builder.append("\"")

        return builder.toString()
    }

    private fun parseDurationMillis(value: String): Long {
        val text = value.trim().lowercase()

        return when {
            text.endsWith("ms") -> text.removeSuffix("ms").toLong()
            text.endsWith("s") -> text.removeSuffix("s").toLong() * 1_000
            text.endsWith("m") -> text.removeSuffix("m").toLong() * 60_000
            text.endsWith("h") -> text.removeSuffix("h").toLong() * 3_600_000
            else -> error("Unsupported duration format: $value")
        }
    }

    data class LockData(
        val keyExpression: String,
        val ttlMillis: Long,
        val acquireTimeoutMillis: Long
    )
}