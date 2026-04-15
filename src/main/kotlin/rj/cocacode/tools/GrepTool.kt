package rj.cocacode.tools

import kotlinx.serialization.Serializable
import rj.cocacode.utils.Shell
import rj.cocacode.utils.Shell.ExecOptions
import rj.cocacode.utils.expandPath
import java.io.File
import kotlin.math.max

/**
 * GrepTool - Ripgrep wrapper for file content search
 * 
 * Migrated from Claude Code's GrepTool with full feature support:
 * - Ripgrep (rg) command execution
 * - Output modes: content, files_with_matches, count
 * - Context options: -B (before), -A (after), -C (context)
 * - Line numbers, case insensitive search
 * - File type filtering
 * - head_limit and offset pagination
 * - Multiline mode support
 */
class GrepToolImpl : Tool("Grep", "Search file contents using ripgrep") {
    
    companion object {
        // VCS directories to exclude from searches
        private val VCS_DIRECTORIES_TO_EXCLUDE = listOf(
            ".git", ".svn", ".hg", ".bzr", ".jj", ".sl"
        )
        
        // Default cap on grep results
        private const val DEFAULT_HEAD_LIMIT = 250
        
        // Max line length to prevent base64/minified content
        private const val MAX_COLUMNS = 500
        
        // Search hint for UI
        const val SEARCH_HINT = "search file contents with regex (ripgrep)"
    }
    
    /**
     * Output mode for grep results
     */
    enum class OutputMode {
        CONTENT,          // Shows matching lines with context
        FILES_WITH_MATCHES, // Shows file paths only
        COUNT             // Shows match counts per file
    }
    
    /**
     * Grep result data class
     */
    data class GrepResult(
        val mode: OutputMode,
        val numFiles: Int = 0,
        val filenames: List<String> = emptyList(),
        val content: String? = null,
        val numLines: Int? = null,
        val numMatches: Int? = null,
        val appliedLimit: Int? = null,
        val appliedOffset: Int? = null
    )
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val pattern = input["pattern"] as? String
            ?: return ToolResult(text = "Error: pattern required", isError = true)
        
        val path = input["path"] as? String ?: System.getProperty("user.dir")
        val glob = input["glob"] as? String
        val outputModeStr = input["output_mode"] as? String ?: "files_with_matches"
        val contextBefore = (input["-B"] as? Number)?.toInt()
        val contextAfter = (input["-A"] as? Number)?.toInt()
        val context = (input["-C"] as? Number)?.toInt()
            ?: (input["context"] as? Number)?.toInt()
        val showLineNumbers = input["-n"] as? Boolean ?: true
        val caseInsensitive = input["-i"] as? Boolean ?: false
        val type = input["type"] as? String
        val headLimit = (input["head_limit"] as? Number)?.toInt()
        val offset = (input["offset"] as? Number)?.toInt() ?: 0
        val multiline = input["multiline"] as? Boolean ?: false
        
        val outputMode = when (outputModeStr.lowercase()) {
            "content" -> OutputMode.CONTENT
            "count" -> OutputMode.COUNT
            else -> OutputMode.FILES_WITH_MATCHES
        }
        
