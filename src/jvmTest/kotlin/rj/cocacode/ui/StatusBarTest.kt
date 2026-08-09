package rj.cocacode.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusBarTest {

    @Test
    fun displayWidth_ascii() {
        assertEquals(5, StatusBar.displayWidth("hello"))
    }

    @Test
    fun displayWidth_cjk_is_double() {
        assertEquals(4, StatusBar.displayWidth("\u4e2d\u6587"))
        assertEquals(5, StatusBar.displayWidth(" \u5f53\u524d"))
    }

    @Test
    fun displayWidth_stripsAnsi() {
        assertEquals(2, StatusBar.displayWidth(Ansi.brightCyan("hi")))
    }

    @Test
    fun formatStatus_usesCleanUnicode() {
        val s = StatusBar.formatStatus("Musing\u2026", 33_000, 553)
        assertTrue("\u273D" in s, s)
        assertTrue("\u2193" in s, s)
        assertTrue("\u00B7" in s, s)
    }

    @Test
    fun truncateToDisplayWidth_cjk() {
        val t = StatusBar.truncateToDisplayWidth("\u4f60\u597d\u4e16\u754c\u6d4b\u8bd5", 5)
        assertTrue(StatusBar.displayWidth(t) <= 5)
        assertTrue(t.endsWith("\u2026"))
    }

    @Test
    fun smallChunks_appendWithoutCursorUp() {
        val buf = java.io.StringWriter()
        val bar = StatusBar(buf, terminalWidth = { 80 }, withPrompt = false)
        bar.show(StatusBar.formatStatus("Musing\u2026", 0, 0))
        for (word in listOf("Let", " me", " check", " context")) {
            bar.printOutput(word)
            bar.setStatus(StatusBar.formatStatus("Musing\u2026", 6000, 30))
        }
        bar.printOutput("\n")
        val out = buf.toString()
        assertFalse("\u001b[1A" in out, "cursor-up breaks native scrollback: ${out.takeLast(400)}")
        assertTrue("Let" in out && "context" in out, out.takeLast(800))
        bar.hide()
    }

    @Test
    fun printOutput_and_setStatus_doNotCorruptTitles() {
        val buf = java.io.StringWriter()
        val bar = StatusBar(buf, terminalWidth = { 80 }, withPrompt = false)
        bar.show("STATUS0")
        bar.printOutput("  \u5f53\u524d\u72b6\u6001\n")
        bar.setStatus("\u273D Musing\u2026 (1s \u00B7 \u2193 10 tokens)")
        bar.printOutput("  \u6ce8\u610f\u4e8b\u9879\n")
        val out = buf.toString()
        assertTrue("\u5f53\u524d\u72b6\u6001" in out, out)
        assertTrue("\u6ce8\u610f\u4e8b\u9879" in out, out)
        assertFalse("\u001b[1A" in out, out)
        bar.hide()
    }

    @Test
    fun permissionBadge_showsAcceptEdits() {
        val prev = rj.cocacode.state.AppStateManager.getState().permissionMode
        try {
            rj.cocacode.state.AppStateManager.setPermissionMode(
                rj.cocacode.state.PermissionMode.ACCEPT_EDITS
            )
            val buf = java.io.StringWriter()
            val bar = StatusBar(buf, terminalWidth = { 80 }, withPrompt = false)
            bar.show(StatusBar.formatStatus("Musing\u2026", 0, 0))
            val out = buf.toString()
            assertTrue("accept edits" in out, out.takeLast(200))
            bar.hide()
        } finally {
            rj.cocacode.state.AppStateManager.setPermissionMode(prev)
        }
    }

    @Test
    fun thinkingChunks_reapplyClaudeGray() {
        val buf = java.io.StringWriter()
        val bar = StatusBar(buf, terminalWidth = { 80 }, withPrompt = false)
        val renderer = StreamRenderer(buf, bar)
        bar.show(StatusBar.formatStatus("Musing\u2026", 0, 0))
        renderer.onThinking("Let")
        bar.setStatus(StatusBar.formatStatus("Musing\u2026", 1000, 5))
        renderer.onThinking(" me")
        renderer.finish()
        bar.hide()
        val out = buf.toString()
        val grayCount = Regex(Regex.escape(Ansi.CLAUDE_GRAY)).findAll(out).count()
        assertTrue(grayCount >= 2, "expected gray on each thinking chunk, got $grayCount in ${out.takeLast(400)}")
        assertFalse("\u001b[1A" in out)
    }

    @Test
    fun textStreamsImmediatelyWithoutWaitingForNewline() {
        val buf = java.io.StringWriter()
        val renderer = StreamRenderer(buf, statusBar = null)
        renderer.onText("你好")
        renderer.onText("世界")
        val out = buf.toString()
        assertTrue("你好" in out, out)
        assertTrue("世界" in out, out)
        renderer.finish()
    }

    @Test
    fun statusUsesCarriageReturnNotCursorUp() {
        val buf = java.io.StringWriter()
        val bar = StatusBar(buf, terminalWidth = { 80 }, withPrompt = false)
        bar.show(StatusBar.formatStatus("Musing\u2026", 0, 0))
        bar.setStatus(StatusBar.formatStatus("Musing\u2026", 1000, 10))
        val out = buf.toString()
        assertTrue("\r" in out || "\u001b[2K" in out, out.takeLast(200))
        assertFalse("\u001b[1A" in out, out.takeLast(200))
        bar.hide()
    }

    @Test
    fun concurrent_printOutput_and_setStatus_stayConsistent() {
        val buf = java.io.StringWriter()
        val bar = StatusBar(buf, terminalWidth = { 120 }, withPrompt = false)
        bar.show(StatusBar.formatStatus("Musing\u2026", 0, 0))
        val threads = (1..4).map { id ->
            Thread {
                repeat(30) { i ->
                    if (id % 2 == 0) {
                        bar.printOutput("  line-$id-$i \u4e2d\u6587\n")
                    } else {
                        bar.setStatus(StatusBar.formatStatus("Musing\u2026", i * 200L, i * 10))
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        bar.hide()
        val out = buf.toString()
        assertTrue(out.contains("line-"), out.takeLast(200))
        assertFalse("\u001b[1A" in out)
    }
}
