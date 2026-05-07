package rj.cocacode.agents

import rj.cocacode.tools.Tool
import rj.cocacode.tools.ToolRegistry
import rj.cocacode.types.Message

/**
 * Tool filtering and utility functions for agents.
 * Mirrors agentToolUtils.ts from the reference implementation.
 */

/**
 * Tools that are disallowed for ALL agents.
 */
val ALL_AGENT_DISALLOWED_TOOLS = setOf(
    "Task",           // Agent tool itself (cannot self-spawn)
    "Agent",          // Legacy agent tool name
    "ExitPlanMode",   // Agent lifecycle management
    "PlanMode",       // Plan mode is for the parent
)

/**
 * Tools disallowed for CUSTOM (non-built-in) agents.
 * More restrictive for safety.
 */
val CUSTOM_AGENT_DISALLOWED_TOOLS: Set<String> = emptySet()

/**
 * Tools allowed for ASYNC (background) agents.
 * More restricted since they run without direct user supervision.
 */
val ASYNC_AGENT_ALLOWED_TOOLS = setOf(
    "Read",
    "Grep",
    "Glob",
    "Search",
    "ListFiles",
    "Bash",
    "Write",
    "Edit",
    "Rename",
)

/**
 * Tools allowed for in-process teammates in swarm mode.
 */
val IN_PROCESS_TEAMMATE_ALLOWED_TOOLS = setOf(
    "Task", // Can spawn sync subagents
)

/**
 * Result of resolving agent tools.
 */
data class ResolvedAgentTools(
    val hasWildcard: Boolean,
    val validTools: List<String>,
    val invalidTools: List<String>,
    val resolvedTools: List<Tool>
)

/**
 * Filter available tools based on agent configuration.
 */
fun filterToolsForAgent(
    tools: List<Tool>,
    isBuiltIn: Boolean,
    isAsync: Boolean = false,
    permissionMode: String? = null
): List<Tool> {
    return tools.filter { tool ->
        // Allow MCP tools for all agents
        if (tool.name.startsWith("mcp__")) return@filter true

        if (ALL_AGENT_DISALLOWED_TOOLS.contains(tool.name)) return@filter false

        if (!isBuiltIn && CUSTOM_AGENT_DISALLOWED_TOOLS.contains(tool.name)) return@filter false

        if (isAsync && !ASYNC_AGENT_ALLOWED_TOOLS.contains(tool.name)) return@filter false

        true
    }
}

/**
 * Resolve and validate agent tools against available tools.
 * Handles wildcard expansion and validation.
 */
fun resolveAgentTools(
    agentDefinition: AgentDefinition,
    availableTools: List<Tool>,
    isAsync: Boolean = false
): ResolvedAgentTools {
    val filteredTools = filterToolsForAgent(
        tools = availableTools,
        isBuiltIn = agentDefinition.source == "built-in",
        isAsync = isAsync,
        permissionMode = agentDefinition.permissionMode
    )

    // Create disallowed tool set
    val disallowedToolSet = agentDefinition.disallowedTools.toSet()

    // Filter available tools based on disallowed list
    val allowedAvailableTools = filteredTools.filter { tool ->
        !disallowedToolSet.contains(tool.name)
    }

    // If tools is ["*"], allow all tools (after filtering)
    val hasWildcard = agentDefinition.allowedTools.size == 1 &&
            agentDefinition.allowedTools[0] == "*"

    if (hasWildcard) {
        return ResolvedAgentTools(
            hasWildcard = true,
            validTools = emptyList(),
            invalidTools = emptyList(),
            resolvedTools = allowedAvailableTools
        )
    }

    val availableToolMap = allowedAvailableTools.associateBy { it.name }

    val validTools = mutableListOf<String>()
    val invalidTools = mutableListOf<String>()
    val resolved = mutableListOf<Tool>()

    for (toolSpec in agentDefinition.allowedTools) {
        val toolName = toolSpec.trim()
        val tool = availableToolMap[toolName]

        if (tool != null) {
            validTools.add(toolName)
            resolved.add(tool)
        } else {
            invalidTools.add(toolName)
        }
    }

    return ResolvedAgentTools(
        hasWildcard = false,
        validTools = validTools,
        invalidTools = invalidTools,
        resolvedTools = resolved
    )
}

/**
 * Count total tool uses across a list of messages.
 */
fun countToolUses(messages: List<Message>): Int {
    return messages.count { it.type == rj.cocacode.types.MessageType.TOOL }
}

/**
 * Finalize agent tool execution and format the output.
 * Returns a formatted result string with agent output and statistics.
 */
fun finalizeAgentTool(
    result: SubAgentResult,
    agentDefinition: AgentDefinition
): String {
    val sb = StringBuilder()

    if (result.isError) {
        sb.appendLine("[Agent:${agentDefinition.agentType}] Task failed: ${result.task}")
        sb.appendLine()
        sb.appendLine(result.output)
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("Status: ERROR")
    } else {
        sb.appendLine("[Agent:${agentDefinition.agentType}] Task complete: ${result.task}")
        if (result.output.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(result.output)
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("Status: COMPLETE")
    }

    sb.appendLine("Turns: ${result.turnCount}")
    sb.appendLine("Tool calls: ${result.toolCallCount}")

    if (result.isAsync) {
        sb.appendLine("Execution: background")
    }

    if (result.memoryUpdated) {
        sb.appendLine("Agent memory: updated")
    }

    return sb.toString()
}

/**
 * Extract partial result from a SubAgentResult for summary display.
 */
fun extractPartialResult(result: SubAgentResult): String {
    val output = result.output.trim()
    if (output.isBlank()) return ""

    // Take first meaningful lines for summary
    val lines = output.lines().filter { it.isNotBlank() }
    val summaryLines = lines.take(5)
    val suffix = if (lines.size > 5) "\n... (${lines.size - 5} more lines)" else ""

    return summaryLines.joinToString("\n") + suffix
}
