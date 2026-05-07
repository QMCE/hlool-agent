package rj.cocacode.agents

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolResult
import rj.cocacode.utils.generateUuid
import rj.cocacode.utils.Logger
import rj.cocacode.state.AbortController

/**
 * SubAgentTool - Spawns subagents to perform delegated tasks autonomously.
 *
 * Allows the main agent to delegate complex or independent subtasks
 * to specialized subagents that can run with their own context, tools, and
 * decision-making capability.
 *
 * Mirrors AgentTool.tsx from the reference implementation.
 *
 * Features:
 * - Multiple agent types (code-reviewer, test-runner, explore, plan, etc.)
 * - Isolated subagent context with restricted tool access
 * - Turn limits and timeouts for safety
 * - Background (async) execution support
 * - Model override per agent call
 * - Result streaming back to parent
 */
class SubAgentTool(
    private val agentLoader: AgentLoader = AgentLoader(),
    private val coroutineScope: CoroutineScope? = null
) : Tool(
    name = "Task",
    description = """
Launch a new agent to handle complex, multi-step tasks autonomously.

Available agent types and the tools they have access to:
- general-purpose: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks. When you are searching for a keyword or file and are not confident that you will find the right match in the first few tries use this agent. (Tools: *)
- explore: Fast agent specialized for exploring codebases. Use this when you need to find files by patterns, search code for keywords, or answer questions about the codebase. (Tools: Read, Grep, Glob, Bash, Search, ListFiles)
- plan: Create detailed implementation plans before making changes. Does not execute any changes. (Tools: Read, Grep, Glob, Search, ListFiles)
- verification: Verify changes compile and tests pass. (Tools: Bash, Read, Grep, Glob, Search)
- code-reviewer: Use this agent when you need a second pair of eyes to review code for bugs, security issues, or style violations. (Tools: Read, Grep, Bash, Edit)
- test-runner: Use this agent when you need to run tests and analyze failures. (Tools: Bash, Read, Grep)
- code-explorer: Fast agent specialized for exploring codebases. (Tools: Read, Grep, Glob, Bash)
- statusline-setup: Use this agent to configure the user's Claude Code status line. (Tools: Read, Edit, Bash)

When using the Task tool, you must specify a subagent_type parameter to select which agent type to use.

When NOT to use the Task tool:
- If you want to read a specific file path, use the Read or Glob tool instead of the Task tool.
- If you are searching for a specific class definition like "class Foo", use the Glob tool instead.
- If you are searching for code within a specific file or set of 2-3 files, use the Read tool instead.
- Other tasks that are simple, single-step operations don't need the Task tool.

Usage notes:
- Launch multiple agents concurrently whenever possible, to maximize performance; to do that, use a single message with multiple tool uses.
- When the agent is done, it will return a single message back to you. The result returned by the agent is not visible to the user. To show the user the result, you should send a text message back to the user with a concise summary.
- Each agent invocation is stateless. You will not be able to send additional messages to the agent, nor will the agent be able to communicate with you outside of its final report.
- The agent's outputs should generally be trusted.
- Clearly tell the agent whether you expect it to write code or just do research.
""".trimIndent(),
    inputSchema = SubAgentToolSchema.inputSchema
) {

    companion object {
        private const val MAX_CONCURRENT_SUBAGENTS = 10
        private val activeSubAgents = java.util.concurrent.atomic.AtomicInteger(0)
    }

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val description = input["description"] as? String
            ?: return ToolResult(text = "Error: description is required", isError = true)
        val prompt = input["prompt"] as? String
            ?: return ToolResult(text = "Error: prompt is required", isError = true)
        val subagentType = input["subagent_type"] as? String ?: "general-purpose"
        val runInBackground = input["run_in_background"] as? Boolean ?: false
        val modelOverride = input["model"] as? String
        val cwdOverride = input["cwd"] as? String
        val memoryScope = input["memory_scope"] as? String

        // Check concurrency limit (skip for background tasks)
        if (!runInBackground && activeSubAgents.get() >= MAX_CONCURRENT_SUBAGENTS) {
            return ToolResult(
                text = "Error: Too many concurrent subagents (max $MAX_CONCURRENT_SUBAGENTS). Wait for some to complete.",
                isError = true
            )
        }

        activeSubAgents.incrementAndGet()

        try {
            // Load agent definition
            val definition = agentLoader.getAgent(subagentType)
            if (definition == null) {
                val availableTypes = agentLoader.listAgents().keys.joinToString(", ")
                return ToolResult(
                    text = "Error: Unknown subagent type '$subagentType'. Available: $availableTypes",
                    isError = true
                )
            }

            // Build additional context from input
            val additionalContext = mutableMapOf<String, String>()
            (input["context"] as? Map<*, *>)?.forEach { (k, v) ->
                additionalContext[k.toString()] = v.toString()
            }

            // Create subagent context
            val workingDir = cwdOverride
                ?: input["working_directory"] as? String
                ?: System.getProperty("user.dir") ?: "."

            val subAgentContext = SubAgentContext(
                definition = definition,
                task = prompt,
                parentMessages = emptyList(),
                workingDirectory = workingDir,
                additionalContext = additionalContext,
                memoryScope = memoryScope ?: definition.memoryScope,
                modelOverride = modelOverride ?: definition.model
            )

            // Handle background execution
            if (runInBackground) {
                val scope = coroutineScope ?: CoroutineScope(Dispatchers.Default)
                val taskNotifId = generateUuid()

                val abortController = AbortController()
                val engine = SubAgentEngineManager.getEngine()
                engine.executeAsync(
                    context = subAgentContext,
                    scope = scope,
                    abortController = abortController,
                    onComplete = { result ->
                        Logger.info("[bg-task:$taskNotifId] Async agent '${result.agentType}' completed: ${result.isError}")
                    }
                )

                return ToolResult(
                    text = "[Background task started] Task '$description' running in background for agent '$subagentType'. Task ID: $taskNotifId. The agent will execute asynchronously; results will be processed when complete.",
                    isError = false,
                    metadata = mapOf(
                        "agent_type" to subagentType,
                        "task_notification_id" to taskNotifId,
                        "description" to description,
                        "is_async" to true
                    )
                )
            }

            // Synchronous execution
            val engine = SubAgentEngineManager.getEngine()
            val result = engine.execute(subAgentContext)

            // Format output using agent_tool_result format
            val formattedOutput = formatAgentToolResult(result, description)

            return ToolResult(
                text = formattedOutput,
                isError = result.isError,
                metadata = mapOf(
                    "agent_type" to result.agentType,
                    "turn_count" to result.turnCount.toString(),
                    "tool_call_count" to result.toolCallCount.toString(),
                    "description" to description
                )
            )
        } catch (e: Exception) {
            Logger.error("Subagent execution failed", e)
            return ToolResult(
                text = "Subagent execution failed: ${e.message}",
                isError = true
            )
        } finally {
            activeSubAgents.decrementAndGet()
        }
    }

    /**
     * Format the agent output with the tool_result format.
     */
    private fun formatAgentToolResult(result: SubAgentResult, description: String): String {
        return finalizeAgentTool(result, result.agentType.let {
            agentLoader.getAgent(it) ?: result.definition
        })
    }

    private val SubAgentResult.definition: AgentDefinition
        get() = AgentDefinition(
            agentType = this.agentType,
            whenToUse = "",
            systemPrompt = ""
        )
}

