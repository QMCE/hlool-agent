package rj.cocacode.services.mcp

import kotlinx.serialization.Serializable

@Serializable
enum class ConfigScope {
    LOCAL, USER, PROJECT, DYNAMIC, ENTERPRISE, CLAUDEAI, MANAGED
}

@Serializable
enum class Transport {
    STDIO, SSE, SSE_IDE, HTTP, WS, SDK
}

@Serializable
data class McpStdioServerConfig(
    val type: String = "stdio",
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String>? = null
)

@Serializable
data class McpSseServerConfig(
    val type: String = "sse",
    val url: String,
    val headers: Map<String, String>? = null
)

@Serializable
data class McpHttpServerConfig(
    val type: String = "http",
    val url: String,
    val headers: Map<String, String>? = null
)

@Serializable
data class McpOAuthConfig(
    val clientId: String? = null,
    val callbackPort: Int? = null,
    val authServerMetadataUrl: String? = null,
    val xaa: Boolean = false
)

@Serializable
data class McpServerConfig(
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String>? = null,
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val transport: Transport = Transport.STDIO,
    val scope: ConfigScope = ConfigScope.LOCAL,
    val enabled: Boolean = true,
    val oauth: McpOAuthConfig? = null
)

data class McpConnection(
    val name: String,
    val config: McpServerConfig,
    val client: McpClient? = null,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

object McpConfigManager {
    private val servers = mutableMapOf<String, McpServerConfig>()
    
    fun addServer(name: String, config: McpServerConfig) {
        servers[name] = config
    }
    
    fun removeServer(name: String) {
        servers.remove(name)
    }
    
    fun getServer(name: String): McpServerConfig? = servers[name]
    
    fun getAllServers(): Map<String, McpServerConfig> = servers.toMap()
    
    fun setServerEnabled(name: String, enabled: Boolean) {
        servers[name]?.let { servers[name] = it.copy(enabled = enabled) }
    }
}