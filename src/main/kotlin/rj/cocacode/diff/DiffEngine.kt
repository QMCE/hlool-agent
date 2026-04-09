package rj.cocacode.diff

import java.io.File

data class DiffResult(
    val filePath: String,
    val oldContent: String,
    val newContent: String,
    val hunks: List<DiffHunk>
)

data class DiffHunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<DiffLine>
)

data class DiffLine(
    val type: LineType,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

enum class LineType {
    CONTEXT, ADDED, REMOVED, HEADER
}

object DiffEngine {
    fun compute(oldContent: String, newContent: String): DiffResult {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        val hunks = computeHunks(oldLines, newLines)
        
        return DiffResult(
            filePath = "",
            oldContent = oldContent,
            newContent = newContent,
            hunks = hunks
        )
    }
    
    fun computeFileDiff(oldPath: String, newPath: String): DiffResult {
        val oldContent = File(oldPath).readText()
        val newContent = File(newPath).readText()
        return compute(oldContent, newContent).copy(filePath = oldPath)
    }
    
    private fun computeHunks(oldLines: List<String>, newLines: List<String>): List<DiffHunk> {
        val lcs = longestCommonSubsequence(oldLines, newLines)
        val hunks = mutableListOf<DiffHunk>()
        
        var oldIdx = 0
        var newIdx = 0
        var hunkStart = 0
        var hunkLines = mutableListOf<DiffLine>()
        var oldLineNum = 1
        var newLineNum = 1
        
        while (oldIdx < oldLines.size || newIdx < newLines.size) {
            if (oldIdx < lcs.size && newIdx < lcs.size && 
                oldLines[oldIdx] == newLines[newIdx] &&
                oldLines[oldIdx] == lcs[lcs.size - 1 - (oldLines.size - 1 - oldIdx)]) {
                
                if (hunkLines.isNotEmpty() && hunkLines.size >= 3) {
                    hunks.add(createHunk(hunkLines, oldIdx, newIdx))
                    hunkLines.clear()
                }
                
                hunkLines.add(DiffLine(LineType.CONTEXT, oldLines[oldIdx], oldLineNum, newLineNum))
                oldIdx++
                newIdx++
                oldLineNum++
                newLineNum++
            } else {
                if (oldIdx < oldLines.size && (newIdx >= newLines.size || shouldRemove(oldLines, newLines, oldIdx, newIdx, lcs))) {
                    hunkLines.add(DiffLine(LineType.REMOVED, oldLines[oldIdx], oldLineNum, null))
                    oldIdx++
                    oldLineNum++
                } else if (newIdx < newLines.size) {
                    hunkLines.add(DiffLine(LineType.ADDED, newLines[newIdx], null, newLineNum))
                    newIdx++
                    newLineNum++
                }
            }
        }
        
        if (hunkLines.isNotEmpty()) {
            hunks.add(createHunk(hunkLines, oldIdx, newIdx))
        }
        
        return hunks
    }
    
    private fun createHunk(lines: List<DiffLine>, oldIdx: Int, newIdx: Int): DiffHunk {
        val removed = lines.filter { it.type == LineType.REMOVED }
        val added = lines.filter { it.type == LineType.ADDED }
        
        return DiffHunk(
            oldStart = removed.firstOrNull()?.oldLineNumber ?: 1,
            oldLines = removed.size,
            newStart = added.firstOrNull()?.newLineNumber ?: 1,
            newLines = added.size,
            lines = lines
        )
    }
    
    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        val result = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                result.add(0, a[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        
        return result
    }
    
    private fun shouldRemove(oldLines: List<String>, newLines: List<String>, oldIdx: Int, newIdx: Int, lcs: List<String>): Boolean {
        if (oldIdx >= oldLines.size) return false
        if (newIdx >= newLines.size) return true
        return oldLines[oldIdx] != newLines[newIdx]
    }
    
    fun formatUnified(diff: DiffResult): String {
        val sb = StringBuilder()
        
        sb.append("--- a/${diff.filePath}\n")
        sb.append("+++ b/${diff.filePath}\n")
        
        for (hunk in diff.hunks) {
            sb.append("@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@\n")
            
            for (line in hunk.lines) {
                when (line.type) {
                    LineType.ADDED -> sb.append("+${line.content}\n")
                    LineType.REMOVED -> sb.append("-${line.content}\n")
                    LineType.CONTEXT -> sb.append(" ${line.content}\n")
                    LineType.HEADER -> sb.append("${line.content}\n")
                }
            }
        }
        
        return sb.toString()
    }
    
    fun applyPatch(content: String, patch: String): String {
        val lines = content.lines().toMutableList()
        val patchLines = patch.lines()
        
        var idx = 0
        var lineOffset = 0
        
        while (idx < patchLines.size) {
            val line = patchLines[idx]
            
            if (line.startsWith("@@")) {
                val match = Regex("@@ -(\\d+),?(\\d*) \\+(\\d+),?(\\d*) @@").find(line)
                if (match != null) {
                    val oldStart = match.groupValues[1].toInt() - 1
                    lineOffset = oldStart
                }
                idx++
                continue
            }
            
            if (line.startsWith("-")) {
                val contentLine = line.substring(1)
                if (lineOffset < lines.size && lines[lineOffset] == contentLine) {
                    lines.removeAt(lineOffset)
                }
            } else if (line.startsWith("+")) {
                val contentLine = line.substring(1)
                lines.add(lineOffset, contentLine)
                lineOffset++
            } else if (line.startsWith(" ")) {
                lineOffset++
            }
            
            idx++
        }
        
        return lines.joinToString("\n")
    }
}