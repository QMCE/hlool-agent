package rj.cocacode.ui.tools

import rj.cocacode.diff.DiffEngine
import rj.cocacode.diff.LineType
import rj.cocacode.tools.ToolResult
import rj.cocacode.ui.Ansi

/**
 * Formats tool results for the transcript (Claude-style compact chrome).
 */
object ToolResultRenderer {

    private const val MAX_BASH_LINES = 40
    private const val MAX_BASH_CHARS = 8_000
    private const val MAX_SUMMARY_CHARS = 2_000

    fun render(toolName: String, result: ToolResult, input: Map<String, Any> = emptyMap()): String {
        return when (toolName) {
            "Edit" -> renderEdit(result, input)
            "Write" -> renderWrite(result, input)
            "Bash" -> renderBash(result, input)
            "Read" -> renderTruncated(result, "Read")
            "Glob", "Grep" -> renderTruncated(result, toolName)
            else -> indentBody(colorize(result))
        }
    }

    private fun renderEdit(result: ToolResult, input: Map<String, Any>): String {
        if (result.isError) return indentBody(Ansi.brightRed(result.text))
        val path = (result.metadata["filePath"] as? String)
            ?: (input["file_path"] as? String)
            ?: "file"
        val oldS = (result.metadata["oldString"] as? String)
            ?: (input["old_string"] as? String)
            ?: ""
        val newS = (result.metadata["newString"] as? String)
            ?: (input["new_string"] as? String)
            ?: ""
        val header = Ansi.dim("  ⎿  Updated $path")
        val diff = if (oldS.isNotEmpty() || newS.isNotEmpty()) {
            formatInlineDiff(oldS, newS)
        } else {
            ""
        }
        return if (diff.isEmpty()) header else "$header\n$diff"
    }

    private fun renderWrite(result: ToolResult, input: Map<String, Any>): String {
        if (result.isError) return indentBody(Ansi.brightRed(result.text))
        val path = (result.metadata["filePath"] as? String)
            ?: (input["file_path"] as? String)
            ?: "file"
        val bytes = result.metadata["bytesWritten"]
        val meta = if (bytes != null) " ($bytes bytes)" else ""
        val content = input["content"] as? String
        val preview = if (content != null && content.isNotEmpty()) {
            "\n" + formatInlineDiff("", content.take(2_000))
        } else ""
        return Ansi.dim("  ⎿  Wrote $path$meta") + preview
    }

    private fun renderBash(result: ToolResult, input: Map<String, Any>): String {
        val cmd = (input["command"] as? String)?.take(120) ?: ""
        val header = Ansi.dim("  ⎿  " + if (cmd.isNotBlank()) "$$cmd" else "Bash")
        val body = truncateBashOutput(result.text)
        val colored = if (result.isError) Ansi.brightRed(body) else body
        return if (body.isBlank()) header else "$header\n${indentBody(colored)}"
    }

    private fun renderTruncated(result: ToolResult, label: String): String {
        val text = result.text
        val truncated = when {
            text.length <= MAX_SUMMARY_CHARS -> text
            else -> text.take(MAX_SUMMARY_CHARS) + "\n… (truncated)"
        }
        val body = if (result.isError) Ansi.brightRed(truncated) else Ansi.dim(truncated)
        return Ansi.dim("  ⎿  $label") + "\n" + indentBody(body)
    }

    private fun colorize(result: ToolResult): String =
        if (result.isError) Ansi.brightRed(result.text) else result.text

    private fun indentBody(text: String): String =
        text.lines().joinToString("\n") { "    $it" }

    private fun truncateBashOutput(text: String): String {
        if (text.isBlank()) return text
        var t = text
        if (t.length > MAX_BASH_CHARS) {
            t = t.take(MAX_BASH_CHARS / 2) + "\n…\n" + t.takeLast(MAX_BASH_CHARS / 2)
        }
        val lines = t.lines()
        if (lines.size > MAX_BASH_LINES) {
            val head = lines.take(MAX_BASH_LINES / 2)
            val tail = lines.takeLast(MAX_BASH_LINES / 2)
            return (head + "… (${lines.size - MAX_BASH_LINES} lines omitted)" + tail).joinToString("\n")
        }
        return t
    }

    /** Colored line diff for small edit hunks. */
    fun formatInlineDiff(oldContent: String, newContent: String): String {
        if (oldContent == newContent) return Ansi.dim("    (no textual change)")
        return try {
            // Prefer simple -/+ when both are short snippets (typical Edit tool).
            if (oldContent.lines().size <= 30 && newContent.lines().size <= 30) {
                val sb = StringBuilder()
                oldContent.lines().forEach { sb.appendLine(Ansi.brightRed("    - $it")) }
                newContent.lines().forEach { sb.appendLine(Ansi.brightGreen("    + $it")) }
                return sb.toString().trimEnd()
            }
            val diff = DiffEngine.compute(oldContent, newContent)
            val sb = StringBuilder()
            var count = 0
            for (hunk in diff.hunks) {
                for (line in hunk.lines) {
                    if (count++ > 80) {
                        sb.appendLine(Ansi.dim("    …"))
                        return sb.toString().trimEnd()
                    }
                    when (line.type) {
                        LineType.ADDED -> sb.appendLine(Ansi.brightGreen("    + ${line.content}"))
                        LineType.REMOVED -> sb.appendLine(Ansi.brightRed("    - ${line.content}"))
                        LineType.CONTEXT -> sb.appendLine(Ansi.dim("      ${line.content}"))
                        LineType.HEADER -> sb.appendLine(Ansi.dim("    ${line.content}"))
                    }
                }
            }
            sb.toString().trimEnd().ifEmpty { Ansi.dim("    (diff empty)") }
        } catch (_: Exception) {
            // Never let diff rendering kill the agent turn.
            val sb = StringBuilder()
            oldContent.lines().take(40).forEach { sb.appendLine(Ansi.brightRed("    - $it")) }
            newContent.lines().take(40).forEach { sb.appendLine(Ansi.brightGreen("    + $it")) }
            sb.toString().trimEnd()
        }
    }

    /** Preview diff for permission prompt (before execute). */
    fun previewEditDiff(input: Map<String, Any>): String {
        val path = input["file_path"] as? String ?: "file"
        val oldS = input["old_string"] as? String ?: ""
        val newS = input["new_string"] as? String ?: ""
        return Ansi.bold("Edit $path") + "\n" + formatInlineDiff(oldS, newS)
    }

    fun previewWrite(input: Map<String, Any>): String {
        val path = input["file_path"] as? String ?: "file"
        val content = (input["content"] as? String) ?: ""
        val lines = content.lines().size
        return Ansi.bold("Write $path") + Ansi.dim(" ($lines lines, ${content.length} chars)")
    }

    fun previewBash(input: Map<String, Any>): String {
        val cmd = input["command"] as? String ?: ""
        return Ansi.bold("Bash") + "\n  " + Ansi.cyan(cmd)
    }
}
