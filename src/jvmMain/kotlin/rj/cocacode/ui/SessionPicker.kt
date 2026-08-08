package rj.cocacode.ui

import org.jline.terminal.Terminal
import rj.cocacode.utils.SessionInfo
import java.io.Writer

/**
 * Interactive session picker for `/resume`. Shows saved sessions and lets the
 * user select one with the up/down arrow keys and Enter (Esc cancels).
 *
 * Works with JLine's raw terminal reader, parsing arrow-key escape sequences.
 */
object SessionPicker {

    /** Select a session; returns null if cancelled or no sessions. */
    fun pick(terminal: Terminal, writer: Writer, sessions: List<SessionInfo>): SessionInfo? {
        if (sessions.isEmpty()) return null

        var selected = 0
        val reader = terminal.reader()
        val count = sessions.size

        writer.write("\n")
        render(writer, sessions, selected)
        writer.flush()

        try {
            while (true) {
                when (readKey(reader)) {
                    "up" -> if (selected > 0) { selected--; render(writer, sessions, selected) }
                    "down" -> if (selected < count - 1) { selected++; render(writer, sessions, selected) }
                    "enter" -> { clear(writer, count); return sessions[selected] }
                    "esc", "ctrl_c", "eof" -> { clear(writer, count); return null }
                }
            }
        } catch (e: Exception) {
            clear(writer, count)
            return null
        }
    }

    private fun render(writer: Writer, sessions: List<SessionInfo>, selected: Int) {
        // Move cursor to the top of the menu and rewrite it.
        writer.write("\r[${sessions.size}A")
        sessions.forEachIndexed { i, s ->
            writer.write("\r[2K")
            val id = s.id.take(8)
            val meta = Ansi.gray("${s.updatedAt.toRelative()}")
            if (i == selected) {
                writer.write("  ${Ansi.brightCyan("›")} ${Ansi.brightCyan(id)}  $meta  ${s.messageCount} msgs")
            } else {
                writer.write("    ${Ansi.dim(id)}  $meta  ${s.messageCount} msgs")
            }
            if (i < sessions.size - 1) writer.write("\n")
        }
        writer.write("\n${Ansi.dim("  ↑↓ select · Enter resume · Esc cancel")}")
        writer.flush()
    }

    private fun clear(writer: Writer, count: Int) {
        // Move to menu top, clear all menu lines plus the hint line.
        writer.write("\r[${count + 2}A")
        repeat(count + 2) {
            writer.write("\r[2K\n")
        }
        writer.write("\r[${count + 1}A")
        writer.flush()
    }

    /** Parse one key press from the raw terminal reader. */
    private fun readKey(reader: java.io.Reader): String {
        val first = reader.read()
        if (first == -1) return "eof"
        when (first.toChar()) {
            '\r', '\n' -> return "enter"
            '' -> {
                // Escape sequence: read the next chars with a short timeout.
                val t = reader as? org.jline.utils.NonBlockingReader
                return when {
                    t == null -> {
                        val a = reader.read()
                        val b = reader.read()
                        when {
                            a == '['.code && b == 'A'.code -> "up"
                            a == '['.code && b == 'B'.code -> "down"
                            a == '['.code && b == 'C'.code -> "right"
                            a == '['.code && b == 'D'.code -> "left"
                            else -> "esc"
                        }
                    }
                    else -> {
                        val a = t.read(100)
                        val b = if (a == '['.code) t.read(100) else -1
                        when {
                            a == '['.code && b == 'A'.code -> "up"
                            a == '['.code && b == 'B'.code -> "down"
                            a == '['.code && b == 'C'.code -> "right"
                            a == '['.code && b == 'D'.code -> "left"
                            else -> "esc"
                        }
                    }
                }
            }
            '' -> return "ctrl_c"
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
