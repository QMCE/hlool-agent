package rj.cocacode.search

import java.io.File

object SearchEngine {
    data class SearchResult(
        val filePath: String,
        val lineNumber: Int,
        val line: String,
        val matchStart: Int,
        val matchEnd: Int
    )
    
    data class SearchOptions(
        val caseSensitive: Boolean = false,
        val wholeWord: Boolean = false,
        val regex: Boolean = false,
        val maxResults: Int = 100
    )
    
    fun search(
        query: String,
        path: String,
        options: SearchOptions = SearchOptions()
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val dir = File(path)
        
        if (!dir.exists()) return results
        
        val pattern = if (options.regex) {
            if (options.caseSensitive) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
        } else {
            val escaped = Regex.escape(query)
            val prefix = if (options.wholeWord) "\\b" else ""
            val suffix = if (options.wholeWord) "\\b" else ""
            if (options.caseSensitive) Regex("$prefix$escaped$suffix")
            else Regex("$prefix$escaped$suffix", RegexOption.IGNORE_CASE)
        }
        
        searchRecursive(dir, pattern, options, results)
        
        return results.take(options.maxResults)
    }
    
    private fun searchRecursive(
        dir: File,
        pattern: Regex,
        options: SearchOptions,
        results: MutableList<SearchResult>
    ) {
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && !file.name.startsWith(".") -> {
                    searchRecursive(file, pattern, options, results)
                }
                file.isFile && isSearchable(file) -> {
                    searchFile(file, pattern, options, results)
                }
            }
        }
    }
    
    private fun searchFile(
        file: File,
        pattern: Regex,
        options: SearchOptions,
        results: MutableList<SearchResult>
    ) {
        try {
            file.readLines().forEachIndexed { lineNum, line ->
                val match = pattern.find(line)
                if (match != null) {
                    results.add(SearchResult(
                        filePath = file.absolutePath,
                        lineNumber = lineNum + 1,
                        line = line,
                        matchStart = match.range.first,
                        matchEnd = match.range.last + 1
                    ))
                }
            }
        } catch (e: Exception) {
        }
    }
    
    private fun isSearchable(file: File): Boolean {
        val binaryExtensions = setOf(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".webp",
            ".mp4", ".mov", ".avi", ".mkv", ".webm",
            ".mp3", ".wav", ".ogg", ".flac",
            ".zip", ".tar", ".gz", ".bz2", ".7z", ".rar",
            ".exe", ".dll", ".so", ".dylib", ".bin", ".o", ".a",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".class", ".jar", ".pyc", ".wasm"
        )
        
        return file.extension.lowercase() !in binaryExtensions
    }
}

object Grep {
    fun grep(query: String, path: String, options: SearchEngine.SearchOptions = SearchEngine.SearchOptions()): List<SearchEngine.SearchResult> {
        return SearchEngine.search(query, path, options)
    }
    
    fun grepRecursive(query: String, path: String, extension: String? = null): List<SearchEngine.SearchResult> {
        val options = SearchEngine.SearchOptions()
        val results = SearchEngine.search(query, path, options)
        
        return if (extension != null) {
            results.filter { it.filePath.endsWith(extension) }
        } else {
            results
        }
    }
    
    fun countMatches(query: String, path: String): Int {
        return SearchEngine.search(query, path, SearchEngine.SearchOptions(maxResults = Int.MAX_VALUE)).size
    }
}