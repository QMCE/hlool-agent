package rj.cocacode.services.api

/**
 * Gson-friendly wire transcript for session persistence.
 * Holds thinking / tool_use / tool_result so chat & messages APIs get full context
 * on the next user turn (not just the final assistant text).
 */
data class WireMessage(
    val role: String,
    val text: String = "",
    val blocks: List<WireBlock>? = null
)

data class WireBlock(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any?>? = null,
    val toolUseId: String? = null,
    val content: String? = null,
    val isError: Boolean = false
)

fun RequestBuilder.ApiMessage.toWireMessage(): WireMessage =
    WireMessage(
        role = role,
        text = text,
        blocks = blocks?.map { it.toWireBlock() }
    )

fun WireMessage.toApiMessage(): RequestBuilder.ApiMessage =
    RequestBuilder.ApiMessage(
        role = role,
        text = text,
        blocks = blocks?.mapNotNull { it.toApiBlock() }
    )

private fun ApiContentBlock.toWireBlock(): WireBlock = when (this) {
    is ApiContentBlock.TextBlock -> WireBlock(type = "text", text = text)
    is ApiContentBlock.ThinkingBlock -> WireBlock(type = "thinking", thinking = thinking)
    is ApiContentBlock.ToolUseBlock -> WireBlock(
        type = "tool_use",
        id = id,
        name = name,
        input = input
    )
    is ApiContentBlock.ToolResultBlock -> WireBlock(
        type = "tool_result",
        toolUseId = toolUseId,
        content = content,
        isError = isError
    )
}

private fun WireBlock.toApiBlock(): ApiContentBlock? = when (type) {
    "text" -> ApiContentBlock.TextBlock(text.orEmpty())
    "thinking" -> ApiContentBlock.ThinkingBlock(thinking.orEmpty())
    "tool_use" -> ApiContentBlock.ToolUseBlock(
        id = id.orEmpty(),
        name = name.orEmpty(),
        input = input?.mapNotNull { (k, v) ->
            if (v == null) null else k to v
        }?.toMap() ?: emptyMap()
    )
    "tool_result" -> ApiContentBlock.ToolResultBlock(
        toolUseId = toolUseId.orEmpty(),
        content = content.orEmpty(),
        isError = isError
    )
    else -> null
}
