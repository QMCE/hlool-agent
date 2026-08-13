package rj.cocacode.ui

import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import java.io.InputStream
import java.io.Writer
import kotlin.math.max
import kotlin.math.min

/**
 * Arrow-key interactive picker that does **not** steal JLine's NonBlockingReader.
 *
 * [SessionPicker] previously called [Terminal.reader], which shares the same
 * NonBlockingReader as LineReader and leaves the REPL hung / unable to select.
 * This menu pauses the terminal, reads raw bytes from [Terminal.input], then
 * resumes so the next [UI.readLine] works.
 */
object InteractiveMenu {

    data class Item(
        val id: String,
        val label: String,
        val meta: String = ""
    )

    /**
     * @return selected item, or null if cancelled / empty.
     */
    fun pick(
        terminal: Terminal,
        writer: Writer,
        title: String,
        items: List<Item>,
        initialIndex: Int = 0,
        maxVisible: Int = 14,
        hint: String = "↑↓ move · enter select · esc cancel · / filter"
    ): Item? {
        if (items.isEmpty()) return null

        // Prefer raw-mode menu; fall back to numbered LineReader prompt.
        return try {
            pickRaw(terminal, writer, title, items, initialIndex, maxVisible, hint)
        } catch (_: Exception) {
            pickNumbered(writer, title, items)
        }
    }

