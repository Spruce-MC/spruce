package org.spruce.loader.commons.gateway

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class GatewayConfig(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val serverId: String
) {

    companion object {

        @JvmStatic
        fun load(file: File): GatewayConfig {
            val env = System.getenv()

            val envEnabled = env["GATEWAY_ENABLED"]?.toBoolean()
            val envHost = env["GATEWAY_HOST"]
            val envPort = env["GATEWAY_PORT"]?.toIntOrNull()
            val envServerId = env["GATEWAY_SERVER_ID"]

            val yamlGatewayConfig: GatewayConfig? = if (file.exists()) {
                val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
                val wrapper = mapper.readValue<GatewayConfigWrapper>(file)
                wrapper.gateway
            } else null

            return GatewayConfig(
                enabled = envEnabled ?: yamlGatewayConfig?.enabled ?: true,
                host = envHost ?: yamlGatewayConfig?.host ?: "127.0.0.1",
                port = envPort ?: yamlGatewayConfig?.port ?: 6565,
                serverId = envServerId ?: yamlGatewayConfig?.serverId ?: "default-spruce-server"
            )
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GatewayConfigWrapper(
    val gateway: GatewayConfig
)
