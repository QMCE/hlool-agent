package rj.cocacode.permissions

import rj.cocacode.state.AppStateManager
import rj.cocacode.state.PermissionMode as AppPermissionMode

/**
 * Decides whether a tool may run, and whether to ask the user.
 */
object PermissionGate {

    private val alwaysAllowSession = mutableSetOf<String>()

    fun resetSessionAllows() {
        alwaysAllowSession.clear()
    }

    fun rememberAllow(toolName: String) {
        alwaysAllowSession.add(toolName)
    }

    /** Tools that never prompt. */
    fun isAutoAllowed(toolName: String): Boolean = toolName in setOf(
        "Read", "Glob", "Grep", "SendMessage", "TeamCreate",
        "Task", "TaskCreate", "TaskList", "TaskOutput", "TaskStop"
    )

    /** Tools that prompt in DEFAULT mode. */
    fun requiresPrompt(toolName: String): Boolean = toolName in setOf("Bash", "Edit", "Write")

    /**
     * @return null if allowed without asking; true/false reserved for ask path via [ask].
     * Call [shouldAsk] first.
     */
    fun shouldAsk(toolName: String): Boolean {
        val mode = AppStateManager.getState().permissionMode
        when (mode) {
            AppPermissionMode.BYPASS_PERMISSIONS -> return false
            AppPermissionMode.DONT_ASK -> return requiresPrompt(toolName) // will auto-deny in ask layer
            AppPermissionMode.ACCEPT_EDITS -> {
                if (toolName == "Edit" || toolName == "Write") return false
                if (toolName == "Bash") return !alwaysAllowSession.contains("Bash")
                return false
            }
            AppPermissionMode.PLAN -> return requiresPrompt(toolName) // plan: deny writes via ask=deny
            else -> { /* DEFAULT / AUTO */ }
        }
        if (isAutoAllowed(toolName)) return false
        if (alwaysAllowSession.contains(toolName)) return false
        return requiresPrompt(toolName)
    }

    /** When DONT_ASK or PLAN blocks a mutating tool without UI. */
    fun autoDeny(toolName: String): Boolean {
        val mode = AppStateManager.getState().permissionMode
        return when (mode) {
            AppPermissionMode.DONT_ASK -> requiresPrompt(toolName)
            AppPermissionMode.PLAN -> toolName in setOf("Bash", "Edit", "Write")
            else -> false
        }
    }
}
