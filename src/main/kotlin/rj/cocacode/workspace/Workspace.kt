package rj.cocacode.workspace

import java.io.File
import rj.cocacode.utils.FileUtils

data class Workspace(
    val rootPath: String,
    val name: String = File(rootPath).name
) {
    fun getFile(path: String): File = File(rootPath, path)
    
    fun fileExists(path: String): Boolean = getFile(path).exists()
    
    fun readFile(path: String): String? = try {
        getFile(path).readText()
    } catch (e: Exception) { null }
    
    fun writeFile(path: String, content: String): Boolean = try {
        getFile(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
        true
    } catch (e: Exception) { false }
    
    fun deleteFile(path: String): Boolean = getFile(path).delete()
    
    fun listFiles(path: String = ""): List<String> = 
        getFile(path).list()?.toList() ?: emptyList()
    
    fun findFiles(pattern: String): List<String> {
        return getFile("").walkTopDown()
            .filter { it.name.matches(Regex(pattern.replace("*", ".*"))) }
            .map { it.relativeTo(getFile("")).path }
            .toList()
    }
    
    fun getStats(): WorkspaceStats {
        var totalFiles = 0
        var totalLines = 0
        var totalSize = 0L
        
        getFile("").walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".")) {
                totalFiles++
                totalSize += file.length()
                try {
                    totalLines += file.readLines().size
                } catch (e: Exception) {}
            }
        }
        
        return WorkspaceStats(
            rootPath = rootPath,
            name = name,
            totalFiles = totalFiles,
            totalLines = totalLines,
            totalSize = totalSize
        )
    }
}

data class WorkspaceStats(
    val rootPath: String,
    val name: String,
    val totalFiles: Int,
    val totalLines: Int,
    val totalSize: Long
) {
    fun formatSize(): String {
        return when {
            totalSize < 1024 -> "$totalSize B"
            totalSize < 1024 * 1024 -> "${totalSize / 1024} KB"
            totalSize < 1024 * 1024 * 1024 -> "${totalSize / (1024 * 1024)} MB"
            else -> "${totalSize / (1024 * 1024 * 1024)} GB"
        }
    }
}

object WorkspaceManager {
    private val workspaces = mutableMapOf<String, Workspace>()
    private var currentWorkspace: Workspace? = null
    
    fun open(rootPath: String): Workspace {
        val ws = Workspace(rootPath)
        workspaces[rootPath] = ws
        currentWorkspace = ws
        return ws
    }
    
    fun close(rootPath: String) {
        workspaces.remove(rootPath)
        if (currentWorkspace?.rootPath == rootPath) {
            currentWorkspace = null
        }
    }
    
    fun getCurrent(): Workspace? = currentWorkspace
    
    fun getWorkspace(rootPath: String): Workspace? = workspaces[rootPath]
    
    fun getAllWorkspaces(): List<Workspace> = workspaces.values.toList()
}