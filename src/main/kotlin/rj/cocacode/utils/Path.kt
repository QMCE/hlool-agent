package rj.cocacode.utils

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

fun expandPath(path: String, baseDir: String? = null): String {
    val actualBaseDir = baseDir ?: System.getProperty("user.dir")
    
    if (path.isBlank()) {
        return File(actualBaseDir).absoluteFile.canonicalPath
    }
    
    val expanded = when {
        path == "~" -> System.getProperty("user.home")
        path.startsWith("~/") -> File(System.getProperty("user.home"), path.substring(2)).path
        path.startsWith("/") && isWindows() -> posixToWindowsPath(path)
        else -> path
    }
    
    val resolved: File = if (File(expanded).isAbsolute) File(expanded) else File(actualBaseDir, expanded)
    return resolved.canonicalPath
}

fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

private fun posixToWindowsPath(path: String): String {
    val match = Regex("^/([a-z])/(.*)$").find(path) ?: return path
    val drive = match.groupValues[1].uppercase()
    val rest = match.groupValues[2].replace("/", "\\")
    return "$drive:\\$rest"
}

fun normalizePath(path: String): String = File(path).absoluteFile.canonicalPath

fun joinPath(vararg parts: String): String = parts.joinToString(File.separator) { it }

fun relativePath(from: String, to: String): String = File(from).toPath().relativize(File(to).toPath()).toString()

fun isAbsolutePath(path: String): Boolean = File(path).isAbsolute

fun getBaseName(path: String): String = File(path).name

fun getDirName(path: String): String = File(path).parent ?: ""

fun getExtension(path: String): String = File(path).extension