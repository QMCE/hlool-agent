package rj.cocacode.hooks

import kotlinx.coroutines.*

class InputBufferService(
    private val maxBufferSize: Int = 50,
    private val debounceMs: Long = 300
) {
    private val buffer = mutableListOf<BufferEntry>()
    private var currentIndex = -1
    private var lastPushTime = 0L
    private var pendingPush: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    data class BufferEntry(
        val text: String,
        val cursorOffset: Int,
        val pastedContents: Map<Int, PastedContent> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class PastedContent(
        val type: String,
        val data: String
    )
    
    fun pushToBuffer(text: String, cursorOffset: Int, pastedContents: Map<Int, PastedContent> = emptyMap()) {
        pendingPush?.cancel()
        
        val now = System.currentTimeMillis()
        if (now - lastPushTime < debounceMs) {
            pendingPush = scope.launch {
                delay(debounceMs)
                pushToBuffer(text, cursorOffset, pastedContents)
            }
            return
        }
        
        lastPushTime = now
        
        val newBuffer = if (currentIndex >= 0) {
            buffer.take(currentIndex + 1).toMutableList()
        } else {
            buffer.toMutableList()
        }
        
        val lastEntry = newBuffer.lastOrNull()
        if (lastEntry?.text != text) {
            newBuffer.add(BufferEntry(text, cursorOffset, pastedContents, now))
        }
        
        while (newBuffer.size > maxBufferSize) {
            newBuffer.removeAt(0)
        }
        
        buffer.clear()
        buffer.addAll(newBuffer)
        
        currentIndex = if (currentIndex >= 0) minOf(currentIndex + 1, maxBufferSize - 1) else buffer.size - 1
    }
    
    fun undo(): BufferEntry? {
        if (currentIndex <= 0 || buffer.isEmpty()) return null
        currentIndex--
        return buffer.getOrNull(currentIndex)
    }
    
    fun canUndo(): Boolean = currentIndex > 0 && buffer.size > 1
    
    fun clearBuffer() {
        buffer.clear()
        currentIndex = -1
        lastPushTime = 0
        pendingPush?.cancel()
    }
    
    fun cancel() {
        pendingPush?.cancel()
        scope.cancel()
    }
}