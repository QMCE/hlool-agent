package rj.cocacode.ui

/**
 * Lightweight markdown -> ANSI renderer, styled to resemble Claude Code's
 * terminal output: bold headers, italic emphasis, inline-code on a dark chip,
 * links underlined, fenced code blocks dimmed and indented, list bullets.
 *
 * Used line-by-line for streaming: call [renderLine] as each line completes;
 * it tracks fenced-code state across lines.
 */
class MarkdownRenderer {

    private var inCodeBlock = false

    /** Render one completed line. */
    fun renderLine(line: String): String {
        val trimmed = line.trimStart()

        // Fenced code blocks toggle on ``` and are dimmed + indented inside.
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return Ansi.dim("  ${trimmed.take(3)}")
        }
        if (inCodeBlock) {
            return Ansi.dim("  $line")
        }

        // Headings
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val rest = trimmed.drop(level).trim()
            return renderInline(rest).let { if (level == 1) Ansi.bold(it) else Ansi.bold(Ansi.underline(it)) }
        }

        // Horizontal rule
        if (trimmed.matches(Regex("-{3,}|\\*{3,}|_{3,}"))) {
            return Ansi.gray(trimmed)
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            return Ansi.dim("▎ " + renderInline(trimmed.drop(1).trimStart()))
        }

        // Unordered list
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            return "  ${Ansi.brightCyan("•")} ${renderInline(trimmed.drop(2))}"
        }

        // Ordered list
        val ordered = Regex("^(\\d+)[.)]\\s+").find(trimmed)
        if (ordered != null) {
            val rest = trimmed.drop(ordered.value.length)
            return "  ${Ansi.brightCyan(ordered.value.trim() + ".")} ${renderInline(rest)}"
        }

        // Checkbox list item
        if (trimmed.startsWith("- [") || trimmed.startsWith("* [")) {
            val checked = trimmed.startsWith("- [x]") || trimmed.startsWith("* [x]")
            val rest = trimmed.replaceFirst(Regex("^[-*] \\[[ xX]\\] "), "")
            val box = if (checked) Ansi.brightGreen("☑") else Ansi.gray("☐")
            return "  $box ${renderInline(rest)}"
        }

        return renderInline(line)
    }

    /** Render a complete multi-line markdown string. */
    fun render(text: String): String {
        val sb = StringBuilder()
        text.lines().forEach { sb.append(renderLine(it)).append("\n") }
        return sb.toString()
    }

    private fun renderInline(text: String): String {
        var s = text
        // Inline code first, so its content is not styled by other rules.
        s = s.replace(Regex("`([^`]+)`")) { m -> Ansi.code(m.groupValues[1]) }
        // Bold
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*")) { m -> Ansi.bold(m.groupValues[1]) }
        s = s.replace(Regex("__([^_]+)__")) { m -> Ansi.bold(m.groupValues[1]) }
        // Links: [text](url)
        s = s.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { m -> Ansi.link(m.groupValues[1]) }
        // Italic (single asterisk / underscore, avoid matching leftovers)
        s = s.replace(Regex("(?<!\\*)\\*([^*\\s][^*]*)\\*(?!\\*)")) { m -> Ansi.italic(m.groupValues[1]) }
        s = s.replace(Regex("(?<!_)_([^_\\s][^_]*)_(?!_)")) { m -> Ansi.italic(m.groupValues[1]) }
        return s
    }
}
