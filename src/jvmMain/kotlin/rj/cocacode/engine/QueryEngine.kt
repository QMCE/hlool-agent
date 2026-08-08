package rj.cocacode.engine

import rj.cocacode.state.AppStateManager
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.tools.ToolResult
import rj.cocacode.utils.generateUuid
import rj.cocacode.services.api.ApiClient
import rj.cocacode.services.api.ApiContentBlock
import rj.cocacode.services.api.ApiResponse
import rj.cocacode.services.api.ApiUsage
import rj.cocacode.services.api.RequestBuilder
import rj.cocacode.services.api.StreamAccumulator
import rj.cocacode.services.api.ToolSchemas
import rj.cocacode.config.ApiConfig
import rj.cocacode.config.ApiType
import rj.cocacode.utils.TokenUsage
import rj.cocacode.utils.Logger

/**
 * QueryEngine - end-to-end model interaction with a streaming tool-use loop.
 *
 * flow: user prompt -> model (streaming text + thinking) -> answer
 *                    -> model (tool_use) -> execute tool -> feed result back -> model...
 *
 * Supports both the Anthropic Messages API and OpenAI-compatible endpoints.
 * Thinking is streamed to [onThinking] and text to [onText] as it arrives.
 */
class QueryEngine {
    private var isProcessing = false
    private val messageHistory = mutableListOf<Message>()

    /** Stable session id used for persistence. */
    var sessionId: String = generateUuid()
        private set

    /** Cap on model<->tool round trips per user prompt. */
    private val maxTurns = 10

    /**
     * Restore a persisted conversation so the next prompt continues it.
     * Used by `--resume` and `/resume`.
     */
    fun restoreHistory(messages: List<Message>) {
        messageHistory.clear()
        messageHistory.addAll(messages)
        messages.forEach { AppStateManager.addMessage(it) }
    }

    /** Resume from the most recent saved session, if any. */
    fun restoreLastSession(): Boolean {
        val data = rj.cocacode.utils.SessionStorage.loadLastSession() ?: return false
        sessionId = data.id
        restoreHistory(data.messages)
        return true
    }

    /** Persist the current conversation to disk. */
    fun saveSession() {
        try {
            rj.cocacode.utils.SessionStorage.saveSession(sessionId, messageHistory.toList())
        } catch (e: Exception) {
            Logger.warn("Failed to auto-save session: ${e.message}")
        }
    }

    /** Clear the conversation and start a fresh session id. */
    fun startNewSession() {
        messageHistory.clear()
        sessionId = generateUuid()
    }

    /**
     * Run a prompt with streaming output. Thinking/text deltas are delivered to
     * [onThinking]/[onText] as they arrive; tool invocations to [onToolCall];
     * the final answer is returned.
     */
    suspend fun processQueryStreaming(
        prompt: String,
        onThinking: (String) -> Unit = {},
        onText: (String) -> Unit = {},
        onToolCall: (String, String) -> Unit = { _, _ -> }
    ): QueryResponse {
        if (isProcessing) {
            return QueryResponse.Error("Already processing a query")
        }
        isProcessing = true

        try {
            if (!ApiConfig.isConfigured) {
                return QueryResponse.Error(
                    "No API key configured. Set COCA_API_KEY (or ANTHROPIC_API_KEY) " +
                        "or add apiKey to ~/.cocacode/config.json, then restart."
                )
            }

            val userMessage = Message(
                id = generateUuid(),
                type = MessageType.USER,
                content = prompt
            )
            messageHistory.add(userMessage)
            AppStateManager.addMessage(userMessage)

            val result = runAgentLoop(prompt, onThinking, onText, onToolCall)
            isProcessing = false
            if (result is QueryResponse.Success) saveSession()
            return result
        } catch (e: Exception) {
            Logger.error("QueryEngine.processQueryStreaming failed", e)
            isProcessing = false
            return QueryResponse.Error(e.message ?: "Unknown error")
        }
    }