/**
 * Input schema for SubAgentTool.
 */
object SubAgentToolSchema {
    val inputSchema = mapOf(
        "description" to mapOf(
            "type" to "string",
            "description" to "A short (3-5 words) description of the task"
        ),
        "prompt" to mapOf(
            "type" to "string",
            "description" to "The task for the agent to perform"
        ),
        "subagent_type" to mapOf(
            "type" to "string",
            "description" to "The type of specialized agent to use for this task",
            "enum" to listOf(
                "general-purpose",
                "code-reviewer",
                "test-runner",
                "code-explorer",
                "explore",
                "plan",
                "verification",
                "statusline-setup",
                "worker"
            )
        ),
        "run_in_background" to mapOf(
            "type" to "boolean",
            "description" to "If true, the agent runs asynchronously in the background"
        ),
        "model" to mapOf(
            "type" to "string",
            "description" to "Model override for the subagent"
        ),
        "cwd" to mapOf(
            "type" to "string",
            "description" to "Working directory override for the subagent"
        ),
        "working_directory" to mapOf(
            "type" to "string",
            "description" to "Working directory for the subagent (defaults to current directory)"
        ),
        "memory_scope" to mapOf(
            "type" to "string",
            "description" to "Memory scope for agent memory persistence: 'none', 'user', 'project', 'local'"
        ),
        "context" to mapOf(
            "type" to "object",
            "description" to "Additional context key-value pairs to pass to the subagent"
        )
    )
}
