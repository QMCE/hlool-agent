package rj.cocacode.services.api

import rj.cocacode.config.ApiType
import kotlin.test.Test
import kotlin.test.assertTrue

class WireHistoryEncodingTest {

    @Test
    fun chatRequest_includesReasoningAndToolCalls() {
        val messages = listOf(
            RequestBuilder.ApiMessage(role = "user", text = "find withdraw"),
            RequestBuilder.ApiMessage(
                role = "assistant",
                text = "",
                blocks = listOf(
                    ApiContentBlock.ThinkingBlock("I should grep the file"),
                    ApiContentBlock.ToolUseBlock(
                        id = "call_1",
                        name = "Bash",
                        input = mapOf("command" to "grep withdraw")
                    )
                )
            ),
            RequestBuilder.ApiMessage(
                role = "user",
                text = "",
                blocks = listOf(
                    ApiContentBlock.ToolResultBlock(
                        toolUseId = "call_1",
                        content = "MyUploadsScreen.kt:42"
                    )
                )
            )
        )
        val body = RequestBuilder.build(
            model = "test",
            system = "sys",
            messages = messages,
            tools = emptyList(),
            maxTokens = 100,
            stream = false,
            apiType = ApiType.CHAT
        ).toString()
        assertTrue("reasoning_content" in body, body)
        assertTrue("I should grep the file" in body, body)
        assertTrue("tool_calls" in body, body)
        assertTrue("call_1" in body, body)
        assertTrue("\"role\":\"tool\"" in body || "\"role\": \"tool\"" in body, body)
        assertTrue("MyUploadsScreen.kt:42" in body, body)
    }

    @Test
    fun wireRoundTrip_preservesToolUse() {
        val original = RequestBuilder.ApiMessage(
            role = "assistant",
            text = "hi",
            blocks = listOf(
                ApiContentBlock.ThinkingBlock("think"),
                ApiContentBlock.TextBlock("hi"),
                ApiContentBlock.ToolUseBlock("id1", "Read", mapOf("file_path" to "/a"))
            )
        )
        val restored = original.toWireMessage().toApiMessage()
        assertTrue(restored.blocks!!.any { it is ApiContentBlock.ThinkingBlock })
        assertTrue(restored.blocks!!.any { it is ApiContentBlock.ToolUseBlock })
        val tu = restored.blocks!!.filterIsInstance<ApiContentBlock.ToolUseBlock>().first()
        assertTrue(tu.name == "Read")
        assertTrue(tu.input["file_path"] == "/a")
    }
}
