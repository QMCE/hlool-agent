package rj.cocacode.hooks

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import rj.cocacode.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

suspend fun executeToolHooks(
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?,
    abortSignal: () -> Boolean = { false }
): ToolHookResult = withContext(Dispatchers.IO) {
    val result = ToolHookResult()
    
    if (abortSignal()) {
        return@withContext result.copy(wasCancelled = true)
    }
    
    val matchers = HookRegistry.getMatchingHooks(
        event = event,
        sessionId = sessionId,
        toolName = toolName,
        toolInput = toolInput
    )
    
    if (matchers.isEmpty()) {
        HookRegistry.getCallbacks(event).forEach { callback ->
            if (!abortSignal()) {
                val input = ToolHookInput(
                    hook_event_name = event,
                    session_id = sessionId,
                    transcript_path = transcriptPath,
                    cwd = cwd,
                    permission_mode = permissionMode,
                    tool_name = toolName,
                    tool_input = toolInput.toJsonObject()
                )
                val callbackResult = HookRegistry.executeCallbacks(event, input)
                result.addCallbackResults(callbackResult)
            }
        }
        return@withContext result
    }
    
    for (matcher in matchers) {
        if (abortSignal()) {
            return@withContext result.copy(wasCancelled = true)
        }
        
        for (hook in matcher.hooks) {
            if (abortSignal()) break
            
            val hookId = generateHookId(matcher, hook)
            
            if (hook is HookCommand.Command && hook.once && HookRegistry.wasOnceHookExecuted(hookId)) {
                continue
            }
            
            val hookResult = when (hook) {
                is HookCommand.Command -> executeCommandHook(
                    hook, event, toolName, toolInput,
                    sessionId, transcriptPath, cwd, permissionMode, hookId
                )
                is HookCommand.Prompt -> executePromptHook(
                    hook, event, toolName, toolInput,
                    sessionId, transcriptPath, cwd, permissionMode
                )
                is HookCommand.Http -> executeHttpHook(
                    hook, event, toolName, toolInput,
                    sessionId, transcriptPath, cwd, permissionMode
                )
                is HookCommand.Agent -> executeAgentHook(
                    hook, event, toolName, toolInput,
                    sessionId, transcriptPath, cwd, permissionMode
                )
            }
            
            result.addResult(hookResult)
            
            if (hook is HookCommand.Command && hook.once) {
                HookRegistry.markOnceHookExecuted(hookId)
            }
            
            if (result.shouldBlock()) break
        }
        
        if (result.preventContinuation) break
    }
    
    result
}

private fun executeCommandHook(
    hook: HookCommand.Command,
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?,
    hookId: String
): HookExecutionResult {
    val input = createHookInput(event, toolName, toolInput, sessionId, transcriptPath, cwd, permissionMode)
    val jsonInput = JsonUtils.stringify(input)
    
    return try {
        val timeout = hook.timeout?.let { it * 1000L } ?: 600000L
        
        val processBuilder = ProcessBuilder()
        val command = when (hook.shell) {
            ShellType.BASH -> {
                if (System.getProperty("os.name").lowercase().contains("win")) {
                    listOf("bash", "-c", hook.command)
                } else {
                    listOf("sh", "-c", hook.command)
                }
            }
            ShellType.POWERSHELL -> {
                listOf("pwsh", "-NoProfile", "-NonInteractive", "-Command", hook.command)
            }
        }
        
        processBuilder.command(command)
        processBuilder.directory(java.io.File(cwd))
        
        val env = processBuilder.environment()
        env["CLAUDE_SESSION_ID"] = sessionId
        env["CLAUDE_TRANSCRIPT_PATH"] = transcriptPath
        env["CLAUDE_CWD"] = cwd
        env["CLAUDE_TOOL_NAME"] = toolName
        permissionMode?.let { env["CLAUDE_PERMISSION_MODE"] = it }
        
        processBuilder.redirectErrorStream(false)
        
        val process = processBuilder.start()
        
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(jsonInput)
            writer.newLine()
            writer.flush()
        }
        
        val exitCode = process.waitFor(timeout, TimeUnit.MILLISECONDS)
        
        if (!exitCode) {
            process.destroyForcibly()
            return HookExecutionResult.BlockingError(
                message = "Hook timed out after ${timeout}ms",
                command = hook.command
            )
        }
        
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.exitValue()
        
        when {
            code == 0 -> parseHookOutput(stdout, hook.command)
            code == 2 -> HookExecutionResult.BlockingError(
                message = stderr.ifEmpty { stdout },
                command = hook.command
            )
            else -> HookExecutionResult.NonBlockingError(
                message = "Hook exited with code $code",
                stderr = stderr
            )
        }
    } catch (e: Exception) {
        Logger.error("Hook execution failed: ${hook.command}", e)
        HookExecutionResult.NonBlockingError(
            message = e.message ?: "Unknown error",
            stderr = e.stackTraceToString()
        )
    }
}

