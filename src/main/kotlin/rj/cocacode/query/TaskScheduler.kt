package rj.cocacode.query

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.PriorityBlockingQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * TaskScheduler.kt - Task scheduling and management
 *
 * Provides prioritized task execution with retry mechanisms,
 * suitable for managing background operations in the Cocacode
 * query and tooling infrastructure.
 */
class TaskScheduler(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRetries: Int = 3,
    private val baseDelay: Duration = 1.seconds
) {
    /**
     * Represents a scheduled task with priority and retry logic.
     */
    data class Task(
        val id: String,
        val priority: Int,
        val block: suspend () -> Result<Any>,
        val retries: Int = maxRetries,
        val delay: Duration = baseDelay
    ) : Comparable<Task> {
        override fun compareTo(other: Task): Int = this.priority.compareTo(other.priority)
    }

    /**
     * Represents the result of a task execution.
     */
    sealed class Result<out T> {
        data class Success<out T>(val value: T) : Result<T>()
        data class Failure(val error: Throwable, val retriesLeft: Int) : Result<Nothing>()
    }

    private val queue = PriorityBlockingQueue<Task>(11) { a, b -> a.compareTo(b) }
    private val mutex = Mutex()
    private var isRunning = false
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * Schedules a task for execution.
     */
    fun schedule(task: Task) {
        mutex.withLock {
            queue.add(task)
            if (!isRunning) start()
        }
    }

    /**
     * Starts the task processing loop.
     */
    private fun start() {
        isRunning = true
        scope.launch {
            while (isRunning) {
                val task = queue.poll() ?: break
                executeTask(task)
            }
        }
    }

    /**
     * Executes a single task with retry logic.
     */
    private suspend fun executeTask(task: Task) {
        var remainingRetries = task.retries
        var currentDelay = task.delay

        while (remainingRetries >= 0) {
            try {
                val result = task.block()
                when (result) {
                    is Result.Success -> {
                        // Task completed successfully
                        return
                    }
                }
            } catch (e: Exception) {
                if (remainingRetries == 0) {
                    // All retries exhausted
                    return
                }
                remainingRetries--
                delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            }
        }
    }

    /**
     * Cancels all pending tasks.
     */
    fun cancelAll() {
        mutex.withLock {
            isRunning = false
            scope.cancel()
        }
    }

    companion object {
        /**
         * Creates a high-priority task.
         */
        fun highPriority(
            id: String,
            block: suspend () -> Result<Any>
        ): Task = Task(id, priority = 1, block = block)

        /**
         * Creates a normal-priority task.
         */
        fun normalPriority(
            id: String,
            block: suspend () -> Result<Any>
        ): Task = Task(id, priority = 5, block = block)

        /**
         * Creates a low-priority task.
         */
        fun lowPriority(
            id: String,
            block: suspend () -> Result<Any>
        ): Task = Task(id, priority = 10, block = block)
    }
}