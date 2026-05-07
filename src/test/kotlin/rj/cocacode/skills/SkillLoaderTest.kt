package rj.cocacode.skills

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class SkillLoaderTest {

    @Test
    fun `registerSkill adds to registry`() = runTest {
        SkillLoader.registerSkill(
            Skill(
                name = "test-skill",
                description = "A test",
                command = "/test",
                prompt = "Test prompt"
            )
        )
        val skill = SkillLoader.getSkill("test-skill")
        assertEquals("test-skill", skill?.name)
        SkillLoader.unregisterSkill("test-skill")
    }

    @Test
    fun `unregisterSkill removes from registry`() = runTest {
        SkillLoader.registerSkill(
            Skill(
                name = "to-remove",
                description = "Will be removed",
                command = "/remove",
                prompt = "Bye"
            )
        )
        SkillLoader.unregisterSkill("to-remove")
        assertNull(SkillLoader.getSkill("to-remove"))
    }

    @Test
    fun `getSkill returns null for unknown`() {
        assertNull(SkillLoader.getSkill("nonexistent-skill-xyz"))
    }

    @Test
    fun `loadSkills returns empty list when no dirs registered`() {
        // Add a nonexistent dir and load — should return empty
        SkillLoader.addSkillDir("nonexistent-dir-xyz")
        val loaded = SkillLoader.loadSkills()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `loadSkills from directory loads skill files`() {
        val tempDir = java.nio.file.Files.createTempDirectory("skilltest").toFile()
        try {
            val skillDir = File(tempDir, "my-custom-skill")
            skillDir.mkdir()
            File(skillDir, "skill.md").writeText(
                """
                ## Command: /custom
                ## Description: A custom skill from disk
                
                This is the prompt template for my custom skill.
                """.trimIndent()
            )

            SkillLoader.addSkillDir(tempDir.absolutePath)
            val loaded = SkillLoader.loadSkills()
            val custom = loaded.find { it.name == "my-custom-skill" }
            assertEquals("my-custom-skill", custom?.name)
            assertEquals("/custom", custom?.command)
            assertEquals("A custom skill from disk", custom?.description)
            assertEquals(SkillSource.BUNDLED, custom?.source)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `SkillExecutor substitutes template arguments`() = runTest {
        val skill = Skill(
            name = "greet",
            description = "Says hello",
            command = "/greet",
            prompt = "Hello {{name}}, welcome to {{place}}!"
        )
        val result = SkillExecutor.execute(
            skill,
            mapOf("name" to "Alice", "place" to "Wonderland")
        )
        assertEquals("Hello Alice, welcome to Wonderland!", result)
    }

    @Test
    fun `SkillExecutor handles missing arguments gracefully`() = runTest {
        val skill = Skill(
            name = "simple",
            description = "Simple",
            command = "/simple",
            prompt = "No template vars here"
        )
        val result = SkillExecutor.execute(skill)
        assertEquals("No template vars here", result)
    }
}
