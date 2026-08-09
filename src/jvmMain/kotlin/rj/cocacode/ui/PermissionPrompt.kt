package rj.cocacode.ui

import org.jline.terminal.Terminal
import rj.cocacode.ui.tools.ToolResultRenderer
import java.io.Writer

/**
 * Interactive y / a / n permission prompt for Bash / Edit / Write.
 *
 * Uses [LineReader] via [UI.readLine] — never [Terminal.reader] directly.
 * Stealing the NonBlockingReader leaves JLine in a half-EOF state and the
 * REPL then exits on the next prompt ("random loop end").
 */
object PermissionPrompt {

    enum class Decision { ALLOW_ONCE, ALLOW_SESSION, DENY }

    fun ask(
        @Suppress("UNUSED_PARAMETER") terminal: Terminal,
        out: Writer,
        statusBar: StatusBar?,
        toolName: String,
        input: Map<String, Any>
    ): Decision {
        val preview = when (toolName) {
            "Edit" -> ToolResultRenderer.previewEditDiff(input)
            "Write" -> ToolResultRenderer.previewWrite(input)
            "Bash" -> ToolResultRenderer.previewBash(input)
            else -> Ansi.bold(toolName)
        }
        val hint = Ansi.dim("  [y] once  [a] always this session  [n] deny")
        val block = "\n${Ansi.brightYellow("⚠ Permission required")}\n$preview\n$hint\n"

        val bar = statusBar
        val wasVisible = bar?.isVisible == true
        if (wasVisible) bar.hide()

        out.write(block)
        out.flush()

        try {
            repeat(8) {
                val answer = try {
                    UI.get().readLine(Ansi.dim("  Allow? [y/a/n] "), "")
                } catch (_: org.jline.reader.EndOfFileException) {
                    // Spurious EOF — do not deny/abort the whole session.
                    null
                } catch (_: org.jline.reader.UserInterruptException) {
                    return Decision.DENY
                }?.trim()?.lowercase()

                when (answer) {
                    null -> { /* retry */ }
                    "y", "yes", "" -> return Decision.ALLOW_ONCE
                    "a", "always" -> return Decision.ALLOW_SESSION
                    "n", "no", "q" -> return Decision.DENY
                    else -> out.write(Ansi.dim("  Please enter y, a, or n\n")).also { out.flush() }
                }
            }
            // Timed-out / repeated EOF: allow once so the agent can continue.
            out.write(Ansi.dim("  (no answer — allowing once)\n"))
            out.flush()
            return Decision.ALLOW_ONCE
        } finally {
            if (wasVisible) {
                bar!!.show(StatusBar.formatStatus("Musing…", 0, 0))
            }
        }
    }
}
