package rj.cocacode.ui

import java.io.Writer

/**
 * A persistent bottom status line, like Claude Code's input-box status:
 *
 *   ✽ Musing… (23m 48s · ↓ 16.3k tokens)
 *
 * While visible, streaming output is printed ABOVE the status line using cursor
 * tracking: inline chunks (no trailing newline) are appended to the current text
 * line rather than forcing a newline, so streamed text flows naturally while the
 * status stays pinned to the bottom.
 */
class StatusBar(private val out: Writer) {

    private var visible = false
    private var line = ""
    private var textCol = 0

    val isVisible: Boolean get() = visible

    /** Show the status on a fresh bottom line. */
    fun show(text: String) {
        out.write("\n" + text)
        out.flush()
        visible = true
        line = text
        textCol = 0
    }

    /** Rewrite the status line in place (no new line). */
    fun update(text: String) {
        if (!visible) {
            show(text)
        } else {
            out.write("\r\u001b[2K" + text)
            out.flush()
            line = text
        }
    }

    /** Clear the status line, returning the terminal to normal. */
    fun hide() {
        if (visible) {
            out.write("\r\u001b[2K")
            out.flush()
            visible = false
            textCol = 0
        }
    }

    /**
     * Print streaming output above the status bar. The cursor is moved to the
     * continuation column on the text line (above the status), the output is
     * written there, then the status is rewritten on the line below.
     */
    fun printOutput(text: String) {
        if (text.isEmpty()) return
        if (!visible) {
            out.write(text)
            out.flush()
            return
        }
        // Cursor is at the end of the status line; go up to the text line.
        out.write("\u001b[1A\r")
        if (textCol > 0) out.write("\u001b[${textCol}C")
        out.write(text)
        // Advance the continuation column to the end of the last written line.
        val lastNl = text.lastIndexOf('\n')
        textCol = if (lastNl >= 0) visibleLength(text, lastNl + 1) else textCol + visibleLength(text)
        // Move below the text, clear the old status line, rewrite the status.
        out.write("\n\r\u001b[2K" + line)
        out.flush()
    }

    /** Visible length of a string, ignoring ANSI escape sequences. */
    private fun visibleLength(s: String, from: Int = 0): Int =
        ANSI_REGEX.replace(s.substring(from), "").length

    companion object {
        private val ANSI_REGEX = Regex("\\u001b\\[[0-9;]*[A-Za-z]")

        /** ✽ + verb + "(duration · ↓ tokens)". */
        fun formatStatus(verb: String, elapsedMs: Long, outputTokens: Int): String {
            val duration = formatDuration(elapsedMs)
            val tokens = formatTokens(outputTokens)
            return "$TEARDROP $verb ($duration · $DOWN $tokens tokens)"
        }

        const val TEARDROP = "✽"
        const val DOWN = "↓"

        fun formatDuration(ms: Long): String {
            if (ms < 60_000) {
                if (ms <= 0) return "0s"
                return "${ms / 1000}s"
            }
            val minutes = ms / 60_000
            val seconds = (ms % 60_000) / 1000
            return if (minutes >= 60) {
                val hours = minutes / 60
                val remMin = minutes % 60
                "${hours}h ${remMin}m ${seconds}s"
            } else {
                "${minutes}m ${seconds}s"
            }
        }

        /** "900" / "1.3k" / "16.3k". */
        fun formatTokens(count: Int): String {
            if (count < 1000) return count.toString()
            val k = count / 1000.0
            val oneDecimal = (Math.round(k * 10).toDouble() / 10).toString()
            return oneDecimal.let { if (it.endsWith(".0")) it.dropLast(2) else it } + "k"
        }
    }
}
