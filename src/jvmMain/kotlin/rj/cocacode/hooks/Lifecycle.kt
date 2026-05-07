package rj.cocacode.hooks

import kotlinx.coroutines.*
import rj.cocacode.state.AppStateManager

object Hooks {
    private val lifecycleHooks = mutableMapOf<String, MutableList< suspend () -> Unit >>()
    private val eventHooks = mutableMapOf<String, MutableList< suspend (Map<String, Any>) -> Unit >>()
    
    fun registerLifecycleHook(phase: String, callback: suspend () -> Unit) {
        lifecycleHooks.getOrPut(phase) { mutableListOf() }.add(callback)
    }
    
    fun registerEventHook(event: String, callback: suspend (Map<String, Any>) -> Unit) {
        eventHooks.getOrPut(event) { mutableListOf() }.add(callback)
    }
    
    suspend fun runLifecycleHook(phase: String) {
        lifecycleHooks[phase]?.forEach { it() }
    }
    
    suspend fun runEventHook(event: String, data: Map<String, Any> = emptyMap()) {
        eventHooks[event]?.forEach { it(data) }
    }
    
    fun clearHooks() {
        lifecycleHooks.clear()
        eventHooks.clear()
    }
    
    fun registerFromConfig(config: HooksConfig) {
        for ((event, matchers) in config) {
            for (matcher in matchers) {
                HookRegistry.registerHook(event, matcher)
            }
        }
    }
}

object LifecyclePhases {
    const val INITIALIZE = "initialize"
    const val READY = "ready"
    const val START = "start"
    const val STOP = "stop"
    const val CLEANUP = "cleanup"
}

object EventTypes {
    const val MESSAGE_RECEIVED = "message_received"
    const val MESSAGE_SENT = "message_sent"
    const val TOOL_CALLED = "tool_called"
    const val TOOL_RESULT = "tool_result"
    const val ERROR = "error"
    const val PERMISSION_REQUEST = "permission_request"
    const val SESSION_START = "session_start"
    const val SESSION_END = "session_end"
}

class LifecycleManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    suspend fun initialize() {
        Hooks.runLifecycleHook(LifecyclePhases.INITIALIZE)
    }
    
    suspend fun onReady() {
        Hooks.runLifecycleHook(LifecyclePhases.READY)
    }
    
    suspend fun onStart() {
        Hooks.runLifecycleHook(LifecyclePhases.START)
    }
    
    suspend fun onStop() {
        Hooks.runLifecycleHook(LifecyclePhases.STOP)
    }
    
    suspend fun cleanup() {
        Hooks.runLifecycleHook(LifecyclePhases.CLEANUP)
        scope.cancel()
    }
}

class TimeoutHook(private val timeoutMs: Long) {
    private var job: Job? = null
    
    fun start(callback: () -> Unit) {
        job = CoroutineScope(Dispatchers.Default).launch {
            delay(timeoutMs)
            callback()
        }
    }
    
    fun cancel() {
        job?.cancel()
    }
}

class IntervalHook(private val intervalMs: Long) {
    private var job: Job? = null
    private var isRunning = false
    
    fun start(callback: () -> Unit) {
        if (isRunning) return
        isRunning = true
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                callback()
                delay(intervalMs)
            }
        }
    }
    
    fun stop() {
        isRunning = false
        job?.cancel()
    }
}