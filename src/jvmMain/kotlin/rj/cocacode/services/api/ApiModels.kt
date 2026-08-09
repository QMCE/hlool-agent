package rj.cocacode.services.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import rj.cocacode.config.ApiConfig
import rj.cocacode.config.ApiType

/**
 * Provider-agnostic content block model. The Anthropic and OpenAI wire formats
 * differ, but both reduce to: assistant text, tool-use requests, and
 * tool-result responses.
 */
sealed class ApiContentBlock {
    data class TextBlock(val text: String) : ApiContentBlock()

    /** Model reasoning/thinking text (Anthropic "thinking" block). */
    data class ThinkingBlock(val thinking: String) : ApiContentBlock()

    data class ToolUseBlock(
        val id: String,
        val name: String,
        val input: Map<String, Any>
    ) : ApiContentBlock()

    data class ToolResultBlock(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false
    ) : ApiContentBlock()
}

/** Parsed assistant message returned by either provider. */
data class ApiResponse(
    val id: String,
    val role: String,
    val content: List<ApiContentBlock>,
    val model: String,
    val stopReason: String?,
    val usage: ApiUsage
) {
    /** Concatenated plain text from all text blocks. */
    val text: String get() = content.filterIsInstance<ApiContentBlock.TextBlock>().joinToString("") { it.text }
    val toolUses: List<ApiContentBlock.ToolUseBlock>
        get() = content.filterIsInstance<ApiContentBlock.ToolUseBlock>()
    /** Concatenated thinking text from all thinking blocks. */
    val thinking: String
        get() = content.filterIsInstance<ApiContentBlock.ThinkingBlock>().joinToString("") { it.thinking }
}

data class ApiUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0
)

/** A tool definition sent to the model. */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
)

/**
 * Builds an API request for a model. Produces the Anthropic Messages format for
 * `api.anthropic.com` and the OpenAI chat-completions format otherwise.
 */
object RequestBuilder {

    val json = Json { ignoreUnknownKeys = true }

    /** A single history entry: role + either a string or a list of blocks. */
    data class ApiMessage(
        val role: String,
        val text: String,
        val blocks: List<ApiContentBlock>? = null
    )

    /** True for the Anthropic Messages format. */
    fun isMessages(apiType: ApiType): Boolean = apiType == ApiType.MESSAGES

    /**
     * The full API path for a chat/messages request:
     *  - messages -> "v1/messages"
     *  - chat     -> "v1/chat/completions"
     */
    fun messagesEndpoint(apiType: ApiType = ApiConfig.apiType): String =
        if (apiType == ApiType.MESSAGES) "v1/messages" else "v1/chat/completions"

    fun build(
        model: String,
        system: String?,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        maxTokens: Int,
        stream: Boolean,
        apiType: ApiType = ApiConfig.apiType,
        /** Token budget for extended thinking; null/0 disables it. */
        thinkingBudget: Int? = null
    ): JsonObject {
        return if (apiType == ApiType.MESSAGES) {
            anthropicRequest(model, system, messages, tools, maxTokens, stream, thinkingBudget)
        } else {
            openAiRequest(model, system, messages, tools, maxTokens, stream)
        }
    }

    // --- Anthropic Messages format ---

