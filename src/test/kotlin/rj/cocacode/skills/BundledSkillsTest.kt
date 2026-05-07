package rj.cocacode.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledSkillsTest {

    @Test
    fun `registerAll populates all built-in skills`() {
        BundledSkills.registerAll()
        val allSkills = SkillLoader.getAllSkills()
        assertTrue(allSkills.size >= 20) // at least 20 built-in skills
    }

    @Test
    fun `agent skill is registered`() {
        BundledSkills.registerAll()
        val skill = SkillLoader.getSkill("agent")
        assertEquals("agent", skill?.name)
        assertTrue(skill?.description?.isNotBlank() == true)
    }

    @Test
    fun `subagent skill is registered with correct category`() {
        BundledSkills.registerAll()
        val skill = SkillLoader.getSkill("subagent")
        assertEquals("subagent", skill?.name)
        assertEquals("agent", skill?.metadata?.get("category"))
    }

    @Test
    fun `delegate skill is registered`() {
        BundledSkills.registerAll()
        val skill = SkillLoader.getSkill("delegate")
        assertEquals("delegate", skill?.name)
        assertTrue(skill?.prompt?.isNotBlank() == true)
    }

    @Test
    fun `commit skill is registered`() {
        BundledSkills.registerAll()
        val commit = SkillLoader.getSkill("commit")
        assertEquals("commit", commit?.name)
    }

    @Test
    fun `searchSkills finds skills by description`() {
        BundledSkills.registerAll()
        val results = SkillLoader.searchSkills("code")
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `skill has valid prompt text`() {
        BundledSkills.registerAll()
        for (skill in SkillLoader.getAllSkills()) {
            assertTrue(skill.prompt.isNotBlank(), "Skill '${skill.name}' has blank prompt")
        }
    }
}
