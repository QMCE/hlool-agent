package rj.cocacode.query

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rj.cocacode.utils.generateUuid
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import rj.cocacode.types.Attachment

data class StopHookResult(
    val blockingErrors: List<Message>,
    val preventContinuation: Boolean,
)

data class StopHookInfo(
    val command: String,
    val promptText: String? = null,
    val durationMs: Long? = null,
)

data class HookProgress(
    val command: String? = null,
    val promptText: String? = null,
)

object HookAttachmentTypes {
    const val HOOK_NON_BLOCKING_ERROR = "hook_non_blocking_error"
    const val HOOK_ERROR_DURING_EXECUTION = "hook_error_during_execution"
    const val HOOK_SUCCESS = "hook_success"
    const val HOOK_STOPPED_CONTINUATION = "hook_stopped_continuation"
}

object HookEventTypes {
    const val STOP = "Stop"
    const val SUBAGENT_STOP = "SubagentStop"
    const val TASK_COMPLETED = "TaskCompleted"
    const val TEAMMATE_IDLE = "TeammateIdle"
}

fun getStopHookMessage(blockingError: String): String = "Stop hook blocked: $blockingError"

fun getTaskCompletedHookMessage(blockingError: String): String = "TaskCompleted hook blocked: $blockingError"

fun getTeammateIdleHookMessage(blockingError: String): String = "TeammateIdle hook blocked: $blockingError"

// createUserMessage and createSystemMessage are defined in Query.kt

fun createAttachmentMessage(
    type: String,
    message: String,
    hookName: String,
    toolUseID: String,
    hookEvent: String,
    stdout: String? = null,
    stderr: String? = null,
    exitCode: Int? = null,
    content: String? = null
): Message = Message(
    id = generateUuid(),
    type = MessageType.TOOL_RESULT,
    content = message,
    attachments = listOf(
        Attachment(
            type = type,
            data = buildString {
                append("toolUseID=$toolUseID")
                append(";hookName=$hookName")
                append(";hookEvent=$hookEvent")
                stdout?.let { append(";stdout=$it") }
                stderr?.let { append(";stderr=$it") }
                exitCode?.let { append(";exitCode=$it") }
                content?.let { append(";content=$it") }
            }
        )
    )
)

// createUserInterruptionMessage is defined in Query.kt

fun createStopHookSummaryMessage(
    hookCount: Int,
    hookInfos: List<StopHookInfo>,
    hookErrors: List<String>,
    preventedContinuation: Boolean,
    stopReason: String,
    hasOutput: Boolean,
    suggestion: String,
    stopHookToolUseID: String
): Message = Message(
    id = generateUuid(),
    type = MessageType.SYSTEM,
    content = buildString {
        append("Stop hooks executed: $hookCount")
        if (hookErrors.isNotEmpty()) {
            append("\\nErrors: ${hookErrors.joinToString(", ")}")
        }
        if (preventedContinuation) {
            append("\\nContinuation prevented: $stopReason")
        }
    }
)

fun isBareMode(): Boolean = System.getProperty("cocacode.bare") == "true" || System.getenv("COCACODE_BARE") == "true"

fun isEnvDefinedFalsy(envKey: String): Boolean {
    val value = System.getenv(envKey) ?: return true
    return value.isBlank() || value == "false" || value == "0"
}

object TeammateDetection {
    private var isTeammateFlag = false
    private var agentName: String? = null
    private var teamName: String? = null
    
    fun setTeammate(isTeammate: Boolean, agentName: String?, teamName: String?) {
        isTeammateFlag = isTeammate
        this.agentName = agentName
        this.teamName = teamName
    }
    
    fun isTeammate(): Boolean = isTeammateFlag
    fun getAgentName(): String? = agentName
    fun getTeamName(): String? = teamName
}

data class TaskListItem(
    val id: String,
    val subject: String,
    val description: String,
    val status: TaskStatus,
    val owner: String?
)

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

object TaskList {
    private val tasks = mutableListOf<TaskListItem>()
    
    fun getTaskListId(): String = "default"
    suspend fun listTasks(listId: String): List<TaskListItem> = tasks.toList()
    fun addTask(task: TaskListItem) { tasks.add(task) }
    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index >= 0) { tasks[index] = tasks[index].copy(status = status) }
    }
}

interface StopHookToolUseContext {
    fun getAppState(): StopHookAppState
    fun appendSystemMessage(message: String)
    val isMainAgent: Boolean
}

data class StopHookAppState(
    val toolPermissionContext: StopHookToolPermissionContext?
)

data class StopHookToolPermissionContext(
    val mode: String?
)

data class HookExecutionResult(
    val message: Message?,
    val blockingError: String?,
    val preventContinuation: Boolean,
    val stopReason: String?,
)

