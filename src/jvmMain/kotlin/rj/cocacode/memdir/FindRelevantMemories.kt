package rj.cocacode.memdir

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Find relevant memories for a given query.
 * Uses a two-phase approach:
 * 1. Fast keyword-based pre-filtering (header scan)
 * 2. Optional LLM-based relevance re-ranking (via scoring prompt)
 */
object FindRelevantMemories {

    private const val MAX_KEYWORD_MATCHES = 50
    private const val MAX_LLM_CANDIDATES = 20
    private const val MAX_FINAL_RESULTS = 10
    private const val MEMORY_SNIPPET_LINES = 15

    data class RelevanceResult(
        val header: MemoryHeader,
        val score: Double,
        val snippet: String,
        val matchedTerms: List<String> = emptyList()
    )

    /**
     * Find relevant memories using keyword matching.
     * Phase 1: filter by header description/type match.
     */
    fun findRelevantHeaders(
        query: String,
        headers: List<MemoryHeader>,
        maxResults: Int = MAX_KEYWORD_MATCHES
    ): List<MemoryHeader> {
        if (headers.isEmpty()) return emptyList()
        val queryTerms = query.lowercase()
            .split(Regex("[\\s,;:.!?()\\[\\]{}]+"))
            .filter { it.length > 2 }

        val scored = headers.mapNotNull { header ->
            val description = header.description?.lowercase() ?: ""
            val filename = header.filename.lowercase()
            val type = header.type?.value?.lowercase() ?: ""

            val matchedTerms = queryTerms.filter { term ->
                description.contains(term) ||
                filename.contains(term) ||
                type.contains(term)
            }

            if (matchedTerms.isNotEmpty()) {
                val score = matchedTerms.size.toDouble() / queryTerms.size
                RelevanceResult(header, score, "", matchedTerms)
            } else {
                // Even without keyword match, include recently modified memories at low score
                val recencyBonus = if (System.currentTimeMillis() - header.mtimeMs < 7 * 86_400_000L) 0.1 else 0.0
                if (recencyBonus > 0) {
                    RelevanceResult(header, recencyBonus, "", emptyList())
                } else null
            }
        }

        return scored
            .sortedByDescending { it.score }
            .take(maxResults)
            .map { it.header }
    }

    /**
     * Read memory content snippets for candidate headers.
     */
    fun readMemorySnippets(
        candidates: List<MemoryHeader>,
        memoryDir: String,
        maxLines: Int = MEMORY_SNIPPET_LINES
    ): Map<String, String> {
        val base = Paths.get(memoryDir)
        val result = mutableMapOf<String, String>()

        for (header in candidates) {
            try {
                val filePath = base.resolve(header.filename)
                if (Files.exists(filePath)) {
                    val content = Files.readString(filePath)
                    val lines = content.lines()
                    val snippet = if (lines.size <= maxLines) {
                        content
                    } else {
                        lines.take(maxLines).joinToString("\n") + "\n..."
                    }
                    result[header.filename] = snippet
                }
            } catch (_: Exception) {
                // skip unreadable files
            }
        }
        return result
    }

    /**
     * Score and rank memory candidates by relevance to the query.
     * Phase 2: content-level scoring using keyword density.
     */
    fun scoreMemories(
        query: String,
        snippets: Map<String, MemoryHeader>,
        contentSnippets: Map<String, String>
    ): List<RelevanceResult> {
        val queryTerms = query.lowercase()
            .split(Regex("[\\s,;:.!?()\\[\\]{}]+"))
            .filter { it.length > 2 }
        val queryWords = queryTerms.toSet()

        val results = mutableListOf<RelevanceResult>()

        for ((filename, header) in snippets) {
            val content = contentSnippets[filename]?.lowercase() ?: continue
            val description = header.description?.lowercase() ?: ""

            // Score based on term frequency in content + description match
            var termMatches = 0
            val matchedTerms = mutableListOf<String>()
            for (term in queryWords) {
                val count = Regex(Regex.escape(term)).findAll(content).count()
                if (count > 0) {
                    termMatches += count
                    matchedTerms.add(term)
                }
            }

            val descBonus = if (queryWords.any { description.contains(it) }) 0.3 else 0.0
            val recencyBonus = if (System.currentTimeMillis() - header.mtimeMs < 3 * 86_400_000L) 0.2 else 0.0
            val typeBonus = if (query.lowercase().contains(header.type?.value?.lowercase() ?: "")) 0.1 else 0.0

            val score = (termMatches.toDouble() / (1 + content.length / 200.0)) +
                    descBonus + recencyBonus + typeBonus

            results.add(RelevanceResult(header, score, content.take(500), matchedTerms))
        }

        return results.sortedByDescending { it.score }.take(MAX_FINAL_RESULTS)
    }

    /**
     * Full pipeline: keyword filter + content scoring.
     * Returns the top N most relevant memory results.
     */
    suspend fun findRelevant(
        query: String,
        headers: List<MemoryHeader>,
        memoryDir: String,
        maxResults: Int = MAX_FINAL_RESULTS
    ): List<RelevanceResult> {
        if (headers.isEmpty()) return emptyList()

        // Phase 1: fast keyword pre-filter
        val candidates = findRelevantHeaders(query, headers, MAX_LLM_CANDIDATES)
        if (candidates.isEmpty()) return emptyList()

        // Read content snippets for candidates
        val contentSnippets = readMemorySnippets(candidates, memoryDir)
        val headerMap = candidates.associateBy { it.filename }

        // Phase 2: score by content
        val scored = scoreMemories(query, headerMap, contentSnippets)
        return scored.take(maxResults)
    }

}
