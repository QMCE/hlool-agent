package rj.cocacode.ui

import java.io.Writer

/**
 * Routes streamed deltas (thinking + text) to the terminal.
 *
 * Deltas are written as they arrive — not held until newline or [finish].
 * Assistant text is indented like Claude Code's gutter; markdown is applied
 * only to completed lines (optional polish), never by delaying the stream.
 */
class StreamRenderer(
    private val out: Writer = UI.get().writer() ?: java.io.PrintWriter(System.out, true),
    private val statusBar: StatusBar? = null
) {
    private var inThinking = false
    private var atLineStart = true
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
        // Re-apply gray on every chunk: StatusBar footer redraws emit RESET via Ansi.dim.
        val body = delta.replace("\n", "\n  ")
        val prefix = if (atLineStart) "  " else ""
        emit(Ansi.CLAUDE_GRAY + prefix + body)
        totalChars += delta.length
        atLineStart = delta.endsWith("\n")
    }

    /** Stream assistant text as soon as each delta arrives. */
    fun onText(delta: String) {
        if (delta.isEmpty()) return
        if (inThinking) {
            emit("${Ansi.RESET}\n\n")
            inThinking = false
            atLineStart = true
        }
        val body = delta.replace("\n", "\n  ")
        val prefix = if (atLineStart) "  " else ""
        emit(prefix + body)
        totalChars += delta.length
        atLineStart = delta.endsWith("\n")
    }

    /** Close an open thinking block so a tool-call line starts on its own row. */
    fun beforeToolCall() {
        if (inThinking) {
            emit("${Ansi.RESET}\n")
            inThinking = false
            atLineStart = true
        }
        if (!atLineStart) {
            emit("\n")
            atLineStart = true
        }
    }

    /** Close a dangling thinking block and end the open text line. */
    fun finish() {
        if (inThinking) {
            emit("${Ansi.RESET}\n")
            inThinking = false
            atLineStart = true
        }
        if (!atLineStart) {
            emit("\n")
            atLineStart = true
        }
    }

    /** Rough output-token estimate for the status bar (~4 chars/token). */
    fun estimatedTokens(): Int = totalChars / 4
}
