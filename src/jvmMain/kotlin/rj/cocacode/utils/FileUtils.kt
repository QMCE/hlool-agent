package rj.cocacode.utils

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object FileUtils {
    fun readFile(path: String): String = File(path).readText()
    
    fun writeFile(path: String, content: String) {
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }
    
    fun appendFile(path: String, content: String) {
        File(path).appendText(content)
    }
    
    fun deleteFile(path: String): Boolean = File(path).delete()
    
    fun fileExists(path: String): Boolean = File(path).exists()
    
    fun isDirectory(path: String): Boolean = File(path).isDirectory
    
    fun isFile(path: String): Boolean = File(path).isFile
    
    fun listFiles(path: String): List<String> = 
        File(path).list()?.toList() ?: emptyList()
    
    fun listDirectories(path: String): List<String> =
        File(path).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    
    fun getFileSize(path: String): Long = File(path).length()
    
    fun getLastModified(path: String): Long = File(path).lastModified()
    
    fun copyFile(source: String, dest: String) {
        File(source).copyTo(File(dest), overwrite = true)
    }
    
    fun moveFile(source: String, dest: String) {
        File(source).copyTo(File(dest), overwrite = true)
        File(source).delete()
    }
    
    fun createDirectory(path: String) {
        File(path).mkdirs()
    }
    
    fun deleteDirectory(path: String, recursive: Boolean = false) {
        val dir = File(path)
        if (recursive) {
            dir.deleteRecursively()
        } else {
            dir.delete()
        }
    }
}

object FileWatcher {
    private val watchers = mutableMapOf<String, java.util.concurrent.ScheduledFuture<*>>()
    
    fun watch(path: String, callback: () -> Unit, intervalMs: Long = 1000) {
        if (watchers.containsKey(path)) return
        
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        watchers[path] = executor.scheduleAtFixedRate({
            val lastModified = File(path).lastModified()
            callback()
        }, 0, intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    fun unwatch(path: String) {
        watchers[path]?.cancel(true)
        watchers.remove(path)
    }
    
    fun unwatchAll() {
        watchers.values.forEach { it.cancel(true) }
        watchers.clear()
    }
}

object FileMimeTypes {
    private val types = mapOf(
        "txt" to "text/plain",
        "html" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "json" to "application/json",
        "xml" to "application/xml",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml"
    )
    
    fun getMimeType(extension: String): String = types[extension.lowercase()] ?: "application/octet-stream"
    
    fun getExtension(mimeType: String): String? = types.entries.find { it.value == mimeType }?.key
}