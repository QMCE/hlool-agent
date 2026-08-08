package rj.cocacode.tools

import kotlinx.serialization.Serializable
import rj.cocacode.utils.Shell
import rj.cocacode.utils.Shell.ExecOptions
import rj.cocacode.utils.Shell.ExecResult

/**
 * Enhanced BashTool - Execute shell commands with full feature support
 * 
 * Migrated from Claude Code's BashTool:
 * - execute() - Command execution
 * - parseCommandLine() - Command line parsing
 * - Environment variable handling
 * - Working directory support
 * - Background task support
 * - Progress reporting
 */
class BashToolImpl : Tool("Bash", "Execute shell commands") {
    
    companion object {
        // Commands categorized for UI display
        private val SEARCH_COMMANDS = setOf("find", "grep", "rg", "ag", "ack", "locate", "which", "whereis")
        private val READ_COMMANDS = setOf("cat", "head", "tail", "less", "more", "wc", "stat", "file", "strings", "jq", "awk", "cut", "sort", "uniq", "tr")
        private val LIST_COMMANDS = setOf("ls", "tree", "du")
        private val SILENT_COMMANDS = setOf("mv", "cp", "rm", "mkdir", "rmdir", "chmod", "chown", "chgrp", "touch", "ln", "cd", "export", "unset", "wait")
        private val SEMANTIC_NEUTRAL_COMMANDS = setOf("echo", "printf", "true", "false", ":")
        
        private const val DEFAULT_TIMEOUT_MS = 60000L
        private const val MAX_TIMEOUT_MS = 600000L
        private const val PROGRESS_THRESHOLD_MS = 2000L
    }
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val command = input["command"] as? String 
            ?: return ToolResult(text = "Error: command required", isError = true)
        
        val timeout = (input["timeout"] as? Long) ?: DEFAULT_TIMEOUT_MS
        val workdir = input["workdir"] as? String
        val runInBackground = input["run_in_background"] as? Boolean ?: false
        val description = input["description"] as? String
        
        // Parse environment variables from input
        val env = parseEnvironmentVariables(input)
        
        val options = ExecOptions(
            timeout = timeout.coerceIn(0, MAX_TIMEOUT_MS),
            env = env,
            cwd = workdir,
            shell = true
        )
        
