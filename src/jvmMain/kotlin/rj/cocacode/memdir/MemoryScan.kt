package rj.cocacode.memdir

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import kotlin.system.measureTimeMillis

data class MemoryHeader(
    val filename: String,
    val filePath: String,
    val mtimeMs: Long,
    val description: String?,
    val type: MemoryType?
)

private const val MAX_MEMORY_FILES = 200
private const val FRONTMATTER_MAX_LINES = 30

class AbortSignal(var aborted: Boolean = false)

data class ReadResult(val content: String, val mtimeMs: Long)
data class FrontMatter(val frontmatter: Map<String, String>)

private fun parseFrontmatter(content: String, filePath: String): FrontMatter {
    val fm = mutableMapOf<String, String>()
    val lines = content.lines()
    if (lines.size >= 2 && lines[0].trim() == "---") {
        var i = 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim() == "---") break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) fm[key] = value
            }
            i++
            if (i > FRONTMATTER_MAX_LINES) break
        }
    }
    return FrontMatter(fm)
}

private fun readFileInRange(
    filePath: String,
    startLine: Int,
    maxLines: Int,
    encoding: String? = null,
    signal: AbortSignal? = null,
): ReadResult {
    val path = Paths.get(filePath)
    val content = Files.readString(path)
    val lines = content.lines()
    val end = minOf(startLine + maxLines, lines.size)
    val sliced = lines.subList(startLine, end).joinToString("\n")
    val mtime = Files.getLastModifiedTime(path).toMillis()
    return ReadResult(sliced, mtime)
}

suspend fun scanMemoryFiles(memoryDir: String, signal: AbortSignal): List<MemoryHeader> {
    try {
        val base = Paths.get(memoryDir)
        if (!Files.exists(base)) return emptyList()
        val mdFiles = Files.walk(base).filter { p ->
            val s = p.toString()
            s.endsWith(".md") && p.fileName.toString() != "MEMORY.md"
        }.toList()
        val headerResults = mdFiles.map { p ->
            val filePath = p.toString()
            val relPath = base.relativize(p).toString()
            val rf = readFileInRange(filePath, 0, FRONTMATTER_MAX_LINES, null, signal)
            val content = rf.content
            val fm = parseFrontmatter(rf.content, filePath).frontmatter
            val description = fm["description"]
            val typeRaw = fm["type"]
            MemoryHeader(
                filename = relPath,
                filePath = filePath,
                mtimeMs = rf.mtimeMs,
                description = description,
                type = parseMemoryType(typeRaw)
            )
        }
        return headerResults
            .sortedByDescending { it.mtimeMs }
            .take(MAX_MEMORY_FILES)
    } catch (e: Exception) {
        return emptyList()
    }
}

fun formatMemoryManifest(memories: List<MemoryHeader>): String {
    return memories.joinToString("\n") { m ->
        val tag = m.type?.let { "[${it.value}] " } ?: ""
        val ts = java.util.Date(m.mtimeMs).toInstant().toString()
        if (m.description != null) "- ${tag}${m.filename} (${ts}): ${m.description}" else "- ${tag}${m.filename} (${ts})"
    }
}
