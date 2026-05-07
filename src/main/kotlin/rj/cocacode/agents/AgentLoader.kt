package rj.cocacode.agents

import rj.cocacode.utils.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

/**
 * Loader for agent definitions.
 * Mirrors loadAgentsDir.ts and builtInAgents.ts from the reference implementation.
 *
 * Supports:
 * - Built-in agents (registered programmatically)
 * - Custom agents loaded from disk (markdown frontmatter, JSON)
 * - Agent caching
 * - MCP server requirement filtering
 */
class AgentLoader {

    private val builtInAgents: MutableMap<String, AgentDefinition> = LinkedHashMap()
    private val customAgents: MutableMap<String, AgentDefinition> = LinkedHashMap()
    private var customAgentsDir: Path? = null
    private var lastLoadTime: Long = 0L
    private val cacheDurationMs = 30_000L // 30 second cache

    init {
        registerBuiltInAgents()
    }

    // ============================================================
    // Built-in agent registration
    // ============================================================

    private fun registerBuiltInAgents() {
        // --- General Purpose Agent ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "general-purpose",
                whenToUse = "General-purpose coding assistant with access to all tools. Use when no specialized agent type is specified.",
                systemPrompt = """
You are a general-purpose coding agent in Claude Code. You have access to all tools.

Your goal is to help the user with their coding tasks. Use the available tools to:
- Read, search, and understand code
- Edit and create files
- Run terminal commands
- Search the web for documentation

Always be thorough in your analysis and precise in your changes.
""".trimIndent(),
                allowedTools = listOf("*"),
                maxTurns = 25,
                temperature = 0.3f
            )
        )

        // --- Code Reviewer Agent ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "code-reviewer",
                whenToUse = "Code review and analysis. When you need a thorough code review with detailed feedback on code quality, potential bugs, security issues, and architectural patterns.",
                systemPrompt = """
You are an expert code reviewer. Analyze code carefully for:
- Logic errors and bugs
- Security vulnerabilities
- Performance issues
- Code style and maintainability
- Architectural patterns and design

Provide constructive, actionable feedback. Focus on the most impactful issues.
""".trimIndent(),
                allowedTools = listOf("Read", "Grep", "Glob", "Search", "ListFiles", "Bash"),
                maxTurns = 15,
                temperature = 0.2f
            )
        )

        // --- Test Runner Agent ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "test-runner",
                whenToUse = "Running and debugging tests. When you need to run tests, analyze test failures, or improve test coverage.",
                systemPrompt = """
You are a testing specialist. Your focus:
- Run tests and interpret results
- Debug test failures
- Identify missing test coverage
- Generate test cases

Always run the full test suite first, then dig into specific failing tests.
""".trimIndent(),
                allowedTools = listOf("Bash", "Read", "Grep", "Glob", "Search", "ListFiles", "Write", "Edit"),
                maxTurns = 20,
                temperature = 0.2f
            )
        )

        // --- Code Explorer Agent ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "code-explorer",
                whenToUse = "Exploring and understanding unfamiliar codebases. When you need to understand architecture, find relevant files, or navigate a new project.",
                systemPrompt = """
You are a code exploration expert. Your task is to understand and navigate codebases.
- Start by examining the project structure
- Look at entry points, configuration files, and key modules
- Trace the flow of important features
- Document your findings clearly

Provide a comprehensive summary of what you find.
""".trimIndent(),
                allowedTools = listOf("Read", "Grep", "Glob", "Search", "ListFiles", "Bash"),
                maxTurns = 20,
                temperature = 0.3f
            )
        )

        // --- Explore Agent (from reference) ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "explore",
                whenToUse = "Explore the codebase to understand its structure and find relevant code. Use when you need to understand how something works or find specific files.",
                systemPrompt = """
You are an exploration agent. Your job is to navigate and understand codebases efficiently.

When exploring:
1. Start broad - look at project structure and key files
2. Follow relevant imports and references
3. Search for specific patterns when needed
4. Report findings clearly with file paths and line numbers

Be thorough but efficient. Don't explore paths that aren't relevant to the task.
""".trimIndent(),
                allowedTools = listOf("Read", "Grep", "Glob", "Search", "ListFiles", "Bash"),
                maxTurns = 15,
                temperature = 0.2f
            )
        )

        // --- Plan Agent (from reference) ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "plan",
                whenToUse = "Plan complex changes before implementing them. Use when a task requires careful thought about design, architecture, or multi-step implementation.",
                systemPrompt = """
You are a planning agent. Your job is to analyze requirements and create a detailed implementation plan.

When planning:
1. Understand the current codebase structure
2. Identify all files that need to be changed
3. Design the approach considering:
   - Existing architecture and patterns
   - Dependencies between changes
   - Potential risks or edge cases
4. Provide a step-by-step plan with concrete file paths and code snippets

The plan should be detailed enough that another agent could execute it.
Do NOT make any changes yourself - only plan.
""".trimIndent(),
                allowedTools = listOf("Read", "Grep", "Glob", "Search", "ListFiles"),
                maxTurns = 10,
                temperature = 0.3f,
                permissionMode = "plan"
            )
        )

        // --- Verification Agent (from reference) ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "verification",
                whenToUse = "Verify that changes work correctly. Run after making changes to ensure nothing is broken.",
                systemPrompt = """
You are a verification agent. Your job is to verify that code changes are correct and complete.

When verifying:
1. Check that all modified files compile/build successfully
2. Run relevant tests
3. Verify the changes meet the requirements
4. Report any issues found

Be thorough and precise. If verification fails, provide clear error information.
""".trimIndent(),
                allowedTools = listOf("Bash", "Read", "Grep", "Glob", "Search"),
                maxTurns = 8,
                temperature = 0.1f
            )
        )

        // --- Statusline Setup Agent (from reference) ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "statusline-setup",
                whenToUse = "Setting up or configuring statusline integrations and UI components.",
                systemPrompt = """
You are a statusline setup agent. Help configure statusline displays and UI integrations.

Focus on:
- Reading existing configuration
- Setting up statusline plugins or components
- Testing that statusline displays correctly
- Troubleshooting display issues
""".trimIndent(),
                allowedTools = listOf("Read", "Write", "Edit", "Bash", "Grep", "Glob"),
                maxTurns = 10,
                temperature = 0.2f
            )
        )

        // --- Claude Code Guide Agent (from reference) ---
        registerBuiltIn(
            AgentDefinition(
                agentType = "claude-code-guide",
                whenToUse = """Use this agent when the user asks questions about: (1) Claude Code (the CLI tool) - features, hooks, slash commands, MCP servers, settings, IDE integrations, keyboard shortcuts; (2) Claude Agent SDK - building custom agents; (3) Claude API (formerly Anthropic API) - API usage, tool use, SDK usage.""".trimIndent(),
                systemPrompt = """
You are the Claude Code guide agent. Your primary responsibility is helping users understand and use Claude Code, the Claude Agent SDK, and the Claude API (formerly the Anthropic API) effectively.

**Your expertise spans three domains:**

1. **Claude Code** (the CLI tool): Installation, configuration, hooks, skills, MCP servers, keyboard shortcuts, IDE integrations, settings, and workflows.

2. **Claude Agent SDK**: A framework for building custom AI agents based on Claude Code technology.

3. **Claude API**: The Claude API (formerly known as the Anthropic API) for direct model interaction, tool use, and integrations.

**Documentation sources:**

- **Claude Code docs** (https://code.claude.com/docs/en/claude_code_docs_map.md): Fetch this for questions about the Claude Code CLI tool, including installation, setup, hooks, custom skills, MCP server configuration, IDE integrations, settings, keyboard shortcuts, subagents and plugins, sandboxing and security.

- **Claude Agent SDK docs** (https://platform.claude.com/llms.txt): Fetch this for questions about building agents with the SDK, including SDK overview, agent configuration, custom tools, session management, permissions, MCP integration.

- **Claude API docs** (https://platform.claude.com/llms.txt): Fetch this for questions about the Claude API, including Messages API and streaming, Tool use, Vision, PDF support, Extended thinking, structured outputs.

**Approach:**
1. Determine which domain the user's question falls into
2. Use WebFetch to fetch the appropriate docs map
3. Identify the most relevant documentation URLs from the map
4. Fetch the specific documentation pages
5. Provide clear, actionable guidance based on official documentation
6. Use WebSearch if docs don't cover the topic
7. Reference local project files (COCACODE.md, .cocacode/ directory) when relevant

**Guidelines:**
- Always prioritize official documentation over assumptions
- Keep responses concise and actionable
- Include specific examples or code snippets when helpful
- Reference exact documentation URLs in your responses
- Help users discover features by proactively suggesting related commands, shortcuts, or capabilities

Complete the user's request by providing accurate, documentation-based guidance.
""".trimIndent(),
                allowedTools = listOf("Read", "Grep", "Glob", "WebFetch", "WebSearch", "Bash"),
                maxTurns = 15,
                temperature = 0.3f,
                permissionMode = "dontAsk",
                model = "haiku"
            )
        )

        Logger.info("Registered ${builtInAgents.size} built-in agents")
    }

    /**
     * Register a built-in agent definition.
     */
    fun registerBuiltIn(definition: AgentDefinition) {
        builtInAgents[definition.agentType] = definition
    }

    /**
     * Register a custom (disk-loaded) agent definition.
     */
    fun registerCustom(definition: AgentDefinition) {
        customAgents[definition.agentType] = definition
    }

    /**
     * Get an agent definition by type name.
     * Searches custom agents first, then built-in.
     */
    fun getAgent(agentType: String): AgentDefinition? {
        return customAgents[agentType] ?: builtInAgents[agentType]
    }

    /**
     * List all available agent types.
     */
    fun listAgents(includeCustom: Boolean = true): Map<String, AgentDefinition> {
        val result = LinkedHashMap<String, AgentDefinition>()
        result.putAll(builtInAgents)
        if (includeCustom) {
            result.putAll(customAgents)
        }
        return result
    }

    /**
     * Check if an agent type is built-in.
     */
    fun isBuiltIn(agentType: String): Boolean {
        return builtInAgents.containsKey(agentType)
    }

    // ============================================================
    // Disk loading support
    // ============================================================

    /**
     * Set the custom agents directory and load agents from it.
     */
    fun setCustomAgentsDir(dir: String) {
        customAgentsDir = Paths.get(dir)
        loadCustomAgents()
    }

    /**
     * Load custom agents from disk.
     */
    private fun loadCustomAgents() {
        val dir = customAgentsDir ?: return
        if (!dir.exists()) {
            dir.createDirectories()
            Logger.info("Created custom agents directory: $dir")
            return
        }

        val agentsDir = dir
        var loadedCount = 0

        // Load markdown files (*.md) with frontmatter
        val mdFiles = agentsDir.listDirectoryEntries("*.md")
        for (file in mdFiles) {
            try {
                val agentDef = parseMarkdownAgent(file)
                if (agentDef != null) {
                    customAgents[agentDef.agentType] = agentDef
                    loadedCount++
                }
            } catch (e: Exception) {
                Logger.warn("Failed to load agent from ${file.name}: ${e.message}")
            }
        }

        // Load JSON files (*.json)
        val jsonFiles = agentsDir.listDirectoryEntries("*.json")
        for (file in jsonFiles) {
            try {
                val agentDef = parseJsonAgent(file)
                if (agentDef != null) {
                    customAgents[agentDef.agentType] = agentDef
                    loadedCount++
                }
            } catch (e: Exception) {
                Logger.warn("Failed to load agent from ${file.name}: ${e.message}")
            }
        }

        if (loadedCount > 0) {
            Logger.info("Loaded $loadedCount custom agents from $dir")
        }

        lastLoadTime = System.currentTimeMillis()
    }

    /**
     * Parse an agent definition from a markdown file with YAML frontmatter.
     */
    private fun parseMarkdownAgent(file: Path): AgentDefinition? {
        val content = file.readText()

        // Extract YAML frontmatter between --- markers
        val frontmatterPattern = Regex("""^---\s*\n(.*?)\n---\s*\n(.*)""", RegexOption.DOT_MATCHES_ALL)
        val match = frontmatterPattern.find(content) ?: return null

        val yamlBlock = match.groupValues[1]
        val bodyContent = match.groupValues[2].trim()

        // Simple YAML-like parsing (for production, use a proper YAML library)
        val fields = parseSimpleYaml(yamlBlock)

        val agentType = fields["agent_type"] ?: fields["name"] ?: return null

        val allowedTools = parseStringList(fields["tools"]) ?: listOf("*")
        val disallowedTools = parseStringList(fields["disallowed_tools"]) ?: emptyList()
        val maxTurns = fields["max_turns"]?.toIntOrNull() ?: AgentConstants.DEFAULT_MAX_TURNS
        val model = fields["model"]
        val permissionMode = fields["permission_mode"]
        val memoryScope = fields["memory_scope"] ?: "none"
        val isAsync = fields["is_async"]?.toBoolean() ?: false
        val color = fields["color"]

        val systemPrompt = bodyContent.ifBlank {
            fields["system_prompt"] ?: ""
        }

        return AgentDefinition(
            agentType = agentType,
            whenToUse = fields["description"] ?: fields["when_to_use"] ?: "Custom agent loaded from ${file.fileName}",
            systemPrompt = systemPrompt,
            allowedTools = allowedTools,
            disallowedTools = disallowedTools,
            maxTurns = maxTurns,
            model = model,
            permissionMode = permissionMode,
            source = "custom",
            baseDir = file.parent.toString(),
            memoryScope = memoryScope,
            isAsync = isAsync,
            color = color
        )
    }

    /**
     * Parse an agent definition from a JSON file.
     */
    private fun parseJsonAgent(file: Path): AgentDefinition? {
        val content = file.readText()

        // Simple JSON parsing (for production, use kotlinx.serialization or a JSON library)
        // We use a basic approach matching the reference implementation
        val agentType = extractJsonStringField(content, "agent_type")
            ?: extractJsonStringField(content, "name") ?: return null

        return AgentDefinition(
            agentType = agentType,
            whenToUse = extractJsonStringField(content, "description")
                ?: extractJsonStringField(content, "when_to_use")
                ?: "Custom agent loaded from ${file.fileName}",
            systemPrompt = extractJsonStringField(content, "system_prompt") ?: "",
            allowedTools = extractJsonStringList(content, "tools") ?: listOf("*"),
            disallowedTools = extractJsonStringList(content, "disallowed_tools") ?: emptyList(),
            maxTurns = extractJsonIntField(content, "max_turns") ?: AgentConstants.DEFAULT_MAX_TURNS,
            model = extractJsonStringField(content, "model"),
            permissionMode = extractJsonStringField(content, "permission_mode"),
            source = "custom",
            baseDir = file.parent.toString(),
            memoryScope = extractJsonStringField(content, "memory_scope") ?: "none",
            isAsync = extractJsonBooleanField(content, "is_async") ?: false,
            color = extractJsonStringField(content, "color")
        )
    }

    /**
     * Get all agents that don't require MCP servers or whose requirements are met.
     * Uses case-insensitive pattern matching (each required server name must match
     * at least one available server as a substring, case-insensitive).
     * Mirrors hasRequiredMcpServers() and filterAgentsByMcpRequirements() from loadAgentsDir.ts.
     */
    fun getAgentsWithAvailableMcpServers(
        availableMcpServers: Set<String> = emptySet()
    ): Map<String, AgentDefinition> {
        val result = LinkedHashMap<String, AgentDefinition>()

        for ((name, def) in listAgents()) {
            if (hasRequiredMcpServers(def, availableMcpServers)) {
                result[name] = def
            }
        }

        return result
    }

    /**
     * Check if an agent's required MCP servers are all available.
     * Each required pattern must match at least one available server (case-insensitive substring).
     */
    fun hasRequiredMcpServers(agent: AgentDefinition, availableServers: Set<String>): Boolean {
        if (agent.requiredMcpServers.isEmpty()) return true
        return agent.requiredMcpServers.all { pattern ->
            availableServers.any { server ->
                server.lowercase().contains(pattern.lowercase())
            }
        }
    }

    /**
     * Filter agents by MCP server availability, removing agents whose
     * required MCP servers are not all available.
     */
    fun filterAgentsByMcpRequirements(
        agents: Map<String, AgentDefinition>,
        availableServers: Set<String>
    ): Map<String, AgentDefinition> {
        return agents.filterValues { hasRequiredMcpServers(it, availableServers) }
    }

    /**
     * Check if an agent type is built-in.
     * Mirrors isBuiltInAgent() from loadAgentsDir.ts.
     */
    fun isBuiltInAgent(agentType: String): Boolean {
        return builtInAgents.containsKey(agentType)
    }

    /**
     * Check if an agent type is a custom disk-loaded agent.
     * Mirrors isCustomAgent() from loadAgentsDir.ts.
     */
    fun isCustomAgent(agentType: String): Boolean {
        return customAgents.containsKey(agentType)
    }

    /**
     * Clear the custom agent definitions cache and force reload on next access.
     * Mirrors clearAgentDefinitionsCache() from loadAgentsDir.ts.
     */
    fun clearAgentDefinitionsCache() {
        customAgents.clear()
        lastLoadTime = 0L
        Logger.info("Agent definitions cache cleared")
    }

    /**
     * Refresh custom agents from disk if cache is stale.
     */
    fun refreshIfNeeded() {
        val dir = customAgentsDir ?: return
        if (!dir.exists()) return

        val elapsed = System.currentTimeMillis() - lastLoadTime
        if (elapsed > cacheDurationMs) {
            loadCustomAgents()
        }
    }

    // ============================================================
    // Simple parsing helpers
    // ============================================================

    /**
     * Simple YAML-like key-value parsing for frontmatter blocks.
     */
    private fun parseSimpleYaml(block: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = block.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue

            val colonIndex = trimmed.indexOf(':')
            if (colonIndex > 0) {
                val key = trimmed.substring(0, colonIndex).trim()
                val value = trimmed.substring(colonIndex + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                if (key.isNotBlank()) {
                    result[key] = value
                }
            }
        }

        return result
    }

    /**
     * Parse a comma-separated list from a YAML field value.
     */
    private fun parseStringList(value: String?): List<String>? {
        if (value == null) return null
        val trimmed = value.trim()

        // Support [item1, item2, ...] format
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isBlank()) return emptyList()
            return inner.split(",").map { it.trim().removeSurrounding("\"") }
        }

        // Support * wildcard
        if (trimmed == "*" || trimmed == "['*']" || trimmed == "[\"*\"]") {
            return listOf("*")
        }

        // Fall back to comma-separated
        return trimmed.split(",").map { it.trim() }
    }

    /**
     * Extract a string field from a JSON-like string.
     */
    private fun extractJsonStringField(json: String, field: String): String? {
        val pattern = Regex("""["']$field["']\s*:\s*["']([^"']+)["']""")
        return pattern.find(json)?.groupValues?.get(1)?.trim()
    }

    /**
     * Extract a string list field from a JSON-like string.
     */
    private fun extractJsonStringList(json: String, field: String): List<String>? {
        val pattern = Regex("""["']$field["']\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(json) ?: return null
        val inner = match.groupValues[1].trim()

        if (inner.isBlank()) return emptyList()

        // Check for [*]
        if (inner.trim() == "*" || inner.trim() == "\"*\"" || inner.trim() == "'*'") {
            return listOf("*")
        }

        return inner.split(",").map {
            it.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        }.filter { it.isNotBlank() }
    }

    /**
     * Extract an integer field from a JSON-like string.
     */
    private fun extractJsonIntField(json: String, field: String): Int? {
        val pattern = Regex("""["']$field["']\s*:\s*(\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Extract a boolean field from a JSON-like string.
     */
    private fun extractJsonBooleanField(json: String, field: String): Boolean? {
        val pattern = Regex("""["']$field["']\s*:\s*(true|false)""")
        return pattern.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }
}
