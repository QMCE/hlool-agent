package rj.cocacode.utils

import java.io.File

object Git {
    private val gitRootCache = mutableMapOf<String, String?>()
    
    fun findGitRoot(startPath: String): String? {
        gitRootCache[startPath]?.let { return it }
        
        var current = File(startPath).absoluteFile.absolutePath
        val root = current.substring(0, current.indexOf(File.separator) + 1)
        
        while (current != root) {
            val gitPath = File(current, ".git")
            if (gitPath.exists()) {
                gitRootCache[startPath] = current
                return current
            }
            val parent = File(current).parentFile
            current = parent?.absoluteFile?.absolutePath ?: break
        }
        
        gitRootCache[startPath] = null
        return null
    }
    
    fun isGitRepository(path: String): Boolean = findGitRoot(path) != null
    
    fun getCurrentBranch(dir: String): String? {
        val gitRoot = findGitRoot(dir) ?: return null
        val headFile = File(File(gitRoot, ".git"), "HEAD")
        if (!headFile.exists()) return null
        
        return try {
            val content = headFile.readText().trim()
            if (content.startsWith("ref: ")) content.substring(5) else content
        } catch (e: Exception) { null }
    }
    
    fun getRemoteUrl(dir: String, remote: String = "origin"): String? {
        val gitRoot = findGitRoot(dir) ?: return null
        val configFile = File(File(gitRoot, ".git"), "config")
        if (!configFile.exists()) return null
        
        return try {
            val content = configFile.readText()
            val regex = Regex("""\[remote\s+"$remote"\]""")
            val match = regex.find(content) ?: return null
            val start = match.range.last
            val urlRegex = Regex("""\s*url\s*=\s*(.+)""")
            urlRegex.find(content, start)?.groupValues?.get(1)?.trim()
        } catch (e: Exception) { null }
    }
    
    fun hasUncommittedChanges(dir: String): Boolean {
        val gitDir = findGitRoot(dir) ?: return false
        return try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(File(gitDir))
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.isNotBlank()
        } catch (e: Exception) { false }
    }
    
    fun getStatus(dir: String): GitStatus {
        val gitDir = findGitRoot(dir) ?: return GitStatus(false, emptyList(), emptyList())
        
        return try {
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(File(gitDir))
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            val staged = mutableListOf<String>()
            val modified = mutableListOf<String>()
            
            output.lines().forEach { line ->
                if (line.length >= 3) {
                    val status = line.substring(0, 2)
                    val file = line.substring(3)
                    if (status.any { it != ' ' && it != '?' }) modified.add(file)
                    if (status[0] != ' ' && status[0] != '?') staged.add(file)
                }
            }
            
            GitStatus(true, staged, modified)
        } catch (e: Exception) {
            GitStatus(false, emptyList(), emptyList())
        }
    }
    
    fun runGitCommand(dir: String, vararg args: String): ProcessResult {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(File(dir))
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            ProcessResult(exitCode == 0, output, error)
        } catch (e: Exception) {
            ProcessResult(false, "", e.message ?: "Unknown error")
        }
    }
}

data class GitStatus(
    val isRepo: Boolean,
    val stagedFiles: List<String>,
    val modifiedFiles: List<String>
)

data class ProcessResult(
    val success: Boolean,
    val output: String,
    val error: String
)