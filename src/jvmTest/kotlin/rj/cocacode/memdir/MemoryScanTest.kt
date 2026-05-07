package rj.cocacode.memdir

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class MemoryScanTest {

    @Test
    fun `scanMemoryFiles returns empty for non-existent directory`() = runTest {
        val signal = AbortSignal()
        val result = scanMemoryFiles("/nonexistent/path", signal)
        assertEquals(0, result.size)
    }

    @Test
    fun `scanMemoryFiles finds memory files in temp dir`() = runTest {
        val tempDir = Files.createTempDirectory("memtest").toFile()
        try {
            val memFile = File(tempDir, "test-memory.md")
            memFile.writeText(
                """
                ---
                type: project
                description: A test memory
                ---
                This is the content of the test memory.
                """.trimIndent()
            )

            val signal = AbortSignal()
            val results = scanMemoryFiles(tempDir.absolutePath, signal)
            assertEquals(1, results.size)
            assertEquals("test-memory.md", results[0].filename)
            assertEquals("A test memory", results[0].description)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanMemoryFiles skips non-md files`() = runTest {
        val tempDir = Files.createTempDirectory("memtest").toFile()
        try {
            File(tempDir, "notes.txt").writeText("plain text")
            File(tempDir, "data.json").writeText("{}")

            val signal = AbortSignal()
            val results = scanMemoryFiles(tempDir.absolutePath, signal)
            assertEquals(0, results.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanMemoryFiles reads description from frontmatter`() = runTest {
        val tempDir = Files.createTempDirectory("memtest").toFile()
        try {
            val memFile = File(tempDir, "design-notes.md")
            memFile.writeText(
                """
                ---
                type: feedback
                description: Why we chose Kotlin for this project
                rationale: Better null safety
                ---
                Content here
                """.trimIndent()
            )

            val signal = AbortSignal()
            val results = scanMemoryFiles(tempDir.absolutePath, signal)
            assertEquals(1, results.size)
            assertEquals("design-notes.md", results[0].filename)
            assertEquals("Why we chose Kotlin for this project", results[0].description)
            assertEquals(MemoryType.FEEDBACK, results[0].type)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `formatMemoryManifest produces expected output`() {
        val headers = listOf(
            MemoryHeader(
                filename = "arch.md",
                filePath = "/m/arch.md",
                mtimeMs = 1_700_000_000_000L,
                description = "System architecture decisions",
                type = MemoryType.USER
            ),
            MemoryHeader(
                filename = "bugs.md",
                filePath = "/m/bugs.md",
                mtimeMs = 1_700_000_100_000L,
                description = "Known bugs",
                type = MemoryType.FEEDBACK
            )
        )

        val manifest = formatMemoryManifest(headers)
        assertTrue(manifest.contains("arch.md"))
        assertTrue(manifest.contains("user"))
        assertTrue(manifest.contains("System architecture decisions"))
        assertTrue(manifest.contains("bugs.md"))
        assertTrue(manifest.contains("feedback"))
        assertTrue(manifest.contains("Known bugs"))
    }

    @Test
    fun `formatMemoryManifest handles empty list`() {
        val manifest = formatMemoryManifest(emptyList())
        assertEquals("", manifest)
    }
}
