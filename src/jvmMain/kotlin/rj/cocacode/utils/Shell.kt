package rj.cocacode.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.isActive

object Shell {
    data class ExecOptions(
        val timeout: Long = 600000,
        val env: Map<String, String>? = null,
        val cwd: String? = null,
        val shell: Boolean = true,
        val onProgress: ((String, Int) -> Unit)? = null
    )
    
    data class ExecResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val timedOut: Boolean = false,
        val backgroundTaskId: String? = null
    )
    
    private val backgroundExecutor = Executors.newCachedThreadPool()
    private val backgroundTasks = mutableMapOf<String, Process>()

    /** Thread pool used to drain process stdout/stderr concurrently. */
    private val drainExecutor = Executors.newCachedThreadPool()

    /**
     * Execute a command, draining stdout/stderr concurrently so the child never
     * blocks on a full pipe buffer. Returns when the process exits or after
     * [ExecOptions.timeout] (killing the process on timeout).
     */
    fun exec(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): ExecResult {
        return try {
            val process = startProcess(command, args, options)
            val stdoutFuture = drainExecutor.submit<String> { readFully(process.inputStream) }
            val stderrFuture = drainExecutor.submit<String> { readFully(process.errorStream) }

            val finished = try {
                process.waitFor(options.timeout, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                false
            }

            if (!finished) {
                destroyProcess(process)
                return ExecResult("", "Command timed out after ${options.timeout}ms", -1, timedOut = true)
            }

            val stdout = try { stdoutFuture.get(5, TimeUnit.SECONDS) } catch (e: Exception) { "" }
            val stderr = try { stderrFuture.get(5, TimeUnit.SECONDS) } catch (e: Exception) { "" }
            ExecResult(stdout, stderr, process.exitValue())
        } catch (e: Exception) {
            ExecResult("", e.message ?: "Unknown error", -1)
        }
    }

    /**
     * Suspend variant of [exec]. Dispatches the blocking process wait to
     * [Dispatchers.IO] so the caller's coroutine is not frozen, checks
     * cancellation every ~100ms, and destroys the process on timeout or
     * cancellation.
     */
    suspend fun execSuspend(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): ExecResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val process = startProcess(command, args, options)
            val stdoutFuture = drainExecutor.submit<String> { readFully(process.inputStream) }
            val stderrFuture = drainExecutor.submit<String> { readFully(process.errorStream) }

            try {
                val deadline = System.nanoTime() + options.timeout * 1_000_000L
                while (process.isAlive) {
                    if (!coroutineContext.isActive) {
                        destroyProcess(process)
                        return@withContext ExecResult("", "Cancelled", -1, timedOut = true)
                    }
                    if (System.nanoTime() >= deadline) {
                        destroyProcess(process)
                        return@withContext ExecResult("", "Command timed out after ${options.timeout}ms", -1, timedOut = true)
                    }
                    process.waitFor(100, TimeUnit.MILLISECONDS)
                }

                val stdout = try { stdoutFuture.get(5, TimeUnit.SECONDS) } catch (e: Exception) { "" }
                val stderr = try { stderrFuture.get(5, TimeUnit.SECONDS) } catch (e: Exception) { "" }
                ExecResult(stdout, stderr, process.exitValue())
            } finally {
                // Belt-and-braces: destroy the child if we're being cancelled.
                if (!coroutineContext.isActive) destroyProcess(process)
            }
        }

    private fun startProcess(command: String, args: List<String>, options: ExecOptions): Process {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val cmdList: List<String> = if (options.shell) {
            // Run the full command string through a real shell.
            if (isWindows) listOf("cmd", "/c", command) else listOf("/bin/sh", "-c", command)
        } else {
            listOf(command) + args
        }
        val builder = ProcessBuilder(cmdList)
        options.env?.let { env ->
            val processEnv = builder.environment()
            env.forEach { (key, value) -> processEnv.put(key, value) }
        }
        options.cwd?.let { builder.directory(java.io.File(it)) }
        return builder.start()
    }

    /** Read an input stream fully into a string, then close it. */
    private fun readFully(stream: java.io.InputStream): String =
        stream.bufferedReader().use { it.readText() }

    private fun destroyProcess(process: Process) {
        process.destroyForcibly()
        try {
            process.waitFor(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    
    fun execWithProgress(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): ExecResult {
        return try {
            val builder = ProcessBuilder(if (options.shell) command else command, *args.toTypedArray())
            
            options.env?.let { env ->
                val processEnv = builder.environment()
                env.forEach { (key, value) -> processEnv.put(key, value) }
            }
            
            options.cwd?.let { builder.directory(java.io.File(it)) }
            
            val process = builder.start()
            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()
            val lineCount = AtomicBoolean(false)
            val executor = Executors.newSingleThreadExecutor()
            
            executor.submit {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputBuilder.appendLine(line)
                        options.onProgress?.invoke(line!!, outputBuilder.count { it == '\n' })
                    }
                }
            }
            
            executor.submit {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorBuilder.appendLine(line)
                    }
                }
            }
            
            val finished = process.waitFor(options.timeout, TimeUnit.MILLISECONDS)
            executor.shutdown()
            
            if (!finished) {
                process.destroyForcibly()
                return ExecResult("", "", -1, timedOut = true)
            }
            
            ExecResult(outputBuilder.toString().trimEnd(), errorBuilder.toString().trimEnd(), process.exitValue())
        } catch (e: Exception) {
            ExecResult("", e.message ?: "Unknown error", -1)
        }
    }
    
    fun execBackground(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): String {
        val taskId = java.util.UUID.randomUUID().toString()
        
        backgroundExecutor.submit {
            try {
                val builder = ProcessBuilder(if (options.shell) command else command, *args.toTypedArray())
                
                options.env?.let { env ->
                    val processEnv = builder.environment()
                    env.forEach { (key, value) -> processEnv.put(key, value) }
                }
                
                options.cwd?.let { builder.directory(java.io.File(it)) }
                
                val process = builder.start()
                backgroundTasks[taskId] = process
                
                process.waitFor()
            } catch (e: Exception) {
                println("Background task $taskId failed: ${e.message}")
            } finally {
                backgroundTasks.remove(taskId)
            }
        }
        
        return taskId
    }
    
    fun killBackgroundTask(taskId: String): Boolean {
        val process = backgroundTasks[taskId] ?: return false
        process.destroyForcibly()
        backgroundTasks.remove(taskId)
        return true
    }
    
    fun getBackgroundTaskStatus(taskId: String): Boolean {
        val process = backgroundTasks[taskId] ?: return false
        return process.isAlive
    }
    
    fun execSync(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): ExecResult {
        return exec(command, args, options)
    }
    
    fun runCommand(command: String, timeout: Long = 60000): String {
        return exec(command, options = ExecOptions(timeout = timeout, shell = true)).stdout
    }
}

fun String.exec(env: Map<String, String>? = null, cwd: String? = null): Shell.ExecResult {
    return Shell.exec(this, emptyList(), Shell.ExecOptions(env = env, cwd = cwd))
}