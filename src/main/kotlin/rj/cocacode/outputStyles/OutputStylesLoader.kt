package rj.cocacode.outputStyles

import java.io.File

data class MarkdownFile(
    val filePath: String,
    val frontmatter: Map<String, Any?>,
    val content: String,
    val source: String
)

suspend fun getOutputStyleDirStyles(cwd: String): List<OutputStyleConfig> {
    return try {
        val markdownFiles = loadMarkdownFilesForSubdir("output-styles", cwd)

        markdownFiles.mapNotNull { file ->
            try {
                val fileName = File(file.filePath).name
                val styleName = fileName.removeSuffix(".md")

                val name = file.frontmatter["name"] as? String ?: styleName
                val description = coerceDescriptionToString(
                    file.frontmatter["description"],
                    styleName
                ) ?: extractDescriptionFromMarkdown(
                    file.content,
                    "Custom $styleName output style"
                )

                val keepCodingInstructionsRaw = file.frontmatter["keep-coding-instructions"]
                val keepCodingInstructions = when (keepCodingInstructionsRaw) {
                    true, "true" -> true
                    false, "false" -> false
                    else -> null
                }

                if (file.frontmatter["force-for-plugin"] != null) {
                }

                OutputStyleConfig(
                    name = name,
                    description = description,
                    prompt = file.content.trim(),
                    source = file.source,
                    keepCodingInstructions = keepCodingInstructions
                )
            } catch (e: Exception) {
                null
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun clearOutputStyleCaches() {
}

private fun coerceDescriptionToString(value: Any?, fallback: String): String? {
    return when (value) {
        is String -> value
        null -> null
        else -> fallback
    }
}

private fun extractDescriptionFromMarkdown(content: String, fallback: String): String {
    val lines = content.lines().filter { it.isNotBlank() }
    return lines.firstOrNull() ?: fallback
}

private suspend fun loadMarkdownFilesForSubdir(subdir: String, cwd: String): List<MarkdownFile> {
    return emptyList()
}