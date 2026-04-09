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
    val isError: Boolean = false
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
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val command = input["command"] as? String ?: return ToolResult(text = "Error: command required", isError = true)
        val timeout = input["timeout"] as? Long ?: 60000
        
        val result = rj.cocacode.utils.Shell.exec(command, options = rj.cocacode.utils.Shell.ExecOptions(timeout = timeout, shell = true))
        return ToolResult(
            text = result.stdout + if (result.stderr.isNotBlank()) "\n${result.stderr}" else "",
            isError = result.exitCode != 0
        )
    }
}

class ReadFileTool : Tool("Read", "Read file contents") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val path = input["file_path"] as? String ?: return ToolResult(text = "Error: file_path required", isError = true)
        
        return try {
            val content = java.io.File(path).readText()
            ToolResult(text = content)
        } catch (e: Exception) {
            ToolResult(text = "Error reading file: ${e.message}", isError = true)
        }
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
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val path = input["file_path"] as? String ?: return ToolResult(text = "Error: file_path required", isError = true)
        val oldString = input["old_string"] as? String ?: return ToolResult(text = "Error: old_string required", isError = true)
        val newString = input["new_string"] as? String ?: return ToolResult(text = "Error: new_string required", isError = true)
        
        return try {
            val file = java.io.File(path)
            val content = file.readText()
            if (!content.contains(oldString)) {
                return ToolResult(text = "Error: old_string not found in file", isError = true)
            }
            val newContent = content.replace(oldString, newString)
            file.writeText(newContent)
            ToolResult(text = "File edited: $path")
        } catch (e: Exception) {
            ToolResult(text = "Error editing file: ${e.message}", isError = true)
        }
    }
}

class GlobTool : Tool("Glob", "Find files by pattern") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val path = input["path"] as? String ?: "."
        val pattern = input["pattern"] as? String ?: "*"
        
        return try {
            val files = java.io.File(path).walkTopDown()
                .filter { it.name.matches(Regex(pattern.replace("*", ".*"))) }
                .map { it.absolutePath }
                .take(100)
                .toList()
            
            ToolResult(text = files.joinToString("\n"))
        } catch (e: Exception) {
            ToolResult(text = "Error: ${e.message}", isError = true)
        }
    }
}

class GrepTool : Tool("Grep", "Search file contents") {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val pattern = input["pattern"] as? String ?: return ToolResult(text = "Error: pattern required", isError = true)
        val path = input["path"] as? String ?: "."
        
        return try {
            val results = java.io.File(path).walkTopDown()
                .filter { it.isFile && !rj.cocacode.constants.Files.hasBinaryExtension(it.name) }
                .mapNotNull { file ->
                    try {
                        val content = file.readText()
                        if (content.contains(Regex(pattern))) {
                            "${file.absolutePath}: found"
                        } else null
                    } catch (e: Exception) { null }
                }
                .take(50)
                .toList()
            
            ToolResult(text = results.joinToString("\n"))
        } catch (e: Exception) {
            ToolResult(text = "Error: ${e.message}", isError = true)
        }
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