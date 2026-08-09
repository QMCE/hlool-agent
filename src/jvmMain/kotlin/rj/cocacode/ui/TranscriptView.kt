package rj.cocacode.ui

/**
 * In-memory transcript blocks for collapsible tool output.
 * (No alt-screen — normal scrollback only.)
 */
class TranscriptView {
    sealed class Block {
        data class Text(val text: String) : Block()
        data class Tool(
            val name: String,
            val summary: String,
            val detail: String,
            var expanded: Boolean = false
        ) : Block()
    }

    private val blocks = mutableListOf<Block>()

    fun addText(text: String) {
        if (text.isNotEmpty()) blocks.add(Block.Text(text))
    }

    fun addTool(name: String, summary: String, detail: String, collapsed: Boolean = true) {
        blocks.add(Block.Tool(name, summary, detail, expanded = !collapsed))
    }

    fun toggleLastTool(): Boolean {
        val tool = blocks.asReversed().filterIsInstance<Block.Tool>().firstOrNull() ?: return false
        tool.expanded = !tool.expanded
        return true
    }

    /** Render a tool result for immediate print (collapsed by default). */
    fun formatToolForPrint(name: String, detail: String, isError: Boolean): String {
        val firstLine = detail.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                val plain = line.replace(Regex("\u001b\\[[0-9;]*[A-Za-z]"), "")
                plain.isNotBlank() && !plain.startsWith("⎿") && plain != name
            }
            ?.replace(Regex("\u001b\\[[0-9;]*[A-Za-z]"), "")
            .orEmpty()
            .ifBlank { name }
        val summary = "● $name — ${firstLine.take(80)}"
        addTool(name, summary, detail, collapsed = true)
        val tip = Ansi.dim("  (Ctrl+O expand last tool)")
        return if (isError) {
            Ansi.brightRed(summary) + "\n" + tip
        } else {
            Ansi.dim(summary) + "\n" + tip
        }
    }

    fun expandLastDetail(): String? {
        val tool = blocks.asReversed().filterIsInstance<Block.Tool>().firstOrNull() ?: return null
        tool.expanded = true
        return tool.detail
    }

    fun clear() {
        blocks.clear()
    }
}
