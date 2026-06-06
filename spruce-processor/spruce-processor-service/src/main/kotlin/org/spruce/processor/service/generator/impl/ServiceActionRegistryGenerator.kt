package org.spruce.processor.service.generator.impl

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import org.spruce.processor.commons.generator.CodeGenerator
import org.spruce.processor.commons.generator.lock.LockInvocationGenerator
import java.io.OutputStreamWriter
import java.util.concurrent.CompletableFuture

object ServiceActionRegistryGenerator : CodeGenerator {

    private const val SERVICE_MODEL = "org.spruce.api.service.ServiceModel"
    private const val SERVICE_CALL = "org.spruce.api.service.ServiceCall"

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val actions = collectActions(clazz, environment)
        if (actions.isEmpty()) return false

        val packageName = clazz.containingFile?.packageName?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName?.asString() ?: return false
        val fileName = "${simpleName}__Actions"
        val hasLocks = actions.any { LockInvocationGenerator.hasLock(it.implFunction) }

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import $qualifiedName\n")
            writer.write("import org.spruce.api.plugin.SpruceContext\n")
            writer.write("import org.spruce.service.SpruceServiceRuntime\n")
            writer.write("import java.util.concurrent.CompletableFuture\n")

            if (hasLocks) {
                LockInvocationGenerator.writeImports(writer)
            }

            writer.write("\n")
            writer.write("object $fileName {\n")
            writer.write("    fun register(ctx: SpruceContext, runtime: SpruceServiceRuntime, instance: $simpleName) {\n")

            if (hasLocks) {
                LockInvocationGenerator.writeLockManager(writer)
            }

            for (action in actions) {
                writer.write("        runtime.registerHandler(\"${action.actionName}\") { payload ->\n")

                if (action.requestType != null) {
                    writer.write("            val ${action.paramName} = runtime.readPayload(payload, ${action.requestType}::class.java)\n")
                }

                val call = if (action.requestType != null) {
                    "instance.${action.methodName}(${action.paramName})"
                } else {
                    "instance.${action.methodName}()"
                }

                val finalCall = if (LockInvocationGenerator.hasLock(action.implFunction)) {
                    LockInvocationGenerator.wrapAsyncCall(action.implFunction, call)
                } else {
                    call
                }

                writer.write("            $finalCall.thenApply { response ->\n")
                writer.write("                runtime.writeResponse(response)\n")
                writer.write("            }\n")
                writer.write("        }\n\n")
            }

            writer.write("    }\n")
            writer.write("}\n")
        }

        return true
    }

    private fun collectActions(
        clazz: KSClassDeclaration,
        environment: SymbolProcessorEnvironment
    ): List<ServiceAction> {
        val result = mutableListOf<ServiceAction>()

        for (superTypeRef in clazz.superTypes) {
            val superType = superTypeRef.resolve()
            val declaration = superType.declaration as? KSClassDeclaration ?: continue

            val modelAnnotation = declaration.annotations.firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SERVICE_MODEL
            } ?: continue

            val serviceName = modelAnnotation.arguments
                .find { it.name?.asString() == "value" || it.name?.asString() == "service" }
                ?.value as? String

            if (serviceName == null) {
                environment.logger.error("@ServiceModel requires service name", declaration)
                continue
            }

            for (ifaceFunction in declaration.getDeclaredFunctions()) {
                val methodName = ifaceFunction.simpleName.asString()

                val returnType = ifaceFunction.returnType?.resolve()
                val returnTypeName = returnType?.declaration?.qualifiedName?.asString()

                if (returnTypeName != CompletableFuture::class.java.name) {
                    environment.logger.error("Service method $methodName must return CompletableFuture<T>", ifaceFunction)
                    continue
                }

                if (ifaceFunction.parameters.size > 1) {
                    environment.logger.error("Service method $methodName must have zero or one argument", ifaceFunction)
                    continue
                }

                val implFunction = findImplementation(clazz, ifaceFunction)

                if (implFunction == null) {
                    environment.logger.error("Missing implementation for service method $methodName", clazz)
                    continue
                }

                val actionName = ifaceFunction.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == SERVICE_CALL
                }?.arguments
                    ?.find { it.name?.asString() == "value" }
                    ?.value as? String ?: methodName

                val param = implFunction.parameters.firstOrNull()
                val paramName = param?.name?.asString() ?: "request"
                val requestType = param?.type?.resolve()?.declaration?.qualifiedName?.asString()

                result += ServiceAction(
                    actionName = actionName,
                    methodName = methodName,
                    implFunction = implFunction,
                    paramName = paramName,
                    requestType = requestType
                )
            }
        }

        return result
    }

    private fun findImplementation(
        clazz: KSClassDeclaration,
        ifaceFunction: KSFunctionDeclaration
    ): KSFunctionDeclaration? {
        val name = ifaceFunction.simpleName.asString()

        val ifaceParams = ifaceFunction.parameters.map {
            it.type.resolve().declaration.qualifiedName?.asString()
        }

        return clazz.getAllFunctions().firstOrNull { fn ->
            if (fn.simpleName.asString() != name) return@firstOrNull false

            val fnParams = fn.parameters.map {
                it.type.resolve().declaration.qualifiedName?.asString()
            }

            fnParams == ifaceParams
        }
    }

    private data class ServiceAction(
        val actionName: String,
        val methodName: String,
        val implFunction: KSFunctionDeclaration,
        val paramName: String,
        val requestType: String?
    )
}