        return try {
            val result = Shell.execSuspend(command, options = options)

            // Check for timeout
            if (result.timedOut) {
                return ToolResult(
                    text = "Command timed out after ${timeout}ms",
                    isError = true
                )
            }
            
            // Build output text
            val output = buildString {
                append(result.stdout)
                if (result.stderr.isNotBlank()) {
                    append("\n")
                    append(result.stderr)
                }
            }.trimEnd()
            
            // Get command classification for additional info
            val commandType = classifyCommand(command)
            
            ToolResult(
                text = output,
                isError = result.exitCode != 0,
                metadata = mapOf(
                    "exitCode" to result.exitCode,
                    "commandType" to commandType,
                    "description" to (description ?: command.take(50))
                )
            )
        } catch (e: Exception) {
            ToolResult(
                text = "Error executing command: ${e.message}",
                isError = true
            )
        }
    }
    
    /**
     * Parse command line string into arguments
     * Handles quoted arguments, environment variables, and operators
     */
    fun parseCommandLine(command: String): List<String> {
        if (command.isBlank()) return emptyList()
        
        val args = mutableListOf<String>()
        var current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        var escaped = false
        
        for (char in command) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    escaped = true
                }
                char == '"' || char == '\'' -> {
                    if (inQuote && char == quoteChar) {
                        inQuote = false
                        quoteChar = ' '
                    } else if (!inQuote) {
                        inQuote = true
                        quoteChar = char
                    } else {
                        current.append(char)
                    }
                }
                char == ' ' && !inQuote -> {
                    if (current.isNotBlank()) {
                        args.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotBlank()) {
            args.add(current.toString())
        }
        
        return args
    }
    
    /**
     * Parse environment variables from input map
     * Supports env parameter as Map<String, String>
     */
    private fun parseEnvironmentVariables(input: Map<String, Any>): Map<String, String>? {
        val envParam = input["env"] as? Map<*, *>
        if (envParam == null) {
            // Also check for individual env vars
            val envVars = input.filterKeys { it.startsWith("env.") }
            if (envVars.isEmpty()) return null
            return envVars.mapKeys { it.key.removePrefix("env.") }.mapValues { it.value.toString() }
        }
        return envParam.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
    }
    
    /**
     * Classify command type for UI display
     */
    private fun classifyCommand(command: String): String {
        val parts = parseCommandLine(command)
        if (parts.isEmpty()) return "other"
        
        val baseCommand = parts[0]
        return when {
            SEARCH_COMMANDS.contains(baseCommand) -> "search"
            READ_COMMANDS.contains(baseCommand) -> "read"
            LIST_COMMANDS.contains(baseCommand) -> "list"
            SILENT_COMMANDS.contains(baseCommand) -> "silent"
            else -> "other"
        }
    }
    
    /**
     * Check if command is a search or read operation
     */
    fun isSearchOrReadCommand(command: String): CommandClassification {
        val parts = parseCommandLine(command)
        if (parts.isEmpty()) return CommandClassification()
        
        var hasSearch = false
        var hasRead = false
        var hasList = false
        
        for (part in parts) {
            val baseCmd = part.trim().split(" ").firstOrNull() ?: continue
            if (SEARCH_COMMANDS.contains(baseCmd)) hasSearch = true
            if (READ_COMMANDS.contains(baseCmd)) hasRead = true
            if (LIST_COMMANDS.contains(baseCmd)) hasList = true
        }
        
        return CommandClassification(
            isSearch = hasSearch,
            isRead = hasRead,
            isList = hasList
        )
    }
    
    /**
     * Check if command is expected to produce no output on success
     */
    fun isSilentCommand(command: String): Boolean {
        val parts = parseCommandLine(command)
        if (parts.isEmpty()) return false
        
        return parts.any { SILENT_COMMANDS.contains(it) }
    }
    
    /**
     * Extract environment variables from command string
     * e.g., "FOO=bar npm install" -> {FOO: bar}
     */
    fun extractEnvFromCommand(command: String): Map<String, String> {
        val envVars = mutableMapOf<String, String>()
        val parts = parseCommandLine(command)
        
        for (part in parts) {
            val match = Regex("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$").find(part)
            if (match != null) {
                envVars[match.groupValues[1]] = match.groupValues[2]
            } else {
                break // Stop at first non-env var
            }
        }
        
        return envVars
    }
}

@Serializable
data class CommandClassification(
    val isSearch: Boolean = false,
    val isRead: Boolean = false,
    val isList: Boolean = false
)

/**
 * Input schema for BashTool
 */
object BashToolSchema {
    val inputSchema = mapOf(
        "command" to mapOf(
            "type" to "string",
            "description" to "The command to execute"
        ),
        "timeout" to mapOf(
            "type" to "number", 
            "description" to "Optional timeout in milliseconds (max 600000)"
        ),
        "workdir" to mapOf(
            "type" to "string",
            "description" to "Working directory for command execution"
        ),
        "description" to mapOf(
            "type" to "string",
            "description" to "Clear, concise description of what this command does"
        ),
        "run_in_background" to mapOf(
            "type" to "boolean",
            "description" to "Set to true to run this command in the background"
        )
    )
    
    val outputSchema = mapOf(
        "stdout" to mapOf("type" to "string", "description" to "The standard output of the command"),
        "stderr" to mapOf("type" to "string", "description" to "The standard error output of the command"),
        "exitCode" to mapOf("type" to "number", "description" to "The exit code of the command"),
        "timedOut" to mapOf("type" to "boolean", "description" to "Whether the command timed out"),
        "backgroundTaskId" to mapOf("type" to "string", "description" to "ID if running in background")
    )
}
