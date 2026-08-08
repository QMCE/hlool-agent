package rj.cocacode.ui

/**
 * ANSI color / style helpers for terminal rendering.
 *
 * Colors are emitted only when the terminal supports them (always on for the
 * interactive REPL; callers in non-TTY mode can suppress via [enabled]).
 */
object Ansi {
    /** Set to false to disable all color output (e.g. piped, non-TTY). */
    var enabled: Boolean = true

    const val RESET = "[0m"
    const val BOLD = "[1m"
    const val DIM = "[2m"
    const val UNDERLINE = "[4m"

    const val BLACK = "[30m"
    const val RED = "[31m"
    const val GREEN = "[32m"
    const val YELLOW = "[33m"
    const val BLUE = "[34m"
    const val MAGENTA = "[35m"
    const val CYAN = "[36m"
    const val WHITE = "[37m"
    const val GRAY = "[90m"

    // Bright variants
    const val BRIGHT_RED = "[91m"
    const val BRIGHT_GREEN = "[92m"
    const val BRIGHT_YELLOW = "[93m"
    const val BRIGHT_BLUE = "[94m"
    const val BRIGHT_MAGENTA = "[95m"
    const val BRIGHT_CYAN = "[96m"

    // 256-color light grays
    const val LIGHT_GRAY = "[38;5;250m"
    const val DIM_GRAY = "[38;5;245m"

    /** Claude Code's dim/thinking gray. */
    const val CLAUDE_GRAY = "[38;5;246m"

    private fun paint(code: String, text: String): String =
        if (enabled) "$code$text$RESET" else text

    fun cyan(text: String) = paint(CYAN, text)
    fun brightCyan(text: String) = paint(BRIGHT_CYAN, text)
    fun green(text: String) = paint(GREEN, text)
    fun brightGreen(text: String) = paint(BRIGHT_GREEN, text)
    fun red(text: String) = paint(RED, text)
    fun brightRed(text: String) = paint(BRIGHT_RED, text)
    fun yellow(text: String) = paint(YELLOW, text)
    fun brightYellow(text: String) = paint(BRIGHT_YELLOW, text)
    fun magenta(text: String) = paint(MAGENTA, text)
    fun brightMagenta(text: String) = paint(BRIGHT_MAGENTA, text)
    fun blue(text: String) = paint(BLUE, text)
    fun brightBlue(text: String) = paint(BRIGHT_BLUE, text)
    fun gray(text: String) = paint(GRAY, text)
    fun lightGray(text: String) = paint(LIGHT_GRAY, text)
    fun dim(text: String) = paint(DIM, text)
    fun bold(text: String) = paint(BOLD, text)
    fun boldCyan(text: String) = paint(BOLD + CYAN, text)
    fun boldGreen(text: String) = paint(BOLD + GREEN, text)

    // --- Markdown-ish styles ---

    /** Italic text. */
    fun italic(text: String) = paint("[3m", text)

    /** Underlined text. */
    fun underline(text: String) = paint("[4m", text)

    /** Inline code: light text on a dark background. */
    fun code(text: String): String =
        if (enabled) "[38;5;252m[48;5;236m$text$RESET" else text

    /** Link: underlined cyan. */
    fun link(text: String) = paint(UNDERLINE + CYAN, text)
}
