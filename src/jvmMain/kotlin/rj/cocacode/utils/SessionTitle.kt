package rj.cocacode.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rj.cocacode.config.ApiConfig
import rj.cocacode.services.api.ApiClient
import rj.cocacode.services.api.RequestBuilder
import rj.cocacode.services.api.ResponseParser
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType

/**
 * Session title helpers — mirrors Claude Code's `sessionTitle.ts` /
 * `deriveTitle` flow:
 *
 * 1. Placeholder from the first user message (truncated first sentence).
 * 2. LLM upgrade once the conversation has enough context
 *    ([AUTO_GENERATE_AFTER] messages).
 */
object SessionTitle {

    /** Trigger AI title generation once total messages exceed this. */
    const val AUTO_GENERATE_AFTER = 10

    private const val TITLE_MAX_LEN = 50
    private const val MAX_CONVERSATION_TEXT = 1000

    private val SESSION_TITLE_PROMPT = """
        Generate a concise, sentence-case title (3-7 words) that captures the main topic or goal of this coding session. The title should be clear enough that the user recognizes the session in a list. Use sentence case: capitalize only the first word and proper nouns.

        Return JSON with a single "title" field.

        Good examples:
        {"title": "Fix login button on mobile"}
        {"title": "Add OAuth authentication"}
        {"title": "Debug failing CI tests"}
        {"title": "Refactor API client error handling"}

        Bad (too vague): {"title": "Code changes"}
        Bad (too long): {"title": "Investigate and fix the issue where the login button does not respond on mobile devices"}
        Bad (wrong case): {"title": "Fix Login Button On Mobile"}
    """.trimIndent()

    /**
     * Quick placeholder: first sentence, collapsed whitespace, truncated.
     * Returns null if empty after cleaning.
     */
    fun deriveTitle(raw: String): String? {
        val clean = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return null

        val firstSentence = Regex("^(.*?[.!?])\\s").find(clean)?.groupValues?.get(1) ?: clean
        val flat = firstSentence.trim()
        if (flat.isEmpty()) return null
        return if (flat.length > TITLE_MAX_LEN) {
            flat.take(TITLE_MAX_LEN - 1) + "…"
        } else {
            flat
        }
    }

    /** Flatten user/assistant text for the title model (tail-sliced). */
    fun extractConversationText(messages: List<Message>): String {
        val parts = messages.mapNotNull { msg ->
            when (msg.type) {
                MessageType.USER, MessageType.ASSISTANT -> msg.content.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        val text = parts.joinToString("\n")
        return if (text.length > MAX_CONVERSATION_TEXT) text.takeLast(MAX_CONVERSATION_TEXT) else text
    }

    /**
     * Ask the configured model for a sentence-case title.
     * Returns null on any failure (caller keeps the placeholder).
     */
    suspend fun generateTitle(conversationText: String): String? = withContext(Dispatchers.IO) {
        val trimmed = conversationText.trim()
        if (trimmed.isEmpty() || !ApiConfig.isConfigured) return@withContext null

        try {
            val request = RequestBuilder.build(
                model = ApiConfig.model,
                system = SESSION_TITLE_PROMPT,
                messages = listOf(RequestBuilder.ApiMessage(role = "user", text = trimmed)),
                tools = emptyList(),
                maxTokens = 80,
                stream = false,
                apiType = ApiConfig.apiType,
                thinkingBudget = null
            )
            val result = ApiClient.post(RequestBuilder.messagesEndpoint(), request)
            result.fold(
                onSuccess = { body ->
                    val parsed = ResponseParser.parse(ApiConfig.baseUrl, body)
                    parseTitleJson(parsed.text)
                },
                onFailure = {
                    Logger.warn("Session title generation failed: ${it.message}")
                    null
                }
            )
        } catch (e: Exception) {
            Logger.warn("Session title generation failed: ${e.message}")
            null
        }
    }

    private fun parseTitleJson(text: String): String? {
        // Prefer {"title":"..."} ; fall back to first non-empty line.
        val jsonMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(text)
        val raw = jsonMatch?.groupValues?.get(1)
            ?: text.lines().map { it.trim().trim('"', '\'') }.firstOrNull { it.isNotEmpty() }
            ?: return null
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.length > TITLE_MAX_LEN) {
            cleaned.take(TITLE_MAX_LEN - 1) + "…"
        } else {
            cleaned
        }
    }
}
