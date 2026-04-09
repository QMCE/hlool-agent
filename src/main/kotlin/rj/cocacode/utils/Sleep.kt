package rj.cocacode.utils

import kotlinx.coroutines.delay

suspend fun sleep(ms: Long): Unit = delay(ms)

suspend fun <T> withTimeout(ms: Long, block: suspend () -> T): T {
    try {
        return kotlinx.coroutines.withTimeoutOrNull(ms) { block() }
            ?: throw TimeoutException("Operation timed out after ${ms}ms")
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw TimeoutException("Operation timed out after ${ms}ms")
    }
}

class TimeoutException(message: String) : Exception(message)

suspend fun sleepWithSignal(ms: Long, signal: Boolean = false): Boolean {
    try {
        delay(ms)
        return true
    } catch (e: kotlinx.coroutines.CancellationException) {
        return false
    }
}