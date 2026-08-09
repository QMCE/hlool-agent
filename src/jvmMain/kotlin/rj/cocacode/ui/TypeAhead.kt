package rj.cocacode.ui

import org.jline.terminal.Terminal
import org.jline.utils.NonBlockingReader

/**
 * Type-ahead input while the engine is busy ("Musing…"). Keystrokes are buffered
 * and mirrored into [StatusBar.setInput]; Enter is ignored. [stop] returns the
 * buffer for the next prompt prefill.
 *
 * Owns [Terminal.reader] exclusively until [stop] joins the worker. Late keystrokes
 * during shutdown are kept in the buffer (not dropped) so the first character of
 * the next prompt is not eaten by a race with LineReader.
 */
class TypeAhead(
    private val terminal: Terminal,
    private val statusBar: StatusBar
) {
    private val buffer = StringBuilder()
    @Volatile private var running = false
    @Volatile private var paused = false
    private var worker: Thread? = null

    /** Invoked when the user hits Ctrl+C while type-ahead is active. */
    var onCtrlC: (() -> Unit)? = null

    /** Invoked on Ctrl+O to expand the last collapsed tool result. */
    var onExpandTool: (() -> Unit)? = null

    fun start() {
        if (running) return
        running = true
        paused = false
        worker = Thread({
            val reader = terminal.reader()
            while (true) {
                try {
                    if (paused) {
                        Thread.sleep(50)
                        if (!running) break
                        continue
                    }
                    val c = reader.read(POLL_MS)
                    if (c == -1) break
                    if (c == NonBlockingReader.READ_EXPIRED) {
                        if (!running) break
                        continue
                    }
                    handleChar(reader, c)
                    if (!running) break
                } catch (_: Exception) {
                    break
                } catch (_: Error) {
                    break
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "cocacode-typeahead").apply {
            isDaemon = true
            start()
        }
    }

    /** Pause key reading so PermissionPrompt can own the reader. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /**
     * Stop the worker, drain any still-pending bytes into the buffer, and return
     * text for the next prompt prefill.
     */
    fun stop(): String {
        running = false
        val t = worker
        worker = null
        if (t != null) {
            try {
                t.join(JOIN_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        drainPending(terminal, buffer)
        return buffer.toString()
    }

    private fun handleChar(reader: NonBlockingReader, first: Int) {
        when (val ch = first.toChar()) {
            '\r', '\n' -> { /* Enter can't send while busy */ }
            '\u007f', '\b' -> deleteLastCodePoint()
            '\u0003' -> onCtrlC?.invoke()
            '\u000f' -> onExpandTool?.invoke() // Ctrl+O
            '\u001b' -> skipEscapeSequence(reader)
            else -> {
                if (ch.isISOControl()) return
                if (Character.isHighSurrogate(ch)) {
                    val second = readTimed(reader)
                    if (second >= 0 && Character.isLowSurrogate(second.toChar())) {
                        buffer.appendCodePoint(Character.toCodePoint(ch, second.toChar()))
                    } else {
                        buffer.append(ch)
                    }
                } else {
                    buffer.append(ch)
                }
                if (running) statusBar.setInput(buffer.toString())
            }
        }
    }

    private fun skipEscapeSequence(reader: NonBlockingReader) {
        val next = readTimed(reader)
        if (next < 0) return
        if (next == '['.code || next == 'O'.code) {
            // Always finish the CSI even while shutting down, so leftovers
            // don't poison the next LineReader prompt.
            while (true) {
                val x = readTimed(reader)
                if (x < 0) break
                if (x in 0x40..0x7E) break
            }
        }
    }

    private fun readTimed(reader: NonBlockingReader): Int {
        return try {
            val c = reader.read(ESC_POLL_MS)
            if (c == NonBlockingReader.READ_EXPIRED) -1 else c
        } catch (_: Exception) {
            -1
        } catch (_: Error) {
            -1
        }
    }

    private fun deleteLastCodePoint() {
        if (buffer.isEmpty()) return
        val cp = buffer.codePointBefore(buffer.length)
        buffer.delete(buffer.length - Character.charCount(cp), buffer.length)
        if (running) statusBar.setInput(buffer.toString())
    }

    companion object {
        private const val POLL_MS = 50L
        private const val ESC_POLL_MS = 50L
        private const val JOIN_MS = 1000L

        /**
         * Pull any printable keystrokes still sitting in [Terminal.reader] into
         * [into] (for prompt prefill). Discards leftover CSI / controls.
         */
        fun drainPending(terminal: Terminal, into: StringBuilder = StringBuilder()): String {
            val reader = terminal.reader()
            try {
                while (true) {
                    val c = try {
                        reader.read(5)
                    } catch (_: Exception) {
                        break
                    } catch (_: Error) {
                        break
                    }
                    if (c < 0 || c == NonBlockingReader.READ_EXPIRED) break
                    when (val ch = c.toChar()) {
                        '\u001b' -> discardCsi(reader)
                        '\r', '\n', '\u0003', '\u007f', '\b' -> { /* drop */ }
                        else -> if (!ch.isISOControl()) into.append(ch)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
            return into.toString()
        }

        private fun discardCsi(reader: NonBlockingReader) {
            val next = try {
                reader.read(ESC_POLL_MS)
            } catch (_: Exception) {
                return
            } catch (_: Error) {
                return
            }
            if (next < 0 || next == NonBlockingReader.READ_EXPIRED) return
            if (next == '['.code || next == 'O'.code) {
                while (true) {
                    val x = try {
                        reader.read(ESC_POLL_MS)
                    } catch (_: Exception) {
                        break
                    } catch (_: Error) {
                        break
                    }
                    if (x < 0 || x == NonBlockingReader.READ_EXPIRED) break
                    if (x in 0x40..0x7E) break
                }
            }
        }
    }
}
