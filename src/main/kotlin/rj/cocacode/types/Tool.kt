package rj.cocacode.types

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

/**
 * Tool definition for available operations.
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap()
) {
    /**
     * Execute the tool with the given input.
     * Returns a ToolResult with the execution outcome.
     */
    suspend fun execute(input: Map<String, Any>): ToolResult {
        // This is a placeholder - actual implementation would use tool handlers
        return ToolResult(
            isError = true,
            content = "Tool '$name' execution not implemented"
        )
    }
}

/**
 * Agent definitions for multi-agent support.
 */
@Serializable
data class AgentDefinitions(
    val agents: Map<String, AgentDefinition> = emptyMap()
)

@Serializable
data class AgentDefinition(
    val name: String,
    val description: String,
    val systemPrompt: String,
    val tools: List<String> = emptyList(),
    val model: String? = null
)

/**
 * MCP server connection configuration.
 */
data class McpServerConnection(
    val id: String,
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)
