package rj.cocacode.tools

/**
 * Result of a tool execution.
 *
 * Reconstructed during the KMP migration. Match the current call sites:
 *  - `text`    : rendered output (or error message)
 *  - `isError` : whether execution failed
 *  - `metadata`: structured info (file paths, line counts, agent turn counts, ...)
 */
data class ToolResult(
    val text: String,
    val isError: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)