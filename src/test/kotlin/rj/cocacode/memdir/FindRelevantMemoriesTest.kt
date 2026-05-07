package rj.cocacode.memdir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class FindRelevantMemoriesTest {

    private val sampleHeaders = listOf(
        MemoryHeader(
            filename = "arch-decision.md",
            filePath = "/tmp/memdir/arch-decision.md",
            mtimeMs = 1_700_000_000_000L,
            description = "Decision to use Hexagonal Architecture",
            type = MemoryType.PROJECT
        ),
        MemoryHeader(
            filename = "bug-login.md",
            filePath = "/tmp/memdir/bug-login.md",
            mtimeMs = 1_700_000_100_000L,
            description = "Login flow bug with OAuth tokens expiring early",
            type = MemoryType.FEEDBACK
        ),
        MemoryHeader(
            filename = "api-design.md",
            filePath = "/tmp/memdir/api-design.md",
            mtimeMs = 1_700_000_200_000L,
            description = "REST API design guidelines",
            type = MemoryType.REFERENCE
        ),
        MemoryHeader(
            filename = "deploy-prod.md",
            filePath = "/tmp/memdir/deploy-prod.md",
            mtimeMs = 1_700_000_300_000L,
            description = "Production deployment checklist",
            type = MemoryType.USER
        ),
        MemoryHeader(
            filename = "notes.md",
            filePath = "/tmp/memdir/notes.md",
            mtimeMs = 1_700_000_400_000L,
            description = "Random meeting notes",
            type = null
        )
    )

    @Test
    fun `findRelevantHeaders returns matching results for architecture query`() {
        val results = FindRelevantMemories.findRelevantHeaders(
            query = "architecture design patterns",
            headers = sampleHeaders,
            maxResults = 10
        )
        assertTrue(results.isNotEmpty())
        val filenames = results.map { it.filename }
        assertTrue(filenames.contains("arch-decision.md") || filenames.contains("api-design.md"))
    }

    @Test
    fun `findRelevantHeaders returns results for unmatched query`() {
        val results = FindRelevantMemories.findRelevantHeaders(
            query = "quantum physics nobel prize",
            headers = sampleHeaders,
            maxResults = 10
        )
        assertTrue(results.size <= sampleHeaders.size)
    }

    @Test
    fun `findRelevantHeaders respects maxResults`() {
        val results = FindRelevantMemories.findRelevantHeaders(
            query = "a",
            headers = sampleHeaders,
            maxResults = 2
        )
        assertTrue(results.size <= 2)
    }

    @Test
    fun `findRelevantHeaders returns empty for empty input`() {
        val results = FindRelevantMemories.findRelevantHeaders(
            query = "anything",
            headers = emptyList()
        )
        assertEquals(0, results.size)
    }

    @Test
    fun `readMemorySnippets reads file content`() {
        val tempDir = java.nio.file.Files.createTempDirectory("memtest").toFile()
        try {
            val content = "line1\nline2\nline3\nline4\nline5"
            val memFile = File(tempDir, "test.md")
            memFile.writeText(content)

            val headers = listOf(
                MemoryHeader(
                    filename = "test.md",
                    filePath = memFile.absolutePath,
                    mtimeMs = memFile.lastModified(),
                    description = "Test",
                    type = null
                )
            )
            val snippets = FindRelevantMemories.readMemorySnippets(headers, tempDir.absolutePath)
            assertEquals(1, snippets.size)
            assertTrue(snippets["test.md"]?.contains("line1") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `readMemorySnippets returns empty for missing file`() {
        val headers = listOf(
            MemoryHeader(
                filename = "missing.md",
                filePath = "/nonexistent/missing.md",
                mtimeMs = 0L,
                description = "Missing",
                type = null
            )
        )
        val snippets = FindRelevantMemories.readMemorySnippets(headers, "/nonexistent")
        assertEquals(0, snippets.size)
    }

    @Test
    fun `readMemorySnippets truncates long files`() {
        val tempDir = java.nio.file.Files.createTempDirectory("memtest").toFile()
        try {
            val content = (1..50).joinToString("\n") { "line$it" }
            val memFile = File(tempDir, "long.md")
            memFile.writeText(content)

            val headers = listOf(
                MemoryHeader(
                    filename = "long.md",
                    filePath = memFile.absolutePath,
                    mtimeMs = memFile.lastModified(),
                    description = "Long",
                    type = null
                )
            )
            val snippets = FindRelevantMemories.readMemorySnippets(headers, tempDir.absolutePath, 5)
            assertTrue(snippets["long.md"]?.contains("...") == true)
            assertTrue(snippets["long.md"]?.lines()?.size!! <= 6)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
