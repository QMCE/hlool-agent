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
import rj.cocacode.services.api.toApiMessage
import rj.cocacode.services.api.toWireMessage
import rj.cocacode.config.ApiConfig
import rj.cocacode.config.ApiType
import rj.cocacode.utils.TokenUsage
import rj.cocacode.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * QueryEngine - end-to-end model interaction with a streaming tool-use loop.
 *
 * flow: user prompt -> model (streaming text + thinking) -> answer
 *                    -> model (tool_use) -> execute tool -> feed result back -> model...
 *
 * Supports both the Anthropic Messages API and OpenAI-compatible endpoints.
 * Thinking is streamed to [onThinking] and text to [onText] as it arrives.
 *
 * [wireHistory] is the API transcript (thinking + tool_calls + tool results) and is
 * what gets sent on the next turn — [messageHistory] is display/UI only.
 */
class QueryEngine {
    private var isProcessing = false
    private val messageHistory = mutableListOf<Message>()
    /** Full provider transcript — must include thinking / tools across user turns. */
    private val wireHistory = mutableListOf<RequestBuilder.ApiMessage>()

    /** Stable session id used for persistence. */
    var sessionId: String = generateUuid()
        private set

    /** Display title: first-message placeholder, later upgraded by the model. */
    var sessionTitle: String? = null
        private set

    private var titleGenerated: Boolean = false
    @Volatile private var titleGenerationInFlight: Boolean = false

    /** Cap on model<->tool round trips per user prompt. */
    private val maxTurns = 10

    /** Active abort for the in-flight query (Esc / Ctrl+C). */
    @Volatile var abortController: rj.cocacode.state.AbortController? = null

    /**
     * Ask the user before running a sensitive tool. Returns true to allow.
     * REPL wires this to [rj.cocacode.ui.PermissionPrompt].
     */
    var permissionAsk: suspend (toolName: String, input: Map<String, Any>) -> Boolean =
        { _, _ -> true }

    /** Optional hook to render tool results in the UI (returns display text). */
    var formatToolResult: (toolName: String, result: ToolResult, input: Map<String, Any>) -> String =
        { _, result, _ -> result.text }

    /** Optional hook after a tool finishes (for printing formatted output). */
    var onToolResult: (toolName: String, display: String, isError: Boolean) -> Unit = { _, _, _ -> }

    /**
     * Restore a persisted conversation so the next prompt continues it.
     * Used by `--resume` and `/resume`.
     */
    fun restoreHistory(messages: List<Message>) {
        messageHistory.clear()
        messageHistory.addAll(messages)
        messages.forEach { AppStateManager.addMessage(it) }
    }

    /** Restore a full saved session (id + messages + title + wire transcript). */
    fun restoreSession(data: rj.cocacode.utils.SessionData) {
        sessionId = data.id
        sessionTitle = data.title
        titleGenerated = data.titleGenerated
        titleGenerationInFlight = false
        wireHistory.clear()
        val wires = data.wireMessages
        if (!wires.isNullOrEmpty()) {
            wireHistory.addAll(wires.map { it.toApiMessage() })
        } else {
            data.messages.mapNotNull { it.toApiMessage() }.forEach { wireHistory.add(it) }
        }
        restoreHistory(data.messages)
        // Backfill placeholder title from first user message if missing.
        if (sessionTitle.isNullOrBlank()) {
            val first = messageHistory.firstOrNull { it.type == MessageType.USER }?.content
            sessionTitle = rj.cocacode.utils.SessionTitle.deriveTitle(first ?: "")
        }
    }

    /** Resume from the most recent saved session, if any. */
    fun restoreLastSession(): Boolean {
        val data = rj.cocacode.utils.SessionStorage.loadLastSession() ?: return false
        restoreSession(data)
        return true
    }

    /** Persist the current conversation to disk. */
    fun saveSession() {
        try {
            ensurePlaceholderTitle()
            rj.cocacode.utils.SessionStorage.saveSession(
                sessionId = sessionId,
                messages = messageHistory.toList(),
                title = sessionTitle,
                titleGenerated = titleGenerated,
                wireMessages = wireHistory.map { it.toWireMessage() }
            )
            maybeGenerateTitleAsync()
        } catch (e: Exception) {
            Logger.warn("Failed to auto-save session: ${e.message}")
        }
    }

