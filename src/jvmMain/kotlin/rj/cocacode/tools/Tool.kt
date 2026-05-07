package rj.cocacode.tools

import kotlinx.serialization.Serializable

/**
 * Tool definition for available operations.
 * Tool implementations extend this class.
 */
@Serializable
open class Tool(
    open val name: String,
    open val description: String,
    open val inputSchema: Map<String, Any> = emptyMap()
) {
    /**
     * Execute the tool with the given input.
     * Returns a ToolResult with the execution outcome.
     */
    open suspend fun execute(input: Map<String, Any>): ToolResult {
        return ToolResult(
            text = "Tool '$name' execution not implemented",
            isError = true
        )
    }
}
