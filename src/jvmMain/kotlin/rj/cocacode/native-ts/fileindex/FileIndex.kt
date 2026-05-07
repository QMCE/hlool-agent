package rj.cocacode.nativets.fileindex

data class SearchResult(val path: String, val score: Double)

class FileIndex {
    private var paths: List<String> = emptyList()
    private var lowerPaths: List<String> = emptyList()
    private var charBits: IntArray = IntArray(0)
    private var pathLens: IntArray = IntArray(0)
    private var topLevelCache: List<SearchResult>? = null
    private var readyCount: Int = 0
    
    fun loadFromFileList(fileList: List<String>) {
        val seen = mutableSetOf<String>()
        val deduped = fileList.filter { it.isNotEmpty() && seen.add(it) }
        paths = deduped
        lowerPaths = deduped.map { it.lowercase() }
        pathLens = IntArray(deduped.size) { deduped[it].length }
        readyCount = deduped.size
    }
    
    fun search(query: String, limit: Int): List<SearchResult> {
        if (limit <= 0) return emptyList()
        if (query.isEmpty()) {
            return topLevelCache?.take(limit) ?: emptyList()
        }
        
        val caseSensitive = query != query.lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        val needleLen = minOf(needle.length, 64)
        
        val results = mutableListOf<SearchResult>()
        
        for (i in 0 until readyCount) {
            val haystack = if (caseSensitive) paths[i] else lowerPaths[i]
            val pos = haystack.indexOf(needle.take(needleLen))
            if (pos >= 0) {
                val positionScore = results.size.toDouble() / maxOf(results.size, 1)
                val finalScore = if (paths[i].contains("test")) minOf(positionScore * 1.05, 1.0) else positionScore
                results.add(SearchResult(paths[i], finalScore))
            }
        }
        
        return results.sortedBy { it.score }.take(limit)
    }
}