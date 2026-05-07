package rj.cocacode.permissions

/**
 * Permission behavior types - what action to take for a permission request
 */
enum class PermissionBehavior {
    ALLOW,
    DENY,
    ASK
}

/**
 * Where a permission rule originated from
 */
enum class PermissionRuleSource {
    USER_SETTINGS,
    PROJECT_SETTINGS,
    LOCAL_SETTINGS,
    FLAG_SETTINGS,
    POLICY_SETTINGS,
    CLI_ARG,
    COMMAND,
    SESSION;

    companion object {
        fun fromString(str: String): PermissionRuleSource {
            return when (str.lowercase()) {
                "usersettings", "user_settings" -> USER_SETTINGS
                "projectsettings", "project_settings" -> PROJECT_SETTINGS
                "localsettings", "local_settings" -> LOCAL_SETTINGS
                "flagsettings", "flag_settings" -> FLAG_SETTINGS
                "policysettings", "policy_settings" -> POLICY_SETTINGS
                "cliarg", "cli_arg" -> CLI_ARG
                "command" -> COMMAND
                "session" -> SESSION
                else -> USER_SETTINGS
            }
        }

        fun toDisplayString(source: PermissionRuleSource): String {
            return when (source) {
                USER_SETTINGS -> "user settings"
                PROJECT_SETTINGS -> "project settings"
                LOCAL_SETTINGS -> "local settings"
                FLAG_SETTINGS -> "flag settings"
                POLICY_SETTINGS -> "policy settings"
                CLI_ARG -> "CLI argument"
                COMMAND -> "command"
                SESSION -> "session"
            }
        }
    }
}

/**
 * The value of a permission rule - specifies which tool and optional content
 */
data class PermissionRuleValue(
    val toolName: String,
    val ruleContent: String? = null
) {
    companion object {
        /**
         * Parse a rule string into PermissionRuleValue.
         * Format: "ToolName" or "ToolName(content)"
         */
        fun fromString(ruleString: String): PermissionRuleValue {
            val match = Regex("^([^()]+)(?:\\(([^)]*)\\))?$").find(ruleString.trim())
            return if (match != null) {
                val (toolName, ruleContent) = match.destructured
                PermissionRuleValue(
                    toolName = toolName.trim(),
                    ruleContent = ruleContent.takeIf { it.isNotEmpty() }
                )
            } else {
                PermissionRuleValue(toolName = ruleString.trim())
            }
        }
    }

    /**
     * Convert back to string format for display
     */
    fun toDisplayString(): String {
        return if (ruleContent != null) {
            "$toolName($ruleContent)"
        } else {
            toolName
        }
    }
}

/**
 * A permission rule with its source and behavior
 */
data class PermissionRule(
    val source: PermissionRuleSource,
    val ruleBehavior: PermissionBehavior,
    val ruleValue: PermissionRuleValue
) {
    /**
     * Create a rule from string components
     */
    companion object {
        fun create(
            source: PermissionRuleSource,
            behavior: PermissionBehavior,
            ruleString: String
        ): PermissionRule {
            return PermissionRule(
                source = source,
                ruleBehavior = behavior,
                ruleValue = PermissionRuleValue.fromString(ruleString)
            )
        }

        /**
         * Create allow rule
         */
        fun allow(source: PermissionRuleSource, toolName: String, content: String? = null): PermissionRule {
            return PermissionRule(
                source = source,
                ruleBehavior = PermissionBehavior.ALLOW,
                ruleValue = PermissionRuleValue(toolName, content)
            )
        }

        /**
         * Create deny rule
         */
        fun deny(source: PermissionRuleSource, toolName: String, content: String? = null): PermissionRule {
            return PermissionRule(
                source = source,
                ruleBehavior = PermissionBehavior.DENY,
                ruleValue = PermissionRuleValue(toolName, content)
            )
        }

        /**
         * Create ask rule
         */
        fun ask(source: PermissionRuleSource, toolName: String, content: String? = null): PermissionRule {
            return PermissionRule(
                source = source,
                ruleBehavior = PermissionBehavior.ASK,
                ruleValue = PermissionRuleValue(toolName, content)
            )
        }
    }
}

