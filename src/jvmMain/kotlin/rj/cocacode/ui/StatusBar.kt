package rj.cocacode.ui

import java.io.Writer

/**
 * Busy-status chrome that keeps native terminal scrollback.
 *
 * Content is always appended (never cursor-up into history). The status line
 * lives only on the *last* row and is refreshed with `\r` + erase-line.
 */
class StatusBar(
    private val out: Writer,
    private val terminalWidth: () -> Int = { 80 },
    @Suppress("UNUSED_PARAMETER")
    private val withPrompt: Boolean = true
) {

    private val lock = Any()

    private var visible = false
    /** True when the cursor is on the status row (may be overwritten with \r). */
    private var onStatusRow = false
    private var statusText: String = ""
    private var inputBuf: String = ""

    val isVisible: Boolean get() = synchronized(lock) { visible }

    fun show(initialStatus: String = "") {
        synchronized(lock) {
            statusText = initialStatus
            inputBuf = ""
            out.write("\n")
            visible = true
            paintStatusLocked()
            out.flush()
        }
    }

    fun setStatus(text: String) {
        synchronized(lock) {
            statusText = text
            if (visible && onStatusRow) {
                paintStatusLocked()
                out.flush()
            }
        }
    }

    fun setInput(buf: String) {
        synchronized(lock) {
            inputBuf = buf
            if (visible && onStatusRow) {
                paintStatusLocked()
                out.flush()
            }
        }
    }

    fun update(text: String) {
        synchronized(lock) {
            statusText = text
            if (!visible) {
                inputBuf = ""
                out.write("\n")
                visible = true
            }
            if (onStatusRow || visible) {
                paintStatusLocked()
                out.flush()
            }
        }
    }

    fun hide() {
        synchronized(lock) {
            if (visible) {
                if (onStatusRow) {
                    out.write("\r\u001b[2K")
                    onStatusRow = false
                }
                out.flush()
                visible = false
                inputBuf = ""
                statusText = ""
            }
        }
    }

    /**
     * Append [text] into scrollback. Clears the status row first if needed,
     * then restores status after a trailing newline.
     */
    fun printOutput(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            if (!visible) {
                out.write(text)
                out.flush()
                return
            }

            if (onStatusRow) {
                out.write("\r\u001b[2K")
                onStatusRow = false
            }

            out.write(text)

            if (text.endsWith("\n")) {
                paintStatusLocked()
            }
            out.flush()
        }
    }

    private fun paintStatusLocked() {
        val maxW = (terminalWidth() - 1).coerceAtLeast(16)
        val mode = permissionModeBadge()
        val core = statusText
        val line = listOf(core, mode).filter { it.isNotBlank() }.joinToString("  ")
        val painted = if (line.isEmpty()) "" else Ansi.dim(line)
        out.write("\r\u001b[2K${truncateToDisplayWidth(painted, maxW)}")
        onStatusRow = true
    }

    private fun permissionModeBadge(): String {
        return when (rj.cocacode.state.AppStateManager.getState().permissionMode) {
            rj.cocacode.state.PermissionMode.ACCEPT_EDITS -> "accept edits"
            rj.cocacode.state.PermissionMode.PLAN -> "plan"
            rj.cocacode.state.PermissionMode.BYPASS_PERMISSIONS -> "bypass"
            rj.cocacode.state.PermissionMode.DONT_ASK -> "don't ask"
            else -> ""
        }
    }

    companion object {
        private val ANSI_REGEX = Regex("\u001b\\[[0-9;]*[A-Za-z]")

        const val TEARDROP = "\u273D"
        const val DOWN = "\u2193"
        private const val DOT = "\u00B7"

        fun formatStatus(verb: String, elapsedMs: Long, outputTokens: Int): String {
            val duration = formatDuration(elapsedMs)
            val tokens = formatTokens(outputTokens)
            return "$TEARDROP $verb ($duration $DOT $DOWN $tokens tokens)"
        }

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

        fun formatTokens(count: Int): String {
            if (count < 1000) return count.toString()
            val k = count / 1000.0
            val oneDecimal = (Math.round(k * 10).toDouble() / 10).toString()
            return oneDecimal.let { if (it.endsWith(".0")) it.dropLast(2) else it } + "k"
        }

        fun displayWidth(s: String, from: Int = 0): Int {
            val plain = ANSI_REGEX.replace(s.substring(from), "")
            var w = 0
            var i = 0
            while (i < plain.length) {
                val cp = plain.codePointAt(i)
                w += codePointWidth(cp)
                i += Character.charCount(cp)
            }
            return w
        }

        fun splitIndexForWidth(s: String, maxW: Int): Int {
            var w = 0
            var i = 0
            var lastGood = 0
            while (i < s.length) {
                if (s[i] == '\u001b') {
                    val m = ANSI_REGEX.find(s, i) ?: break
                    i = m.range.last + 1
                    continue
                }
                val cp = s.codePointAt(i)
                val cw = codePointWidth(cp)
                if (w + cw > maxW) break
                w += cw
                i += Character.charCount(cp)
                lastGood = i
            }
            return lastGood.coerceAtLeast(1).coerceAtMost(s.length)
        }

        private fun codePointWidth(cp: Int): Int {
            if (cp == 0) return 0
            val type = Character.getType(cp)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            ) {
                return 0
            }
            if (cp in 0x1100..0x115F ||
                cp in 0x2E80..0xA4CF ||
                cp in 0xAC00..0xD7A3 ||
                cp in 0xF900..0xFAFF ||
                cp in 0xFE10..0xFE19 ||
                cp in 0xFE30..0xFE6F ||
                cp in 0xFF00..0xFF60 ||
                cp in 0xFFE0..0xFFE6 ||
                cp in 0x1F300..0x1FAFF ||
                cp in 0x20000..0x3FFFD
            ) {
                return 2
            }
            return 1
        }

        fun truncateToDisplayWidth(s: String, maxWidth: Int): String {
            if (displayWidth(s) <= maxWidth) return s
            val plain = ANSI_REGEX.replace(s, "")
            val sb = StringBuilder()
            var w = 0
            var i = 0
            val limit = (maxWidth - 1).coerceAtLeast(1)
            while (i < plain.length) {
                val cp = plain.codePointAt(i)
                val cw = codePointWidth(cp)
                if (w + cw > limit) break
                sb.appendCodePoint(cp)
                w += cw
                i += Character.charCount(cp)
            }
            return sb.toString() + "\u2026"
        }
    }
}
