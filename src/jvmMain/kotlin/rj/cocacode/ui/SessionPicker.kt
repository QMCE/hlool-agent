package rj.cocacode.ui

import org.jline.terminal.Terminal
import rj.cocacode.utils.SessionInfo
import java.io.Writer

/**
 * Interactive session picker for `/resume`.
 *
 * Delegates to [InteractiveMenu] so we never steal JLine's NonBlockingReader
 * (which previously froze selection / the next REPL prompt).
 */
object SessionPicker {

    /** Select a session; returns null if cancelled or no sessions. */
    fun pick(terminal: Terminal, writer: Writer, sessions: List<SessionInfo>): SessionInfo? {
        if (sessions.isEmpty()) return null
        val items = sessions.map { s ->
            InteractiveMenu.Item(
                id = s.id,
                label = s.title,
                meta = "${s.updatedAt.toRelative()} · ${s.messageCount} msgs · ${s.id.take(8)}"
            )
        }
        val currentId = try {
            rj.cocacode.engine.QueryEngineManager.getEngine().sessionId
        } catch (_: Exception) {
            null
        }
        val initial = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val picked = InteractiveMenu.pick(
            terminal = terminal,
            writer = writer,
            title = "Resume session",
            items = items,
            initialIndex = initial,
            maxVisible = 12
        ) ?: return null
        return sessions.firstOrNull { it.id == picked.id }
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