private fun executePromptHook(
    hook: HookCommand.Prompt,
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?
): HookExecutionResult {
    Logger.info("Prompt hooks require LLM integration - not yet implemented")
    return HookExecutionResult.NonBlockingError(
        message = "Prompt hooks not yet implemented",
        stderr = ""
    )
}

private fun executeHttpHook(
    hook: HookCommand.Http,
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?
): HookExecutionResult {
    Logger.info("HTTP hooks require HTTP client integration - not yet implemented")
    return HookExecutionResult.NonBlockingError(
        message = "HTTP hooks not yet implemented",
        stderr = ""
    )
}

private fun executeAgentHook(
    hook: HookCommand.Agent,
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?
): HookExecutionResult {
    Logger.info("Agent hooks require agentic integration - not yet implemented")
    return HookExecutionResult.NonBlockingError(
        message = "Agent hooks not yet implemented",
        stderr = ""
    )
}

private fun parseHookOutput(stdout: String, command: String): HookExecutionResult {
    val trimmed = stdout.trim()
    
    if (!trimmed.startsWith("{")) {
        return HookExecutionResult.Success(output = trimmed)
    }
    
    return try {
        val output = JsonUtils.toMap(trimmed)
        
        val preventContinuation = output["continue"] as? Boolean == false
        val decision = output["decision"] as? String
        val reason = output["reason"] as? String
        val hookSpecificOutput = output["hookSpecificOutput"] as? Map<*, *>
        
        when {
            preventContinuation -> {
                val stopReason = output["stopReason"] as? String
                HookExecutionResult.Success(
                    output = trimmed,
                    permissionBehavior = PermissionBehavior.PASSTHROUGH
                )
            }
            decision == "approve" || decision == "allow" -> {
                HookExecutionResult.Success(
                    output = trimmed,
                    permissionBehavior = PermissionBehavior.ALLOW
                )
            }
            decision == "block" -> {
                HookExecutionResult.BlockingError(
                    message = reason ?: "Blocked by hook",
                    command = command
                )
            }
            hookSpecificOutput != null -> {
                val hookEventName = hookSpecificOutput["hookEventName"] as? String
                val permissionDecision = hookSpecificOutput["permissionDecision"] as? String
                val additionalContext = hookSpecificOutput["additionalContext"] as? String
                val updatedInput = (hookSpecificOutput["updatedInput"] as? Map<*, *>)?.mapKeys { it.key as String }
                
                val behavior = when (permissionDecision) {
                    "allow" -> PermissionBehavior.ALLOW
                    "deny" -> PermissionBehavior.DENY
                    "ask" -> PermissionBehavior.ASK
                    else -> null
                }
                
                HookExecutionResult.Success(
                    output = trimmed,
                    additionalContext = additionalContext,
                    permissionBehavior = behavior,
                    updatedInput = updatedInput?.mapValues { it.value }
                )
            }
            else -> HookExecutionResult.Success(output = trimmed)
        }
    } catch (e: Exception) {
        HookExecutionResult.Success(output = trimmed)
    }
}

private fun createHookInput(
    event: String,
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?
): Map<String, Any?> = mapOf(
    "hook_event_name" to event,
    "session_id" to sessionId,
    "transcript_path" to transcriptPath,
    "cwd" to cwd,
    "permission_mode" to permissionMode,
    "tool_name" to toolName,
    "tool_input" to toolInput
)

