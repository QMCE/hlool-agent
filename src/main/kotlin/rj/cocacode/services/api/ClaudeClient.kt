package rj.cocacode.services.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import rj.cocacode.utils.*
import java.util.concurrent.ConcurrentHashMap

class ClaudeApiClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120000
            connectTimeoutMillis = 30000
        }
    }
    
    private val apiKey = System.getenv("ANTHROPIC_API_KEY") ?: ""
    private val baseUrl = "https://api.anthropic.com/v1"
    
    suspend fun sendMessage(request: ChatRequest): Result<ChatResponse> {
        return try {
            val response = client.post("$baseUrl/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                header("content-type", "application/json")
                setBody(request.toJson())
            }
            
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                Result.success(parseChatResponse(body))
            } else {
                Result.failure(Exception("API error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun streamMessage(
        request: ChatRequest,
        onChunk: (String) -> Unit
    ): Result<Unit> {
        return try {
            client.post("$baseUrl/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                header("content-type", "application/json")
                setBody(request.toJson())
            }.bodyAsText().lines().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data != "[DONE]") {
                        onChunk(data)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseChatResponse(json: String): ChatResponse {
        val map = Json.decodeFromString<Map<String, Any>>(json)
        return ChatResponse(
            id = map["id"] as? String ?: "",
            type = map["type"] as? String ?: "message",
            role = "assistant",
            content = parseContent(map["content"]),
            model = map["model"] as? String ?: "",
            stopReason = map["stop_reason"] as? String,
            usage = parseUsage(map["usage"])
        )
    }
    
    private fun parseContent(content: Any?): List<ContentBlock> {
        if (content is List<*>) {
            return content.mapNotNull { block ->
                when (block) {
                    is Map<*, *> -> {
                        when (block["type"]) {
                            "text" -> ContentBlock.TextBlock(block["text"] as? String ?: "")
                            "tool_use" -> {
                                val inputMap = block["input"] as? Map<*, *>
                                val typedInput: Map<String, Any> = inputMap?.entries?.associate { 
                                    (it.key as String) to (it.value as Any) 
                                } ?: emptyMap()
                                ContentBlock.ToolUseBlock(
                                    id = block["id"] as? String ?: "",
                                    name = block["name"] as? String ?: "",
                                    input = typedInput
                                )
                            }
                            else -> null
                        }
                    }
                    else -> null
                }
            }
        }
        return emptyList()
    }
    
    private fun parseUsage(usage: Any?): Usage {
        if (usage is Map<*, *>) {
            return Usage(
                inputTokens = (usage["input_tokens"] as? Number)?.toInt() ?: 0,
                outputTokens = (usage["output_tokens"] as? Number)?.toInt() ?: 0
            )
        }
        return Usage()
    }
    
    private fun ChatRequest.toJson(): String {
        val messagesJson = messages.joinToString(",") { msg ->
            """{"role":"${msg.role}","content":"${msg.content.replace("\"", "\\\"")}"}"""
        }
        return buildString {
            append("{")
            append("\"model\":\"$model\",")
            append("\"max_tokens\":$maxTokens")
            if (system != null) {
                append(",\"system\":\"${system.replace("\"", "\\\"")}\"")
            }
            append(",\"messages\":[$messagesJson]")
            append("}")
        }
    }
}

@Serializable
data class ChatRequest(
    val model: String = "claude-sonnet-4-20250514",
    val maxTokens: Int = 4096,
    val system: String? = null,
    val messages: List<Message> = emptyList()
) {
    @Serializable
    data class Message(
        val role: String,
        val content: String
    )
}

data class ChatResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    val stopReason: String?,
    val usage: Usage
)

sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class ToolUseBlock(val id: String, val name: String, val input: Map<String, Any>) : ContentBlock()
}

data class Usage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0
)

object ApiRateLimiter {
    private val requestTimes = ConcurrentHashMap<String, Long>()
    private val limits = mapOf(
        "default" to RateLimit(60, 60000),
        "fast" to RateLimit(1000, 60000)
    )
    
    data class RateLimit(val maxRequests: Int, val windowMs: Long)
    
    fun canMakeRequest(key: String = "default"): Boolean {
        val limit = limits[key] ?: limits["default"]!!
        val now = System.currentTimeMillis()
        val lastRequest = requestTimes[key]
        
        return if (lastRequest == null) {
            true
        } else if (now - lastRequest > limit.windowMs) {
            true
        } else {
            requestTimes.values.count { it > now - limit.windowMs } < limit.maxRequests
        }
    }
    
    fun recordRequest(key: String = "default") {
        requestTimes[key] = System.currentTimeMillis()
    }
    
    suspend fun waitIfNeeded(key: String = "default") {
        while (!canMakeRequest(key)) {
            delay(100)
        }
        recordRequest(key)
    }
}