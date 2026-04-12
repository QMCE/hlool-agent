package rj.cocacode.engine

import rj.cocacode.state.AppStateManager
import rj.cocacode.state.AppState
import rj.cocacode.types.Message
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolResult
import rj.cocacode.utils.generateUuid
import rj.cocacode.services.api.ApiClient
import rj.cocacode.config.ApiConfig

class QueryEngine {
    private var isProcessing = false
    private val messageHistory = mutableListOf<Message>()
    
    suspend fun processQuery(prompt: String): QueryResponse {
        if (isProcessing) {
            return QueryResponse.Error("Already processing a query")
        }
        
        isProcessing = true
        
        try {
            val userMessage = Message(
                id = generateUuid(),
                type = rj.cocacode.types.MessageType.USER,
                content = prompt
            )
            
            messageHistory.add(userMessage)
            AppStateManager.addMessage(userMessage)
            
            val response = callModel(prompt)
            
            val assistantMessage = Message(
                id = generateUuid(),
                type = rj.cocacode.types.MessageType.ASSISTANT,
                content = response.content
            )
            
            messageHistory.add(assistantMessage)
            AppStateManager.addMessage(assistantMessage)
            
            isProcessing = false
            return QueryResponse.Success(response)
            
        } catch (e: Exception) {
            isProcessing = false
            return QueryResponse.Error(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun callModel(prompt: String): ModelResponse {
        val systemPrompt = buildSystemPrompt()
        val messages = messageHistory.map { mapToApiFormat(it) }
        
        val result = ApiClient.post("/messages", mapOf(
            "model" to ApiConfig.model,
            "max_tokens" to 4096,
            "system" to systemPrompt,
            "messages" to messages
        ))
        
        return result.fold(
            onSuccess = { parseApiResponse(it) },
            onFailure = { ModelResponse("Error: ${it.message}", null) }
        )
    }
    
    private fun buildSystemPrompt(): String {
        return """
You are CocaCode, an AI coding assistant.
Help the user with their coding tasks.
Be concise and helpful.
        """.trimIndent()
    }
    
    private fun mapToApiFormat(message: Message): Map<String, Any> {
        return mapOf(
            "role" to when (message.type) {
                rj.cocacode.types.MessageType.USER -> "user"
                rj.cocacode.types.MessageType.ASSISTANT -> "assistant"
                rj.cocacode.types.MessageType.SYSTEM -> "system"
                else -> "user"
            },
            "content" to message.content
        )
    }
    
    private fun parseApiResponse(json: String): ModelResponse {
        return ModelResponse("Parsed response", null)
    }
    
    suspend fun executeTool(toolName: String, input: Map<String, Any>): ToolResult {
        val tool = ToolRegistry.get(toolName)
        return if (tool != null) {
            tool.execute(input)
        } else {
            ToolResult(text = "Tool not found: $toolName", isError = true)
        }
    }
    
    fun getHistory(): List<Message> = messageHistory.toList()
    
    fun clearHistory() {
        messageHistory.clear()
    }
}

sealed class QueryResponse {
    data class Success(val response: ModelResponse) : QueryResponse()
    data class Error(val message: String) : QueryResponse()
}

data class ModelResponse(
    val content: String,
    val usage: rj.cocacode.utils.TokenEstimator.TokenUsage?
)

object QueryEngineManager {
    private var engine: QueryEngine? = null
    
    fun getEngine(): QueryEngine {
        if (engine == null) {
            engine = QueryEngine()
        }
        return engine!!
    }
    
    fun reset() {
        engine = null
    }
}