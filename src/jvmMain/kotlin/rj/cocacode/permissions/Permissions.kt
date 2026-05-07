package rj.cocacode.permissions

/**
 * Permission System Module
 * 
 * Provides permission checking, rules management, and mode handling
 * for the cocacode tool system.
 */
object Permissions {
    fun createContext(
        mode: PermissionMode = PermissionMode.DEFAULT,
        allowRules: ToolPermissionRulesBySource = emptyMap(),
        denyRules: ToolPermissionRulesBySource = emptyMap(),
        askRules: ToolPermissionRulesBySource = emptyMap()
    ): ToolPermissionContext {
        return ToolPermissionContext(
            mode = mode,
            alwaysAllowRules = allowRules,
            alwaysDenyRules = denyRules,
            alwaysAskRules = askRules
        )
    }

    fun createChecker(context: ToolPermissionContext): PermissionChecker {
        return PermissionChecker(context)
    }

    fun parseRule(ruleString: String): PermissionRuleValue {
        return PermissionRuleValue.fromString(ruleString)
    }

    fun createRule(
        source: PermissionRuleSource,
        behavior: PermissionBehavior,
        toolName: String,
        content: String? = null
    ): PermissionRule {
        return PermissionRule(source, behavior, PermissionRuleValue(toolName, content))
    }
}
