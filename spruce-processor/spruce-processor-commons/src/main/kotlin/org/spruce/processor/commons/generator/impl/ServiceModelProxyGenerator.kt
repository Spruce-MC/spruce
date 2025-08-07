package org.spruce.processor.commons.generator.impl

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import org.spruce.processor.commons.generator.CodeGenerator
import java.io.OutputStreamWriter
import java.util.concurrent.CompletableFuture

object ServiceModelProxyGenerator : CodeGenerator {

    private val skipMethods = setOf("equals", "hashCode", "toString")

    override fun process(clazz: KSClassDeclaration, environment: SymbolProcessorEnvironment): Boolean {
        val annotation = clazz.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.service.ServiceModel"
        } ?: return false

        if (clazz.classKind != ClassKind.INTERFACE) {
            environment.logger.error("@ServiceModel must be applied to interface", clazz)
            return false
        }

        val serviceName = annotation.arguments.find { it.name?.asString() == "value" || it.name?.asString() == "service" }
            ?.value as? String ?: return false

        val packageName = clazz.containingFile?.packageName?.asString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Can't determine package for ${clazz.simpleName.asString()}")

        val simpleName = clazz.simpleName.asString()
        val qualifiedName = clazz.qualifiedName?.asString() ?: return false
        val fileName = "${simpleName}__Proxy"

        val file = environment.codeGenerator.createNewFile(
            Dependencies(false), packageName, fileName
        )

        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import $qualifiedName\n")
            writer.write("import org.spruce.api.gateway.GatewayCall\n")
            writer.write("import org.spruce.api.gateway.SpruceGatewayClient\n")
            writer.write("import java.util.concurrent.CompletableFuture\n")
            writer.write("import javax.annotation.processing.Generated\n\n")

            writer.write("@Generated(\"Spruce KSP\")\n")
            writer.write("class $fileName(private val gatewayClient: SpruceGatewayClient) : $simpleName {\n")

            for (function in clazz.getAllFunctions()) {
                val methodName = function.simpleName.asString()

                if (methodName in skipMethods) continue

                val returnType = function.returnType?.resolve()
                val returnTypeName = returnType?.declaration?.qualifiedName?.asString()

                if (returnTypeName != CompletableFuture::class.java.name) {
                    environment.logger.error("Method $methodName must return CompletableFuture<T>", function)
                    continue
                }

                val typeArg = returnType?.arguments?.firstOrNull()?.type?.resolve()
                val responseTypeFqcn = typeArg?.declaration?.qualifiedName?.asString() ?: continue

                if (function.parameters.size != 1) {
                    environment.logger.error("Method $methodName must have exactly one argument", function)
                    continue
                }

                val param = function.parameters.first()
                val paramFqcn = param.type.resolve().declaration.qualifiedName?.asString() ?: continue
                val paramName = "request"

                val serviceCallAnnotation = function.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == "org.spruce.api.service.ServiceCall"
                }

                val actionName = serviceCallAnnotation
                    ?.arguments
                    ?.find { it.name?.asString() == "value" }
                    ?.value as? String ?: methodName

                writer.write("    override fun $methodName($paramName: $paramFqcn): CompletableFuture<$responseTypeFqcn> {\n")
                writer.write("        return gatewayClient.call(\n")
                writer.write("            GatewayCall.of(\n")
                writer.write("                \"$serviceName\",\n")
                writer.write("                \"$actionName\",\n")
                writer.write("                $paramName,\n")
                writer.write("                $responseTypeFqcn::class.java\n")
                writer.write("            )\n")
                writer.write("        )\n")
                writer.write("    }\n\n")
            }

            writer.write("}")
        }

        return true
    }
}
