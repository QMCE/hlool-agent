package rj.cocacode.ext

import java.io.File

object ExtensionManager {
    private val extensions = mutableMapOf<String, Extension>()
    
    data class Extension(
        val id: String,
        val name: String,
        val version: String,
        val main: String,
        val enabled: Boolean = true
    )
    
    fun register(extension: Extension) {
        extensions[extension.id] = extension
    }
    
    fun unregister(id: String) {
        extensions.remove(id)
    }
    
    fun get(id: String): Extension? = extensions[id]
    
    fun getAll(): List<Extension> = extensions.values.toList()
    
    fun getEnabled(): List<Extension> = extensions.values.filter { it.enabled }
    
    fun enable(id: String) {
        extensions[id]?.let { extensions[id] = it.copy(enabled = true) }
    }
    
    fun disable(id: String) {
        extensions[id]?.let { extensions[id] = it.copy(enabled = false) }
    }
    
    fun loadFromDir(dir: String) {
        val extDir = File(dir)
        if (!extDir.exists()) return
        
        extDir.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val manifestFile = File(folder, "extension.json")
            if (manifestFile.exists()) {
                parseExtension(manifestFile)?.let { register(it) }
            }
        }
    }
    
    private fun parseExtension(file: File): Extension? {
        return try {
            val json = com.google.gson.Gson().fromJson(file.readText(), Map::class.java)
            Extension(
                id = json["id"] as? String ?: file.parentFile.name,
                name = json["name"] as? String ?: file.parentFile.name,
                version = json["version"] as? String ?: "1.0.0",
                main = json["main"] as? String ?: "index.js"
            )
        } catch (e: Exception) {
            null
        }
    }
}

class ExtensionContext(
    private val extensionId: String
) {
    fun registerCommand(
        name: String,
        description: String,
        handler: (List<String>) -> Any
    ) {
        // Skip registration - commands need explicit class
    }
    
    fun registerTool(
        name: String,
        description: String,
        handler: suspend (Map<String, Any>) -> rj.cocacode.tools.ToolResult
    ) {
        rj.cocacode.tools.ToolRegistry.register(object : rj.cocacode.tools.Tool(name, description) {
            override suspend fun execute(input: Map<String, Any>): rj.cocacode.tools.ToolResult {
                return handler(input)
            }
        })
    }
    
    fun getState(): Map<String, Any> = rj.cocacode.state.AppStateManager.getState().let {
        mapOf(
            "sessionId" to it.sessionId,
            "model" to it.currentModel,
            "theme" to it.theme
        )
    }
    
    fun setState(key: String, value: Any) {
        when (key) {
            "model" -> rj.cocacode.state.AppStateManager.setModel(value as String)
            "theme" -> rj.cocacode.state.AppStateManager.setTheme(value as String)
        }
    }
}

object ExtensionAPI {
    fun createContext(extensionId: String): ExtensionContext = ExtensionContext(extensionId)
    
    fun getExtensions(): List<ExtensionManager.Extension> = ExtensionManager.getAll()
    
    fun isEnabled(extensionId: String): Boolean = ExtensionManager.get(extensionId)?.enabled == true
}