suspend fun handleStopHooks(
    messagesForQuery: List<Message>,
    assistantMessages: List<Message>,
    systemPrompt: String,
    userContext: Map<String, String>,
    systemContext: Map<String, String>,
    toolUseContext: StopHookToolUseContext,
    querySource: QuerySource,
    stopHookActive: Boolean = false
): StopHookResult = coroutineScope {
    val hookStartTime = System.currentTimeMillis()
    
    try {
        val blockingErrors = mutableListOf<Message>()
        
        val hookResults = executeStopHooksFlow(
            toolUseContext,
            messagesForQuery + assistantMessages,
            stopHookActive,
        )
        
        var hookCount = 0
        var preventedContinuation = false
        var stopReason = ""
        var hasOutput = false
        val hookErrors = mutableListOf<String>()
        
        hookResults.collect { result ->
            result.message?.let { message ->
                if (message.type == MessageType.TOOL_RESULT) { hookCount++ }
            }
            
            result.blockingError?.let { blockingError ->
                val userMessage = createUserMessage(
                    content = getStopHookMessage(blockingError),
                    isMeta = true
                )
                blockingErrors.add(userMessage)
                hasOutput = true
                hookErrors.add(blockingError)
            }
            
            if (result.preventContinuation) {
                preventedContinuation = true
                stopReason = result.stopReason ?: "Stop hook prevented continuation"
            }
        }
        
        if (hookCount > 0) {
        }
        
        if (preventedContinuation) {
            return@coroutineScope StopHookResult(blockingErrors = emptyList(), preventContinuation = true)
        }
        
        if (blockingErrors.isNotEmpty()) {
            return@coroutineScope StopHookResult(blockingErrors = blockingErrors, preventContinuation = false)
        }
        
        if (TeammateDetection.isTeammate()) {
            val teammateName = TeammateDetection.getAgentName() ?: ""
            val teamName = TeammateDetection.getTeamName() ?: ""
            val teammateBlockingErrors = mutableListOf<Message>()
            var teammatePreventedContinuation = false
            var teammateStopReason: String? = null
            
            val taskItems = TaskList.listTasks(TaskList.getTaskListId())
            val inProgressTasks = taskItems.filter { it.status == TaskStatus.IN_PROGRESS && it.owner == teammateName }
            
            for (task in inProgressTasks) {
                val taskResults = executeTaskCompletedHooksFlow(
                    task.id, task.subject, task.description, teammateName, teamName, toolUseContext
                )
                
                taskResults.collect { result ->
                    result.blockingError?.let { blockingError ->
                        val userMessage = createUserMessage(
                            content = getTaskCompletedHookMessage(blockingError), isMeta = true
                        )
                        teammateBlockingErrors.add(userMessage)
                    }
                    
                    if (result.preventContinuation) {
                        teammatePreventedContinuation = true
                        teammateStopReason = result.stopReason ?: "TaskCompleted hook prevented continuation"
                    }
                }
            }
            
            val teammateIdleResults = executeTeammateIdleHooksFlow(teammateName, teamName, toolUseContext)
            
            teammateIdleResults.collect { result ->
                result.blockingError?.let { blockingError ->
                    val userMessage = createUserMessage(
                        content = getTeammateIdleHookMessage(blockingError), isMeta = true
                    )
                    teammateBlockingErrors.add(userMessage)
                }
                
                if (result.preventContinuation) {
                    teammatePreventedContinuation = true
                    teammateStopReason = result.stopReason ?: "TeammateIdle hook prevented continuation"
                }
            }
            
            if (teammatePreventedContinuation) {
                return@coroutineScope StopHookResult(blockingErrors = emptyList(), preventContinuation = true)
            }
            
            if (teammateBlockingErrors.isNotEmpty()) {
                return@coroutineScope StopHookResult(blockingErrors = teammateBlockingErrors, preventContinuation = false)
            }
        }
        
        StopHookResult(blockingErrors = emptyList(), preventContinuation = false)
    } catch (error: Exception) {
        val durationMs = System.currentTimeMillis() - hookStartTime
        System.err.println("Stop hook error after ${durationMs}ms: ${error.message}")
        
        StopHookResult(blockingErrors = emptyList(), preventContinuation = false)
    }
}

fun executeStopHooksFlow(
    toolUseContext: StopHookToolUseContext,
    messages: List<Message>,
    stopHookActive: Boolean,
): Flow<HookExecutionResult> = flow {
    emit(HookExecutionResult(message = null, blockingError = null, preventContinuation = false, stopReason = null))
}

fun executeTaskCompletedHooksFlow(
    taskId: String,
    taskSubject: String,
    taskDescription: String,
    teammateName: String,
    teamName: String,
    toolUseContext: StopHookToolUseContext,
): Flow<HookExecutionResult> = flow {
    emit(HookExecutionResult(message = null, blockingError = null, preventContinuation = false, stopReason = null))
}

fun executeTeammateIdleHooksFlow(
    teammateName: String,
    teamName: String,
    toolUseContext: StopHookToolUseContext,
): Flow<HookExecutionResult> = flow {
    emit(HookExecutionResult(message = null, blockingError = null, preventContinuation = false, stopReason = null))
}

fun isExtractModeActive(): Boolean = System.getenv("EXTRACT_MODE") == "true"

fun isExtractMemoriesEnabled(): Boolean = System.getenv("CLAUDE_CODE_ENABLE_EXTRACT_MEMORIES") == "true"

suspend fun extractMemories(context: StopHookToolUseContext, appendSystemMessage: (String) -> Unit) {
    try {
        println("Extracting memories...")
    } catch (e: Exception) {
        System.err.println("Memory extraction failed: ${e.message}")
    }
}

suspend fun executeAutoDream(context: StopHookToolUseContext, appendSystemMessage: (String) -> Unit): Unit {
    return executeAutoDreamInternal(context, appendSystemMessage)
}

private suspend fun executeAutoDreamInternal(context: StopHookToolUseContext, appendSystemMessage: (String) -> Unit) {
    try {
        println("Executing auto-dream...")
    } catch (e: Exception) {
        System.err.println("Auto-dream failed: ${e.message}")
    }
}

suspend fun executePromptSuggestion(context: StopHookToolUseContext): Unit {
    try {
        println("Executing prompt suggestion...")
    } catch (e: Exception) {
        System.err.println("Prompt suggestion failed: ${e.message}")
    }
}