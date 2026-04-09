package rj.cocacode.hooks

import kotlinx.coroutines.*

object DoublePress {
    const val TIMEOUT_MS = 800L
    
    private var lastPressTime = 0L
    private var timeoutJob: Job? = null
    private var pendingCallback: (() -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun checkDoublePress(
        onDoublePress: () -> Unit,
        onFirstPress: () -> Unit = {}
    ): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastPress = now - lastPressTime
        val isDoublePress = timeSinceLastPress <= TIMEOUT_MS && timeoutJob?.isActive == true
        
        return if (isDoublePress) {
            timeoutJob?.cancel()
            onDoublePress()
            true
        } else {
            onFirstPress()
            pendingCallback = { }
            timeoutJob = scope.launch {
                delay(TIMEOUT_MS)
                pendingCallback = null
            }
            lastPressTime = now
            false
        }
    }
    
    fun reset() {
        timeoutJob?.cancel()
        lastPressTime = 0L
        pendingCallback = null
    }
}