    /** Non-streaming convenience wrapper (accumulates all deltas internally). */
    suspend fun processQuery(prompt: String): QueryResponse {
        val text = StringBuilder()
        val thinking = StringBuilder()
        return processQueryStreaming(prompt, { thinking.append(it) }, { text.append(it) })
    }

    /**
     * Run the model-tool loop until the model stops requesting tools (or
     * [maxTurns] is reached). Each model turn streams deltas to the callbacks.
     */
    private suspend fun runAgentLoop(
        initialPrompt: String,
        onThinking: (String) -> Unit,
        onText: (String) -> Unit,
        onToolCall: (String, String) -> Unit
    ): QueryResponse {
        // Wire-format conversation seeded from the persisted history, then
        // extended with tool turns for this prompt.
        val apiHistory = mutableListOf<RequestBuilder.ApiMessage>()
        messageHistory.forEach { msg -> msg.toApiMessage()?.let { apiHistory.add(it) } }
        if (apiHistory.none { it.role == "user" && it.text == initialPrompt }) {
            apiHistory.add(RequestBuilder.ApiMessage(role = "user", text = initialPrompt))
        }
        val systemPrompt = buildSystemPrompt()

        var lastText = ""

        for (turn in 1..maxTurns) {
            val request = RequestBuilder.build(
                model = ApiConfig.model,
                system = systemPrompt,
                messages = apiHistory,
                tools = ToolSchemas.specsForRegisteredTools(),
                maxTokens = ApiConfig.maxTokens,
                stream = true,
                apiType = ApiConfig.apiType,
                thinkingBudget = ApiConfig.thinkingBudget
            )

            AppStateManager.setThinking(true)
            val accumulator = StreamAccumulator(ApiConfig.apiType)
            val streamResult = ApiClient.stream(
                RequestBuilder.messagesEndpoint(),
                request
            ) { data ->
                val delta = accumulator.feed(data)
                if (delta.thinking.isNotEmpty()) onThinking(delta.thinking)
                if (delta.text.isNotEmpty()) onText(delta.text)
            }
            accumulator.finalize()
            AppStateManager.setThinking(false)

            val error = streamResult.exceptionOrNull()
            if (error != null) {
                return QueryResponse.Error("API call failed: ${error.message}")
            }

            val thinking = accumulator.thinking
            val text = accumulator.text
            val toolUses = accumulator.toolUsesList

            // Append assistant turn to the wire history.
            val assistantBlocks = mutableListOf<ApiContentBlock>()
            // Anthropic requires the prior thinking block on the next turn when
            // thinking is enabled; OpenAI has no place for it in history.
            if (ApiConfig.apiType == ApiType.MESSAGES && thinking.isNotBlank()) {
                assistantBlocks.add(ApiContentBlock.ThinkingBlock(thinking))
            }
            if (text.isNotBlank()) assistantBlocks.add(ApiContentBlock.TextBlock(text))
            assistantBlocks.addAll(toolUses)
            apiHistory.add(
                RequestBuilder.ApiMessage(
                    role = "assistant",
                    text = text,
                    blocks = assistantBlocks
                )
            )

            if (text.isNotBlank()) lastText = text

            if (toolUses.isEmpty()) {
                // Model is done: persist the final assistant message and return.
                if (lastText.isBlank()) {
                    lastText = accumulator.stopReasonValue?.let { "[stopped: $it]" } ?: "(no response)"
                }
                recordAssistantMessage(lastText, null)
                return QueryResponse.Success(ModelResponse(lastText, accumulator.usageValue.toTokenUsage()))
            }

            // Execute tools and build a tool_result user message.
            val resultBlocks = toolUses.map { toolUse ->
                onToolCall(toolUse.name, describeToolCall(toolUse.name, toolUse.input))
                val toolResult = executeTool(toolUse)
                ApiContentBlock.ToolResultBlock(
                    toolUseId = toolUse.id,
                    content = toolResult.text,
                    isError = toolResult.isError
                )
            }
            apiHistory.add(
                RequestBuilder.ApiMessage(
                    role = "user",
                    text = "",
                    blocks = resultBlocks
                )
            )
        }

        // maxTurns reached
        val message = if (lastText.isNotBlank()) {
            lastText + "\n\n[stopped: reached max $maxTurns tool turns]"
        } else {
            "[stopped: reached max $maxTurns tool turns]"
        }
        recordAssistantMessage(message, null)
        return QueryResponse.Success(ModelResponse(message, null))
    }

