package rj.cocacode.plugins

import java.io.File

data class Plugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String = "",
    val commands: List<PluginCommand> = emptyList(),
    val tools: List<String> = emptyList(),
    val hooks: List<String> = emptyList(),
    val enabled: Boolean = true
)

data class PluginCommand(
    val name: String,
    val description: String,
    val handler: (List<String>) -> Unit
)

object PluginManager {
    private val plugins = mutableMapOf<String, Plugin>()
    private val pluginDirs = mutableListOf<String>()
    
    fun addPluginDir(path: String) {
        pluginDirs.add(path)
    }
    
    fun loadPlugins(): List<Plugin> {
        plugins.clear()
        
        for (dir in pluginDirs) {
            loadPluginsFromDir(dir)
        }
        
        return plugins.values.toList()
    }
    
    private fun loadPluginsFromDir(dir: String) {
        val pluginsDir = File(dir)
        if (!pluginsDir.exists()) return
        
        pluginsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                parsePluginFromDir(file)?.let { plugin ->
                    plugins[plugin.id] = plugin
                }
            }
        }
    }
    
    private fun parsePluginFromDir(dir: File): Plugin? {
        val configFile = File(dir, "plugin.json")
        if (!configFile.exists()) return null
        
        return try {
            val json = com.google.gson.Gson().fromJson(configFile.readText(), Map::class.java)
            Plugin(
                id = dir.name,
                name = json["name"] as? String ?: dir.name,
                version = json["version"] as? String ?: "1.0.0",
                description = json["description"] as? String ?: "",
                author = json["author"] as? String ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getPlugin(id: String): Plugin? = plugins[id]
    
    fun getAllPlugins(): List<Plugin> = plugins.values.toList()
    
    fun getEnabledPlugins(): List<Plugin> = plugins.values.filter { it.enabled }
    
    fun enablePlugin(id: String) {
        plugins[id]?.let { plugins[id] = it.copy(enabled = true) }
    }
    
    fun disablePlugin(id: String) {
        plugins[id]?.let { plugins[id] = it.copy(enabled = false) }
    }
    
    fun registerPlugin(plugin: Plugin) {
        plugins[plugin.id] = plugin
    }
    
    fun unregisterPlugin(id: String) {
        plugins.remove(id)
    }
}

class PluginContext(
    val plugin: Plugin,
    val config: Map<String, Any> = emptyMap()
) {
    fun registerCommand(name: String, handler: (List<String>) -> Unit) {
        val cmd = PluginCommand(name, "", handler)
        val updated = plugin.copy(commands = plugin.commands + cmd)
        PluginManager.registerPlugin(updated)
    }
    
    fun registerTool(toolName: String) {
        val updated = plugin.copy(tools = plugin.tools + toolName)
        PluginManager.registerPlugin(updated)
    }
    
    fun registerHook(event: String) {
        val updated = plugin.copy(hooks = plugin.hooks + event)
        PluginManager.registerPlugin(updated)
    }
}