package rj.cocacode.hooks

import kotlinx.serialization.Serializable

/**
 * Hook event types supported by cocacode.
 * Based on Claude Code's HOOK_EVENTS.
 */
object HookEvents {
    const val PRE_TOOL_USE = "PreToolUse"
    const val POST_TOOL_USE = "PostToolUse"
    const val POST_TOOL_USE_FAILURE = "PostToolUseFailure"
    const val NOTIFICATION = "Notification"
    const val USER_PROMPT_SUBMIT = "UserPromptSubmit"
    const val SESSION_START = "SessionStart"
    const val SESSION_END = "SessionEnd"
    const val STOP = "Stop"
    const val STOP_FAILURE = "StopFailure"
    const val SUBAGENT_START = "SubagentStart"
    const val SUBAGENT_STOP = "SubagentStop"
    const val PRE_COMPACT = "PreCompact"
    const val POST_COMPACT = "PostCompact"
    const val PERMISSION_REQUEST = "PermissionRequest"
    const val PERMISSION_DENIED = "PermissionDenied"
    const val SETUP = "Setup"
    const val TEAMMATE_IDLE = "TeammateIdle"
    const val TASK_CREATED = "TaskCreated"
    const val TASK_COMPLETED = "TaskCompleted"
    const val ELICITATION = "Elicitation"
    const val ELICITATION_RESULT = "ElicitationResult"
    const val CONFIG_CHANGE = "ConfigChange"
    const val WORKTREE_CREATE = "WorktreeCreate"
    const val WORKTREE_REMOVE = "WorktreeRemove"
    const val INSTRUCTIONS_LOADED = "InstructionsLoaded"
    const val CWD_CHANGED = "CwdChanged"
    const val FILE_CHANGED = "FileChanged"

    val ALL = listOf(
        PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE, NOTIFICATION,
        USER_PROMPT_SUBMIT, SESSION_START, SESSION_END, STOP, STOP_FAILURE,
        SUBAGENT_START, SUBAGENT_STOP, PRE_COMPACT, POST_COMPACT,
        PERMISSION_REQUEST, PERMISSION_DENIED, SETUP, TEAMMATE_IDLE,
        TASK_CREATED, TASK_COMPLETED, ELICITATION, ELICITATION_RESULT,
        CONFIG_CHANGE, WORKTREE_CREATE, WORKTREE_REMOVE, INSTRUCTIONS_LOADED,
        CWD_CHANGED, FILE_CHANGED
    )
}

/**
 * Base input shared by all hook types.
 */
@Serializable
data class BaseHookInput(
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val agent_id: String? = null,
    val agent_type: String? = null
)

/**
 * Input for PreToolUse, PostToolUse, PostToolUseFailure hooks.
 */
@Serializable
data class ToolHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val agent_id: String? = null,
    val agent_type: String? = null,
    val tool_name: String,
    val tool_input: Map<String, Any?>
)

/**
 * Input for SessionStart hook.
 */
@Serializable
data class SessionStartHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val source: String = "manual"
)

/**
 * Input for SessionEnd hook.
 */
@Serializable
data class SessionEndHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val reason: String
)

/**
 * Input for UserPromptSubmit hook.
 */
@Serializable
data class UserPromptSubmitHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val agent_id: String? = null,
    val agent_type: String? = null,
    val prompt: String
)

/**
 * Input for Stop hook.
 */
@Serializable
data class StopHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val error: String? = null
)

/**
 * Input for SubagentStart/SubagentStop hooks.
 */
@Serializable
data class SubagentHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val agent_id: String? = null,
    val agent_type: String
)

/**
 * Input for Notification hook.
 */
@Serializable
data class NotificationHookInput(
    val hook_event_name: String,
    val session_id: String,
    val transcript_path: String,
    val cwd: String,
    val permission_mode: String? = null,
    val notification_type: String,
    val message: String? = null
)

/**
 * Permission behavior from hook responses.
 */
enum class PermissionBehavior {
    ALLOW, DENY, ASK, PASSTHROUGH
}

/**
 * Result of hook execution.
 */
sealed class HookExecutionResult {
    data class Success(
        val output: String = "",
        val additionalContext: String? = null,
        val permissionBehavior: PermissionBehavior? = null,
        val updatedInput: Map<String, Any?>? = null
    ) : HookExecutionResult()
    
    data class BlockingError(
        val message: String,
        val command: String
    ) : HookExecutionResult()
    
    data class NonBlockingError(
        val message: String,
        val stderr: String = ""
    ) : HookExecutionResult()
    
    object Cancelled : HookExecutionResult()
    
    data class Async(
        val processId: String
    ) : HookExecutionResult()
}

/**
 * Hook command types.
 */
sealed class HookCommand {
    /**
     * Shell command hook.
     */
    data class Command(
        val command: String,
        val `if`: String? = null,
        val shell: ShellType = ShellType.BASH,
        val timeout: Long? = null,
        val statusMessage: String? = null,
        val once: Boolean = false,
        val async: Boolean = false,
        val asyncRewake: Boolean = false
    ) : HookCommand()
    
    /**
     * LLM prompt hook.
     */
    data class Prompt(
        val prompt: String,
        val `if`: String? = null,
        val timeout: Long? = null,
        val model: String? = null,
        val statusMessage: String? = null,
        val once: Boolean = false
    ) : HookCommand()
    
    /**
     * HTTP hook.
     */
    data class Http(
        val url: String,
        val `if`: String? = null,
        val timeout: Long? = null,
        val headers: Map<String, String>? = null,
        val allowedEnvVars: List<String>? = null,
        val statusMessage: String? = null,
        val once: Boolean = false
    ) : HookCommand()
    
    /**
     * Agentic verifier hook.
     */
    data class Agent(
        val prompt: String,
        val `if`: String? = null,
        val timeout: Long? = null,
        val model: String? = null,
        val statusMessage: String? = null,
        val once: Boolean = false
    ) : HookCommand()
}

/**
 * Shell types for command hooks.
 */
enum class ShellType {
    BASH, POWERSHELL
}

/**
 * Hook matcher configuration.
 */
data class HookMatcher(
    val matcher: String? = null,
    val hooks: List<HookCommand> = emptyList(),
    val pluginRoot: String? = null,
    val pluginId: String? = null,
    val skillRoot: String? = null,
    val pluginName: String? = null,
    val skillName: String? = null
)

/**
 * Hooks configuration map.
 */
typealias HooksConfig = Map<String, List<HookMatcher>>

/**
 * Aggregated result from multiple hook executions.
 */
data class AggregatedHookResult(
    val message: String? = null,
    val blockingErrors: List<HookExecutionResult.BlockingError> = emptyList(),
    val preventContinuation: Boolean = false,
    val stopReason: String? = null,
    val permissionBehavior: PermissionBehavior? = null,
    val additionalContexts: List<String> = emptyList(),
val updatedInput: Map<String, Any?>? = null,
     val retry: Boolean? = null
)

/**
 * Progress event during hook execution.
 */
data class HookProgress(
    val hookEvent: String,
    val hookName: String,
    val command: String,
    val promptText: String? = null,
    val statusMessage: String? = null
)

/**
 * Callback hook type for internal use.
 */
data class HookCallback(
    val name: String,
    val callback: suspend (input: ToolHookInput, toolUseId: String?) -> HookExecutionResult,
    val timeout: Long? = null,
    val internal: Boolean = false
)
