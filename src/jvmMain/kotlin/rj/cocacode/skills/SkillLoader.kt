package rj.cocacode.skills

import java.io.File

data class Skill(
    val name: String,
    val description: String,
    val command: String,
    val prompt: String,
    val source: SkillSource = SkillSource.BUNDLED,
    val metadata: Map<String, String> = emptyMap()
)

enum class SkillSource {
    BUNDLED, USER, PLUGIN, REMOTE
}

object SkillLoader {
    private val skills = mutableMapOf<String, Skill>()
    private val skillDirs = mutableListOf<String>()
    
    fun addSkillDir(path: String) {
        skillDirs.add(path)
    }
    
    fun loadSkills(): List<Skill> {
        skills.clear()
        
        for (dir in skillDirs) {
            loadSkillsFromDir(dir)
        }
        
        return skills.values.toList()
    }
    
    private fun loadSkillsFromDir(dir: String) {
        val skillsDir = File(dir)
        if (!skillsDir.exists()) return
        
        skillsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                parseSkillFromDir(file)?.let { skill ->
                    skills[skill.name] = skill
                }
            }
        }
    }
    
    private fun parseSkillFromDir(dir: File): Skill? {
        val manifestFile = File(dir, "skill.md")
        if (!manifestFile.exists()) return null
        
        return try {
            val content = manifestFile.readText()
            parseSkillManifest(dir.name, content)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseSkillManifest(name: String, content: String): Skill {
        val lines = content.lines()
        var command = ""
        var description = name
        
        for (line in lines) {
            if (line.startsWith("## Command:")) {
                command = line.substringAfter("## Command:").trim()
            }
            if (line.startsWith("## Description:")) {
                description = line.substringAfter("## Description:").trim()
            }
        }
        
        return Skill(
            name = name,
            description = description,
            command = command,
            prompt = content
        )
    }
    
    fun getSkill(name: String): Skill? = skills[name]
    
    fun getAllSkills(): List<Skill> = skills.values.toList()
    
    fun searchSkills(query: String): List<Skill> {
        val lowerQuery = query.lowercase()
        return skills.values.filter { 
            it.name.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery)
        }
    }
    
    fun registerSkill(skill: Skill) {
        skills[skill.name] = skill
    }
    
    fun unregisterSkill(name: String) {
        skills.remove(name)
    }
}

object SkillExecutor {
    suspend fun execute(skill: Skill, args: Map<String, String> = emptyMap()): String {
        val prompt = substituteArguments(skill.prompt, args)
        return prompt
    }
    
    private fun substituteArguments(template: String, args: Map<String, String>): String {
        var result = template
        for ((key, value) in args) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}