    private fun anthropicRequest(
        model: String,
        system: String?,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        maxTokens: Int,
        stream: Boolean,
        thinkingBudget: Int? = null
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("stream", stream)
        if (system != null && system.isNotBlank()) put("system", system)
        if (thinkingBudget != null && thinkingBudget > 0) {
            put("thinking", buildJsonObject {
                put("type", "enabled")
                put("budget_tokens", thinkingBudget)
            })
        }
        put(
            "messages",
            buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        val content = m.blocks
                        if (content != null && content.isNotEmpty()) {
                            put("content", buildJsonArray {
                                content.forEach { block -> add(block.toAnthropicJson()) }
                            })
                        } else {
                            put("content", m.text)
                        }
                    })
                }
            }
        )
        if (tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", ApiClient.toJsonElement(tool.inputSchema))
                        })
                    }
                }
            )
        }
    }

    private fun ApiContentBlock.toAnthropicJson(): JsonObject = when (this) {
        is ApiContentBlock.TextBlock -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        is ApiContentBlock.ThinkingBlock -> buildJsonObject {
            put("type", "thinking")
            put("thinking", thinking)
        }
        is ApiContentBlock.ToolUseBlock -> buildJsonObject {
            put("type", "tool_use")
            put("id", id)
            put("name", name)
            put("input", ApiClient.toJsonElement(input))
        }
        is ApiContentBlock.ToolResultBlock -> buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", toolUseId)
            put("content", content)
            put("is_error", isError)
        }
    }

    // --- OpenAI chat-completions format ---

    private fun openAiRequest(
        model: String,
        system: String?,
        messages: List<ApiMessage>,
        tools: List<ToolSpec>,
        maxTokens: Int,
        stream: Boolean
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", stream)
        put("max_tokens", maxTokens)
        put(
            "messages",
            buildJsonArray {
                if (system != null && system.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", system)
                    })
                }
                messages.forEach { m -> m.addOpenAiMessages(this) }
            }
        )
        if (tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", ApiClient.toJsonElement(tool.inputSchema))
                            })
                        })
                    }
                }
            )
        }
    }

    /**
     * Encode one [ApiMessage] into the OpenAI wire format. The Anthropic-style
     * content blocks are mapped to OpenAI's shape:
     *  - tool_result blocks -> one `role: "tool"` message each
     *  - text + tool_use    -> one `assistant` message with `content` + `tool_calls`
     */
    private fun ApiMessage.addOpenAiMessages(out: kotlinx.serialization.json.JsonArrayBuilder) {
        val blocks = this.blocks
        if (blocks == null || blocks.isEmpty()) {
            out.add(buildJsonObject {
                put("role", role)
                put("content", text)
            })
            return
        }

        blocks.filterIsInstance<ApiContentBlock.ToolResultBlock>().forEach { tr ->
            out.add(buildJsonObject {
                put("role", "tool")
                put("tool_call_id", tr.toolUseId)
                put("content", tr.content)
            })
        }

        val textBlocks = blocks.filterIsInstance<ApiContentBlock.TextBlock>()
        val thinkingBlocks = blocks.filterIsInstance<ApiContentBlock.ThinkingBlock>()
        val toolUses = blocks.filterIsInstance<ApiContentBlock.ToolUseBlock>()
        if (textBlocks.isEmpty() && thinkingBlocks.isEmpty() && toolUses.isEmpty()) return

        val text = textBlocks.joinToString("") { it.text }
        val reasoning = thinkingBlocks.joinToString("") { it.thinking }
        out.add(buildJsonObject {
            put("role", "assistant")
            // Keep content = visible reply only; reasoning goes in its own field
            // so chat proxies still accept tool_calls + history replay.
            if (text.isBlank()) put("content", "") else put("content", text)
            if (reasoning.isNotBlank()) {
                put("reasoning_content", reasoning)
            }
            if (toolUses.isNotEmpty()) {
                put("tool_calls", buildJsonArray {
                    toolUses.forEach { tu ->
                        add(buildJsonObject {
                            put("id", tu.id)
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tu.name)
                                put("arguments", ApiClient.toJsonElement(tu.input).toString())
                            })
                        })
                    }
                })
            }
        })
    }
}

/**
 * Parses a non-streaming model response into [ApiResponse].
 */
object ResponseParser {

    /**
     * Parse a non-streaming model response into [ApiResponse].
     *
     * The wire format is sniffed from the response shape rather than the base
     * URL, so Anthropic-compatible and OpenAI-compatible proxies both parse
     * correctly regardless of the configured host.
     */
    fun parse(baseUrl: String, body: String): ApiResponse {
        val root = Json.parseToJsonElement(body).jsonObject
        return if (root["choices"] != null) parseOpenAi(root) else parseAnthropic(root)
    }

