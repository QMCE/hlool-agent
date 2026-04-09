package rj.cocacode.utils

data class TokenMessage(val type: String, val message: TokenMessageBody? = null)
data class TokenMessageBody(val id: String? = null, val model: String? = null, val content: List<ContentBlock> = emptyList(), val usage: Usage? = null)
data class ContentBlock(val type: String, val text: String? = null, val thinking: String? = null, val data: String? = null, val input: Any? = null)
data class Usage(val input_tokens: Int, val output_tokens: Int, val cache_creation_input_tokens: Int? = null, val cache_read_input_tokens: Int? = null)

val SYNTHETIC_MESSAGES: Set<String> = emptySet()
const val SYNTHETIC_MODEL: String = "synthetic"

fun getTokenUsage(message: TokenMessage): Usage? {
    val m = message
    val content = m.message?.content
    if (m.type != "assistant" || m.message?.usage == null) return null
    val first = content?.firstOrNull()
    val isText = first?.type == "text" && first.text != null && SYNTHETIC_MESSAGES.contains(first.text)
    val model = m.message?.model
    if (isText == true) return null
    if (model == SYNTHETIC_MODEL) return null
    return m.message?.usage
}

private fun getAssistantTokenMessageId(message: TokenMessage): String? {
    val m = message
    val hasId = m.message?.id != null
    val model = m.message?.model
    if (m.type == "assistant" && hasId == true && model != SYNTHETIC_MODEL) {
        return m.message?.id
    }
    return null
}

fun getTokenCountFromUsage(usage: Usage): Int {
    val cacheSum = (usage.cache_creation_input_tokens ?: 0) + (usage.cache_read_input_tokens ?: 0)
    return usage.input_tokens + cacheSum + usage.output_tokens
}

fun tokenCountFromLastAPIResponse(messages: List<TokenMessage>): Int {
    for (i in messages.size - 1 downTo 0) {
        val usage = getTokenUsage(messages[i])
        if (usage != null) {
            return getTokenCountFromUsage(usage)
        }
    }
    return 0
}

fun finalContextTokensFromLastResponse(messages: List<TokenMessage>): Int {
    for (i in messages.size - 1 downTo 0) {
        val usage = getTokenUsage(messages[i])
        if (usage != null) {
            val iterations = null
            return getTokenCountFromUsage(usage)
        }
    }
    return 0
}

fun messageTokenCountFromLastAPIResponse(messages: List<TokenMessage>): Int {
    for (i in messages.size - 1 downTo 0) {
        val usage = getTokenUsage(messages[i])
        if (usage != null) {
            return usage.output_tokens
        }
    }
    return 0
}

fun getCurrentUsage(messages: List<TokenMessage>): CurrentUsage? {
    for (i in messages.size - 1 downTo 0) {
        val usage = getTokenUsage(messages[i])
        if (usage != null) {
            return CurrentUsage(usage.input_tokens, usage.output_tokens, usage.cache_creation_input_tokens ?: 0, usage.cache_read_input_tokens ?: 0)
        }
    }
    return null
}

data class CurrentUsage(val input_tokens: Int, val output_tokens: Int, val cache_creation_input_tokens: Int, val cache_read_input_tokens: Int)

fun doesMostRecentAssistantTokenMessageExceed200k(messages: List<TokenMessage>): Boolean {
    val THRESHOLD = 200_000
    val lastAsst = messages.findLast { it.type == "assistant" }
    if (lastAsst == null) return false
    val usage = getTokenUsage(lastAsst)
    return usage?.let { getTokenCountFromUsage(it) > THRESHOLD } ?: false
}

fun jsonStringify(input: Any?): String = input?.toString() ?: "null"

data class AssistantTokenMessage(val message: TokenMessageBody)

fun getAssistantTokenMessageContentLength(message: AssistantTokenMessage): Int {
    var contentLength = 0
    for (block in message.message.content) {
        when (block.type) {
            "text" -> contentLength += block.text?.length ?: 0
            "thinking" -> contentLength += block.thinking?.length ?: 0
            "redacted_thinking" -> contentLength += block.data?.length ?: 0
            "tool_use" -> contentLength += jsonStringify(block.input).length
        }
    }
    return contentLength
}

fun tokenCountWithEstimation(messages: List<TokenMessage>): Int {
    var index = messages.size - 1
    for (i in messages.size - 1 downTo 0) {
        val usage = getTokenUsage(messages[i])
        if (usage != null) {
            val responseId = getAssistantTokenMessageId(messages[i])
            if (responseId != null) {
                var j = i - 1
                while (j >= 0) {
                    val prior = messages[j]
                    val priorId = getAssistantTokenMessageId(prior)
                    if (priorId == responseId) {
                        index = j
                    } else if (priorId != null) {
                        break
                    }
                    j--
                }
            }
            val used = getTokenCountFromUsage(usage)
            val est = roughTokenCountEstimationForTokenMessages(messages.subList(index + 1, messages.size))
            return used + est
        }
    }
    return roughTokenCountEstimationForTokenMessages(messages)
}

fun roughTokenCountEstimationForTokenMessages(messages: List<TokenMessage>): Int {
    var sum = 0
    for (m in messages) {
        sum += (m.toString().length / 4)
    }
    return if (sum == 0) 1 else sum
}
