package rj.cocacode.agents

import rj.cocacode.utils.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * Memory scopes for agent memory persistence.
 * Mirrors AgentMemoryScope from agentMemory.ts.
 */
enum class AgentMemoryScope(val key: String) {
    NONE("none"),
    USER("user"),
    PROJECT("project"),
    LOCAL("local");

    companion object {
        fun fromKey(key: String): AgentMemoryScope {
            return entries.firstOrNull { it.key == key } ?: NONE
        }
    }
}

/**
 * Loaded memory data for an agent type.
 */
data class AgentMemory(
    val agentType: String,
    val scope: AgentMemoryScope,
    val content: String = "",
    val filePath: String? = null,
    val isLoaded: Boolean = false
)

/**
 * Manages persistent agent memories stored on disk.
 * Mirrors agentMemory.ts from the reference implementation.
 *
 * Memories are stored as text files in scope-specific directories:
 * - USER: ~/.cocacode/memories/<agentType>.md
 * - PROJECT: <project>/.cocacode/memories/<agentType>.md
 * - LOCAL: <project>/.cocacode/local-memories/<agentType>.md
 */
class AgentMemoryManager {

    companion object {
        private const val MEMORIES_DIR = "memories"
        private const val LOCAL_MEMORIES_DIR = "local-memories"
    }

    /**
     * Get the memory directory for a given scope.
     */
    fun getMemoryDir(scope: AgentMemoryScope, workingDir: String = "."): Path {
        return when (scope) {
            AgentMemoryScope.USER -> {
                val homeDir = System.getProperty("user.home") ?: "."
                Paths.get(homeDir, ".cocacode", MEMORIES_DIR)
            }
            AgentMemoryScope.PROJECT -> {
                Paths.get(workingDir, ".cocacode", MEMORIES_DIR)
            }
            AgentMemoryScope.LOCAL -> {
                Paths.get(workingDir, ".cocacode", LOCAL_MEMORIES_DIR)
            }
            AgentMemoryScope.NONE -> {
                Paths.get(workingDir, ".cocacode", MEMORIES_DIR)
            }
        }
    }

    /**
     * Ensure the memory directory exists.
     */
    fun ensureMemoryDir(scope: AgentMemoryScope, workingDir: String = "."): Path {
        val dir = getMemoryDir(scope, workingDir)
        dir.createDirectories()
        return dir
    }

    /**
     * Load memory for a specific agent type from disk.
     * Returns AgentMemory with isLoaded = false if no memory file exists.
     */
    fun loadMemory(agentType: String, scope: AgentMemoryScope, workingDir: String = "."): AgentMemory {
        if (scope == AgentMemoryScope.NONE) {
            return AgentMemory(agentType = agentType, scope = scope, isLoaded = false)
        }

        val memDir = getMemoryDir(scope, workingDir)
        val memFile = memDir.resolve("${agentType}.md")

        if (memFile.exists()) {
            return try {
                val content = memFile.readText()
                AgentMemory(
                    agentType = agentType,
                    scope = scope,
                    content = content,
                    filePath = memFile.toString(),
                    isLoaded = true
                )
            } catch (e: Exception) {
                Logger.warn("Failed to read agent memory for '$agentType': ${e.message}")
                AgentMemory(agentType = agentType, scope = scope, isLoaded = false)
            }
        }

        return AgentMemory(agentType = agentType, scope = scope, isLoaded = false)
    }

    /**
     * Save memory content for an agent type.
     */
    fun saveMemory(agentType: String, scope: AgentMemoryScope, content: String, workingDir: String = "."): Boolean {
        if (scope == AgentMemoryScope.NONE) return false

        return try {
            val memDir = ensureMemoryDir(scope, workingDir)
            val memFile = memDir.resolve("${agentType}.md")
            memFile.writeText(content)
            Logger.info("Saved agent memory for '$agentType' (scope: ${scope.key})")
            true
        } catch (e: Exception) {
            Logger.warn("Failed to save agent memory for '$agentType': ${e.message}")
            false
        }
    }

    /**
     * Delete memory for an agent type.
     */
    fun deleteMemory(agentType: String, scope: AgentMemoryScope, workingDir: String = "."): Boolean {
        if (scope == AgentMemoryScope.NONE) return false

        return try {
            val memDir = getMemoryDir(scope, workingDir)
            val memFile = memDir.resolve("${agentType}.md")
            if (memFile.exists()) {
                memFile.deleteIfExists()
                Logger.info("Deleted agent memory for '$agentType' (scope: ${scope.key})")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.warn("Failed to delete agent memory for '$agentType': ${e.message}")
            false
        }
    }

    /**
     * List all agent types that have saved memories in a scope.
     */
    fun listMemories(scope: AgentMemoryScope, workingDir: String = "."): List<String> {
        if (scope == AgentMemoryScope.NONE) return emptyList()

        val memDir = getMemoryDir(scope, workingDir)
        if (!memDir.exists()) return emptyList()

        return memDir.listDirectoryEntries("*.md").map { it.nameWithoutExtension }
    }

    /**
     * Build a memory prompt section for inclusion in the system prompt.
     * Returns empty string if no memory is loaded.
     */
    fun buildMemoryPrompt(memory: AgentMemory): String {
        if (!memory.isLoaded || memory.content.isBlank()) return ""

        return """
<agent-memory type="${memory.agentType}">
${memory.content.trim()}
</agent-memory>
""".trimIndent()
    }
}
