package rj.cocacode.hooks

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object ElapsedTime {
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var isRunning: Boolean = false
    private var endTime: Long? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null
    private var listeners = mutableListOf<() -> Unit>()
    
    fun start(start: Long = System.currentTimeMillis()) {
        startTime = start
        isRunning = true
        pausedDuration = 0
        endTime = null
    }
    
    fun pause() {
        if (isRunning) {
            pausedDuration += System.currentTimeMillis() - startTime
            isRunning = false
        }
    }
    
    fun resume() {
        if (!isRunning) {
            startTime = System.currentTimeMillis()
            isRunning = true
        }
    }
    
    fun stop(end: Long = System.currentTimeMillis()) {
        endTime = end
        isRunning = false
    }
    
    fun getFormatted(updateIntervalMs: Long = 1000L): String {
        val end = endTime ?: System.currentTimeMillis()
        val elapsed = maxOf(0L, end - startTime - pausedDuration)
        return formatDuration(elapsed)
    }
    
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        listeners.forEach { it() }
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    fun reset() {
        startTime = 0
        pausedDuration = 0
        isRunning = false
        endTime = null
        updateJob?.cancel()
    }
}