package rj.cocacode.constants

object Files {
    val BINARY_EXTENSIONS = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".webp", ".tiff", ".tif",
        ".mp4", ".mov", ".avi", ".mkv", ".webm", ".wmv", ".flv", ".m4v", ".mpeg", ".mpg",
        ".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a", ".wma", ".aiff", ".opus",
        ".zip", ".tar", ".gz", ".bz2", ".7z", ".rar", ".xz", ".z", ".tgz", ".iso",
        ".exe", ".dll", ".so", ".dylib", ".bin", ".o", ".a", ".obj", ".lib", ".app", ".msi", ".deb", ".rpm",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp",
        ".ttf", ".otf", ".woff", ".woff2", ".eot",
        ".pyc", ".pyo", ".class", ".jar", ".war", ".ear", ".node", ".wasm", ".rlib",
        ".sqlite", ".sqlite3", ".db", ".mdb", ".idx",
        ".psd", ".ai", ".eps", ".sketch", ".fig", ".xd", ".blend", ".3ds", ".max",
        ".swf", ".fla",
        ".lockb", ".dat", ".data"
    )
    
    private const val BINARY_CHECK_SIZE = 8192
    
    fun hasBinaryExtension(filePath: String): Boolean {
        val ext = filePath.substringAfterLast(".").lowercase()
        return ext in BINARY_EXTENSIONS
    }
    
    fun isBinaryContent(data: ByteArray): Boolean {
        val checkSize = minOf(data.size, BINARY_CHECK_SIZE)
        
        var nonPrintable = 0
        for (i in 0 until checkSize) {
            val byte = data[i].toInt()
            if (byte == 0) return true
            if (byte < 32 && byte != 9 && byte != 10 && byte != 13) {
                nonPrintable++
            }
        }
        
        return nonPrintable.toDouble() / checkSize > 0.1
    }
}