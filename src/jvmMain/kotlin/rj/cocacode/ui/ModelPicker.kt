package rj.cocacode.ui

import org.jline.terminal.Terminal
import java.io.Writer

/**
 * Interactive model picker for `/model`.
 */
object ModelPicker {

    fun pick(
        terminal: Terminal,
        writer: Writer,
        models: List<String>,
        current: String
    ): String? {
        if (models.isEmpty()) return null
        val ordered = buildList {
            if (current.isNotBlank() && models.none { it.equals(current, ignoreCase = true) }) {
                add(current)
            }
            // Prefer current first, then alpha
            val rest = models.distinct().sortedWith(
                compareBy<String> { !it.equals(current, ignoreCase = true) }
                    .thenBy { it.lowercase() }
            )
            addAll(rest)
        }.distinct()

        val items = ordered.map { name ->
            InteractiveMenu.Item(
                id = name,
                label = name,
                meta = if (name.equals(current, ignoreCase = true)) "current" else ""
            )
        }
        val initial = items.indexOfFirst { it.id.equals(current, ignoreCase = true) }.coerceAtLeast(0)
        return InteractiveMenu.pick(
            terminal = terminal,
            writer = writer,
            title = "Select model",
            items = items,
            initialIndex = initial,
            maxVisible = 16,
            hint = "↑↓ move · type to filter · enter select · esc cancel"
        )?.id
    }
}
