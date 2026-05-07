package rj.cocacode.memdir

import java.nio.file.Paths

/**
 * Core memdir system prompt builder.
 * Assembles memory content, type definitions, and age annotations
 * into sections usable in the system prompt.
 */
object MemdirSystem {

    /** Build the full memory system prompt. */
    fun buildMemoryPrompt(
        manifest: List<MemoryHeader>,
        memoryDir: String,
        includeTypes: Boolean = true,
        includeHowToUse: Boolean = true,
    ): String {
        val parts = mutableListOf<String>()

        parts.add(buildMemoryHeaderSection(manifest, memoryDir))
        if (includeTypes) {
            parts.add(TYPES_SECTION_COMBINED.joinToString("\n"))
        }
        parts.add(WHEN_TO_ACCESS_SECTION.joinToString("\n"))
        parts.add(TRUSTING_RECALL_SECTION.joinToString("\n"))
        parts.add(WHAT_NOT_TO_SAVE_SECTION.joinToString("\n"))
        if (includeHowToUse) {
            parts.add(buildMemoryUseGuidance())
        }

        return parts.joinToString("\n\n")
    }

    /** Build a short memory prompt for individual memory types. */
    fun buildTypeSpecificPrompt(type: MemoryType, manifest: List<MemoryHeader>): String {
        val filtered = manifest.filter { it.type == type }
        if (filtered.isEmpty()) return ""

        val parts = mutableListOf<String>()
        parts.add("## ${type.value} memories")
        parts.add("")

        val typeDef = TYPES_SECTION_INDIVIDUAL.joinToString("\n")
            .let { extractTypeSection(it, type) }
        if (typeDef != null) parts.add(typeDef)

        for (mem in filtered) {
            val ageNote = memoryFreshnessNote(mem.mtimeMs)
            parts.add("- **${mem.filename}** ${ageNote.trim()}")
            if (!mem.description.isNullOrBlank()) {
                parts.add("  ${mem.description}")
            }
        }

        return parts.joinToString("\n")
    }

    /** Build the memory usage guidance section. */
    private fun buildMemoryUseGuidance(): String {
        return """
## How to use memory

Memory records are a tool for continuity across sessions and conversations. Use them to:

1. **Recall user preferences** - What approach did the user validate? What did they push back on?
2. **Stay consistent** - If the user established a convention or preference, follow it.
3. **Avoid repeating mistakes** - Past corrections are recorded so you don't need to be corrected twice.
4. **Build on prior work** - Understand ongoing initiatives without asking "what's going on with X?"

Memory is NOT:
- A replacement for reading the codebase (always verify claims from memory)
- A log of every action you take (save meaningful/novel information only)
- A place for temporary task state (that's the conversation)
        """.trimIndent()
    }

    /** Extract a type-specific section from the combined types text. */
    private fun extractTypeSection(typesText: String, type: MemoryType): String? {
        val marker = "<name>${type.value}</name>"
        val startIdx = typesText.indexOf(marker)
        if (startIdx == -1) return null

        val typeStart = typesText.lastIndexOf("<type>", startIdx)
        if (typeStart == -1) return null
        val typeEnd = typesText.indexOf("</type>", typeStart)
        if (typeEnd == -1) return null

        return typesText.substring(typeStart, typeEnd + "</type>".length)
    }

    /** Build the memory header section listing available memories. */
    private fun buildMemoryHeaderSection(manifest: List<MemoryHeader>, memoryDir: String): String {
        if (manifest.isEmpty()) {
            return """
## Available memory

You have a memory directory at `$memoryDir` but no memory files have been created yet.
Use memory tools to store important information for future conversations.
            """.trimIndent()
        }

        val manifestStr = formatMemoryManifest(manifest)
        return """
## Available memory

Your memory files are stored in `$memoryDir`.

### Files (${manifest.size} total)
$manifestStr
        """.trimIndent()
    }

    /** Build the prompt for reading a specific memory file. */
    fun buildMemoryFilePrompt(memory: MemoryHeader, content: String): String {
        val age = memoryAge(memory.mtimeMs)
        return """
### ${memory.filename}
**Age:** $age
**Type:** ${memory.type?.value ?: "unknown"}
**Description:** ${memory.description ?: "none"}

```markdown
$content
```
        """.trimIndent()
    }

    /** Build a section that summarizes recent changes to memory. */
    fun buildMemoryActivityPrompt(added: List<String>, removed: List<String>): String {
        if (added.isEmpty() && removed.isEmpty()) return ""

        val parts = mutableListOf("<system-reminder>Memory activity (this turn):")
        if (added.isNotEmpty()) {
            parts.add("  Saved: ${added.joinToString(", ")}")
        }
        if (removed.isNotEmpty()) {
            parts.add("  Removed: ${removed.joinToString(", ")}")
        }
        parts.add("</system-reminder>")
        return parts.joinToString("\n")
    }
}
