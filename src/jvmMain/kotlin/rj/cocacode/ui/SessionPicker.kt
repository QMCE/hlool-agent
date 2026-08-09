package rj.cocacode.ui

import org.jline.terminal.Terminal
import org.jline.utils.NonBlockingReader
import rj.cocacode.utils.SessionInfo
import java.io.Writer
import kotlin.math.min

/**
 * Interactive session picker for `/resume`.
 *
 * Shows titled sessions (first-message placeholder or AI-generated), relative
 * time, and message count. Arrow keys move; Enter resumes; Esc cancels.
 */
object SessionPicker {

    private const val MAX_VISIBLE = 12
    private const val HEADER_LINES = 2 // blank + "Resume session"
    private const val FOOTER_LINES = 2 // blank + hint

    /** Select a session; returns null if cancelled or no sessions. */
    fun pick(terminal: Terminal, writer: Writer, sessions: List<SessionInfo>): SessionInfo? {
        if (sessions.isEmpty()) return null

        val visible = sessions.take(MAX_VISIBLE)
        var selected = 0
        val reader = terminal.reader()
        val totalLines = HEADER_LINES + visible.size + FOOTER_LINES

        draw(writer, visible, selected, moveUp = 0)
        writer.flush()

        try {
            while (true) {
                when (readKey(reader)) {
                    "up" -> {
                        if (selected > 0) {
                            selected--
                            draw(writer, visible, selected, moveUp = totalLines)
                        }
                    }
                    "down" -> {
                        if (selected < visible.size - 1) {
                            selected++
                            draw(writer, visible, selected, moveUp = totalLines)
                        }
                    }
                    "enter" -> {
                        clear(writer, totalLines)
                        return visible[selected]
                    }
                    "esc", "ctrl_c", "eof" -> {
                        clear(writer, totalLines)
                        return null
                    }
                }
            }
        } catch (_: Exception) {
            clear(writer, totalLines)
            return null
        }
    }

    private fun draw(writer: Writer, sessions: List<SessionInfo>, selected: Int, moveUp: Int) {
        if (moveUp > 0) {
            writer.write("\u001b[${moveUp}A")
        }

        writer.write("\r\u001b[2K\n")
        writer.write("\r\u001b[2K  ${Ansi.bold("Resume session")}\n")

        val titleWidth = titleColumnWidth(sessions)
        sessions.forEachIndexed { i, s ->
            writer.write("\r\u001b[2K")
            val marker = if (i == selected) Ansi.brightCyan("›") else " "
            val title = padTitle(s.title, titleWidth)
            val meta = Ansi.dim("${s.updatedAt.toRelative()} · ${s.messageCount} msgs")
            val titled = if (i == selected) Ansi.brightCyan(title) else title
            writer.write("  $marker $titled  $meta\n")
        }

        writer.write("\r\u001b[2K\n")
        writer.write("\r\u001b[2K  ${Ansi.dim("?? move · enter resume · esc cancel")}")
        writer.flush()
    }

    private fun clear(writer: Writer, totalLines: Int) {
        writer.write("\r\u001b[${totalLines}A")
        repeat(totalLines) {
            writer.write("\r\u001b[2K\n")
        }
        writer.write("\r\u001b[${totalLines}A")
        writer.flush()
    }

    private fun titleColumnWidth(sessions: List<SessionInfo>): Int {
        val longest = sessions.maxOfOrNull { visibleWidth(it.title) } ?: 0
        return min(48, longest.coerceAtLeast(12))
    }

    private fun padTitle(title: String, width: Int): String {
        val truncated = truncateToWidth(title, width)
        val pad = (width - visibleWidth(truncated)).coerceAtLeast(0)
        return truncated + " ".repeat(pad)
    }

    private fun truncateToWidth(text: String, maxWidth: Int): String {
        if (visibleWidth(text) <= maxWidth) return text
        if (maxWidth <= 1) return "…"
        val sb = StringBuilder()
        var w = 0
        val it = text.codePoints().iterator()
        while (it.hasNext()) {
            val cp = it.nextInt()
            val cw = if (cp > 0xFF) 2 else 1 // rough CJK double-width
            if (w + cw > maxWidth - 1) break
            sb.appendCodePoint(cp)
            w += cw
        }
        return sb.toString() + "…"
    }

    private fun visibleWidth(text: String): Int {
        var w = 0
        val it = text.codePoints().iterator()
        while (it.hasNext()) {
            val cp = it.nextInt()
            w += if (cp > 0xFF) 2 else 1
        }
        return w
    }

    private fun readKey(reader: java.io.Reader): String {
        val first = reader.read()
        if (first == -1) return "eof"
        when (first.toChar()) {
            '\r', '\n' -> return "enter"
            '\u001b' -> {
                val t = reader as? NonBlockingReader
                return if (t == null) {
                    val a = reader.read()
                    val b = reader.read()
                    when {
                        a == '['.code && b == 'A'.code -> "up"
                        a == '['.code && b == 'B'.code -> "down"
                        else -> "esc"
                    }
                } else {
                    val a = t.read(100)
                    val b = if (a == '['.code) t.read(100) else -1
                    when {
                        a == '['.code && b == 'A'.code -> "up"
                        a == '['.code && b == 'B'.code -> "down"
                        else -> "esc"
                    }
                }
            }
            '\u0003' -> return "ctrl_c"
            'k', 'K' -> return "up"
            'j', 'J' -> return "down"
            else -> return "other"
        }
    }

    private fun Long.toRelative(): String {
        val diff = System.currentTimeMillis() - this
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }
}
