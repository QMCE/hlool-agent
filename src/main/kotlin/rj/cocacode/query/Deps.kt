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
typealias CallModel = suspend (CallModelInput) -> Flow<Message>

/**
 * MicroCompact dependency - lightweight message compression before full autocompact.
 */
typealias MicroCompact = suspend (MicroCompactInput) -> MicroCompactOutput

/**
 * AutoCompact dependency - context compaction when approaching token limits.
 */
typealias AutoCompact = suspend (AutoCompactInput, QuerySource, AutoCompactTrackingState?, Int) -> AutoCompactResult

/**
 * UuidGenerator - platform dependency for UUID generation.
 */
typealias UuidGenerator = () -> String

/**
 * Query dependencies for dependency injection.
 * 
 * Following the TypeScript pattern: using `typeof fn` keeps signatures in sync
 * with the real implementations automatically.
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
