package rj.cocacode.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class Config(
    val version: Int = 1,
    val model: String? = null,
    val apiUrl: String? = null,
    val apiKey: String? = null,
    val theme: String = "auto",
    val fastMode: Boolean = false,
    val permissionMode: String = "default",
    val allowedTools: List<String> = emptyList(),
    val mcpServers: Map<String, String> = emptyMap()
)

object ConfigManager {
    private val cache = ConcurrentHashMap<String, Config>()
    private var globalConfig: Config? = null
    private val json = Json { ignoreUnknownKeys = true }
    
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
        val configDir: String = System.getenv("COCACODE_CONFIG_DIR") 
            ?: System.getenv("CLAUDE_CONFIG_DIR")
            ?: File(System.getProperty("user.home"), ".cocacode").absolutePath
        return File(configDir, "config.json")
    }
    
    private fun loadConfig(file: File): Config? {
        return try {
            json.decodeFromString<Config>(file.readText())
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveGlobalConfig(config: Config) {
        val configFile = getConfigFile()
        configFile.parentFile?.mkdirs()
        configFile.writeText(json.encodeToString(config))
        globalConfig = config
    }
    
    fun getProjectConfig(projectDir: String): Config {
        return cache.getOrPut(projectDir) {
            val configFile = File(projectDir, ".cocacode.json")
            if (configFile.exists()) loadConfig(configFile) ?: Config() else Config()
        }
    }
}