        return executeGrep(
            pattern = pattern,
            path = path,
            glob = glob,
            outputMode = outputMode,
            contextBefore = contextBefore,
            contextAfter = contextAfter,
            context = context,
            showLineNumbers = showLineNumbers,
            caseInsensitive = caseInsensitive,
            type = type,
            headLimit = headLimit,
            offset = offset,
            multiline = multiline
        )
    }
    
    private suspend fun executeGrep(
        pattern: String,
        path: String,
        glob: String?,
        outputMode: OutputMode,
        contextBefore: Int?,
        contextAfter: Int?,
        context: Int?,
        showLineNumbers: Boolean,
        caseInsensitive: Boolean,
        type: String?,
        headLimit: Int?,
        offset: Int,
        multiline: Boolean
    ): ToolResult {
        val absolutePath = expandPath(path)
        
        // Verify path exists
        if (!File(absolutePath).exists()) {
            return ToolResult(text = "Path does not exist: $path", isError = true)
        }
        
        val args = mutableListOf<String>()
        
        // Basic flags
        args.add("--hidden")
        
        // Exclude VCS directories
        for (dir in VCS_DIRECTORIES_TO_EXCLUDE) {
            args.add("--glob")
            args.add("!$dir")
        }
        
        // Max line length
        args.add("--max-columns")
        args.add(MAX_COLUMNS.toString())
        
        // Multiline mode
        if (multiline) {
            args.add("-U")
            args.add("--multiline-dotall")
        }
        
        // Case insensitive
        if (caseInsensitive) {
            args.add("-i")
        }
        
        // Output mode
        when (outputMode) {
            OutputMode.FILES_WITH_MATCHES -> args.add("-l")
            OutputMode.COUNT -> args.add("-c")
            OutputMode.CONTENT -> { /* no flag needed */ }
        }
        
        // Line numbers for content mode
        if (showLineNumbers && outputMode == OutputMode.CONTENT) {
            args.add("-n")
        }
        
        // Context flags
        if (outputMode == OutputMode.CONTENT) {
            when {
                context != null -> {
                    args.add("-C")
                    args.add(context.toString())
                }
                contextBefore != null || contextAfter != null -> {
                    contextBefore?.let {
                        args.add("-B")
                        args.add(it.toString())
                    }
                    contextAfter?.let {
                        args.add("-A")
                        args.add(it.toString())
                    }
                }
            }
        }
        
        // Pattern - use -e if starts with dash
        if (pattern.startsWith("-")) {
            args.add("-e")
            args.add(pattern)
        } else {
            args.add(pattern)
        }
        
        // Type filter
        type?.let {
            args.add("--type")
            args.add(it)
        }
        
        // Glob pattern
        glob?.let { globPattern ->
            // Handle multiple patterns separated by commas or spaces
            val patterns = globPattern.split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            for (p in patterns) {
                args.add("--glob")
                args.add(p)
            }
        }
        
        // Execute ripgrep
        val result = Shell.exec("rg", args + listOf(absolutePath), ExecOptions(timeout = 60000))
        
        if (result.exitCode != 0 && result.stdout.isBlank()) {
            // No matches found (exit code 1 is normal for no matches)
            return when (outputMode) {
                OutputMode.CONTENT -> ToolResult(text = "No matches found", metadata = mapOf("mode" to "content"))
                OutputMode.COUNT -> ToolResult(text = "No matches found", metadata = mapOf("mode" to "count", "numFiles" to 0, "numMatches" to 0))
                OutputMode.FILES_WITH_MATCHES -> ToolResult(text = "No files found", metadata = mapOf("mode" to "files_with_matches", "numFiles" to 0))
            }
        }
        
        val rawLines = result.stdout.lines().filter { it.isNotEmpty() }
        
        return when (outputMode) {
            OutputMode.CONTENT -> handleContentMode(rawLines, headLimit, offset)
            OutputMode.COUNT -> handleCountMode(rawLines, headLimit, offset)
            OutputMode.FILES_WITH_MATCHES -> handleFilesMode(rawLines, headLimit, offset)
        }
    }
    
    private fun handleContentMode(
        lines: List<String>,
        headLimit: Int?,
        offset: Int
    ): ToolResult {
        val (limitedLines, appliedLimit) = applyHeadLimit(lines, headLimit, offset)
        
        // Convert absolute paths to relative
        val finalLines = limitedLines.map { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val filePath = line.substring(0, colonIndex)
                val rest = line.substring(colonIndex)
                toRelativePath(filePath) + rest
            } else {
                line
            }
        }
        
        val content = finalLines.joinToString("\n")
        val numLines = finalLines.size
        
        val limitInfo = formatLimitInfo(appliedLimit, if (offset > 0) offset else null)
        val finalContent = if (limitInfo.isNotEmpty()) {
            "$content\n\n[Showing results with pagination = $limitInfo]"
        } else {
            content
        }
        
        return ToolResult(
            text = finalContent,
            metadata = mapOf(
                "mode" to "content",
                "numLines" to numLines,
                "appliedLimit" to (appliedLimit ?: 0),
                "appliedOffset" to offset
            )
        )
    }
    
    private fun handleCountMode(
        lines: List<String>,
        headLimit: Int?,
        offset: Int
    ): ToolResult {
        val (limitedLines, appliedLimit) = applyHeadLimit(lines, headLimit, offset)
        
        // Convert absolute paths to relative
        val finalLines = limitedLines.map { line ->
            val colonIndex = line.lastIndexOf(':')
            if (colonIndex > 0) {
                val filePath = line.substring(0, colonIndex)
                val count = line.substring(colonIndex)
                toRelativePath(filePath) + count
            } else {
                line
            }
        }
        
        // Parse count output
        var totalMatches = 0
        var fileCount = 0
        for (line in finalLines) {
            val colonIndex = line.lastIndexOf(':')
            if (colonIndex > 0) {
                val countStr = line.substring(colonIndex + 1)
                val count = countStr.toIntOrNull() ?: 0
                if (count > 0) {
                    totalMatches += count
                    fileCount++
                }
            }
        }
        
        val content = finalLines.joinToString("\n")
        val limitInfo = formatLimitInfo(appliedLimit, if (offset > 0) offset else null)
        val summary = buildString {
            append("\n\nFound $totalMatches ")
            append(if (totalMatches == 1) "occurrence" else "occurrences")
            append(" across $fileCount ")
            append(if (fileCount == 1) "file" else "files")
            if (limitInfo.isNotEmpty()) {
                append(" with pagination = $limitInfo")
            }
        }
        
        return ToolResult(
            text = content + summary,
            metadata = mapOf(
                "mode" to "count",
                "numFiles" to fileCount,
                "numMatches" to totalMatches,
                "appliedLimit" to (appliedLimit ?: 0),
                "appliedOffset" to offset
            )
        )
    }
    
    private fun handleFilesMode(
        lines: List<String>,
        headLimit: Int?,
        offset: Int
    ): ToolResult {
        // Sort files by modification time (most recent first)
        val sortedFiles = lines.mapNotNull { line ->
            val colonIndex = line.indexOf(':')
            val filePath = if (colonIndex > 0) line.substring(0, colonIndex) else line
            try {
                val file = File(filePath)
                val mtime = if (file.exists()) file.lastModified() else 0L
                Pair(filePath, mtime)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.second }
          .map { it.first }
        
        // Apply head_limit with offset
        val (limitedFiles, appliedLimit) = applyHeadLimit(sortedFiles, headLimit, offset)
        
        // Convert to relative paths
        val relativeFiles = limitedFiles.map { toRelativePath(it) }
        
        val numFiles = relativeFiles.size
        val limitInfo = formatLimitInfo(appliedLimit, if (offset > 0) offset else null)
        
        val content = if (numFiles == 0) {
            "No files found"
        } else {
            buildString {
                append("Found $numFiles ${if (numFiles == 1) "file" else "files"}")
                if (limitInfo.isNotEmpty()) append(" $limitInfo")
                append("\n")
                append(relativeFiles.joinToString("\n"))
            }
        }
        
        return ToolResult(
            text = content,
            metadata = mapOf(
                "mode" to "files_with_matches",
                "numFiles" to numFiles,
                "filenames" to relativeFiles,
                "appliedLimit" to (appliedLimit ?: 0),
                "appliedOffset" to offset
            )
        )
    }
    
    /**
     * Apply head_limit with offset to a list
     */
    private fun <T> applyHeadLimit(
        items: List<T>,
        limit: Int?,
        offset: Int = 0
    ): Pair<List<T>, Int?> {
        // Explicit 0 = unlimited
        if (limit == 0) {
            return Pair(items.drop(offset), null)
        }
        val effectiveLimit = limit ?: DEFAULT_HEAD_LIMIT
        val sliced = items.subList(max(0, offset), minOf(items.size, offset + effectiveLimit))
        
        // Only report limit when truncation occurred
        val wasTruncated = items.size - offset > effectiveLimit
        return Pair(sliced, if (wasTruncated) effectiveLimit else null)
    }
    
    /**
     * Format limit/offset info for display
     */
    private fun formatLimitInfo(appliedLimit: Int?, appliedOffset: Int?): String {
        val parts = mutableListOf<String>()
        if (appliedLimit != null) parts.add("limit: $appliedLimit")
        if (appliedOffset != null && appliedOffset > 0) parts.add("offset: $appliedOffset")
        return parts.joinToString(", ")
    }
    
    /**
     * Convert absolute path to relative path from cwd
     */
    private fun toRelativePath(absolutePath: String): String {
        val cwd = System.getProperty("user.dir")
        return try {
            val cwdFile = File(cwd)
            val targetFile = File(absolutePath)
            cwdFile.toPath().relativize(targetFile.toPath()).toString()
        } catch (e: Exception) {
            absolutePath
        }
    }
    
    /**
     * Check if command is a search operation
     */
    fun isSearchCommand(): Boolean = true
    
    /**
     * Get tool use summary for activity display
     */
    fun getToolUseSummary(input: Map<String, Any>): String {
        val pattern = input["pattern"] as? String ?: return ""
        val path = input["path"] as? String
        return if (path != null) "$pattern in $path" else pattern
    }
}

