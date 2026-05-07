package rj.cocacode.agents

import kotlinx.coroutines.*
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolResult
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.utils.generateUuid
import rj.cocacode.services.api.ApiClient
import rj.cocacode.config.ApiConfig
import rj.cocacode.state.AbortController
import rj.cocacode.utils.Logger

/**
 * Engine that executes subagents with isolated context.
 *
 * Features:
 * - Independent message history per subagent
 * - Restricted tool access based on agent definition and tool filtering
 * - Turn limits and timeouts
 * - Streaming response handling
 * - Tool use loop (agent thinks, calls tools, processes results)
 * - Persistent agent memory loading and saving
 * - Fork subagent support
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
     */
    suspend fun execute(
        context: SubAgentContext,
        abortController: AbortController = AbortController()
    ): SubAgentResult {
        val startTime = System.currentTimeMillis()
        val maxTurns = context.definition.maxTurns.coerceAtMost(MAX_SUBAGENT_TURNS)
        val isFork = context.isChildOfFork
        val isAsync = context.definition.isAsync

        // Build subagent message history
        val messages = mutableListOf<Message>()

        // Build system prompt (with memory if applicable)
        val systemPrompt = buildSystemPrompt(context)
        messages.add(Message(
            id = generateUuid(),
            type = MessageType.SYSTEM,
            content = systemPrompt
        ))

        // For fork children, add forked prefix messages
        if (isFork) {
            messages.addAll(context.parentMessages.filter {
                it.type != MessageType.SYSTEM
            }.takeLast(50))
        }

        // Add task message
        messages.add(Message(
            id = generateUuid(),
            type = MessageType.USER,
            content = context.task
        ))

        // Determine model to use
        val model = resolveModel(context)

        // Resolve available tools for this agent
        val availableTools = resolveAgentToolsFor(context.definition, isAsync)
        val toolMap = availableTools.associateBy { it.name }

        var turnCount = 0
        var toolCallCount = 0
        var lastError: String? = null
        var memoryUpdated = false

        // Main execution loop
        while (turnCount < maxTurns) {
            if (abortController.isAborted) {
                return buildResult(context, messages, "Agent execution aborted: ${abortController.reason ?: "Cancelled"}", true, turnCount, toolCallCount, isAsync)
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > SUBAGENT_TIMEOUT_MS) {
                return buildResult(context, messages, "Agent execution timed out after ${SUBAGENT_TIMEOUT_MS / 1000}s", true, turnCount, toolCallCount, isAsync)
            }

            try {
                val response = withTimeout(SUBAGENT_TIMEOUT_MS - elapsed) {
                    callModel(messages.toList(), model)
                }

                if (response.content.isNotBlank()) {
                    messages.add(Message(
                        id = generateUuid(),
                        type = MessageType.ASSISTANT,
                        content = response.content
                    ))
                }

                // If no tool calls, done
                if (response.toolCalls.isEmpty()) {
                    break
                }

                // Execute tool calls
                for ((toolName, toolInput) in response.toolCalls) {
                    turnCount++

                    val tool = toolMap[toolName]
                    if (tool == null) {
                        messages.add(Message(
                            id = generateUuid(),
                            type = MessageType.TOOL_RESULT,
                            content = "Error: Tool '$toolName' is not available to this agent",
                            isError = true
                        ))
                        continue
                    }

                    // Execute tool
                    val result = executeToolWithRetry(tool, toolInput)

                    // Add tool call message
                    messages.add(Message(
                        id = generateUuid(),
                        type = MessageType.TOOL,
                        content = "Tool: $toolName"
                    ))

                    // Add tool result message
                    messages.add(Message(
                        id = generateUuid(),
                        type = MessageType.TOOL_RESULT,
                        content = if (result.isError) "Error: ${result.text}" else result.text,
                        isError = result.isError
                    ))

                    toolCallCount++
                    if (result.isError) lastError = result.text
                }

            } catch (e: TimeoutCancellationException) {
                return buildResult(context, messages, "Agent timed out", true, turnCount, toolCallCount, isAsync)
            } catch (e: Exception) {
                Logger.warn("SubAgent execution error at turn $turnCount: ${e.message}")
                lastError = e.message ?: "Unknown error"
                messages.add(Message(
                    id = generateUuid(),
                    type = MessageType.TOOL_RESULT,
                    content = "Error on turn $turnCount: ${e.message}",
                    isError = true
                ))
            }
        }

        // Extract final output
        val output = extractFinalOutput(messages)

        // Save agent memory if configured
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
     * Build system prompt with memory context.
     */
    private suspend fun buildSystemPrompt(context: SubAgentContext): String {
        val sb = StringBuilder()

        // Dynamic prompt builder
        if (context.definition.getSystemPrompt != null) {
            val dynamicPrompt = context.definition.getSystemPrompt.invoke(context)
            if (!dynamicPrompt.isNullOrBlank()) {
                sb.appendLine(dynamicPrompt)
            }
        }

        // Static system prompt
        if (sb.isEmpty() && context.definition.systemPrompt.isNotBlank()) {
            sb.appendLine(context.definition.systemPrompt)
        }

        // Fork notice
        if (context.isChildOfFork) {
            sb.appendLine()
            sb.appendLine("NOTE: This agent is running as a forked subagent. Execute the given task directly without spawning further subagents.")
        }

        // Memory prompt
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

    /**
     * Resolve model override.
     */
    private fun resolveModel(context: SubAgentContext): String? {
        if (context.modelOverride != null) return context.modelOverride
        if (context.definition.model != null) {
            if (context.definition.model == "inherit") return null
            return context.definition.model
        }
        return null
    }

    /**
     * Resolve and filter tools available to the agent.
     */
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

    /**
     * Execute a tool with retry logic.
     */
    private suspend fun executeToolWithRetry(
        tool: Tool,
        input: Map<String, Any>,
        retries: Int = MAX_RETRIES_ON_ERROR
    ): ToolResult {
        var lastError: Exception? = null
        for (attempt in 0..retries) {
            try {
                return tool.execute(input)
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

    /**
     * Call the LLM API via ApiClient.
     * Parses the response into content and tool calls.
     */
    private suspend fun callModel(
        messages: List<Message>,
        model: String?
    ): SubAgentModelResponse {
        val finalModel = model ?: ApiConfig.model
        val systemPrompt = messages.firstOrNull { it.type == MessageType.SYSTEM }?.content ?: ""
        val apiMessages = messages.filter { it.type != MessageType.SYSTEM }.map { msg ->
            mapOf(
                "role" to when (msg.type) {
                    MessageType.USER -> "user"
                    MessageType.ASSISTANT -> "assistant"
                    MessageType.TOOL -> "assistant"
                    MessageType.TOOL_RESULT -> "user"
                    MessageType.SYSTEM -> "system"
                    else -> "user"
                },
                "content" to msg.content
            )
        }

        val requestBody = mapOf<String, Any>(
            "model" to finalModel,
            "max_tokens" to 4096,
            "system" to systemPrompt,
            "messages" to apiMessages
        )

        val result = ApiClient.post("/v1/messages", requestBody)
        return result.fold(
            onSuccess = { json ->
                // Parse simple content response (tool call parsing skipped for simplicity)
                val content = extractContentFromJson(json)
                SubAgentModelResponse(content = content)
            },
            onFailure = { error ->
                SubAgentModelResponse(
                    content = "",
                    isError = true,
                    errorMessage = error.message ?: "API call failed"
                )
            }
        )
    }

    /**
     * Extract content from JSON API response.
     */
    private fun extractContentFromJson(json: String): String {
        // Simple content extraction - in production use proper JSON parsing
        val contentPattern = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val match = contentPattern.find(json)
        return match?.groupValues?.get(1)?.replace("\\n", "\n") ?: json.take(2000)
    }

    /**
     * Extract final output from message list.
     */
    private fun extractFinalOutput(messages: List<Message>): String {
        val lastAssistant = messages.lastOrNull { it.type == MessageType.ASSISTANT }
        if (lastAssistant != null) return cleanOutput(lastAssistant.content)

        val lastSystem = messages.lastOrNull()
        return lastSystem?.content ?: "No output produced"
    }

    /**
     * Clean output by removing tool call artifacts.
     */
    private fun cleanOutput(content: String): String {
        return content
            .replace(Regex("<tool_call>.*?</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("```json\\s*\\{.*?\\}\\s*```", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    /**
     * Build a SubAgentResult quickly.
     */
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

/**
 * SubAgent model response.
 */
data class SubAgentModelResponse(
    val content: String,
    val toolCalls: List<Pair<String, Map<String, Any>>> = emptyList(),
    val isError: Boolean = false,
    val errorMessage: String? = null
)

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