private fun generateHookId(matcher: HookMatcher, hook: HookCommand): String {
    val pluginPart = matcher.pluginRoot ?: matcher.skillRoot ?: ""
    val hookPart = when (hook) {
        is HookCommand.Command -> "${hook.shell}:${hook.command}"
        is HookCommand.Prompt -> hook.prompt
        is HookCommand.Http -> hook.url
        is HookCommand.Agent -> hook.prompt
    }
    return "$pluginPart:$hookPart"
}

data class ToolHookResult(
    val results: MutableList<HookExecutionResult> = mutableListOf(),
    val callbackResults: MutableList<HookExecutionResult> = mutableListOf(),
    var preventContinuation: Boolean = false,
    var stopReason: String? = null,
    var wasCancelled: Boolean = false
) {
    fun addResult(result: HookExecutionResult) {
        results.add(result)
        
        if (result is HookExecutionResult.Success && result.permissionBehavior == PermissionBehavior.DENY) {
            preventContinuation = true
        }
    }
    
    fun addCallbackResults(callbackResults: List<HookExecutionResult>) {
        this.callbackResults.addAll(callbackResults)
    }
    
    fun shouldBlock(): Boolean {
        return results.any { it is HookExecutionResult.BlockingError }
    }
    
    fun getBlockingError(): HookExecutionResult.BlockingError? {
        return results.filterIsInstance<HookExecutionResult.BlockingError>().firstOrNull()
    }
    
    fun getUpdatedInput(): Map<String, Any?>? {
        return results
            .filterIsInstance<HookExecutionResult.Success>()
            .firstNotNullOfOrNull { it.updatedInput }
    }
    
    fun getPermissionBehavior(): PermissionBehavior? {
        for (result in results) {
            if (result is HookExecutionResult.Success && result.permissionBehavior != null) {
                return result.permissionBehavior
            }
        }
        return null
    }
    
    fun getAdditionalContexts(): List<String> {
        return results
            .filterIsInstance<HookExecutionResult.Success>()
            .mapNotNull { it.additionalContext }
    }
}

suspend fun runPreToolUseHooks(
    toolName: String,
    toolInput: Map<String, Any?>,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?,
    abortSignal: () -> Boolean = { false }
): ToolHookResult = executeToolHooks(
    event = HookEvents.PRE_TOOL_USE,
    toolName = toolName,
    toolInput = toolInput,
    sessionId = sessionId,
    transcriptPath = transcriptPath,
    cwd = cwd,
    permissionMode = permissionMode,
    abortSignal = abortSignal
)

suspend fun runPostToolUseHooks(
    toolName: String,
    toolInput: Map<String, Any?>,
    toolOutput: Any?,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?,
    abortSignal: () -> Boolean = { false }
): ToolHookResult = executeToolHooks(
    event = HookEvents.POST_TOOL_USE,
    toolName = toolName,
    toolInput = toolInput + mapOf("__tool_output__" to toolOutput),
    sessionId = sessionId,
    transcriptPath = transcriptPath,
    cwd = cwd,
    permissionMode = permissionMode,
    abortSignal = abortSignal
)

suspend fun runPostToolUseFailureHooks(
    toolName: String,
    toolInput: Map<String, Any?>,
    error: String,
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: String?,
    abortSignal: () -> Boolean = { false }
): ToolHookResult = executeToolHooks(
    event = HookEvents.POST_TOOL_USE_FAILURE,
    toolName = toolName,
    toolInput = toolInput + mapOf("__error__" to error),
    sessionId = sessionId,
    transcriptPath = transcriptPath,
    cwd = cwd,
    permissionMode = permissionMode,
    abortSignal = abortSignal
)

/** Convert a Map<String, Any?> to JsonObject for serialization-friendly hook input. */
fun Map<String, Any?>.toJsonObject(): JsonObject {
    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(
            @Suppress("UNCHECKED_CAST")
            (this as Map<String, Any?>).mapValues { it.value.toJsonElement() }
        )
        is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
        is Array<*> -> JsonArray(this.map { it.toJsonElement() })
        else -> JsonPrimitive(this.toString())
    }
    return JsonObject(mapValues { it.value.toJsonElement() })
}
