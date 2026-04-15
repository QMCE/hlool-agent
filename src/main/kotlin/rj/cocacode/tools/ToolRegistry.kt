package rj.cocacode.tools

import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import rj.cocacode.services.api.HttpClient

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any> = emptyMap()
)

@Serializable
data class ToolResult(
    val type: String = "text",
    val text: String = "",
    val isError: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

abstract class Tool(val name: String, val description: String) {
    abstract suspend fun execute(input: Map<String, Any>): ToolResult
    
    fun validateInput(input: Map<String, Any>): Boolean = true
}

object ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun unregister(name: String) {
        tools.remove(name)
    }
    
    fun get(name: String): Tool? = tools[name]
    
    fun getAll(): List<Tool> = tools.values.toList()
    
    fun getAllDefinitions(): List<ToolDefinition> = tools.values.map {
        ToolDefinition(it.name, it.description)
    }
}

class BashTool : Tool("Bash", "Execute shell commands") {
    private val bashTool = BashToolImpl()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return bashTool.execute(input)
    }
}

class ReadFileTool : Tool("Read", "Read file contents") {
    private val readTool = ReadToolImpl()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return readTool.readFile(
            filePath = input["file_path"] as? String ?: return ToolResult(text = "Error: file_path required", isError = true),
            offset = (input["offset"] as? Number)?.toInt(),
            limit = (input["limit"] as? Number)?.toInt()
        )
    }
}

class WriteFileTool : Tool("Write", "Write content to file") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val path = input["file_path"] as? String ?: return ToolResult(text = "Error: file_path required", isError = true)
        val content = input["content"] as? String ?: return ToolResult(text = "Error: content required", isError = true)
        
        return try {
            java.io.File(path).writeText(content)
            ToolResult(text = "File written: $path")
        } catch (e: Exception) {
            ToolResult(text = "Error writing file: ${e.message}", isError = true)
        }
    }
}

class EditFileTool : Tool("Edit", "Edit file content") {
    private val editTool = EditToolImpl()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return editTool.execute(input)
    }
}

class GlobTool : Tool(GlobToolConstants.TOOL_NAME, GlobToolConstants.TOOL_DESCRIPTION) {
    companion object {
        private const val DEFAULT_MAX_RESULTS = GlobToolConstants.DEFAULT_MAX_RESULTS
    }
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val pattern = input["pattern"] as? String 
            ?: return ToolResult(text = "Error: pattern required", isError = true)
        
        // Use provided path or default to current working directory
        val searchPath = input["path"] as? String ?: System.getProperty("user.dir") ?: "."
        
        val startTime = System.currentTimeMillis()
        
        return try {
            val searchDir = java.io.File(searchPath)
            
            // Validate that path exists and is a directory
            if (!searchDir.exists()) {
                return ToolResult(
                    text = "Directory does not exist: $searchPath",
                    isError = true,
                    metadata = mapOf("errorCode" to 1)
                )
            }
            
            if (!searchDir.isDirectory) {
                return ToolResult(
                    text = "Path is not a directory: $searchPath",
                    isError = true,
                    metadata = mapOf("errorCode" to 2)
                )
            }
            
            // Convert glob pattern to regex
            val regexPattern = globToRegex(pattern)
            val regex = Regex(regexPattern, RegexOption.IGNORE_CASE)
            
            // Find matching files
            val files = mutableListOf<String>()
            var truncated = false
            
            searchDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    if (files.size >= DEFAULT_MAX_RESULTS) {
                        truncated = true
                        return@forEach
                    }
                    if (file.name.matches(regex) || file.absolutePath.matches(regex)) {
                        files.add(file.absolutePath)
                    }
                }
            
            // Relativize paths to save tokens
            val relativizedPaths = files.map { makeRelativePath(it, searchPath) }
            
            val durationMs = System.currentTimeMillis() - startTime
            
            val output = GlobOutput(
                durationMs = durationMs,
                numFiles = relativizedPaths.size,
                filenames = relativizedPaths,
                truncated = truncated
            )
            
            val content = if (relativizedPaths.isEmpty()) {
                "No files found"
            } else {
                buildString {
                    append(relativizedPaths.joinToString("\n"))
                    if (truncated) {
                        append("\n(Results are truncated. Consider using a more specific path or pattern.)")
                    }
                }
            }
            
            ToolResult(
                text = content,
                metadata = mapOf(
                    "durationMs" to durationMs,
                    "numFiles" to relativizedPaths.size,
                    "truncated" to truncated
                )
            )
        } catch (e: Exception) {
            ToolResult(
                text = "Error: ${e.message}",
                isError = true
            )
        }
    }
    
    /**
     * Convert glob pattern to regex
     */
    private fun globToRegex(pattern: String): String {
        val sb = StringBuilder("^")
        
        for (char in pattern) {
            when (char) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.' -> sb.append("\\.")
                '[' -> sb.append("[")
                ']' -> sb.append("]")
                '\\' -> sb.append("\\\\")
                else -> {
                    if (char.isLetterOrDigit() || char == '_' || char == '-' || char == '/' || char == ' ') {
                        sb.append(char)
                    } else {
                        sb.append("\\").append(char)
                    }
                }
            }
        }
        
        sb.append("$")
        return sb.toString()
    }
    
    /**
     * Make path relative to base directory
     */
    private fun makeRelativePath(absolutePath: String, basePath: String): String {
        val baseDir = java.io.File(basePath)
        val absoluteFile = java.io.File(absolutePath)
        
        return try {
            baseDir.toURI().relativize(absoluteFile.toURI()).path
        } catch (e: Exception) {
            absolutePath
        }
    }
}

class GrepTool : Tool("Grep", "Search file contents using ripgrep") {
    private val grepTool = GrepToolImpl()
    
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        return grepTool.execute(input)
    }
}

class WebSearchTool : Tool("WebSearch", "Search the web") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val query = input["query"] as? String ?: return ToolResult(text = "Error: query required", isError = true)
        
        return try {
            val client = rj.cocacode.services.api.HttpClient.client
            val results = client.get("https://api.duckduckgo.com") {
                url { 
                    parameters.append("q", query)
                    parameters.append("format", "json")
                }
            }.bodyAsText()
            
            val data = com.google.gson.Gson().fromJson(results, Map::class.java)
            val answer = data["AbstractText"] as? String ?: "No results found"
            
            ToolResult(text = answer)
        } catch (e: Exception) {
            ToolResult(text = "Search failed: ${e.message}", isError = true)
        }
    }
}