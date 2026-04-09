package rj.cocacode.concurrency

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class WorkerPool(
    private val name: String,
    private val poolSize: Int = Runtime.getRuntime().availableProcessors()
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeWorkers = AtomicInteger(0)
    private val isRunning = AtomicBoolean(true)
    
    suspend fun <T> submit(
        id: String = generateId(),
        priority: Int = 0,
        block: suspend () -> T
    ): Deferred<T> {
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            if (!isRunning.get()) throw IllegalStateException("Pool is shutdown")
            activeWorkers.incrementAndGet()
            try {
                block()
            } finally {
                activeWorkers.decrementAndGet()
            }
        }
        
        jobs[id] = deferred
        deferred.invokeOnCompletion { jobs.remove(id) }
        deferred.start()
        
        return deferred
    }
    
    suspend fun <T> submitAll(
        tasks: List<suspend () -> T>
    ): List<Deferred<T>> = coroutineScope {
        tasks.map { task ->
            async { task() }
        }
    }
    
    suspend fun <T> submitWithRetry(
        id: String = generateId(),
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        block: suspend () -> T
    ): T {
        var lastError: Exception? = null
        var result: T? = null
        
        repeat(maxRetries) { attempt ->
            try {
                result = submit(id) { block() }.await()
                return@repeat
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    delay(delayMs * (attempt + 1))
                }
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return result as T ?: throw lastError ?: Exception("Unknown error")
    }
    
    fun cancel(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
    }
    
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
    
    fun shutdown() {
        isRunning.set(false)
        scope.cancel()
    }
    
    fun getActiveCount(): Int = activeWorkers.get()
    
    fun getPendingCount(): Int = jobs.size
    
    private fun generateId(): String = "job-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
}

class ReadWriteLock {
    private val readCount = AtomicInteger(0)
    private val writeCount = AtomicInteger(0)
    private val readLock = java.util.concurrent.locks.ReentrantLock()
    private val writeLock = java.util.concurrent.locks.ReentrantLock()
    
    fun <T>read(block: () -> T): T {
        readLock.lock()
        try {
            readCount.incrementAndGet()
            return block()
        } finally {
            readCount.decrementAndGet()
            readLock.unlock()
        }
    }
    
    fun <T>write(block: () -> T): T {
        writeLock.lock()
        try {
            writeCount.incrementAndGet()
            return block()
        } finally {
            writeCount.decrementAndGet()
            writeLock.unlock()
        }
    }
}

class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long
) {
    private val requests = ConcurrentHashMap<String, Long>()
    private val lock = java.util.concurrent.locks.ReentrantLock()
    
    fun tryAcquire(key: String): Boolean {
        lock.lock()
        try {
            val now = System.currentTimeMillis()
            val lastRequest = requests[key]
            
            if (lastRequest == null || now - lastRequest >= windowMs) {
                requests[key] = now
                return true
            }
            
            return false
        } finally {
            lock.unlock()
        }
    }
    
    suspend fun acquire(key: String) {
        while (!tryAcquire(key)) {
            delay(50)
        }
    }
    
    fun reset(key: String) {
        requests.remove(key)
    }
    
    fun resetAll() {
        requests.clear()
    }
}

object BatchProcessor {
    suspend fun <T, R> processBatch(
        items: List<T>,
        batchSize: Int = 10,
        parallelism: Int = 4,
        processor: suspend (List<T>) -> List<R>
    ): List<R> = coroutineScope {
        items.chunked(batchSize).map { chunk ->
            async(Dispatchers.IO) {
                processor(chunk)
            }
        }.awaitAll().flatten()
    }
}

class Semaphore(private val permits: Int) {
    private val used = AtomicInteger(0)
    private val queue = java.util.LinkedList<kotlinx.coroutines.CompletableDeferred<Unit>>()
    private val lock = java.util.concurrent.locks.ReentrantLock()
    
    suspend fun acquire() {
        lock.lock()
        try {
            if (used.get() < permits) {
                used.incrementAndGet()
                return
            }
            
            val deferred = CompletableDeferred<Unit>()
            queue.add(deferred)
            lock.unlock()
            deferred.await()
            return
        } finally {
            lock.unlock()
        }
    }
    
    fun release() {
        lock.lock()
        try {
            val deferred = queue.poll()
            if (deferred != null) {
                deferred.complete(Unit)
            } else {
                used.decrementAndGet()
            }
        } finally {
            lock.unlock()
        }
    }
    
    fun available(): Int = permits - used.get()
}