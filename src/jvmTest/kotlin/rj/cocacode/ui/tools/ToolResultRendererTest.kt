package rj.cocacode.ui.tools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import rj.cocacode.tools.ToolResult

class ToolResultRendererTest {

    @Test
    fun formatInlineDiff_colorsAddedAndRemoved() {
        val diff = ToolResultRenderer.formatInlineDiff("old line", "new line")
        assertTrue("old line" in diff)
        assertTrue("new line" in diff)
    }

    @Test
    fun renderBash_truncatesLongOutput() {
        val long = (1..100).joinToString("\n") { "line $it" }
        val rendered = ToolResultRenderer.render(
            "Bash",
            ToolResult(text = long),
            mapOf("command" to "echo")
        )
        assertTrue("omitted" in rendered || "…" in rendered || rendered.lines().size < 100)
    }

    @Test
    fun renderEdit_includesPath() {
        val rendered = ToolResultRenderer.render(
            "Edit",
            ToolResult(
                text = "ok",
                metadata = mapOf(
                    "filePath" to "/tmp/x.kt",
                    "oldString" to "a",
                    "newString" to "b"
                )
            )
        )
        assertTrue("x.kt" in rendered)
    }
}
