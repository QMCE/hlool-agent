package rj.cocacode.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object Shell {
    data class ExecOptions(
        val timeout: Long = 600000,
        val env: Map<String, String>? = null,
        val cwd: String? = null,
        val shell: Boolean = true
    )
    
    data class ExecResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val timedOut: Boolean = false
    )
    
    fun exec(command: String, args: List<String> = emptyList(), options: ExecOptions = ExecOptions()): ExecResult {
        return try {
            val builder = ProcessBuilder(if (options.shell) command else command, *args.toTypedArray())
            
            options.env?.let { env ->
                val processEnv = builder.environment()
                env.forEach { (key, value) -> processEnv.put(key, value) }
            }
            
            options.cwd?.let { builder.directory(java.io.File(it)) }
            
            val process = builder.start()
            
            val finished = process.waitFor(options.timeout, TimeUnit.MILLISECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return ExecResult("", "", -1, timedOut = true)
            }
            
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            
            ExecResult(stdout, stderr, process.exitValue())
        } catch (e: Exception) {
            ExecResult("", e.message ?: "Unknown error", -1)
        }
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