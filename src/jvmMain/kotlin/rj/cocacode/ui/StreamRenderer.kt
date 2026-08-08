package rj.cocacode.ui

import java.io.Writer

/**
 * Routes streamed deltas (thinking + text) to the terminal, mirroring Claude
 * Code's output:
 *
 *   ∴ Thinking…
 *     <thinking text, Claude gray, indented 2>
 *     <assistant text: 2-space indent + markdown rendering>
 *
 * Thinking is always expanded and has no box bars. Assistant text is indented
 * like Claude Code's message gutter and rendered as markdown. When a [StatusBar]
 * is attached, all output is printed above the pinned status line.
 */
class StreamRenderer(
    private val out: Writer = UI.get().writer() ?: java.io.PrintWriter(System.out, true),
    private val statusBar: StatusBar? = null
) {
    private val markdown = MarkdownRenderer()
    private val textBuffer = StringBuilder()

    private var inThinking = false
    private var atLineStart = false
    private var totalChars = 0

    private fun emit(text: String) {
        if (text.isEmpty()) return
        if (statusBar != null) {
            statusBar.printOutput(text)
        } else {
            out.write(text)
            out.flush()
        }
    }

    /** Handle a thinking delta. Prints the "∴ Thinking…" label on first chunk. */
    fun onThinking(delta: String) {
        if (delta.isEmpty()) return
        if (!inThinking) {
            emit("\n${Ansi.dim("∴ Thinking…")}\n")
            inThinking = true
            atLineStart = true
        }
        val indented = delta.replace("\n", "\n${Ansi.CLAUDE_GRAY}  ")
        emit((if (atLineStart) "${Ansi.CLAUDE_GRAY}  " else "") + indented)
        totalChars += delta.length
        atLineStart = delta.endsWith("\n")
    }

    /** Handle a text delta. Closes thinking, buffers lines, renders markdown. */
    fun onText(delta: String) {
        if (delta.isEmpty()) return
        if (inThinking) {
            emit("${Ansi.RESET}\n\n")
            inThinking = false
        }
        textBuffer.append(delta)
        totalChars += delta.length
        flushLines()
    }

    /** Close an open thinking block so a tool-call line starts on its own row. */
    fun beforeToolCall() {
        if (inThinking) {
            emit("${Ansi.RESET}\n")
            inThinking = false
        }
        // Don't flush partial text — the model continues on the next turn.
    }

    /** Flush buffered text and close a dangling thinking block. */
    fun finish() {
        if (inThinking) {
            emit("${Ansi.RESET}\n")
            inThinking = false
        }
        if (textBuffer.isNotEmpty()) {
            emit("  " + markdown.renderLine(textBuffer.toString()) + "\n")
            textBuffer.clear()
        }
    }

    /** Emit completed lines from the text buffer with indent + markdown. */
    private fun flushLines() {
        while (true) {
            val nl = textBuffer.indexOf("\n")
            if (nl < 0) break
            val line = textBuffer.substring(0, nl)
            textBuffer.delete(0, nl + 1)
            emit("  " + markdown.renderLine(line) + "\n")
        }
    }

    /** Rough output-token estimate for the status bar (~4 chars/token). */
    fun estimatedTokens(): Int = totalChars / 4
}