/**
 * GrepTool schema definitions for API documentation
 */
object GrepToolSchema {
    val inputSchema = mapOf(
        "pattern" to mapOf(
            "type" to "string",
            "description" to "The regular expression pattern to search for in file contents"
        ),
        "path" to mapOf(
            "type" to "string",
            "description" to "File or directory to search in. Defaults to current working directory."
        ),
        "glob" to mapOf(
            "type" to "string",
            "description" to "Glob pattern to filter files (e.g. \"*.js\", \"*.{ts,tsx}\")"
        ),
        "output_mode" to mapOf(
            "type" to "string",
            "description" to "Output mode: content (matching lines with context), files_with_matches (file paths), count (match counts per file). Defaults to files_with_matches."
        ),
        "-B" to mapOf(
            "type" to "number",
            "description" to "Number of lines to show before each match. Requires output_mode: content."
        ),
        "-A" to mapOf(
            "type" to "number",
            "description" to "Number of lines to show after each match. Requires output_mode: content."
        ),
        "-C" to mapOf(
            "type" to "number",
            "description" to "Number of lines to show before and after each match (context)."
        ),
        "context" to mapOf(
            "type" to "number",
            "description" to "Alias for -C option."
        ),
        "-n" to mapOf(
            "type" to "boolean",
            "description" to "Show line numbers in output. Requires output_mode: content. Defaults to true."
        ),
        "-i" to mapOf(
            "type" to "boolean",
            "description" to "Case insensitive search."
        ),
        "type" to mapOf(
            "type" to "string",
            "description" to "File type to search (e.g. js, py, rust, go, java)."
        ),
        "head_limit" to mapOf(
            "type" to "number",
            "description" to "Limit output to first N lines/entries. Defaults to 250. Pass 0 for unlimited."
        ),
        "offset" to mapOf(
            "type" to "number",
            "description" to "Skip first N lines/entries before applying head_limit. Defaults to 0."
        ),
        "multiline" to mapOf(
            "type" to "boolean",
            "description" to "Enable multiline mode where . matches newlines. Default: false."
        )
    )
    
    val outputSchema = mapOf(
        "mode" to mapOf(
            "type" to "string",
            "description" to "The output mode used: content, files_with_matches, or count"
        ),
        "numFiles" to mapOf(
            "type" to "number",
            "description" to "Number of files with matches"
        ),
        "filenames" to mapOf(
            "type" to "array",
            "description" to "List of file paths (for files_with_matches mode)"
        ),
        "content" to mapOf(
            "type" to "string",
            "description" to "The actual content lines or formatted results"
        ),
        "numLines" to mapOf(
            "type" to "number",
            "description" to "Number of content lines (for content mode)"
        ),
        "numMatches" to mapOf(
            "type" to "number",
            "description" to "Total number of matches (for count mode)"
        ),
        "appliedLimit" to mapOf(
            "type" to "number",
            "description" to "The limit that was applied (if any)"
        ),
        "appliedOffset" to mapOf(
            "type" to "number",
            "description" to "The offset that was applied"
        )
    )
}
