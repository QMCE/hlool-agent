package rj.cocacode.tools

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val text: String = "",
    val isError: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)
