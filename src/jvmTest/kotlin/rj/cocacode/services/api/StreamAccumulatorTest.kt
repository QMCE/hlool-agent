package rj.cocacode.services.api

import rj.cocacode.config.ApiType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamAccumulatorTest {

    // --- OpenAI ---

    @Test
    fun `openai feeds text and reasoning deltas`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        var text = ""
        var thinking = ""
        acc.feed("""{"choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}""").let {
            text += it.text
        }
        acc.feed("""{"choices":[{"delta":{"reasoning_content":"deep "},"finish_reason":null}]}""").let {
            thinking += it.thinking
        }
        acc.feed("""{"choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}]}""").let {
            text += it.text
        }
        assertEquals("Hello", text)
        assertEquals("deep ", thinking)
        assertEquals("stop", acc.stopReasonValue)
    }

    @Test
    fun `openai content array parts are concatenated`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed(
            """{"choices":[{"delta":{"content":[{"type":"text","text":"你好"},{"type":"text","text":"世界"}]},"finish_reason":"stop"}]}"""
        )
        assertEquals("你好世界", acc.text)
    }

    @Test
    fun `openai message field used when delta missing`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed("""{"choices":[{"message":{"content":"final"},"finish_reason":"stop"}]}""")
        assertEquals("final", acc.text)
    }

    @Test
    fun `openai accumulates tool calls across deltas`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"Bash","arguments":"{\"com"}}]},"finish_reason":null}]}""")
        acc.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"mand\":\"ls\"}"}}]},"finish_reason":"tool_calls"}]}""")
        acc.finalize()
        assertEquals(1, acc.toolUsesList.size)
        val call = acc.toolUsesList[0]
        assertEquals("call_1", call.id)
        assertEquals("Bash", call.name)
        assertEquals("ls", call.input["command"])
    }

    @Test
    fun `openai tool calls get synthetic id when missing`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"Bash","arguments":"{\"command\":\"pwd\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        acc.finalize()
        assertEquals(1, acc.toolUsesList.size)
        assertTrue(acc.toolUsesList[0].id.startsWith("call_"), acc.toolUsesList[0].id)
        assertEquals("Bash", acc.toolUsesList[0].name)
        assertEquals("pwd", acc.toolUsesList[0].input["command"])
    }

    @Test
    fun `openai tool call id is not duplicated across deltas`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","function":{"name":"Bash","arguments":"{\"com"}}]},"finish_reason":null}]}"""
        )
        acc.feed(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_abc","function":{"name":"Bash","arguments":"mand\":\"pwd\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        acc.finalize()
        assertEquals("call_abc", acc.toolUsesList[0].id)
        assertEquals("Bash", acc.toolUsesList[0].name)
        assertEquals("pwd", acc.toolUsesList[0].input["command"])
    }

    @Test
    fun `openai sse error payload is captured`() {
        val acc = StreamAccumulator(ApiType.CHAT)
        acc.feed("""{"error":{"message":"invalid tool_call_id"}}""")
        assertEquals("invalid tool_call_id", acc.streamError)
    }

    // --- Anthropic ---

    @Test
    fun `anthropic feeds thinking and text deltas`() {
        val acc = StreamAccumulator(ApiType.MESSAGES)
        var text = ""
        var thinking = ""
        acc.feed("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
        acc.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""").let {
            thinking += it.thinking
        }
        acc.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"..."}}""").let {
            thinking += it.thinking
        }
        acc.feed("""{"type":"content_block_stop","index":0}""")
        acc.feed("""{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""")
        acc.feed("""{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Hi"}}""").let {
            text += it.text
        }
        assertEquals("hmm...", thinking)
        assertEquals("Hi", text)
    }

    @Test
    fun `anthropic builds tool_use from input_json_delta`() {
        val acc = StreamAccumulator(ApiType.MESSAGES)
        acc.feed("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t1","name":"Read","input":{}}}""")
        acc.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"file_path\":\""}}""")
        acc.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"/x\"}"}}""")
        acc.feed("""{"type":"content_block_stop","index":0}""")
        acc.finalize()
        assertEquals(1, acc.toolUsesList.size)
        val call = acc.toolUsesList[0]
        assertEquals("t1", call.id)
        assertEquals("Read", call.name)
        assertEquals("/x", call.input["file_path"])
    }
}
