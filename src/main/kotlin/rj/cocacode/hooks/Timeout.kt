package rj.cocacode.hooks

import kotlinx.coroutines.*

class TimeoutService(private val delayMs: Long, private val resetTrigger: Long = 0) {
    private var isElapsed = false
    private var currentTrigger = resetTrigger
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    
    fun reset() {
        isElapsed = false
        startTimer()
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(delayMs)
            isElapsed = true
        }
    }
    
    fun elapsed(): Boolean = isElapsed
    
    fun cancel() {
        timerJob?.cancel()
        scope.cancel()
    }
}

fun useTimeout(delayMs: Long, resetTrigger: Long = 0): TimeoutService {
    return TimeoutService(delayMs, resetTrigger)
}