    /** Clear the conversation and start a fresh session id. */
    fun startNewSession() {
        messageHistory.clear()
        wireHistory.clear()
        sessionId = generateUuid()
        sessionTitle = null
        titleGenerated = false
        titleGenerationInFlight = false
    }

    /** First-message placeholder title (not AI-generated). */
    private fun ensurePlaceholderTitle() {
        if (!sessionTitle.isNullOrBlank()) return
        val first = messageHistory.firstOrNull { it.type == MessageType.USER }?.content ?: return
        sessionTitle = rj.cocacode.utils.SessionTitle.deriveTitle(first)
    }

    /**
     * Once the session has more than [SessionTitle.AUTO_GENERATE_AFTER]
     * messages, fire-and-forget an LLM title upgrade (Claude Code style).
     */
    private fun maybeGenerateTitleAsync() {
        if (titleGenerated || titleGenerationInFlight) return
        if (messageHistory.size <= rj.cocacode.utils.SessionTitle.AUTO_GENERATE_AFTER) return
        if (!ApiConfig.isConfigured) return

        titleGenerationInFlight = true
        val snapshot = messageHistory.toList()
        val sid = sessionId
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = rj.cocacode.utils.SessionTitle.extractConversationText(snapshot)
                val generated = rj.cocacode.utils.SessionTitle.generateTitle(text)
                if (generated != null && sessionId == sid) {
                    sessionTitle = generated
                    titleGenerated = true
                    rj.cocacode.utils.SessionStorage.updateTitle(sid, generated, generated = true)
                }
            } catch (e: Exception) {
                Logger.warn("Async session title failed: ${e.message}")
            } finally {
                titleGenerationInFlight = false
            }
        }
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
        onToolCall: (String, String) -> Unit = { _, _ -> },
        onNotice: (String) -> Unit = {}
    ): QueryResponse {
        if (isProcessing) {
            return QueryResponse.Error("Already processing a query")
        }
        isProcessing = true
        val abort = rj.cocacode.state.AbortController()
        abortController = abort

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
            wireHistory.add(RequestBuilder.ApiMessage(role = "user", text = prompt))

            val result = runAgentLoop(prompt, onThinking, onText, onToolCall, onNotice, abort)
            if (result is QueryResponse.Success) saveSession()
            return result
        } catch (e: rj.cocacode.state.AbortException) {
            return QueryResponse.Error("Interrupted")
        } catch (e: Exception) {
            Logger.error("QueryEngine.processQueryStreaming failed", e)
            val msg = e.message ?: e::class.simpleName ?: "Unknown error"
            return QueryResponse.Error(msg)
        } finally {
            isProcessing = false
            abortController = null
        }
    }

    fun requestAbort() {
        abortController?.abort("user")
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
        onToolCall: (String, String) -> Unit,
        onNotice: (String) -> Unit,
        abort: rj.cocacode.state.AbortController
    ): QueryResponse {
        // Continue from the full wire transcript (thinking + tools), not the
        // display-only messageHistory which only keeps final assistant text.
        val apiHistory = wireHistory.toMutableList()
        // Deduplicate trailing identical user bubbles (re-add races).
        while (apiHistory.size >= 2) {
            val a = apiHistory[apiHistory.lastIndex - 1]
            val b = apiHistory.last()
            if (a.role == "user" && b.role == "user" && a.text == b.text && a.blocks.isNullOrEmpty() && b.blocks.isNullOrEmpty()) {
                apiHistory.removeAt(apiHistory.lastIndex)
            } else break
        }
        // Ensure the current user turn is last.
        if (apiHistory.lastOrNull()?.let { it.role == "user" && it.text == initialPrompt } != true) {
            apiHistory.add(RequestBuilder.ApiMessage(role = "user", text = initialPrompt))
        }
        val systemPrompt = buildSystemPrompt()

        var lastText = ""
        var awaitingPostToolReply = false

        fun commitWire() {
            wireHistory.clear()
            wireHistory.addAll(apiHistory)
        }

        for (turn in 1..maxTurns) {
            if (abort.isAborted) throw rj.cocacode.state.AbortException("Aborted")

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
            val (accumulator, streamError) = streamModelTurn(
                request = request,
                abort = abort,
                onThinking = onThinking,
                onText = onText,
                onNotice = onNotice
            )
            AppStateManager.setThinking(false)

            if (abort.isAborted) throw rj.cocacode.state.AbortException("Aborted")

            if (streamError != null) {
                if (streamError is rj.cocacode.state.AbortException) throw streamError
                commitWire()
                val prefix = if (awaitingPostToolReply) "After tools: " else ""
                dumpToolDebug(apiHistory, awaitingPostToolReply)
                return QueryResponse.Error("${prefix}API call failed: ${streamError.message}")
            }
            accumulator.streamError?.let { err ->
                commitWire()
                val prefix = if (awaitingPostToolReply) "After tools: " else ""
                dumpToolDebug(apiHistory, awaitingPostToolReply)
                return QueryResponse.Error("$prefix$err")
            }

            val thinking = accumulator.thinking
            val text = accumulator.text
            val toolUses = accumulator.toolUsesList

            if (awaitingPostToolReply && toolUses.isEmpty() && text.isBlank()) {
                if (thinking.isBlank()) {
                    commitWire()
                    dumpToolDebug(apiHistory, true)
                    return QueryResponse.Error(
                        "Model returned empty response after tools " +
                            "(often duplicated/missing tool_call_id on chat endpoints). Retry the prompt."
                    )
                }
            }

            // Persist thinking + text + tool_use together so the next user turn
            // (and chat reasoning_content) still sees them.
            val assistantBlocks = mutableListOf<ApiContentBlock>()
            if (thinking.isNotBlank()) {
                assistantBlocks.add(ApiContentBlock.ThinkingBlock(thinking))
            }
            if (text.isNotBlank()) {
                assistantBlocks.add(ApiContentBlock.TextBlock(text))
            }
            assistantBlocks.addAll(toolUses)
            apiHistory.add(
                RequestBuilder.ApiMessage(
                    role = "assistant",
                    text = text,
                    blocks = assistantBlocks.ifEmpty { null }
                )
            )

            if (text.isNotBlank()) {
                lastText = text
            } else if (toolUses.isEmpty() && thinking.isNotBlank()) {
                lastText = thinking
            }

            if (toolUses.isEmpty()) {
                if (lastText.isBlank()) {
                    lastText = accumulator.stopReasonValue?.let { "[stopped: $it]" } ?: "(no response)"
                }
                recordAssistantMessage(lastText, null)
                commitWire()
                return QueryResponse.Success(ModelResponse(lastText, accumulator.usageValue.toTokenUsage()))
            }

            val resultBlocks = toolUses.map { toolUse ->
                if (abort.isAborted) throw rj.cocacode.state.AbortException("Aborted")
                onToolCall(toolUse.name, describeToolCall(toolUse.name, toolUse.input))
                val toolResult = executeTool(toolUse)
                val display = try {
                    formatToolResult(toolUse.name, toolResult, toolUse.input)
                } catch (e: Exception) {
                    Logger.warn("formatToolResult(${toolUse.name}) failed: ${e.message}")
                    toolResult.text
                }
                try {
                    onToolResult(toolUse.name, display, toolResult.isError)
                } catch (e: Exception) {
                    Logger.warn("onToolResult(${toolUse.name}) failed: ${e.message}")
                }
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
            commitWire()
            awaitingPostToolReply = true
        }

        val message = if (lastText.isNotBlank()) {
            lastText + "\n\n[stopped: reached max $maxTurns tool turns]"
        } else {
            "[stopped: reached max $maxTurns tool turns]"
        }
        recordAssistantMessage(message, null)
        commitWire()
        return QueryResponse.Success(ModelResponse(message, null))
    }

    /**
     * One model SSE turn with retries on transient network / incomplete streams.
     * Does not commit partial wire history — caller only uses a finished accumulator.
     */
    private suspend fun streamModelTurn(
        request: kotlinx.serialization.json.JsonObject,
        abort: rj.cocacode.state.AbortController,
        onThinking: (String) -> Unit,
        onText: (String) -> Unit,
        onNotice: (String) -> Unit
    ): Pair<StreamAccumulator, Throwable?> {
        var lastError: Throwable? = null
        for (attempt in 1..STREAM_MAX_ATTEMPTS) {
            if (abort.isAborted) {
                return StreamAccumulator(ApiConfig.apiType) to rj.cocacode.state.AbortException("Aborted")
            }
            val accumulator = StreamAccumulator(ApiConfig.apiType)
            val streamResult = ApiClient.stream(
                RequestBuilder.messagesEndpoint(),
                request,
                isAborted = { abort.isAborted }
            ) { data ->
                val delta = accumulator.feed(data)
                if (delta.thinking.isNotEmpty()) onThinking(delta.thinking)
                if (delta.text.isNotEmpty()) onText(delta.text)
            }
            accumulator.finalize()

            val error = streamResult.exceptionOrNull()
            if (error == null) {
                // Empty successful stream is also suspicious — retry once.
                val empty = accumulator.text.isBlank() &&
                    accumulator.thinking.isBlank() &&
                    accumulator.toolUsesList.isEmpty() &&
                    accumulator.streamError == null &&
                    accumulator.stopReasonValue == null
                if (empty && attempt < STREAM_MAX_ATTEMPTS) {
                    lastError = ApiClient.IncompleteStreamException("empty stream response")
                    onNotice("Empty stream — retrying ($attempt/$STREAM_MAX_ATTEMPTS)…")
                    kotlinx.coroutines.delay(STREAM_RETRY_BASE_MS * attempt)
                    continue
                }
                return accumulator to null
            }
            if (error is rj.cocacode.state.AbortException) return accumulator to error
            lastError = error
            if (ApiClient.isRetryableNetwork(error) && attempt < STREAM_MAX_ATTEMPTS) {
                Logger.warn("Stream attempt $attempt failed (${error.message}); retrying")
                onNotice("Network hiccup — retrying ($attempt/$STREAM_MAX_ATTEMPTS)…")
                kotlinx.coroutines.delay(STREAM_RETRY_BASE_MS * attempt)
                continue
            }
            return accumulator to error
        }
        return StreamAccumulator(ApiConfig.apiType) to (
            lastError ?: ApiClient.IncompleteStreamException("stream failed after retries")
            )
    }

    companion object {
        private const val STREAM_MAX_ATTEMPTS = 3
        private const val STREAM_RETRY_BASE_MS = 800L
    }

    private fun dumpToolDebug(apiHistory: List<RequestBuilder.ApiMessage>, postTool: Boolean) {
        if (!postTool) return
        try {
            val dir = java.io.File(System.getProperty("user.home"), ".cocacode")
            dir.mkdirs()
            val out = java.io.File(dir, "last-tool-followup.json")
            val body = RequestBuilder.build(
                model = ApiConfig.model,
                system = "(debug dump)",
                messages = apiHistory,
                tools = emptyList(),
                maxTokens = ApiConfig.maxTokens,
                stream = false,
                apiType = ApiConfig.apiType,
                thinkingBudget = null
            )
            out.writeText(body.toString())
            Logger.warn("Wrote tool follow-up request dump to ${out.absolutePath}")
        } catch (e: Exception) {
            Logger.warn("Failed to dump tool follow-up request: ${e.message}")
        }
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

        val gate = rj.cocacode.permissions.PermissionGate
        if (gate.autoDeny(toolUse.name)) {
            return ToolResult(
                text = "Permission denied: tool '${toolUse.name}' blocked by permission mode",
                isError = true
            )
        }
        if (gate.shouldAsk(toolUse.name)) {
            val allowed = permissionAsk(toolUse.name, toolUse.input)
            if (!allowed) {
                return ToolResult(
                    text = "Permission denied by user for tool '${toolUse.name}'",
                    isError = true
                )
            }
        }

        return try {
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
        wireHistory.clear()
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
    // Empty assistant stubs would wipe conversational context on chat endpoints.
    if (content.isBlank()) return null
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