    private fun pickRaw(
        terminal: Terminal,
        writer: Writer,
        title: String,
        allItems: List<Item>,
        initialIndex: Int,
        maxVisible: Int,
        hint: String
    ): Item? {
        var filter = ""
        var filtered = allItems
        var selected = initialIndex.coerceIn(0, (allItems.size - 1).coerceAtLeast(0))
        var scroll = 0

        fun visible(): List<Item> {
            val end = min(filtered.size, scroll + maxVisible)
            return filtered.subList(scroll, end)
        }

        fun ensureVisible() {
            if (filtered.isEmpty()) {
                scroll = 0
                selected = 0
                return
            }
            selected = selected.coerceIn(0, filtered.size - 1)
            if (selected < scroll) scroll = selected
            if (selected >= scroll + maxVisible) scroll = selected - maxVisible + 1
            scroll = scroll.coerceIn(0, max(0, filtered.size - maxVisible))
        }

        fun applyFilter(newFilter: String) {
            filter = newFilter
            filtered = if (filter.isEmpty()) {
                allItems
            } else {
                val q = filter.lowercase()
                allItems.filter {
                    it.label.lowercase().contains(q) ||
                        it.id.lowercase().contains(q) ||
                        it.meta.lowercase().contains(q)
                }
            }
            selected = 0
            scroll = 0
            ensureVisible()
        }

        // Pause LineReader's pump so we can safely read terminal.input().
        val wasPaused = try {
            terminal.paused()
        } catch (_: Exception) {
            false
        }
        try {
            if (!wasPaused) {
                try {
                    terminal.pause(true)
                } catch (_: Exception) {
                    terminal.pause()
                }
            }
        } catch (_: Exception) {
            // Some terminals don't support pause — still try raw mode.
        }

        val prevAttrs: Attributes? = try {
            terminal.enterRawMode()
        } catch (_: Exception) {
            null
        }

        val input: InputStream = terminal.input()
        var drawnLines = 0

        fun redraw() {
            if (drawnLines > 0) {
                writer.write("\u001b[${drawnLines}A")
            }
            val vis = visible()
            val lines = mutableListOf<String>()
            lines += ""
            val filterSuffix = if (filter.isEmpty()) "" else Ansi.dim("  filter: \"$filter\"")
            lines += "  ${Ansi.bold(title)}$filterSuffix"
            if (filtered.isEmpty()) {
                lines += "  ${Ansi.dim("(no matches)")}"
            } else {
                val labelWidth = labelColumnWidth(vis)
                vis.forEachIndexed { i, item ->
                    val abs = scroll + i
                    val marker = if (abs == selected) Ansi.brightCyan("›") else " "
                    val label = padLabel(item.label, labelWidth)
                    val titled = if (abs == selected) Ansi.brightCyan(label) else label
                    val meta = if (item.meta.isNotBlank()) "  ${Ansi.dim(item.meta)}" else ""
                    lines += "  $marker $titled$meta"
                }
                if (filtered.size > maxVisible) {
                    lines += "  ${Ansi.dim("  showing ${scroll + 1}–${scroll + vis.size} of ${filtered.size}")}"
                }
            }
            lines += ""
            lines += "  ${Ansi.dim(hint)}"

            lines.forEach { line ->
                writer.write("\r\u001b[2K$line\n")
            }
            writer.flush()
            drawnLines = lines.size
        }

        fun clearDrawn() {
            if (drawnLines <= 0) return
            writer.write("\r\u001b[${drawnLines}A")
            repeat(drawnLines) { writer.write("\r\u001b[2K\n") }
            writer.write("\r\u001b[${drawnLines}A")
            writer.flush()
            drawnLines = 0
        }

        try {
            ensureVisible()
            redraw()
            while (true) {
                when (val key = readKey(input)) {
                    "up" -> {
                        if (filtered.isNotEmpty() && selected > 0) {
                            selected--
                            ensureVisible()
                            redraw()
                        }
                    }
                    "down" -> {
                        if (filtered.isNotEmpty() && selected < filtered.size - 1) {
                            selected++
                            ensureVisible()
                            redraw()
                        }
                    }
                    "enter" -> {
                        if (filtered.isEmpty()) continue
                        val choice = filtered[selected]
                        clearDrawn()
                        return choice
                    }
                    "esc", "ctrl_c" -> {
                        clearDrawn()
                        return null
                    }
                    "backspace" -> {
                        if (filter.isNotEmpty()) {
                            applyFilter(filter.dropLast(1))
                            redraw()
                        }
                    }
                    "eof" -> {
                        clearDrawn()
                        return null
                    }
                    else -> {
                        if (key.startsWith("char:")) {
                            val ch = key.removePrefix("char:")
                            if (ch.length == 1 && !ch[0].isISOControl()) {
                                applyFilter(filter + ch)
                                redraw()
                            }
                        }
                    }
                }
            }
        } finally {
            clearDrawn()
            if (prevAttrs != null) {
                try {
                    terminal.setAttributes(prevAttrs)
                } catch (_: Exception) { /* ignore */ }
            }
            try {
                if (!wasPaused) terminal.resume()
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun pickNumbered(writer: Writer, title: String, items: List<Item>): Item? {
        writer.write("\n  ${Ansi.bold(title)}\n")
        items.take(30).forEachIndexed { i, item ->
            val meta = if (item.meta.isNotBlank()) "  ${Ansi.dim(item.meta)}" else ""
            writer.write("  ${Ansi.brightCyan("${i + 1}.")} ${item.label}$meta\n")
        }
        writer.write("  ${Ansi.dim("Enter number (or q to cancel)")}\n")
        writer.flush()
        val answer = try {
            UI.get().readLine(Ansi.dim("  # ▸ "), "")?.trim()
        } catch (_: Exception) {
            null
        } ?: return null
        if (answer.equals("q", true) || answer.equals("esc", true)) return null
        val idx = answer.toIntOrNull()?.minus(1) ?: return null
        return items.getOrNull(idx)
    }

    private fun readKey(input: InputStream): String {
        val first = readByte(input) ?: return "eof"
        when (first) {
            '\r'.code, '\n'.code -> return "enter"
            0x7f, 0x08 -> return "backspace"
            0x03 -> return "ctrl_c"
            0x1b -> {
                // CSI sequences: ESC [ A/B  or ESC alone
                val a = readByte(input, timeoutHint = true)
                if (a == null) return "esc"
                if (a == '['.code) {
                    val b = readByte(input, timeoutHint = true)
                    return when (b) {
                        'A'.code -> "up"
                        'B'.code -> "down"
                        'C'.code -> "right"
                        'D'.code -> "left"
                        else -> "esc"
                    }
                }
                return "esc"
            }
            'k'.code, 'K'.code, 'p'.code -> return "up"
            'j'.code, 'J'.code, 'n'.code -> return "down"
            'q'.code, 'Q'.code -> return "esc"
            else -> {
                if (first in 32..126) return "char:${first.toChar()}"
                return "other"
            }
        }
    }

    /**
     * Blocking read with a short non-blocking peek for ESC sequences.
     * When [timeoutHint] is true and no byte is available quickly, return null.
     */
    private fun readByte(input: InputStream, timeoutHint: Boolean = false): Int? {
        if (timeoutHint) {
            val deadline = System.nanoTime() + 80_000_000L // 80ms
            while (System.nanoTime() < deadline) {
                if (input.available() > 0) {
                    val b = input.read()
                    return if (b == -1) null else b
                }
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    return null
                }
            }
            return null
        }
        val b = input.read()
        return if (b == -1) null else b
    }

    private fun labelColumnWidth(items: List<Item>): Int {
        val longest = items.maxOfOrNull { visibleWidth(it.label) } ?: 0
        return min(56, longest.coerceAtLeast(12))
    }

    private fun padLabel(label: String, width: Int): String {
        val truncated = truncateToWidth(label, width)
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
            val cw = if (cp > 0xFF) 2 else 1
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
}
