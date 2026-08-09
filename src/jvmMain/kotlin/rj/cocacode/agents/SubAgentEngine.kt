package rj.cocacode.agents

import kotlinx.coroutines.*
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolResult
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.utils.generateUuid
import rj.cocacode.services.api.ApiClient
import rj.cocacode.services.api.ApiContentBlock
import rj.cocacode.services.api.RequestBuilder
import rj.cocacode.services.api.StreamAccumulator
import rj.cocacode.services.api.ToolSpec
import rj.cocacode.services.api.ToolSchemas
import rj.cocacode.config.ApiConfig
import rj.cocacode.config.ApiType
import rj.cocacode.state.AbortController
import rj.cocacode.utils.Logger

/**
 * Engine that executes subagents with isolated context.
 *
 * Features:
 * - Independent message history per subagent
 * - Restricted tool access based on agent definition and tool filtering
 * - Turn limits and timeouts
 * - Streaming model calls (same path as the main QueryEngine)
 * - Tool use loop (agent thinks, calls tools, processes results)
 * - Persistent agent memory loading and saving
 * - Teammate / fork conversation seeding via [SubAgentContext.parentMessages]
 * - Background (async) execution
 */
class SubAgentEngine(
    private val memoryManager: AgentMemoryManager = AgentMemoryManager()
) {

    companion object {
        private const val MAX_SUBAGENT_TURNS = 25
        private const val SUBAGENT_TIMEOUT_MS = 300_000L // 5 minutes
        private const val MAX_RETRIES_ON_ERROR = 2
    }

    /**
     * Execute a subagent with the given context.
     *
     * Optional [onThinking] / [onText] receive streamed deltas (teammates may
     * ignore them; one-shot Task callers can surface them later).
     */
    suspend fun execute(
        context: SubAgentContext,
        abortController: AbortController = AbortController(),
        onThinking: (String) -> Unit = {},
        onText: (String) -> Unit = {}
    ): SubAgentResult {
        val startTime = System.currentTimeMillis()
        val maxTurns = context.definition.maxTurns.coerceAtMost(MAX_SUBAGENT_TURNS)
        val isAsync = context.definition.isAsync

        val systemPrompt = buildSystemPrompt(context)
        val model = resolveModel(context) ?: ApiConfig.model
        val availableTools = resolveAgentToolsFor(context.definition, isAsync)
        val toolMap = availableTools.associateBy { it.name }
        val toolSpecs = availableTools.map { tool ->
            ToolSpec(
                name = tool.name,
                description = tool.description,
                inputSchema = ToolSchemas.schemaForTool(tool.name)
            )
        }

        // Display / result history (Message list for callers).
        val messages = mutableListOf<Message>()
        messages.add(
            Message(id = generateUuid(), type = MessageType.SYSTEM, content = systemPrompt)
        )

        // Wire-format history for the streaming tool loop.
        val apiHistory = mutableListOf<RequestBuilder.ApiMessage>()

        // Seed prior conversation when provided (teammate continuity, fork, etc.).
        // Gated on non-empty parentMessages — not on a fork/teammate flag.
        for (msg in context.parentMessages.filter { it.type != MessageType.SYSTEM }.takeLast(50)) {
            msg.toApiMessage()?.let { apiHistory.add(it) }
            messages.add(msg)
        }

        apiHistory.add(RequestBuilder.ApiMessage(role = "user", text = context.task))
        messages.add(
            Message(id = generateUuid(), type = MessageType.USER, content = context.task)
        )

        var turnCount = 0
        var toolCallCount = 0
        var lastError: String? = null
        var memoryUpdated = false
        var lastText = ""

        while (turnCount < maxTurns) {
            if (abortController.isAborted) {
                return buildResult(
                    context, messages,
                    "Agent execution aborted: ${abortController.reason ?: "Cancelled"}",
                    true, turnCount, toolCallCount, isAsync
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > SUBAGENT_TIMEOUT_MS) {
                return buildResult(
                    context, messages,
                    "Agent execution timed out after ${SUBAGENT_TIMEOUT_MS / 1000}s",
                    true, turnCount, toolCallCount, isAsync
                )
            }

            try {
                val turn = withTimeout(SUBAGENT_TIMEOUT_MS - elapsed) {
                    streamModelTurn(
                        systemPrompt = systemPrompt,
                        model = model,
                        apiHistory = apiHistory,
                        tools = toolSpecs,
                        onThinking = onThinking,
                        onText = onText
                    )
                }

                if (turn.isError) {
                    lastError = turn.errorMessage
                    messages.add(
                        Message(
                            id = generateUuid(),
                            type = MessageType.TOOL_RESULT,
                            content = "Error: ${turn.errorMessage}",
                            isError = true
                        )
                    )
                    break
                }

                val text = turn.text
                val thinking = turn.thinking
                val toolUses = turn.toolUses

                // Append assistant turn to wire history (thinking required for Anthropic).
                val assistantBlocks = mutableListOf<ApiContentBlock>()
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

                if (text.isNotBlank()) {
                    lastText = text
                    messages.add(
                        Message(
                            id = generateUuid(),
                            type = MessageType.ASSISTANT,
                            content = text
                        )
                    )
                }

                if (toolUses.isEmpty()) break

                // Execute tools and append tool_result user message.
                val resultBlocks = mutableListOf<ApiContentBlock>()
                for (toolUse in toolUses) {
                    turnCount++
                    val tool = toolMap[toolUse.name]
                    val result = if (tool == null) {
                        ToolResult(
                            text = "Error: Tool '${toolUse.name}' is not available to this agent",
                            isError = true
                        )
                    } else {
                        executeToolWithRetry(tool, toolUse.input)
                    }

                    messages.add(
                        Message(
                            id = generateUuid(),
                            type = MessageType.TOOL,
                            content = "Tool: ${toolUse.name}"
                        )
                    )
                    messages.add(
                        Message(
                            id = generateUuid(),
                            type = MessageType.TOOL_RESULT,
                            content = if (result.isError) "Error: ${result.text}" else result.text,
                            isError = result.isError,
                            toolUseId = toolUse.id
                        )
                    )

                    resultBlocks.add(
                        ApiContentBlock.ToolResultBlock(
                            toolUseId = toolUse.id,
                            content = result.text,
                            isError = result.isError
                        )
                    )

                    toolCallCount++
                    if (result.isError) lastError = result.text
                }
                apiHistory.add(
                    RequestBuilder.ApiMessage(
                        role = "user",
                        text = "",
                        blocks = resultBlocks
                    )
                )
            } catch (e: TimeoutCancellationException) {
                return buildResult(context, messages, "Agent timed out", true, turnCount, toolCallCount, isAsync)
            } catch (e: Exception) {
                Logger.warn("SubAgent execution error at turn $turnCount: ${e.message}")
                lastError = e.message ?: "Unknown error"
                messages.add(
                    Message(
                        id = generateUuid(),
                        type = MessageType.TOOL_RESULT,
                        content = "Error on turn $turnCount: ${e.message}",
                        isError = true
                    )
                )
            }
        }

        if (turnCount >= maxTurns && lastText.isNotBlank()) {
            lastText += "\n\n[stopped: reached max $maxTurns tool turns]"
        }

        val output = extractFinalOutput(messages).ifBlank { lastText }

        if (context.definition.memoryScope != "none" && output.isNotBlank()) {
            try {
                val scope = AgentMemoryScope.fromKey(context.definition.memoryScope)
                if (scope != AgentMemoryScope.NONE) {
                    memoryManager.saveMemory(
                        agentType = context.definition.agentType,
                        scope = scope,
                        content = output,
                        workingDir = context.workingDirectory
                    )
                    memoryUpdated = true
                }
            } catch (e: Exception) {
                Logger.warn("Failed to save agent memory: ${e.message}")
            }
        }

        return SubAgentResult(
            agentType = context.definition.agentType,
            task = context.task,
            output = output,
            messages = messages.toList(),
            isError = lastError != null && output.isBlank(),
            turnCount = turnCount,
            toolCallCount = toolCallCount,
            isAsync = isAsync,
            memoryUpdated = memoryUpdated
        )
    }

    /**
     * Execute a subagent asynchronously (background).
     */
    fun executeAsync(
        context: SubAgentContext,
        scope: CoroutineScope,
        abortController: AbortController = AbortController(),
        onComplete: ((SubAgentResult) -> Unit)? = null
    ): Job {
        return scope.launch {
            try {
                val result = execute(context, abortController)
                onComplete?.invoke(result)
            } catch (e: Exception) {
                Logger.error("Async agent execution failed: ${e.message}")
                onComplete?.invoke(
                    SubAgentResult(
                        agentType = context.definition.agentType,
                        task = context.task,
                        output = "Async agent failed: ${e.message}",
                        messages = emptyList(),
                        isError = true,
                        isAsync = true
                    )
                )
            }
        }
    }

    /**
     * One streamed model turn: POST SSE, accumulate text/thinking/tool_uses.
     */
    private suspend fun streamModelTurn(
        systemPrompt: String,
        model: String,
        apiHistory: List<RequestBuilder.ApiMessage>,
        tools: List<ToolSpec>,
        onThinking: (String) -> Unit,
        onText: (String) -> Unit
    ): StreamedTurn {
        val request = RequestBuilder.build(
            model = model,
            system = systemPrompt,
            messages = apiHistory,
            tools = tools,
            maxTokens = ApiConfig.maxTokens,
            stream = true,
            apiType = ApiConfig.apiType,
            thinkingBudget = ApiConfig.thinkingBudget
        )

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

        val error = streamResult.exceptionOrNull()
        if (error != null) {
            return StreamedTurn(
                text = "",
                thinking = "",
                toolUses = emptyList(),
                isError = true,
                errorMessage = error.message ?: "API stream failed"
            )
        }

        return StreamedTurn(
            text = accumulator.text,
            thinking = accumulator.thinking,
            toolUses = accumulator.toolUsesList
        )
    }

    private suspend fun buildSystemPrompt(context: SubAgentContext): String {
        val sb = StringBuilder()

        if (context.definition.getSystemPrompt != null) {
            val dynamicPrompt = context.definition.getSystemPrompt.invoke(context)
            if (!dynamicPrompt.isNullOrBlank()) {
                sb.appendLine(dynamicPrompt)
            }
        }

        if (sb.isEmpty() && context.definition.systemPrompt.isNotBlank()) {
            sb.appendLine(context.definition.systemPrompt)
        }

        // Teammate role notice (explicit flag — not overloaded onto fork).
        if (context.isTeammate) {
            sb.appendLine()
            sb.appendLine("You are a teammate worker in a team led by the main agent. Execute the given task autonomously and completely.")
            sb.appendLine("Your text output is delivered back to the leader. If you need to ask something, make it part of your final report.")
        }

        if (context.definition.memoryScope != "none") {
            val scope = AgentMemoryScope.fromKey(context.definition.memoryScope)
            if (scope != AgentMemoryScope.NONE) {
                val memory = memoryManager.loadMemory(
                    agentType = context.definition.agentType,
                    scope = scope,
                    workingDir = context.workingDirectory
                )
                val memoryPrompt = memoryManager.buildMemoryPrompt(memory)
                if (memoryPrompt.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine(memoryPrompt)
                }
            }
        }

        return sb.toString().trim()
    }

    private fun resolveModel(context: SubAgentContext): String? {
        if (context.modelOverride != null) return context.modelOverride
        if (context.definition.model != null) {
            if (context.definition.model == "inherit") return null
            return context.definition.model
        }
        return null
    }

    private fun resolveAgentToolsFor(
        definition: AgentDefinition,
        isAsync: Boolean
    ): List<Tool> {
        val allTools = ToolRegistry.all()
        return filterToolsForAgent(
            tools = allTools,
            isBuiltIn = definition.source == "built-in",
            isAsync = isAsync,
            permissionMode = definition.permissionMode
        )
    }

    private suspend fun executeToolWithRetry(
        tool: Tool,
        input: Map<String, Any>,
        retries: Int = MAX_RETRIES_ON_ERROR
    ): ToolResult {
        var lastError: Exception? = null
        for (attempt in 0..retries) {
            try {
                return withContext(Dispatchers.IO) { tool.execute(input) }
            } catch (e: Exception) {
                lastError = e
                if (attempt < retries) delay(1000L * (attempt + 1))
            }
        }
        return ToolResult(
            text = "Tool execution failed after ${retries + 1} attempts: ${lastError?.message}",
            isError = true
        )
    }

    private fun extractFinalOutput(messages: List<Message>): String {
        val lastAssistant = messages.lastOrNull { it.type == MessageType.ASSISTANT }
        if (lastAssistant != null) return cleanOutput(lastAssistant.content)

        val lastSystem = messages.lastOrNull()
        return lastSystem?.content ?: "No output produced"
    }

    private fun cleanOutput(content: String): String {
        return content
            .replace(Regex("<tool_call>.*?</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<tool_calls>.*?</tool_calls>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<invoke name=\"[^\"]*\">.*?</invoke>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("```json\\s*\\{.*?\\}\\s*```", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    private fun buildResult(
        context: SubAgentContext,
        messages: MutableList<Message>,
        output: String,
        isError: Boolean,
        turnCount: Int,
        toolCallCount: Int,
        isAsync: Boolean
    ): SubAgentResult {
        return SubAgentResult(
            agentType = context.definition.agentType,
            task = context.task,
            output = output,
            messages = messages.toList(),
            isError = isError,
            turnCount = turnCount,
            toolCallCount = toolCallCount,
            isAsync = isAsync
        )
    }
}

/** One completed streamed model turn. */
private data class StreamedTurn(
    val text: String,
    val thinking: String,
    val toolUses: List<ApiContentBlock.ToolUseBlock>,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

/** Convert a seed [Message] into a wire-format API message. */
private fun Message.toApiMessage(): RequestBuilder.ApiMessage? {
    val role = when (type) {
        MessageType.USER, MessageType.TOOL_RESULT -> "user"
        MessageType.ASSISTANT, MessageType.TOOL -> "assistant"
        MessageType.SYSTEM, MessageType.ATTACHMENT -> return null
    }
    return RequestBuilder.ApiMessage(role = role, text = content)
}

/**
 * Singleton manager for SubAgentEngine.
 */
object SubAgentEngineManager {
    private var engine: SubAgentEngine? = null

    fun getEngine(): SubAgentEngine {
        if (engine == null) {
            engine = SubAgentEngine()
        }
        return engine!!
    }

    fun reset() {
        engine = null
    }
}
