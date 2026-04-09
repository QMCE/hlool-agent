package rj.cocacode.mcp

import rj.cocacode.services.mcp.McpClient
import rj.cocacode.services.mcp.McpServerConfig
import rj.cocacode.services.mcp.McpTool

object McpServer {
    private val clients = mutableMapOf<String, McpClient>()
    
    suspend fun connect(name: String, config: McpServerConfig): Boolean {
        val client = McpClient(config)
        val success = client.connect()
        if (success) {
            clients[name] = client
        }
        return success
    }
    
    suspend fun disconnect(name: String) {
        clients[name]?.disconnect()
        clients.remove(name)
    }
    
    fun isConnected(name: String): Boolean = clients[name]?.isConnected == true
    
    suspend fun listTools(name: String): List<McpTool> = clients[name]?.listTools() ?: emptyList()
    
    suspend fun callTool(name: String, toolName: String, args: Map<String, Any>) = 
        clients[name]?.callTool(toolName, args)
    
    fun getAllConnections(): Map<String, Boolean> = clients.mapValues { it.value.isConnected }
}

object McpProtocol {
    data class Request(
        val method: String,
        val params: Map<String, Any>? = null
    )
    
    data class Response(
        val result: Any? = null,
        val error: Error? = null
    )
    
    data class Error(
        val code: Int,
        val message: String
    )
    
    fun parseRequest(json: String): Request? {
        return try {
            val map = com.google.gson.Gson().fromJson(json, Map::class.java)
            Request(
                method = map["method"] as? String ?: "",
                params = map["params"] as? Map<String, Any>
            )
        } catch (e: Exception) { null }
    }
    
    fun createResponse(result: Any?): String {
        return com.google.gson.Gson().toJson(Response(result = result))
    }
    
    fun createError(code: Int, message: String): String {
        return com.google.gson.Gson().toJson(Response(error = Error(code, message)))
    }
}

object McpResourceManager {
    private val resources = mutableMapOf<String, Resource>()
    
    data class Resource(
        val uri: String,
        val name: String,
        val type: String,
        val content: String
    )
    
    fun register(uri: String, name: String, type: String, content: String) {
        resources[uri] = Resource(uri, name, type, content)
    }
    
    fun get(uri: String): Resource? = resources[uri]
    
    fun list(): List<Resource> = resources.values.toList()
    
    fun unregister(uri: String) {
        resources.remove(uri)
    }
}

object McpPromptManager {
    private val prompts = mutableMapOf<String, Prompt>()
    
    data class Prompt(
        val name: String,
        val description: String,
        val arguments: List<Argument>,
        val template: String
    )
    
    data class Argument(
        val name: String,
        val description: String,
        val required: Boolean = false
    )
    
    fun register(name: String, description: String, arguments: List<Argument>, template: String) {
        prompts[name] = Prompt(name, description, arguments, template)
    }
    
    fun get(name: String): Prompt? = prompts[name]
    
    fun list(): List<Prompt> = prompts.values.toList()
    
    fun render(name: String, args: Map<String, String>): String? {
        val prompt = prompts[name] ?: return null
        var result = prompt.template
        args.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}