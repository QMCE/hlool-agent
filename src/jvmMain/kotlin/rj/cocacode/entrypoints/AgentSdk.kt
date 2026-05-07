package rj.cocacode.entrypoints

import kotlinx.serialization.Serializable

@Serializable
data class SDKMessage(
    val type: String,
    val subtype: String? = null,
    val message: String? = null,
    val result: String? = null,
    val isError: Boolean = false,
    val uuid: String = rj.cocacode.utils.generateUuid(),
    val sessionId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SDKControlRequest(
    val type: String,
    val requestId: String? = null,
    val data: Map<String, Any>? = null
)

data class SDKControlResponse(
    val type: String,
    val requestId: String? = null,
    val success: Boolean = true,
    val data: Map<String, Any>? = null,
    val error: String? = null
)

@Serializable
data class SDKSessionInfo(
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val model: String? = null,
    val status: SessionStatus = SessionStatus.ACTIVE
)

enum class SessionStatus {
    ACTIVE, PAUSED, COMPLETED, ERROR
}

data class ForkSessionOptions(
    val sessionId: String? = null,
    val messages: List<Map<String, Any>>? = null,
    val context: Map<String, Any>? = null
)

@Serializable
data class ForkSessionResult(
    val sessionId: String,
    val success: Boolean
)

@Serializable
data class QueryOptions(
    val prompt: String? = null,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
)

@Serializable
data class QueryResult(
    val message: String,
    val usage: TokenUsage? = null,
    val finishReason: String? = null
)

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0
)

data class ToolCall(
    val name: String,
    val input: Map<String, Any> = emptyMap(),
    val id: String = rj.cocacode.utils.generateUuid()
)

@Serializable
data class ToolResult(
    val id: String,
    val name: String,
    val output: String,
    val isError: Boolean = false
)

object SdkClient {
    private var sessionId: String? = null
    
    fun initialize(config: Map<String, Any>): Boolean {
        sessionId = rj.cocacode.utils.generateUuid()
        return true
    }
    
    fun query(options: QueryOptions): QueryResult? {
        return null
    }
    
    fun forkSession(options: ForkSessionOptions): ForkSessionResult? {
        return ForkSessionResult(rj.cocacode.utils.generateUuid(), true)
    }
    
    fun getSessionInfo(): SDKSessionInfo? {
        return sessionId?.let { SDKSessionInfo(it) }
    }
    
    fun close() {
        sessionId = null
    }
}