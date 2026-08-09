package rj.cocacode.ui

import org.jline.terminal.TerminalBuilder
import org.jline.terminal.Terminal
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import rj.cocacode.constants.Figures
import rj.cocacode.state.AppStateManager

/**
 * Terminal abstraction with styled rendering.
 *
 * All styling goes through [Ansi] so it can be disabled in non-TTY mode.
 */
class TerminalUI {
    companion object {
        /** Prompt shown at the input line. */
        val PROMPT: String get() = Ansi.boldCyan("cocacode") + Ansi.cyan(" ▸ ")
    }

    private var terminal: Terminal? = null
    private var reader: LineReader? = null

    fun initialize() {
        terminal = TerminalBuilder.builder()
            .jna(true)
            .build()

        val term = terminal!!
        reader = LineReaderBuilder.builder()
            .terminal(term)
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, Ansi.dim("... "))
            .option(LineReader.Option.INSERT_BRACKET, true)
            .completer(CompositeCompleter())
            .build()

        val lr = reader!!
        lr.widgets["cocacode-cycle-perm"] = org.jline.reader.Widget {
            val next = AppStateManager.cyclePermissionMode()
            term.writer().println()
            term.writer().println(
                Ansi.dim("Permission mode: ${next.name.lowercase().replace('_', ' ')}")
            )
            term.writer().flush()
            true
        }
        try {
            // Shift+Tab (CSI Z)
            lr.keyMaps[LineReader.MAIN]?.bind(
                org.jline.reader.Reference("cocacode-cycle-perm"),
                "\u001b[Z"
            )
        } catch (_: Exception) { /* optional */ }
    }

    /**
     * Completer for `/commands` (with descriptions) and `@path` file prefixes.
     */
    private class CompositeCompleter : org.jline.reader.Completer {
        override fun complete(
            reader: LineReader,
            line: org.jline.reader.ParsedLine,
            candidates: MutableList<org.jline.reader.Candidate>
        ) {
            val raw = line.line()
            val word = line.word()
            when {
                raw.trimStart().startsWith("/") && !raw.contains(' ') -> {
                    val q = word.removePrefix("/")
                    rj.cocacode.commands.CommandRegistry.getAll()
                        .filter { it.name.startsWith(q) }
                        .forEach {
                            candidates.add(
                                org.jline.reader.Candidate(
                                    "/" + it.name,
                                    "/" + it.name,
                                    null,
                                    it.description,
                                    null,
                                    null,
                                    true
                                )
                            )
                        }
                }
                word.startsWith("@") -> {
                    val prefix = word.removePrefix("@")
                    completePaths(prefix).forEach { path ->
                        candidates.add(
                            org.jline.reader.Candidate(
                                "@$path",
                                "@$path",
                                null,
                                "file",
                                null,
                                null,
                                true
                            )
                        )
                    }
                }
            }
        }

        private fun completePaths(prefix: String): List<String> {
            val cwd = java.io.File(System.getProperty("user.dir") ?: ".")
            val base: java.io.File
            val namePrefix: String
            val slash = prefix.lastIndexOf('/')
            if (slash >= 0) {
                base = java.io.File(cwd, prefix.substring(0, slash).ifEmpty { "." })
                namePrefix = prefix.substring(slash + 1)
            } else {
                base = cwd
                namePrefix = prefix
            }
            if (!base.isDirectory) return emptyList()
            return base.listFiles()
                ?.filter { it.name.startsWith(namePrefix) }
                ?.sortedBy { it.name }
                ?.take(30)
                ?.map {
                    val rel = if (slash >= 0) prefix.substring(0, slash + 1) + it.name else it.name
                    if (it.isDirectory) "$rel/" else rel
                }
                ?: emptyList()
        }
    }

    fun readLine(prompt: String = PROMPT): String? {
        return reader?.readLine(prompt)
    }

    /** Read a line with a pre-filled initial buffer (e.g. type-ahead text). */
    fun readLine(prompt: String, initialBuffer: String): String? {
        // Do not drain Terminal.reader here — concurrent NonBlockingReader use
        // with LineReader hangs the next prompt (seen as "second message freeze").
        return if (initialBuffer.isEmpty()) {
            reader?.readLine(prompt)
        } else {
            reader?.readLine(prompt, "", null as org.jline.reader.MaskingCallback?, initialBuffer)
        }
    }

    /** The underlying terminal writer (or null before initialize). */
    fun writer(): java.io.Writer? = terminal?.writer()

    /** The underlying JLine terminal (for raw key reading). */
    fun terminal(): Terminal? = terminal

    fun print(message: String) {
        val w = terminal?.writer() ?: return
        w.println(message)
        w.flush()
    }

    /** Print without a trailing newline. */
    fun printInline(message: String) {
        terminal?.writer()?.print(message)
        terminal?.writer()?.flush()
    }

    fun printError(message: String) {
        print("${Ansi.brightRed(Figures.BLACK_CIRCLE)} ${message}")
    }

    fun printSuccess(message: String) {
        print("${Ansi.brightGreen("✓")} ${message}")
    }

    fun printWarning(message: String) {
        print("${Ansi.brightYellow("⚠")} ${message}")
    }

    fun printInfo(message: String) {
        print("${Ansi.gray(Figures.BULLET_OPERATOR)} ${message}")
    }

    // --- Styled message rendering ---

    /** A user message, prefixed with a prompt marker. */
    fun printUserMessage(content: String) {
        val prefix = Ansi.brightCyan("❯")
        content.lines().forEachIndexed { i, line ->
            if (i == 0) print("$prefix ${line}") else print("  $line")
        }
    }

    /** A plain assistant response line. */
    fun printAssistantMessage(content: String) {
        print(content)
    }

    /** A system message (e.g. session notices). */
    fun printSystemMessage(content: String) {
        print("${Ansi.dim(content)}")
    }

    /** A tool execution status line, e.g. "● Bash (ls -la)". */
    fun printToolCall(toolName: String, description: String) {
        print("${Ansi.gray(Figures.BLACK_CIRCLE)} ${Ansi.bold(toolName)} ${Ansi.dim("(${description})")}")
    }

    /** A thinking block label. */
    fun printThinkingLabel() {
        print("\n${Ansi.dim("∴ Thinking…")}")
    }

    // --- Streaming (kept from previous implementation) ---

    fun printChunk(text: String) {
        terminal?.writer()?.print(text)
        terminal?.writer()?.flush()
    }

    fun printThinkingChunk(text: String) {
        terminal?.writer()?.print("${Ansi.CLAUDE_GRAY}$text${Ansi.RESET}")
        terminal?.writer()?.flush()
    }

    fun printThinkingHeader() {
        terminal?.writer()?.println("\n${Ansi.dim("∴ Thinking…")}")
    }

    /** No visual footer — thinking just flows into the answer. */
    fun printThinkingFooter() {
        terminal?.writer()?.println()
    }

    // --- Chrome ---

    fun printBanner() {
        print("")
        print(Ansi.boldCyan(" ██████╗   ██████╗   ██████╗   █████╗    ██████╗   ██████╗  ██████═╗  ███████╗"))
        print(Ansi.boldCyan("██╔════╝  ██╔═══██╗ ██╔════╝  ██╔══██╗  ██╔════╝  ██╔═══██╗ ██╔══██║  ██╔════╝"))
        print(Ansi.boldCyan("██║       ██║   ██║ ██║       ███████║  ██║       ██║   ██║ ██║  ██║  █████╗  "))
        print(Ansi.boldCyan("██║       ██║   ██║ ██║       ██╔══██║  ██║       ██║   ██║ ██║  ██║  ██╔══╝  "))
        print(Ansi.boldCyan("╚██████╗  ╚██████╔╝ ╚██████╗  ██║  ██║  ╚██████╗  ╚██████╔╝ ██████╔╝  ███████╗"))
        print(Ansi.boldCyan(" ╚═════╝   ╚═════╝   ╚═════╝  ╚═╝  ╚═╝   ╚═════╝   ╚═════╝  ╚═════╝   ╚══════╝"))
        print(Ansi.gray("  AI Coding Assistant — ${Ansi.bold("v" + rj.cocacode.BuildKonfig.APP_VERSION)}"))
        print(Ansi.gray("  Type ${Ansi.brightCyan("/help")} for commands, ${Ansi.brightCyan("!cmd")} for shell, or just ask."))
        print("")
    }

    /** A status line: e.g. "◇ thinking…" or "◇ running". */
    fun printStatus(label: String) {
        print("${Ansi.brightCyan(Figures.DIAMOND_OPEN)} ${Ansi.gray(label)}")
    }

    fun clear() {
        terminal?.writer()?.print("[H[2J")
        terminal?.writer()?.flush()
    }

    fun showSpinner(message: String) {
        var frame = 0
        terminal?.writer()?.print("$message ${Figures.BRIDGE_SPINNER_FRAMES[frame]}")
    }

    fun updateSpinner(frame: Int) {
        terminal?.writer()?.print("\r${Figures.BRIDGE_SPINNER_FRAMES[frame % Figures.BRIDGE_SPINNER_FRAMES.size]}")
    }

    fun stopSpinner() {
        terminal?.writer()?.println()
    }

    fun drawBox(title: String, content: List<String>) {
        val width = 60
        val border = "━".repeat(width - 2)

        print("┌$border┐")
        print("│ ${title.padEnd(width - 4)} │")
        print("├$border┤")

        content.forEach { line ->
            print("│ ${line.padEnd(width - 4)} │")
        }

        print("└$border┘")
    }

    fun drawTable(headers: List<String>, rows: List<List<String>>) {
        val colWidths = headers.indices.map { col ->
            val maxContent = rows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
            maxOf(headers[col].length, maxContent) + 2
        }

        print(colWidths.mapIndexed { i, w -> headers[i].padEnd(w) }.joinToString("│"))
        print(colWidths.map { "─".repeat(it) }.joinToString("┼"))

        rows.forEach { row ->
            print(row.mapIndexed { i, cell -> cell.padEnd(colWidths[i]) }.joinToString("│"))
        }
    }

    fun shutdown() {
        terminal?.close()
    }
}

object UI {
    private var instance: TerminalUI? = null

    fun init(): TerminalUI {
        if (instance == null) {
            instance = TerminalUI()
            instance!!.initialize()
        }
        return instance!!
    }

    fun get(): TerminalUI = instance ?: init()

    fun shutdown() {
        instance?.shutdown()
        instance = null
    }
}
