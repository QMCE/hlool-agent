package rj.cocacode.memdir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryAgeTest {

    @Test
    fun `memoryAge returns today for memories just created`() {
        val now = System.currentTimeMillis()
        val age = memoryAge(now - 1000) // 1 second ago
        assertEquals("today", age)
    }

    @Test
    fun `memoryAge returns today for recent memories`() {
        val now = System.currentTimeMillis()
        val age = memoryAge(now - 120_000) // 2 minutes ago
        assertEquals("today", age)
    }

    @Test
    fun `memoryAge returns today for hours-old memories`() {
        val now = System.currentTimeMillis()
        val age = memoryAge(now - 7_200_000) // 2 hours ago
        assertEquals("today", age)
    }

    @Test
    fun `memoryAge returns yesterday for one-day-old memories`() {
        val now = System.currentTimeMillis()
        val age = memoryAge(now - 86_400_000) // exactly 1 day
        assertEquals("yesterday", age)
    }

    @Test
    fun `memoryAge returns days ago for older memories`() {
        val now = System.currentTimeMillis()
        val age = memoryAge(now - 172_800_000) // 2 days ago
        assertEquals("2 days ago", age)
    }

    @Test
    fun `memoryFreshnessNote returns empty for recent memories`() {
        val now = System.currentTimeMillis()
        val note = memoryFreshnessNote(now - 300_000) // 5 minutes ago
        assertTrue(note.isEmpty())
    }

    @Test
    fun `memoryFreshnessNote returns non-empty for old memories`() {
        val now = System.currentTimeMillis()
        val note = memoryFreshnessNote(now - 30L * 86_400_000) // 30 days
        assertTrue(note.isNotBlank())
    }
}
