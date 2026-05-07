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
import rj.cocacode.config.ApiConfig
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
    
    private val apiKey get() = ApiConfig.apiKey
    private val baseUrl get() = "${ApiConfig.baseUrl}/v1"
    
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
        val map = Json.decodeFromString<Map<String, JsonElement>>(json)
        return ChatResponse(
            id = map["id"]?.jsonPrimitive?.content ?: "",
            type = map["type"]?.jsonPrimitive?.content ?: "message",
            role = "assistant",
            content = parseContent(map["content"]),
            model = map["model"]?.jsonPrimitive?.content ?: "",
            stopReason = map["stop_reason"]?.jsonPrimitive?.content,
            usage = parseUsage(map["usage"])
        )
    }
    
    private fun parseContent(content: JsonElement?): List<ContentBlock> {
        if (content is JsonArray) {
            return content.mapNotNull { element ->
                val block = element.jsonObject
                when (block["type"]?.jsonPrimitive?.content) {
                    "text" -> ContentBlock.TextBlock(block["text"]?.jsonPrimitive?.content ?: "")
                    "tool_use" -> {
                        val inputJson = block["input"]?.jsonObject
                        val typedInput: Map<String, Any> = inputJson?.entries?.associate {
                            it.key to it.value.toKotlinObject()
                        } ?: emptyMap()
                        ContentBlock.ToolUseBlock(
                            id = block["id"]?.jsonPrimitive?.content ?: "",
                            name = block["name"]?.jsonPrimitive?.content ?: "",
                            input = typedInput
                        )
                    }
                    else -> null
                }
            }
        }
        return emptyList()
    }
    
    private fun parseUsage(usage: JsonElement?): Usage {
        val obj = usage?.jsonObject
        return Usage(
            inputTokens = obj?.get("input_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = obj?.get("output_tokens")?.jsonPrimitive?.int ?: 0
        )
    }
    
    private fun JsonElement.toKotlinObject(): Any = when (this) {
        is JsonPrimitive -> {
            if (isString) content else jsonPrimitive.content
        }
        is JsonArray -> jsonArray.map { it.toKotlinObject() }
        is JsonObject -> jsonObject.entries.associate { it.key to it.value.toKotlinObject() }
        JsonNull -> Any()
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