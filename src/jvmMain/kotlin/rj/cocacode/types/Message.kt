package rj.cocacode.types

/**
 * Core message type used across the query engine, agents, and session storage.
 *
 * Reconstructed during the KMP migration (the `src/main/` -> `src/jvmMain/` move
 * dropped this file). Fields match the current call sites:
 *  - id, type, content, attachments      (base shape)
 *  - isError / toolUseId / apiError      (tool & API error correlation)
 */
data class Message(
    val id: String,
    val type: MessageType,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val isError: Boolean = false,
    val toolUseId: String? = null,
    val apiError: String? = null
)

/**
 * Message role classification. Values mirror the reference Claude Code SDK
 * message types plus the tool/attachment variants used by the Kotlin port.
 */
enum class MessageType {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
    TOOL_RESULT,
    ATTACHMENT
}

/**
 * Attachment payload attached to a message (e.g. hook output, file content).
 * `data` carries the raw payload; `type` mirrors the reference attachment type.
 */
data class Attachment(
    val type: String,
    val data: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null
)