/**
 * Mapping of permission rules by their source
 */
typealias ToolPermissionRulesBySource = Map<PermissionRuleSource, List<String>>

/**
 * Additional working directory
 */
data class AdditionalWorkingDirectory(
    val path: String,
    val source: PermissionRuleSource
)

/**
 * Context needed for permission checking in tools
 */
data class ToolPermissionContext(
    val mode: PermissionMode = PermissionMode.DEFAULT,
    val additionalWorkingDirectories: Map<String, AdditionalWorkingDirectory> = emptyMap(),
    val alwaysAllowRules: ToolPermissionRulesBySource = emptyMap(),
    val alwaysDenyRules: ToolPermissionRulesBySource = emptyMap(),
    val alwaysAskRules: ToolPermissionRulesBySource = emptyMap(),
    val isBypassPermissionsModeAvailable: Boolean = false,
    val strippedDangerousRules: ToolPermissionRulesBySource? = null,
    val shouldAvoidPermissionPrompts: Boolean = false,
    val awaitAutomatedChecksBeforeDialog: Boolean = false,
    val prePlanMode: PermissionMode? = null
) {
    /**
     * Get all allow rules from all sources
     */
    fun getAllowRules(): List<PermissionRule> {
        return PERMISSION_RULE_SOURCES.flatMap { source ->
            (alwaysAllowRules[source] ?: emptyList()).map { ruleString ->
                PermissionRule.create(source, PermissionBehavior.ALLOW, ruleString)
            }
        }
    }

    /**
     * Get all deny rules from all sources
     */
    fun getDenyRules(): List<PermissionRule> {
        return PERMISSION_RULE_SOURCES.flatMap { source ->
            (alwaysDenyRules[source] ?: emptyList()).map { ruleString ->
                PermissionRule.create(source, PermissionBehavior.DENY, ruleString)
            }
        }
    }

    /**
     * Get all ask rules from all sources
     */
    fun getAskRules(): List<PermissionRule> {
        return PERMISSION_RULE_SOURCES.flatMap { source ->
            (alwaysAskRules[source] ?: emptyList()).map { ruleString ->
                PermissionRule.create(source, PermissionBehavior.ASK, ruleString)
            }
        }
    }

    /**
     * Check if the entire tool matches an allow rule
     */
    fun getToolAlwaysAllowedRule(toolName: String): PermissionRule? {
        return getAllowRules().find { rule -> toolMatchesRule(toolName, rule) }
    }

    /**
     * Check if the entire tool matches a deny rule
     */
    fun getDenyRuleForTool(toolName: String): PermissionRule? {
        return getDenyRules().find { rule -> toolMatchesRule(toolName, rule) }
    }

    /**
     * Check if the entire tool matches an ask rule
     */
    fun getAskRuleForTool(toolName: String): PermissionRule? {
        return getAskRules().find { rule -> toolMatchesRule(toolName, rule) }
    }

    companion object {
        private val PERMISSION_RULE_SOURCES = listOf(
            PermissionRuleSource.USER_SETTINGS,
            PermissionRuleSource.PROJECT_SETTINGS,
            PermissionRuleSource.LOCAL_SETTINGS,
            PermissionRuleSource.FLAG_SETTINGS,
            PermissionRuleSource.POLICY_SETTINGS,
            PermissionRuleSource.CLI_ARG,
            PermissionRuleSource.COMMAND,
            PermissionRuleSource.SESSION
        )

        /**
         * Check if a tool name matches a rule.
         * The rule must not have content to match the entire tool.
         */
        private fun toolMatchesRule(toolName: String, rule: PermissionRule): Boolean {
            if (rule.ruleValue.ruleContent != null) {
                return false
            }
            return rule.ruleValue.toolName == toolName
        }
    }
}
