package rj.cocacode.types

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

@Serializable
enum class MessageType {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
    TOOL_RESULT
}

@Serializable
data class Attachment(
    val type: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val data: String? = null
)

@Serializable
data class Conversation(
    val id: String,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun addMessage(message: Message): Conversation = copy(
        messages = messages + message,
        updatedAt = System.currentTimeMillis()
    )
}

object MessageBuilder {
    fun userMessage(content: String) = Message(
        id = rj.cocacode.utils.generateUuid(),
        type = MessageType.USER,
        content = content
    )
    
    fun assistantMessage(content: String) = Message(
        id = rj.cocacode.utils.generateUuid(),
        type = MessageType.ASSISTANT,
        content = content
    )
    
    fun systemMessage(content: String) = Message(
        id = rj.cocacode.utils.generateUuid(),
        type = MessageType.SYSTEM,
        content = content
    )
    
    fun toolResultMessage(toolName: String, result: String) = Message(
        id = rj.cocacode.utils.generateUuid(),
        type = MessageType.TOOL_RESULT,
        content = "$toolName: $result"
    )
}