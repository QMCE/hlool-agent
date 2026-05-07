package rj.cocacode.state

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * AbortController for cancelling operations.
 * Mimics the Web API AbortController interface.
 */
class AbortController {
    private var _signal: AbortSignal = AbortSignal()
    
    val signal: AbortSignal
        get() = _signal
    
    val isAborted: Boolean
        get() = _signal.aborted
    
    var reason: String? = null
        private set
    
    fun abort(reason: String = "Aborted") {
        this.reason = reason
        _signal.aborted = true
        _signal.onAbort?.invoke()
    }
    
    fun reset() {
        _signal = AbortSignal()
        reason = null
    }
}

/**
 * AbortSignal represents the state of an abort request.
 */
class AbortSignal {
    var aborted: Boolean = false
        internal set
    
    var onAbort: (() -> Unit)? = null
    
    fun throwIfAborted() {
        if (aborted) {
            throw AbortException("Operation was aborted")
        }
    }
}

/**
 * Exception thrown when an operation is aborted.
 */
class AbortException(message: String) : Exception(message)
