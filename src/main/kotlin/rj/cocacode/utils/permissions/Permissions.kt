package rj.cocacode.utils.permissions

import rj.cocacode.state.AppStateManager

enum class PermissionMode {
    DEFAULT,
    BYPASS_PERMISSIONS,
    PLAN,
    AUTO,
    DONT_ASK
}

enum class PermissionDecision {
    ALLOW,
    DENY,
    ASK
}

sealed class PermissionResult {
    object Allow : PermissionResult()
    object Deny : PermissionResult()
    data class Ask(val toolName: String, val details: String) : PermissionResult()
}

data class PermissionRule(
    val toolPattern: String,
    val decision: PermissionDecision,
    val source: String = "default"
)

object PermissionManager {
    private val rules = mutableListOf<PermissionRule>()
    private val pendingRequests = mutableMapOf<String, PermissionResult>()
    
    fun setMode(mode: PermissionMode) {
        AppStateManager.updateState { it.copy(permissionMode = rj.cocacode.state.PermissionMode.valueOf(mode.name)) }
    }
    
    fun getMode(): PermissionMode {
        val state = AppStateManager.getState()
        @Suppress("ALWAYS_TRUE")
        return when (val mode = state.permissionMode) {
            rj.cocacode.state.PermissionMode.DEFAULT -> PermissionMode.DEFAULT
            rj.cocacode.state.PermissionMode.BYPASS_PERMISSIONS -> PermissionMode.BYPASS_PERMISSIONS
            rj.cocacode.state.PermissionMode.PLAN -> PermissionMode.PLAN
            rj.cocacode.state.PermissionMode.AUTO -> PermissionMode.AUTO
            rj.cocacode.state.PermissionMode.DONT_ASK -> PermissionMode.DONT_ASK
        }
    }
    
    fun checkPermission(toolName: String): PermissionResult {
        val mode = getMode()
        
        return when (mode) {
            PermissionMode.BYPASS_PERMISSIONS -> PermissionResult.Allow
            PermissionMode.PLAN -> PermissionResult.Ask(toolName, "Plan mode: approval required")
            PermissionMode.AUTO -> {
                val rule = rules.find { toolName.matches(Regex(it.toolPattern)) }
                when (rule?.decision) {
                    PermissionDecision.ALLOW -> PermissionResult.Allow
                    PermissionDecision.DENY -> PermissionResult.Deny
                    else -> PermissionResult.Ask(toolName, "Auto mode decision pending")
                }
            }
            PermissionMode.DONT_ASK -> {
                val rule = rules.find { toolName.matches(Regex(it.toolPattern)) }
                when (rule?.decision) {
                    PermissionDecision.ALLOW -> PermissionResult.Allow
                    else -> PermissionResult.Deny
                }
            }
            PermissionMode.DEFAULT -> PermissionResult.Ask(toolName, "Permission required")
        }
    }
    
    fun addRule(rule: PermissionRule) {
        rules.add(rule)
    }
    
    fun removeRule(toolPattern: String) {
        rules.removeAll { it.toolPattern == toolPattern }
    }
    
    fun clearRules() {
        rules.clear()
    }
    
    fun allowTool(toolName: String) {
        addRule(PermissionRule(toolName, PermissionDecision.ALLOW, "user"))
    }
    
    fun denyTool(toolName: String) {
        addRule(PermissionRule(toolName, PermissionDecision.DENY, "user"))
    }
    
    fun isToolAllowed(toolName: String): Boolean {
        return checkPermission(toolName) == PermissionResult.Allow
    }
}