    /** A concise, tool-specific description for the status line. */
    private fun describeToolCall(name: String, input: Map<String, Any>): String = when (name) {
        "Bash" -> (input["command"] as? String) ?: ""
        "Read", "Write", "Edit" -> (input["file_path"] as? String) ?: ""
        "Glob", "Grep" -> (input["pattern"] as? String) ?: ""
        "SendMessage" -> input["to"] as? String ?: ""
        else -> input.values.firstOrNull()?.toString() ?: ""
    }

    private suspend fun executeTool(toolUse: ApiContentBlock.ToolUseBlock): ToolResult {
        val tool = ToolRegistry.get(toolUse.name)
            ?: return ToolResult(
                text = "Unknown tool: ${toolUse.name}. Available tools: " +
                    ToolRegistry.all().joinToString(", ") { it.name },
                isError = true
            )
        return try {
            // Run on the IO dispatcher so a blocking tool never freezes the
            // caller's coroutine (REPL / -p share a single-threaded dispatcher).
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                tool.execute(toolUse.input)
            }
        } catch (e: Exception) {
            ToolResult(text = "Tool ${toolUse.name} threw: ${e.message}", isError = true)
        }
    }

    private fun recordAssistantMessage(content: String, response: ApiResponse?) {
        val assistantMessage = Message(
            id = generateUuid(),
            type = MessageType.ASSISTANT,
            content = content
        )
        messageHistory.add(assistantMessage)
        AppStateManager.addMessage(assistantMessage)
        response?.let { AppStateManager.setModel(it.model.ifBlank { ApiConfig.model }) }
    }

    private fun buildSystemPrompt(): String {
        return """
You are CocaCode, an AI coding assistant running in the user's terminal. You are the LEADER of a team of AI agents.

# Your tools
You can use tools to read, write, and edit files, run shell commands, and search code.
- TeamCreate — enable/disable teammates mode.
- Task (Agent) — spawn a subagent. With teammates mode ON (default), spawning with a `name` creates a persistent teammate that works autonomously in the background.
- SendMessage — send follow-up work to a teammate or request its results.

# Teammates (recommended workflow)
- Prefer delegating large, independent pieces of work to named teammates so they run in the background while you continue.
- When you delegate, give the teammate a clear, self-contained task and tell it whether you expect code or just research.
- Wait for teammates to finish (via SendMessage or their output) before claiming a result. Summarize each teammate's result for the user concisely.

# Rules
- Be concise. When using a tool, wait for its result before continuing.
- When a teammate completes, surface its output to the user in your own words.
        """.trimIndent()
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

private fun ApiUsage.toTokenUsage(): TokenUsage =
    TokenUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cacheCreationInputTokens = cacheCreationInputTokens,
        cacheReadInputTokens = cacheReadInputTokens
    )

/** Convert a persisted [Message] into a wire-format API message. */
private fun Message.toApiMessage(): RequestBuilder.ApiMessage? {
    val role = when (type) {
        MessageType.USER, MessageType.TOOL_RESULT -> "user"
        MessageType.ASSISTANT, MessageType.TOOL -> "assistant"
        MessageType.SYSTEM, MessageType.ATTACHMENT -> return null
    }
    return RequestBuilder.ApiMessage(role = role, text = content)
}

sealed class QueryResponse {
    data class Success(val response: ModelResponse) : QueryResponse()
    data class Error(val message: String) : QueryResponse()
}

data class ModelResponse(
    val content: String,
    val usage: TokenUsage?
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
