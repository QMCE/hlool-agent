package rj.cocacode.stream

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StreamProcessor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun processStream(
        source: Flow<String>,
        onToken: (String) -> Unit,
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        scope.launch {
            try {
                source.collect { token ->
                    onToken(token)
                }
                onComplete()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
    
    fun cancel() {
        scope.cancel()
    }
}

class TextStream(bufferSize: Int = 100) {
    private val _tokens = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = bufferSize)
    val tokens: SharedFlow<String> = _tokens.asSharedFlow()
    
    private var fullText = StringBuilder()
    val text: String get() = fullText.toString()
    
    suspend fun addToken(token: String) {
        fullText.append(token)
        _tokens.emit(token)
    }
    
    fun clear() {
        fullText.clear()
    }
}

class SSEParser {
    data class SSEMessage(
        val event: String? = null,
        val data: String? = null,
        val id: String? = null,
        val retry: Int? = null
    )
    
    fun parse(input: String): List<SSEMessage> {
        val messages = mutableListOf<SSEMessage>()
        var currentMessage = SSEMessage()
        
        input.lines().forEach { line ->
            when {
                line.isEmpty() -> {
                    if (currentMessage.data != null || currentMessage.event != null) {
                        messages.add(currentMessage)
                    }
                    currentMessage = SSEMessage()
                }
                line.startsWith("event:") -> {
                    currentMessage = currentMessage.copy(event = line.substring(6).trim())
                }
                line.startsWith("data:") -> {
                    val data = line.substring(5).trim()
                    currentMessage = currentMessage.copy(
                        data = if (currentMessage.data != null) 
                            "${currentMessage.data}\n$data" 
                        else 
                            data
                    )
                }
                line.startsWith("id:") -> {
                    currentMessage = currentMessage.copy(id = line.substring(3).trim())
                }
                line.startsWith("retry:") -> {
                    currentMessage = currentMessage.copy(retry = line.substring(6).trim().toIntOrNull())
                }
            }
        }
        
        if (currentMessage.data != null || currentMessage.event != null) {
            messages.add(currentMessage)
        }
        
        return messages
    }
}

class NDJSONParser {
    fun parse(input: String): List<Map<String, Any>> {
        return input.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    parseJsonToMap(line)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    private fun parseJsonToMap(json: String): Map<String, Any> {
        return com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any>
    }
}

class EventEmitter<T> {
    private val listeners = mutableMapOf<String, MutableList<(T) -> Unit>>()
    
    fun on(event: String, handler: (T) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(handler)
    }
    
    fun off(event: String, handler: (T) -> Unit) {
        listeners[event]?.remove(handler)
    }
    
    fun emit(event: String, data: T) {
        listeners[event]?.forEach { it(data) }
    }
    
    fun clear() {
        listeners.clear()
    }
}

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    object Complete : StreamEvent()
    data class ToolUse(val name: String, val input: Map<String, Any>) : StreamEvent()
    data class ToolResult(val name: String, val output: String) : StreamEvent()
}