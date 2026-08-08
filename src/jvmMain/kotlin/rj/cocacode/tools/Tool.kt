package rj.cocacode.tools

/**
 * Base class for all tools. Concrete tools subclass this and override [execute].
 *
 * Reconstructed during the KMP migration. Subclasses are declared as
 * `SomeToolImpl : Tool(name, description)` and override the suspend [execute].
 */
open class Tool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap()
) {
    open suspend fun execute(input: Map<String, Any>): ToolResult =
        ToolResult(text = "Tool '$name' execution not implemented", isError = true)
}