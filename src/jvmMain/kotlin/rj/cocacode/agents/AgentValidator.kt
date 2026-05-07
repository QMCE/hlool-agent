package rj.cocacode.agents

import rj.cocacode.utils.Logger

/**
 * Agent validation utilities.
 * Mirrors validateAgent.ts from the reference implementation.
 */
object AgentValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    /**
     * Validate an agent definition for correctness.
     */
    fun validate(definition: AgentDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Required fields
        if (definition.agentType.isBlank()) {
            errors.add("agentType must not be blank")
        }

        if (definition.systemPrompt.isBlank() && definition.getSystemPrompt == null) {
            errors.add("Either systemPrompt or getSystemPrompt must be provided")
        }

        // Check agent type format
        if (definition.agentType.isNotBlank()) {
            if (!definition.agentType.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                errors.add("agentType must only contain alphanumeric, hyphens, and underscores: '${definition.agentType}'")
            }
        }

        // Max turns
        if (definition.maxTurns < 1) {
            errors.add("maxTurns must be >= 1")
        }

        // Temperature
        if (definition.temperature != null) {
            if (definition.temperature < 0f || definition.temperature > 2f) {
                warnings.add("temperature should be between 0 and 2, got ${definition.temperature}")
            }
        }

        // Permission mode
        if (definition.permissionMode != null) {
            val validModes = setOf("bubble", "acceptEdits", "plan", "dontAsk", "")
            if (definition.permissionMode !in validModes) {
                warnings.add("Unknown permissionMode '${definition.permissionMode}'. Valid values: ${validModes.filter { it.isNotEmpty() }}")
            }
        }

        // Memory scope
        val validMemoryScopes = setOf("none", "user", "project", "local", "global")
        if (definition.memoryScope !in validMemoryScopes) {
            warnings.add("Unknown memoryScope '${definition.memoryScope}'. Valid values: $validMemoryScopes")
        }

        // Allowed tools validation
        if (definition.allowedTools.isEmpty()) {
            errors.add("allowedTools must not be empty (use [\"*\"] for all tools)")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Check if a tool is allowed for this agent definition.
     */
    fun isToolAllowed(definition: AgentDefinition, toolName: String): Boolean {
        // Check disallowed first
        if (toolName in definition.disallowedTools) return false

        // If allowed is ["*"], all tools are allowed (except disallowed)
        if (definition.allowedTools.size == 1 && definition.allowedTools[0] == "*") return true

        // Check exact match
        return toolName in definition.allowedTools
    }

    /**
     * Get the effective tools for an agent, filtering out disallowed and ensuring
     * only allowed tools are available.
     */
    fun getEffectiveTools(
        definition: AgentDefinition,
        allTools: Set<String>
    ): Set<String> {
        if (definition.allowedTools.size == 1 && definition.allowedTools[0] == "*") {
            return allTools - definition.disallowedTools.toSet()
        }

        val allowed = definition.allowedTools.toSet()
        val disallowed = definition.disallowedTools.toSet()

        return allowed.intersect(allTools) - disallowed
    }
}
