package rj.cocacode.query

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import rj.cocacode.types.Message
import rj.cocacode.services.api.ClaudeApiClient
import rj.cocacode.services.compact.Compactor

/**
 * CallModel dependency - model invocation with streaming support.
 */
/**
 * Input for model invocation.
 */
internal data class CallModelInput(
    val messages: List<Map<String, Any>>,
    val systemPrompt: String = "",
    val options: CallModelOptions = CallModelOptions()
)

/**
 * Options for model invocation.
 */
internal data class CallModelOptions(
    val model: String = "claude-sonnet-4-20250514",
    val maxOutputTokensOverride: Int? = null
)

/**
 * MicroCompact dependency - lightweight message compression before full autocompact.
 */
typealias MicroCompact = suspend (MicroCompactInput) -> MicroCompactOutput

/**
 * Input for micro compact operation.
 */
internal data class MicroCompactInput(
    val messages: List<Message>,
    val options: Map<String, Any> = emptyMap()
)

/**
 * Output from micro compact operation.
 */
internal data class MicroCompactOutput(
    val messages: List<Message>,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * AutoCompact dependency - context compaction when approaching token limits.
 */
typealias AutoCompact = suspend (AutoCompactInput, QuerySource, AutoCompactTrackingState?, Int) -> AutoCompactResult

/**
 * Input for auto compact operation.
 */
internal data class AutoCompactInput(
    val messages: List<Message>,
    val currentTokens: Int,
    val maxTokens: Int
)

/**
 * Tracking state for auto compact.
 */
internal data class AutoCompactTrackingState(
    val consecutiveFailures: Int = 0
)

/**
 * Result from auto compact operation.
 */
internal data class AutoCompactResult(
    val output: AutoCompactOutput?,
    val consecutiveFailures: Int?
)

/**
 * Output from auto compact operation.
 */
internal data class AutoCompactOutput(
    val summaryMessages: List<Message>,
    val preCompactTokenCount: Int,
    val postCompactTokenCount: Int
)

/**
 * UuidGenerator - platform dependency for UUID generation.
 */
typealias UuidGenerator = () -> String

/**
 * CallModel - model invocation with streaming support.
 */
typealias CallModel = suspend (CallModelInput) -> Flow<Message>

/**
 * Query dependencies for dependency injection.
 */
data class QueryDeps(
    val callModel: CallModel,
    val microcompact: MicroCompact,
    val autocompact: AutoCompact,
    val uuid: UuidGenerator
)

/**
 * Production dependencies - uses real service implementations.
 */
fun productionDeps(): QueryDeps = QueryDeps(
    callModel = ::callModelWithStreaming,
    microcompact = ::microcompactMessages,
    autocompact = ::autoCompactIfNeeded,
    uuid = { UUID.randomUUID().toString() }
)

/**
 * Model invocation with streaming support.
 * Wraps ClaudeApiClient to match the CallModel signature.
 */
private suspend fun callModelWithStreaming(input: CallModelInput): Flow<Message> = flow {
    val client = ClaudeApiClient()
    
    val chatMessages = input.messages.map { msg ->
        rj.cocacode.services.api.ChatRequest.Message(
            role = msg["role"] as? String ?: "user",
            content = msg["content"] as? String ?: ""
        )
    }
    
    val request = rj.cocacode.services.api.ChatRequest(
        model = input.options.model,
        maxTokens = input.options.maxOutputTokensOverride ?: 4096,
        system = input.systemPrompt.takeIf { it.isNotEmpty() },
        messages = chatMessages
    )
    
    client.streamMessage(request) { chunk ->
        // Process streaming chunks - emit parsed messages
        // Full implementation would parse SSE stream and emit individual messages
    }
}

/**
 * Micro compact - lightweight compression before full autocompact.
 */
private suspend fun microcompactMessages(input: MicroCompactInput): MicroCompactOutput {
    val trimmed = input.messages.map { msg ->
        if (msg.content.length > 8000) {
            msg.copy(content = msg.content.take(8000) + "... [trimmed]")
        } else {
            msg
        }
    }.filter { msg ->
        msg.type != rj.cocacode.types.MessageType.TOOL_RESULT
    }
    
    return MicroCompactOutput(messages = trimmed)
}

/**
 * Auto compact - context compaction when approaching token limits.
 */
private suspend fun autoCompactIfNeeded(
    input: AutoCompactInput,
    querySource: QuerySource,
    tracking: AutoCompactTrackingState?,
    snipTokensFreed: Int
): AutoCompactResult {
    val totalContent = input.messages.joinToString("") { it.content }
    val estimatedTokens = totalContent.length / 4
    
    val compactThreshold = 150000
    
    return if (estimatedTokens > compactThreshold) {
        val result = Compactor.compact(input.messages)
        
        AutoCompactResult(
            output = AutoCompactOutput(
                summaryMessages = if (result.summary != null) {
                    listOf(
                        rj.cocacode.types.Message(
                            id = UUID.randomUUID().toString(),
                            type = rj.cocacode.types.MessageType.SYSTEM,
                            content = result.summary
                        )
                    )
                } else {
                    emptyList()
                },
                preCompactTokenCount = estimatedTokens,
                postCompactTokenCount = result.messages.joinToString("") { it.content }.length / 4
            ),
            consecutiveFailures = 0
        )
    } else {
        AutoCompactResult(output = null, consecutiveFailures = null)
    }
}
