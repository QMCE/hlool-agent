package rj.cocacode.tools

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * GlobTool - Find files matching a glob pattern.
 *
 * Mirrors the reference Claude Code GlobTool: accepts `pattern` (required) and an
 * optional `path` (defaults to the current working directory). Results are limited
 * to 100 files.
 */
class GlobToolImpl : Tool("Glob", "Find files matching a glob pattern") {

    companion object {
        private const val MAX_RESULTS = 100
    }

    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val pattern = input["pattern"] as? String
            ?: return ToolResult(text = "Error: pattern required", isError = true)

        val basePath = (input["path"] as? String)?.let { File(it).absolutePath } ?: File(".").absolutePath
        val baseDir = File(basePath)
        if (!baseDir.exists()) {
            return ToolResult(text = "Directory does not exist: $basePath", isError = true)
        }
        if (!baseDir.isDirectory) {
            return ToolResult(text = "Path is not a directory: $basePath", isError = true)
        }

        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val matches = mutableListOf<String>()

        try {
            Files.walkFileTree(baseDir.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (matcher.matches(file)) {
                        matches.add(file.toAbsolutePath().toString())
                        if (matches.size >= MAX_RESULTS) {
                            return FileVisitResult.TERMINATE
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            return ToolResult(text = "Error performing glob: ${e.message}", isError = true)
        }

        val truncated = matches.size >= MAX_RESULTS
        val output = buildString {
            matches.forEach { appendLine(it) }
            if (truncated) append("... (results truncated to $MAX_RESULTS files)")
        }

        return ToolResult(
            text = output.trimEnd(),
            metadata = mapOf(
                "numFiles" to matches.size,
                "truncated" to truncated
            )
        )
    }
}