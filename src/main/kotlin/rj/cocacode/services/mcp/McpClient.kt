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
        return response ?: ""
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
    private var input: java.io.OutputStream? = null
    private var output: java.io.BufferedReader? = null
    private var error: java.io.BufferedReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stderrBuffer = StringBuilder()
    private var closed = false
    private val timeoutMs = 30000L

    override suspend fun connect() {
        if (process != null) return
        try {
            val builder = ProcessBuilder(listOf(command) + args)
            builder.redirectErrorStream(false)
            env?.forEach { (k, v) -> builder.environment()[k] = v }
            process = builder.start()
            input = process!!.outputStream
            output = java.io.BufferedReader(java.io.InputStreamReader(process!!.inputStream, Charsets.UTF_8))
            error = java.io.BufferedReader(java.io.InputStreamReader(process!!.errorStream, Charsets.UTF_8))
            startStderrReader()
            delay(100)
            LogManager.logInfo("StdioTransport: Process started - $command")
        } catch (e: Exception) {
            LogManager.logError("StdioTransport: Failed to start - ${e.message}")
            closeStreams()
            throw e
        }
    }

    private fun startStderrReader() {
        scope.launch {
            try {
                error?.let { r ->
                    var line: String?
                    while (!closed && r.readLine().also { line = it } != null) {
                        line?.takeIf { it.isNotEmpty() }?.let {
                            stderrBuffer.appendLine(it)
                            if (stderrBuffer.length < 65536) LogManager.logDebug("StdioTransport stderr: $it")
                        }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override suspend fun send(request: McpRequest): String? {
        if (closed || process == null || input == null) throw IllegalStateException("Not connected")
        return try withContext(Dispatchers.IO) {
            val json = com.google.gson.Gson().toJson(request)
            input?.write((json + "\n").toByteArray(Charsets.UTF_8))
            input?.flush()
            LogManager.logDebug("StdioTransport: Sent - $json")
            val response = output?.readLine()
            if (response != null) {
                LogManager.logDebug("StdioTransport: Received - $response")
                response
            } else {
                LogManager.logError("StdioTransport: Timeout")
                null
            }
        } catch (e: Exception) {
            LogManager.logError("StdioTransport: Send failed - ${e.message}")
            null
        }
    }

    override suspend fun disconnect() {
        if (closed) return
        closed = true
        try {
            input?.close()
            process?.let { p ->
                val ok = p.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!ok) p.destroyForcibly()
            }
        } catch (e: Exception) {
            LogManager.logError("StdioTransport: Disconnect error - ${e.message}")
            process?.destroyForcibly()
        } finally {
            closeStreams()
        }
    }

    private fun closeStreams() {
        try { input?.close() } catch (e: Exception) { }
        try { output?.close() } catch (e: Exception) { }
        try { error?.close() } catch (e: Exception) { }
        process?.destroy()
        process = null
        input = null
        output = null
        error = null
        scope.cancel()
    }

    fun getStderr(): String = stderrBuffer.toString()
    fun isRunning(): Boolean = process?.isAlive == true
}

class SseTransport(
    private val url: String,
    private val headers: Map<String, String>?
) : McpTransport {
    override suspend fun connect() {
        LogManager.logInfo("SseTransport: CONNECT TO $url")
    }
    override suspend fun send(request: McpRequest): String? = null
    override suspend fun disconnect() {}
}

class HttpTransport(
    private val url: String,
    private val headers: Map<String, String>?
) : McpTransport {
    override suspend fun connect() {
        LogManager.logInfo("HttpTransport: CONNECT TO $url")
    }
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