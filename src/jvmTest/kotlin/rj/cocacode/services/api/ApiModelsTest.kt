package rj.cocacode.services.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import rj.cocacode.config.ApiType

class ApiModelsTest {

    // --- Request building ---

    @Test
    fun `anthropic request has system as top-level field and input_schema tools`() {
        val req = RequestBuilder.build(
            model = "claude-test",
            system = "sys",
            messages = listOf(RequestBuilder.ApiMessage("user", "hi")),
            tools = listOf(ToolSpec("Read", "read files", mapOf("type" to "object"))),
            maxTokens = 100,
            stream = false
        )
        assertEquals("claude-test", req["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals("sys", req["system"]?.jsonPrimitive?.contentOrNull)
        assertTrue(req.containsKey("tools"))
        val tool = req["tools"]?.jsonArray?.first()?.jsonObject!!
        assertEquals("Read", tool["name"]?.jsonPrimitive?.contentOrNull)
        assertTrue(tool.containsKey("input_schema"))
    }

    @Test
    fun `openai request embeds system as a message and wraps tools in function`() {
        val req = RequestBuilder.build(
            model = "gpt-test",
            system = "sys",
            messages = listOf(RequestBuilder.ApiMessage("user", "hi")),
            tools = listOf(ToolSpec("Read", "read files", mapOf("type" to "object"))),
            maxTokens = 100,
            stream = false,
            apiType = ApiType.CHAT
        )
        // No top-level system in OpenAI format.
        assertEquals(null, req["system"]?.jsonPrimitive?.contentOrNull)
        val messages = req["messages"]?.jsonArray!!
        val firstRole = messages.first().jsonObject["role"]?.jsonPrimitive?.contentOrNull
        assertEquals("system", firstRole)
        val tool = req["tools"]?.jsonArray?.first()?.jsonObject!!
        assertEquals("function", tool["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Read", tool["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull)
    }

    // --- Response parsing ---

    private val anthropicBody = """
        {"id":"m1","type":"message","role":"assistant","model":"claude-test",
         "content":[
           {"type":"text","text":"hello"},
           {"type":"tool_use","id":"t1","name":"Read","input":{"file_path":"/x"}}
         ],
         "stop_reason":"tool_use",
         "usage":{"input_tokens":10,"output_tokens":5}}
    """.trimIndent()

    private val openAiBody = """
        {"id":"c1","object":"chat.completion","model":"gpt-test",
         "choices":[{"index":0,"message":{"role":"assistant",
            "content":"hello",
            "tool_calls":[{"id":"call1","type":"function",
                "function":{"name":"Read","arguments":"{\"file_path\": \"/x\"}"}}]},
            "finish_reason":"tool_calls"}],
         "usage":{"prompt_tokens":10,"completion_tokens":5}}
    """.trimIndent()

    @Test
    fun `parse extracts text and tool_use from anthropic response`() {
        val resp = ResponseParser.parse("https://api.anthropic.com", anthropicBody)
        assertEquals("hello", resp.text)
        assertEquals(1, resp.toolUses.size)
        assertEquals("Read", resp.toolUses[0].name)
        assertEquals("/x", resp.toolUses[0].input["file_path"])
        assertEquals("tool_use", resp.stopReason)
        assertEquals(10, resp.usage.inputTokens)
    }

    @Test
    fun `parse sniffs openai response even with anthropic base url`() {
        val resp = ResponseParser.parse("https://api.anthropic.com", openAiBody)
        assertEquals("hello", resp.text)
        assertEquals(1, resp.toolUses.size)
        assertEquals("Read", resp.toolUses[0].name)
        assertEquals("/x", resp.toolUses[0].input["file_path"])
    }

    @Test
    fun `parse sniffs anthropic response even with openai base url`() {
        val resp = ResponseParser.parse("http://127.0.0.1:9999", anthropicBody)
        assertEquals("hello", resp.text)
        assertEquals("tool_use", resp.stopReason)
    }

    @Test
    fun `openai request encodes tool calls and tool results in openai format`() {
        val req = RequestBuilder.build(
            model = "gpt-test",
            system = "sys",
            messages = listOf(
                // Assistant turn: text + tool_use
                RequestBuilder.ApiMessage(
                    role = "assistant",
                    text = "I'll check.",
                    blocks = listOf(
                        ApiContentBlock.TextBlock("I'll check."),
                        ApiContentBlock.ToolUseBlock("call1", "Bash", mapOf("command" to "ls"))
                    )
                ),
                // User turn wrapping the tool result
                RequestBuilder.ApiMessage(
                    role = "user",
                    text = "",
                    blocks = listOf(
                        ApiContentBlock.ToolResultBlock("call1", "file1 file2", false)
                    )
                )
            ),
            tools = listOf(ToolSpec("Bash", "run", mapOf("type" to "object"))),
            maxTokens = 100,
            stream = false,
            apiType = ApiType.CHAT
        )

        val messages = req["messages"]?.jsonArray!!
        // index 0 = system, 1 = assistant, 2 = tool
        assertEquals(3, messages.size)

        val assistant = messages[1].jsonObject
        assertEquals("assistant", assistant["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("I'll check.", assistant["content"]?.jsonPrimitive?.contentOrNull)
        val toolCall = assistant["tool_calls"]?.jsonArray?.first()?.jsonObject!!
        assertEquals("call1", toolCall["id"]?.jsonPrimitive?.contentOrNull)
        assertEquals("function", toolCall["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Bash", toolCall["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull)
        assertEquals("""{"command":"ls"}""", toolCall["function"]?.jsonObject?.get("arguments")?.jsonPrimitive?.contentOrNull)

        val toolMsg = messages[2].jsonObject
        assertEquals("tool", toolMsg["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals("call1", toolMsg["tool_call_id"]?.jsonPrimitive?.contentOrNull)
        assertEquals("file1 file2", toolMsg["content"]?.jsonPrimitive?.contentOrNull)
        assertTrue(!toolMsg.containsKey("type"))
    }

    @Test
    fun `messagesEndpoint selects format-correct path`() {
        assertEquals("v1/messages", RequestBuilder.messagesEndpoint(ApiType.MESSAGES))
        assertEquals("v1/chat/completions", RequestBuilder.messagesEndpoint(ApiType.CHAT))
    }

    @Test
    fun `toJsonElement converts nested maps and lists`() {
        val el = ApiClient.toJsonElement(mapOf("a" to 1, "b" to listOf("x", true)))
        val json = el.toString()
        assertTrue(json.contains("\"a\":1"))
        assertTrue(json.contains("\"x\""))
        assertTrue(json.contains("true"))
    }
}
