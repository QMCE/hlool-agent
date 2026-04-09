package rj.cocacode.utils

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson

data class Config(
    val version: Int = 1,
    val autoModeEnabled: Boolean = false,
    val autoModeOptIn: Boolean = false,
    val model: String? = null,
    val provider: String? = null,
    val theme: String = "auto",
    val permissionMode: String = "default",
    val fastMode: Boolean = false,
    val skipKeybindings: Boolean = false,
    val allowedTools: List<String> = emptyList(),
    val mcpServers: Map<String, String> = emptyMap()
)

object ConfigManager {
    private val cache = ConcurrentHashMap<String, Config>()
    private var globalConfig: Config? = null
    private val gson = Gson()
    
    fun getGlobalConfig(): Config {
        globalConfig?.let { return it }
        
        val configFile = getConfigFile()
        return if (configFile.exists()) {
            loadConfig(configFile) ?: Config().also { globalConfig = it }
        } else {
            Config().also { globalConfig = it }
        }
    }
    
    private fun getConfigFile(): File {
        val configDir: String = System.getenv("CLAUDE_CONFIG_DIR") 
            ?: File(System.getProperty("user.home"), ".cocacode").absolutePath
        return File(configDir, "config.json")
    }
    
    private fun loadConfig(file: File): Config? {
        return try {
            gson.fromJson(file.readText(), Config::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveGlobalConfig(config: Config) {
        val configFile = getConfigFile()
        configFile.parentFile?.mkdirs()
        configFile.writeText(gson.toJson(config))
        globalConfig = config
    }
    
    fun getProjectConfig(projectDir: String): Config {
        return cache.getOrPut(projectDir) {
            val configFile = File(projectDir, ".cocacode.json")
            if (configFile.exists()) loadConfig(configFile) ?: Config() else Config()
        }
    }
}