    // --- Anthropic ---

    private fun parseAnthropic(root: JsonObject): ApiResponse {
        val content = root["content"]?.jsonArray?.mapNotNull { el ->
            val obj = el.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> ApiContentBlock.TextBlock(obj["text"]?.jsonPrimitive?.contentOrNull ?: "")
                "thinking" -> ApiContentBlock.ThinkingBlock(obj["thinking"]?.jsonPrimitive?.contentOrNull ?: "")
                "tool_use" -> ApiContentBlock.ToolUseBlock(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    input = (obj["input"] ?: JsonObject(emptyMap())).toKotlinMap()
                )
                else -> null
            }
        } ?: emptyList()

        return ApiResponse(
            id = root["id"]?.jsonPrimitive?.contentOrNull ?: "",
            role = root["role"]?.jsonPrimitive?.contentOrNull ?: "assistant",
            content = content,
            model = root["model"]?.jsonPrimitive?.contentOrNull ?: "",
            stopReason = root["stop_reason"]?.jsonPrimitive?.contentOrNull,
            usage = root["usage"]?.jsonObject?.let { u ->
                ApiUsage(
                    inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                    outputTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                    cacheReadInputTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                    cacheCreationInputTokens = u["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                )
            } ?: ApiUsage()
        )
    }

    // --- OpenAI ---

    private fun parseOpenAi(root: JsonObject): ApiResponse {
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        val message = choice?.get("message")?.jsonObject
        val toolCalls = message?.get("tool_calls")?.jsonArray

        val content = mutableListOf<ApiContentBlock>()
        (message?.get("content") as? JsonPrimitive)?.contentOrNull?.let { text ->
            if (text.isNotBlank()) content.add(ApiContentBlock.TextBlock(text))
        }
        toolCalls?.forEach { tc ->
            val fn = tc.jsonObject["function"]?.jsonObject
            val name = fn?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val args = fn?.get("arguments")?.jsonPrimitive?.contentOrNull
            content.add(ApiContentBlock.ToolUseBlock(
                id = tc.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "",
                name = name,
                input = parseArguments(args)
            ))
        }

        return ApiResponse(
            id = root["id"]?.jsonPrimitive?.contentOrNull ?: "",
            role = message?.get("role")?.jsonPrimitive?.contentOrNull ?: "assistant",
            content = content,
            model = root["model"]?.jsonPrimitive?.contentOrNull ?: "",
            stopReason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull,
            usage = root["usage"]?.jsonObject?.let { u ->
                ApiUsage(
                    inputTokens = u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                    outputTokens = u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                )
            } ?: ApiUsage()
        )
    }

    private fun parseArguments(args: String?): Map<String, Any> {
        if (args.isNullOrBlank()) return emptyMap()
        return try {
            Json.parseToJsonElement(args).toKotlinMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/** Convert a JSON object element into a Kotlin map (values via [toKotlinValue]). */
fun JsonElement.toKotlinMap(): Map<String, Any> = when (this) {
    is JsonObject -> entries.associate { it.key to it.value.toKotlinValue() }
    is JsonArray -> map { it.toKotlinValue() }.let { list ->
        // Not a map input; callers of toKotlinMap expect an object.
        emptyMap<String, Any>()
    }
    else -> emptyMap()
}

private fun JsonElement.toKotlinValue(): Any = when (this) {
    is JsonObject -> entries.associate { it.key to it.value.toKotlinValue() }
    is JsonArray -> map { it.toKotlinValue() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> boolean
        intOrNull != null -> int
        longOrNull != null -> longOrNull!!
        doubleOrNull != null -> doubleOrNull!!
        else -> content
    }
    JsonNull -> "null"
}

/**
 * A single chunk of streamed output, handed to the renderer as it arrives.
 */
data class StreamDelta(
    val text: String = "",
    val thinking: String = ""
)

/**
 * Stateful parser for a streaming (SSE) model response.
 *
 * Feed it one `data:` payload at a time via [feed]; it accumulates text,
 * thinking, tool calls and usage, and returns the incremental [StreamDelta]
 * to render. Call [finalize] after the stream ends to flush any partial
 * OpenAI tool calls.
 *
 * Handles both Anthropic Messages SSE and OpenAI chat-completions SSE.
 */
class StreamAccumulator(private val apiType: ApiType) {

    private val textBuf = StringBuilder()
    private val thinkingBuf = StringBuilder()
    private val toolUses = mutableListOf<ApiContentBlock.ToolUseBlock>()
    private var stopReason: String? = null
    private var usage = ApiUsage()

    /** Set when an SSE payload carries a top-level `error` object. */
    var streamError: String? = null
        private set

    // Anthropic tool_use block state (accumulated across deltas).
    private var anBlockType: String? = null
    private var anBlockId: String? = null
    private var anBlockName: String? = null
    private val anInputJson = StringBuilder()

    // OpenAI tool_call state, keyed by index.
    private data class OaiToolCall(val id: StringBuilder = StringBuilder(), val name: StringBuilder = StringBuilder(), val args: StringBuilder = StringBuilder())
    private val oaiToolCalls = LinkedHashMap<Int, OaiToolCall>()

    val text: String get() = textBuf.toString()
    val thinking: String get() = thinkingBuf.toString()
    val toolUsesList: List<ApiContentBlock.ToolUseBlock> get() = toolUses
    val stopReasonValue: String? get() = stopReason
    val usageValue: ApiUsage get() = usage

    /** Parse one SSE data payload; returns the chunk to render. */
    fun feed(data: String): StreamDelta {
        val root = try {
            Json.parseToJsonElement(data).jsonObject
        } catch (e: Exception) {
            return StreamDelta()
        }
        // Chat proxies often return 200 + `data: {"error":...}` instead of HTTP 4xx.
        root["error"]?.let { err ->
            streamError = when (err) {
                is JsonPrimitive -> err.contentOrNull
                is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
                    ?: err["msg"]?.jsonPrimitive?.contentOrNull
                    ?: err.toString()
                else -> err.toString()
            } ?: "API stream error"
            return StreamDelta()
        }
        return if (apiType == ApiType.MESSAGES) feedAnthropic(root) else feedOpenAi(root)
    }

    /** Flush accumulated OpenAI tool calls into [toolUses]. */
    fun finalize() {
        if (apiType != ApiType.MESSAGES && oaiToolCalls.isNotEmpty()) {
            oaiToolCalls.forEach { (index, call) ->
                val id = call.id.toString().ifBlank {
                    // Many chat proxies omit ids on streamed tool_calls; empty
                    // tool_call_id makes the follow-up request fail / return empty.
                    "call_${index}_${System.nanoTime().toString(36)}"
                }
                toolUses.add(
                    ApiContentBlock.ToolUseBlock(
                        id = id,
                        name = call.name.toString(),
                        input = parseJsonMap(call.args.toString())
                    )
                )
            }
            oaiToolCalls.clear()
        }
    }

    // --- Anthropic ---

    private fun feedAnthropic(root: JsonObject): StreamDelta {
        when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_start" -> {
                val block = root["content_block"]?.jsonObject
                anBlockType = block?.get("type")?.jsonPrimitive?.contentOrNull
                if (anBlockType == "tool_use") {
                    anBlockId = block?.get("id")?.jsonPrimitive?.contentOrNull
                    anBlockName = block?.get("name")?.jsonPrimitive?.contentOrNull
                    anInputJson.clear()
                }
            }
            "content_block_delta" -> {
                val delta = root["delta"]?.jsonObject
                when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                    "thinking_delta" -> {
                        val t = delta["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                        thinkingBuf.append(t)
                        return StreamDelta(thinking = t)
                    }
                    "text_delta" -> {
                        val t = delta["text"]?.jsonPrimitive?.contentOrNull ?: ""
                        textBuf.append(t)
                        return StreamDelta(text = t)
                    }
                    "input_json_delta" -> {
                        anInputJson.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: "")
                    }
                }
            }
            "content_block_stop" -> {
                if (anBlockType == "tool_use") {
                    toolUses.add(
                        ApiContentBlock.ToolUseBlock(
                            id = anBlockId ?: "",
                            name = anBlockName ?: "",
                            input = parseJsonMap(anInputJson.toString())
                        )
                    )
                }
                anBlockType = null
            }
            "message_start", "message_delta" -> {
                root["usage"]?.jsonObject?.let { usage = parseAnthropicUsage(it) }
                if (root["type"]?.jsonPrimitive?.contentOrNull == "message_delta") {
                    stopReason = root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull
                }
            }
        }
        return StreamDelta()
    }

    private fun parseAnthropicUsage(u: JsonObject): ApiUsage = ApiUsage(
        inputTokens = u["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        outputTokens = u["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheReadInputTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
        cacheCreationInputTokens = u["cache_creation_input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
    )

    // --- OpenAI ---

    private fun feedOpenAi(root: JsonObject): StreamDelta {
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return StreamDelta()
        // Some proxies emit `message` instead of `delta` on the final chunk.
        val delta = choice["delta"]?.jsonObject
            ?: choice["message"]?.jsonObject

        var text = extractOpenAiText(delta?.get("content"))
        var thinking = extractOpenAiText(delta?.get("reasoning_content"))
        if (thinking.isEmpty()) {
            thinking = extractOpenAiText(delta?.get("reasoning"))
        }
        if (text.isNotEmpty()) textBuf.append(text)
        if (thinking.isNotEmpty()) thinkingBuf.append(thinking)

        delta?.get("tool_calls")?.jsonArray?.forEach { tc ->
            val obj = tc.jsonObject
            val index = obj["index"]?.jsonPrimitive?.intOrNull
                ?: obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: 0
            val call = oaiToolCalls.getOrPut(index) { OaiToolCall() }
            // Id/name are often repeated on every delta — append would corrupt
            // tool_call_id (call_abccall_abc) and abort the follow-up turn.
            (obj["id"] as? JsonPrimitive)?.contentOrNull?.let { id ->
                if (call.id.isEmpty() && id.isNotBlank()) call.id.append(id)
            }
            obj["function"]?.jsonObject?.let { fn ->
                (fn["name"] as? JsonPrimitive)?.contentOrNull?.let { name ->
                    if (call.name.isEmpty() && name.isNotBlank()) call.name.append(name)
                }
                (fn["arguments"] as? JsonPrimitive)?.contentOrNull?.let { call.args.append(it) }
            }
        }

        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { stopReason = it }
        return StreamDelta(text = text, thinking = thinking)
    }

    /**
     * OpenAI-compatible content may be a plain string or an array of parts
     * (`[{type:text,text:...}]`). Only handling strings dropped whole replies
     * for some chat proxies — context then lost the previous assistant turn.
     */
    private fun extractOpenAiText(el: JsonElement?): String {
        if (el == null || el is JsonNull) return ""
        when (el) {
            is JsonPrimitive -> return el.contentOrNull ?: ""
            is JsonArray -> {
                return el.joinToString("") { part ->
                    when (part) {
                        is JsonPrimitive -> part.contentOrNull ?: ""
                        is JsonObject -> {
                            part["text"]?.jsonPrimitive?.contentOrNull
                                ?: part["content"]?.jsonPrimitive?.contentOrNull
                                ?: ""
                        }
                        else -> ""
                    }
                }
            }
            is JsonObject -> {
                return el["text"]?.jsonPrimitive?.contentOrNull
                    ?: el["content"]?.let { extractOpenAiText(it) }
                    ?: ""
            }
        }
    }

    private fun parseJsonMap(json: String): Map<String, Any> = try {
        Json.parseToJsonElement(json).toKotlinMap()
    } catch (e: Exception) {
        emptyMap()
    }
}
