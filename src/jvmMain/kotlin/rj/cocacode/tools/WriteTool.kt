package rj.cocacode.tools

import java.io.File

/**
 * WriteTool - Write content to a file.
 *
 * Mirrors the reference Claude Code FileWriteTool: accepts `file_path` (absolute)
 * and `content`. Creates parent directories as needed.
 */
class WriteToolImpl : Tool("Write", "Write content to a file") {

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val filePath = input["file_path"] as? String
            ?: return ToolResult(text = "Error: file_path required", isError = true)
        val content = input["content"] as? String
            ?: return ToolResult(text = "Error: content required", isError = true)

        val file = File(filePath)
        val absolutePath = file.absolutePath

        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult(
                text = "File written successfully to $absolutePath",
                metadata = mapOf(
                    "filePath" to absolutePath,
                    "bytesWritten" to content.encodeToByteArray().size
                )
            )
        } catch (e: Exception) {
            ToolResult(text = "Error writing file: ${e.message}", isError = true)
        }
    }
}