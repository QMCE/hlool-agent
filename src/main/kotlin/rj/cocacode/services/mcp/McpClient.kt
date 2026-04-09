package rj.cocacode.services.mcp

import kotlinx.coroutines.*
import rj.cocacode.utils.generateUuid
import rj.cocacode.utils.LogManager

class McpClient(
    private val config: McpServerConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    var isConnected = false
    val tools = mutableMapOf<String, McpTool>()
    val resources = mutableMapOf<String, McpResource>()
    private var transport: McpTransport? = null
    
    suspend fun connect(): Boolean {
        return try {
            transport = when (config.transport) {
                Transport.STDIO -> StdioTransport(config.command ?: "", config.args, config.env)
                Transport.SSE -> SseTransport(config.url!!, config.headers)
                Transport.HTTP -> HttpTransport(config.url!!, config.headers)
                else -> throw IllegalArgumentException("Unsupported transport: ${config.transport}")
            }
            
            transport?.connect()
            isConnected = true
            LogManager.logInfo("MCP client connected: ${config.command ?: config.url}")
            true
        } catch (e: Exception) {
            LogManager.logError("MCP connection failed: ${e.message}")
            false
        }
    }
    
    suspend fun disconnect() {
        isConnected = false
        transport?.disconnect()
        transport = null
        tools.clear()
        resources.clear()
    }
    
    suspend fun listTools(): List<McpTool> {
        if (!isConnected) throw IllegalStateException("Not connected")
        
        val request = McpRequest(
            jsonrpc = "2.0",
            id = generateUuid(),
            method = "tools/list"
        )
        
        val response = transport?.send(request) ?: return emptyList()
        return parseToolsResponse(response)
    }
    
    suspend fun callTool(name: String, arguments: Map<String, Any>): McpToolResult {
        if (!isConnected) throw IllegalStateException("Not connected")
        
        val request = McpRequest(
            jsonrpc = "2.0",
            id = generateUuid(),
            method = "tools/call",
            params = mapOf("name" to name, "arguments" to arguments)
        )
        
        val response = transport?.send(request)
        return response?.let { parseToolResult(it) } ?: McpToolResult("", false)
    }
    
    suspend fun listResources(): List<McpResource> {
        if (!isConnected) throw IllegalStateException("Not connected")
        
        val request = McpRequest(
            jsonrpc = "2.0",
            id = generateUuid(),
            method = "resources/list"
        )
        
        val response = transport?.send(request) ?: return emptyList()
        return parseResourcesResponse(response)
    }
    
    suspend fun readResource(uri: String): String {
        if (!isConnected) throw IllegalStateException("Not connected")
        
        val request = McpRequest(
            jsonrpc = "2.0",
            id = generateUuid(),
            method = "resources/read",
            params = mapOf("uri" to uri)
        )
        
        val response = transport?.send(request)
        return response?.toString() ?: ""
    }
    
    private fun parseToolsResponse(response: String): List<McpTool> {
        return try {
            val data = com.google.gson.Gson().fromJson(response, Map::class.java)
            val toolsList = data["result"] as? Map<*, *>
            val tools = toolsList?.get("tools") as? List<*>
            tools?.mapNotNull { tool ->
                val t = tool as? Map<*, *> ?: return@mapNotNull null
                McpTool(
                    name = t["name"] as? String ?: "",
                    description = t["description"] as? String ?: "",
                    inputSchema = (t["inputSchema"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                        (k as? String)?.let { key -> key to v as Any }
                    }?.toMap() ?: emptyMap()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            LogManager.logError("Failed to parse tools response: ${e.message}")
            emptyList()
        }
    }
    
    private fun parseToolResult(response: String): McpToolResult {
        return try {
            val data = com.google.gson.Gson().fromJson(response, Map::class.java)
            val result = data["result"] as? Map<*, *>
            val content = result?.get("content") as? List<*>
            val text = content?.firstOrNull()?.let { (it as? Map<*, *>)?.get("text") as? String } ?: ""
            McpToolResult(text, false)
        } catch (e: Exception) {
            McpToolResult("Error: ${e.message}", true)
        }
    }
    
    private fun parseResourcesResponse(response: String): List<McpResource> {
        return try {
            val data = com.google.gson.Gson().fromJson(response, Map::class.java)
            val resourcesList = data["result"] as? Map<*, *>
            val resources = resourcesList?.get("resources") as? List<*>
            resources?.mapNotNull { resource ->
                val r = resource as? Map<*, *> ?: return@mapNotNull null
                McpResource(
                    uri = r["uri"] as? String ?: "",
                    name = r["name"] as? String ?: "",
                    description = r["description"] as? String ?: "",
                    mimeType = r["mimeType"] as? String
                )
            } ?: emptyList()
        } catch (e: Exception) {
            LogManager.logError("Failed to parse resources response: ${e.message}")
            emptyList()
        }
    }
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

data class McpResource(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String?
)

data class McpToolResult(
    val content: String,
    val isError: Boolean
)

data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: Map<String, Any>? = null
)

interface McpTransport {
    suspend fun connect()
    suspend fun send(request: McpRequest): String?
    suspend fun disconnect()
}

class StdioTransport(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>?
) : McpTransport {
    private var process: Process? = null
    
    override suspend fun connect() {
        val builder = ProcessBuilder(command, *args.toTypedArray())
        env?.forEach { builder.environment().put(it.key, it.value) }
        process = builder.start()
    }
    
    override suspend fun send(request: McpRequest): String? {
        return null
    }
    
    override suspend fun disconnect() {
        process?.destroy()
        process = null
    }
}

class SseTransport(
    private val url: String,
    private val headers: Map<String, String>?
) : McpTransport {
    override suspend fun connect() {}
    override suspend fun send(request: McpRequest): String? = null
    override suspend fun disconnect() {}
}

class HttpTransport(
    private val url: String,
    private val headers: Map<String, String>?
) : McpTransport {
    override suspend fun connect() {}
    override suspend fun send(request: McpRequest): String? = null
    override suspend fun disconnect() {}
}

object McpClientManager {
    private val clients = mutableMapOf<String, McpClient>()
    
    suspend fun connectServer(name: String, config: McpServerConfig): Boolean {
        val client = McpClient(config)
        val success = client.connect()
        if (success) {
            clients[name] = client
        }
        return success
    }
    
    fun disconnectServer(name: String) {
        clients[name]?.let {
            runBlocking { it.disconnect() }
            clients.remove(name)
        }
    }
    
    fun getClient(name: String): McpClient? = clients[name]
    
    fun getAllClients(): Map<String, McpClient> = clients.toMap()
    
    suspend fun disconnectAll() {
        clients.values.forEach { it.disconnect() }
        clients.clear()
    }
}