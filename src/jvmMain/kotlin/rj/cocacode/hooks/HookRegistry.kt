package rj.cocacode.hooks

import rj.cocacode.utils.Logger

object HookRegistry {
    private val registeredHooks = mutableMapOf<String, MutableList<HookMatcher>>()
    private val callbackHooks = mutableMapOf<String, MutableList<HookCallback>>()
    private val sessionHooks = mutableMapOf<String, MutableMap<String, MutableList<HookMatcher>>>()
    private val onceHooksExecuted = mutableSetOf<String>()
    
    fun registerHook(event: String, matcher: HookMatcher) {
        registeredHooks.getOrPut(event) { mutableListOf() }.add(matcher)
    }
    
    fun registerCallbackHook(event: String, callback: HookCallback) {
        callbackHooks.getOrPut(event) { mutableListOf() }.add(callback)
    }
    
    fun registerSessionHook(sessionId: String, event: String, matcher: HookMatcher) {
        sessionHooks.getOrPut(sessionId) { mutableMapOf() }
            .getOrPut(event) { mutableListOf() }
            .add(matcher)
    }
    
    fun unregisterHook(event: String, matcher: HookMatcher) {
        registeredHooks[event]?.remove(matcher)
    }
    
    fun unregisterAllHooks() {
        registeredHooks.clear()
        callbackHooks.clear()
        sessionHooks.clear()
        onceHooksExecuted.clear()
    }
    
    fun getMatchingHooks(
        event: String,
        sessionId: String? = null,
        toolName: String? = null,
        toolInput: Map<String, Any?>? = null
    ): List<HookMatcher> {
        val matchers = mutableListOf<HookMatcher>()
        
        registeredHooks[event]?.let { matchers.addAll(it) }
        
        sessionId?.let { sid ->
            sessionHooks[sid]?.get(event)?.let { matchers.addAll(it) }
        }
        
        callbackHooks[event]?.let { callbacks ->
            callbacks.filter { !it.internal }.forEach { callback ->
                matchers.add(HookMatcher(
                    hooks = listOf(HookCommand.Command(
                        command = "[callback:${callback.name}]",
                        timeout = callback.timeout
                    ))
                ))
            }
        }
        
        return if (toolName != null) {
            matchers.filter { matcher ->
                matcher.matcher == null || 
                matcher.matcher == "*" ||
                matchesPattern(toolName, matcher.matcher)
            }.filter { matcher ->
                matcher.hooks.all { hook ->
                    val `if` = when (hook) {
                        is HookCommand.Command -> hook.`if`
                        is HookCommand.Prompt -> hook.`if`
                        is HookCommand.Http -> hook.`if`
                        is HookCommand.Agent -> hook.`if`
                    }
                    `if` == null || evaluateIfCondition(`if`, toolName, toolInput)
                }
            }
        } else {
            matchers
        }
    }
    
    fun getCallbacks(event: String): List<HookCallback> {
        return callbackHooks[event] ?: emptyList()
    }
    
    suspend fun executeCallbacks(
        event: String,
        input: ToolHookInput
    ): List<HookExecutionResult> {
        val results = mutableListOf<HookExecutionResult>()
        val callbacks = callbackHooks[event] ?: return results
        
        for (callback in callbacks) {
            try {
                val result = callback.callback(input, input.tool_name)
                results.add(result)
            } catch (e: Exception) {
                Logger.error("Hook callback error: ${callback.name}", e)
                results.add(HookExecutionResult.NonBlockingError(
                    message = e.message ?: "Unknown error",
                    stderr = e.stackTraceToString()
                ))
            }
        }
        
        return results
    }
    
    private fun matchesPattern(query: String, pattern: String): Boolean {
        if (pattern == "*") return true
        
        if (!pattern.contains(Regex("[|^.*+?"))) {
            return query == pattern || pattern.split("|").any { it.trim() == query }
        }
        
        return try {
            Regex(pattern).matches(query)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun evaluateIfCondition(
        condition: String,
        toolName: String,
        toolInput: Map<String, Any?>?
    ): Boolean {
        return try {
            val parts = condition.split("(", ")")
            if (parts.size < 2) return true
            
            val conditionToolName = parts[0].trim()
            val conditionPattern = parts.getOrNull(1)?.trim() ?: "*"
            
            if (normalizeToolName(toolName) != normalizeToolName(conditionToolName)) {
                return false
            }
            
            if (conditionPattern == "*" || conditionPattern.isEmpty()) {
                return true
            }
            
            if (toolInput == null) return false
            
            evaluatePatternMatch(conditionPattern, toolInput)
        } catch (e: Exception) {
            Logger.warn("Failed to evaluate if condition: $condition - ${e.message}")
            false
        }
    }
    
    private fun normalizeToolName(name: String): String {
        return name.replace(Regex("[_-]"), "").lowercase()
    }
    
    private fun evaluatePatternMatch(
        pattern: String,
        toolInput: Map<String, Any?>
    ): Boolean {
        val parts = pattern.split(" ", limit = 2)
        if (parts.isEmpty()) return true
        
        val filePattern = parts[0]
        
        fun matchesGlob(str: String, glob: String): Boolean {
            val regex = glob
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
            return Regex("^$regex$", RegexOption.IGNORE_CASE).matches(str)
        }
        
        for ((_, value) in toolInput) {
            when (value) {
                is String -> {
                    if (matchesGlob(value, filePattern)) return true
                    if (value.contains("/") || value.contains("\\")) {
                        val fileName = value.substringAfterLast("/").substringAfterLast("\\")
                        if (matchesGlob(fileName, filePattern)) return true
                    }
                }
                is List<*> -> {
                    for (item in value) {
                        if (item is String && matchesGlob(item, filePattern)) return true
                    }
                }
            }
        }
        
        return false
    }
    
    fun markOnceHookExecuted(hookId: String) {
        onceHooksExecuted.add(hookId)
    }
    
    fun wasOnceHookExecuted(hookId: String): Boolean {
        return onceHooksExecuted.contains(hookId)
    }
    
    fun getHooksConfig(): HooksConfig {
        return registeredHooks.toMap().mapValues { entry ->
            entry.value.toList()
        }
    }
    
    fun hasHooksForEvent(event: String): Boolean {
        if (registeredHooks[event]?.isNotEmpty() == true) return true
        if (callbackHooks[event]?.isNotEmpty() == true) return true
        for (session in sessionHooks.values) {
            if (session[event]?.isNotEmpty() == true) return true
        }
        return false
    }
}
