package rj.cocacode.realtime

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class EventBus {
    private val listeners = mutableMapOf<String, MutableList<(Any) -> Unit>>()
    
    fun <T> subscribe(event: String, handler: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        listeners.getOrPut(event) { mutableListOf() }.add(handler as (Any) -> Unit)
    }
    
    fun unsubscribe(event: String, handler: (Any) -> Unit) {
        listeners[event]?.remove(handler)
    }
    
    fun publish(event: String, data: Any) {
        listeners[event]?.forEach { it(data) }
    }
    
    fun clear(event: String) {
        listeners[event]?.clear()
    }
    
    fun clearAll() {
        listeners.clear()
    }
}

class MessageBus {
    private val channels = mutableMapOf<String, Channel<Any>>()
    private val subscribers = mutableMapOf<String, MutableSet<String>>()
    
    fun openChannel(name: String): Channel<Any> {
        return channels.getOrPut(name) { Channel(Channel.BUFFERED) }
    }
    
    fun closeChannel(name: String) {
        channels[name]?.close()
        channels.remove(name)
        subscribers.remove(name)
    }
    
    suspend fun send(channel: String, message: Any) {
        channels[channel]?.send(message)
    }
    
    suspend fun receive(channel: String): Any? {
        return channels[channel]?.receive()
    }
    
    fun subscribeToChannel(channel: String, subscriberId: String) {
        subscribers.getOrPut(channel) { mutableSetOf() }.add(subscriberId)
    }
    
    fun unsubscribeFromChannel(channel: String, subscriberId: String) {
        subscribers[channel]?.remove(subscriberId)
    }
    
    fun getSubscriberCount(channel: String): Int = subscribers[channel]?.size ?: 0
}

class Observable<T>(initialValue: T) {
    private val _value = MutableStateFlow(initialValue)
    val value: StateFlow<T> = _value.asStateFlow()
    
    fun set(newValue: T) {
        _value.value = newValue
    }
    
    fun update(transform: (T) -> T) {
        _value.value = transform(_value.value)
    }
}

class ReactiveMap<K, V> {
    private val _map = MutableStateFlow<Map<K, V>>(emptyMap())
    val map: StateFlow<Map<K, V>> = _map.asStateFlow()
    
    fun set(key: K, value: V) {
        _map.value = _map.value + (key to value)
    }
    
    fun remove(key: K) {
        _map.value = _map.value - key
    }
    
    fun get(key: K): V? = _map.value[key]
    
    fun clear() {
        _map.value = emptyMap()
    }
    
    fun update(key: K, transform: (V?) -> V) {
        set(key, transform(get(key)))
    }
}

class BatchEmitter<T>(
    private val delayMs: Long = 100
) {
    private val buffer = mutableListOf<T>()
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _emissions = MutableSharedFlow<List<T>>(replay = 1)
    val emissions: SharedFlow<List<T>> = _emissions.asSharedFlow()
    
    fun emit(item: T) {
        buffer.add(item)
        scheduleFlush()
    }
    
    private fun scheduleFlush() {
        if (job?.isActive != true) {
            job = scope.launch {
                delay(delayMs)
                flush()
            }
        }
    }
    
    private suspend fun flush() {
        if (buffer.isNotEmpty()) {
            _emissions.emit(buffer.toList())
            buffer.clear()
        }
    }
    
    fun close() {
        job?.cancel()
        scope.cancel()
    }
}

class Debouncer<T>(
    private val delayMs: Long = 300
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    
    fun emit(action: suspend () -> T, onResult: (T) -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            onResult(action())
        }
    }
    
    fun cancel() {
        job?.cancel()
    }
}