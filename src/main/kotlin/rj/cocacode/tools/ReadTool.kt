package rj.cocacode.tools

import java.io.File

class ReadToolImpl : Tool(ReadToolConstants.TOOL_NAME, ReadToolConstants.TOOL_DESCRIPTION) {
    
    companion object {
        private const val DEFAULT_MAX_SIZE_BYTES = 10_000_000L
        private const val MAX_LINES_TO_CACHE = 10_000
    }
    
    private val readFileTimestamps = mutableMapOf<String, Long>()
    private val partialReadState = mutableMapOf<String, ReadState>()
    
    data class ReadState(
        val offset: Int?,
        val limit: Int?,
        val timestamp: Long,
        val totalLines: Int
    )
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val filePath = input["file_path"] as? String 
            ?: return ToolResult(text = "Error: file_path required", isError = true)
        
        val offset = (input["offset"] as? Number)?.toInt()
        val limit = (input["limit"] as? Number)?.toInt()
        
        return readFile(filePath, offset, limit)
    }
    
    fun readFile(filePath: String, offset: Int? = null, limit: Int? = null): ToolResult {
        val absolutePath = File(filePath).absolutePath
        val file = File(absolutePath)
        
        if (!file.exists()) {
            return ToolResult(
                text = "Error: File does not exist: $absolutePath",
                isError = true,
                metadata = mapOf("errorCode" to 1)
            )
        }
        
        if (file.isDirectory) {
            return ToolResult(
                text = "Error: Path is a directory, not a file: $absolutePath",
                isError = true,
                metadata = mapOf("errorCode" to 2)
            )
        }
        
        val fileSize = file.length()
        if (fileSize > DEFAULT_MAX_SIZE_BYTES) {
            return ToolResult(
                text = "Error: File is too large (${formatFileSize(fileSize)}). " +
                       "Maximum file size is ${formatFileSize(DEFAULT_MAX_SIZE_BYTES)}. " +
                       "Use offset and limit to read specific portions.",
                isError = true,
                metadata = mapOf(
                    "errorCode" to 3,
                    "fileSize" to fileSize,
                    "maxSize" to DEFAULT_MAX_SIZE_BYTES
                )
            )
        }
        
        return try {
            readFileContent(file, offset, limit)
        } catch (e: Exception) {
            ToolResult(text = "Error reading file: ${e.message}", isError = true)
        }
    }
    
    private fun readFileContent(file: File, offset: Int?, limit: Int?): ToolResult {
        val absolutePath = file.absolutePath
        val allLines = file.readLines()
        val totalLines = allLines.size
        
        val effectiveOffset = if (offset != null && offset > 0) offset - 1 else 0
        val effectiveLimit = limit ?: (totalLines - effectiveOffset)
        val endLine = minOf(effectiveOffset + effectiveLimit, totalLines)
        
        if (effectiveOffset >= totalLines) {
            return ToolResult(
                text = "<system-reminder>Warning: the file exists but is shorter than the provided offset ($offset). The file has $totalLines lines.</system-reminder>",
                isError = false,
                metadata = mapOf(
                    "filePath" to absolutePath,
                    "totalLines" to totalLines,
                    "startLine" to offset ?: 1,
                    "requestedOffset" to offset
                )
            )
        }
        
        val requestedLines = allLines.subList(effectiveOffset, endLine)
        val lineCount = requestedLines.size
        val formattedContent = formatLinesWithNumbers(requestedLines, effectiveOffset + 1)
        
        val timestamp = file.lastModified()
        readFileTimestamps[absolutePath] = timestamp
        partialReadState[absolutePath] = ReadState(
            offset = offset,
            limit = limit,
            timestamp = timestamp,
            totalLines = totalLines
        )
        
        val output = buildString {
            append("<file>\n")
            append("<path>$absolutePath</path>\n")
            append("<lines>$lineCount</lines>\n")
            append("<total>$totalLines</total>\n")
            if (offset != null) append("<start>$offset</start>\n")
            append("</file>\n\n")
            append(formattedContent)
        }
        
        return ToolResult(
            text = output,
            metadata = mapOf(
                "filePath" to absolutePath,
                "numLines" to lineCount,
                "startLine" to (effectiveOffset + 1),
                "totalLines" to totalLines
            )
        )
    }
    
    private fun formatLinesWithNumbers(lines: List<String>, startLine: Int): String {
        val lineNumberWidth = (startLine + lines.size - 1).toString().length
        return lines.mapIndexed { index, line ->
            "${(startLine + index).toString().padStart(lineNumberWidth)}: $line"
        }.joinToString("\n")
    }
    
    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
    
    fun hasFileChanged(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return true
        val lastRead = readFileTimestamps[file.absolutePath] ?: return true
        return file.lastModified() > lastRead
    }
    
    fun getLastReadTime(filePath: String): Long? = readFileTimestamps[File(filePath).absolutePath]
    fun getReadState(filePath: String): ReadState? = partialReadState[File(filePath).absolutePath]
    
    fun clearReadState(filePath: String) {
        val absolutePath = File(filePath).absolutePath
        readFileTimestamps.remove(absolutePath)
        partialReadState.remove(absolutePath)
    }
}

object ReadToolConstants {
    const val TOOL_NAME = "Read"
    const val TOOL_DESCRIPTION = "Read the contents of a file. Use file_path for the absolute path, offset for starting line, and limit for number of lines."
    
    const val INPUT_SCHEMA = """
        {
            "file_path": {
                "type": "string",
                "description": "The absolute path to the file to read"
            },
            "offset": {
                "type": "number",
                "description": "The line number to start reading from (1-indexed, defaults to 1)"
            },
            "limit": {
                "type": "number", 
                "description": "The maximum number of lines to read"
            }
        }
    """
}
