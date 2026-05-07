package rj.cocacode.types

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val isError: Boolean = false,
    val toolUseId: String? = null,
    val data: Any? = null,
    val error: String? = null,
    val content: String? = null
)

@Serializable
data class ToolExecutionResult(
    val toolUseId: String,
    val isError: Boolean,
    val content: String,
    val toolName: String
)