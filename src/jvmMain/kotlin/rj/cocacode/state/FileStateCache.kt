package rj.cocacode.state

/**
 * FileStateCache for tracking file read states.
 */
class FileStateCache {
    private val cache = mutableMapOf<String, FileState>()
    
    fun get(filePath: String): FileState? = cache[filePath]
    
    fun set(filePath: String, state: FileState) {
        cache[filePath] = state
    }
    
    fun remove(filePath: String) {
        cache.remove(filePath)
    }
    
    fun clear() {
        cache.clear()
    }
    
    fun has(filePath: String): Boolean = cache.containsKey(filePath)
    
    fun all(): Map<String, FileState> = cache.toMap()
}

/**
 * FileState represents the state of a file at a point in time.
 */
data class FileState(
    val path: String,
    val content: String,
    val hash: String,
    val timestamp: Long = System.currentTimeMillis()
)
