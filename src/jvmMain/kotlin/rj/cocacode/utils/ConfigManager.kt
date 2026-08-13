package rj.cocacode.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rj.cocacode.config.SettingsConfig
import rj.cocacode.config.SettingsSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
// AppPaths resolves ~/.hlool-agent (with legacy ~/.cocacode fallback)

@Serializable
data class Config(
    val version: Int = 1,
    val model: String? = null,
    val baseUrl: String? = null,
    val apiUrl: String? = null,
    val apiKey: String? = null,
    /** "chat" (OpenAI-compatible /chat/completions) or "messages" (Anthropic). */
    val apiType: String? = null,
    val maxTokens: Int? = null,
    /** Token budget for thinking; null/0 disables. */
    val maxThinkingTokens: Int? = null,
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
        
        val configFile = AppPaths.resolveConfigFileForRead()
        return if (configFile.exists()) {
            loadConfig(configFile) ?: Config().also { globalConfig = it }
        } else {
            Config().also { globalConfig = it }
        }
    }
    
    private fun getConfigFile(): File = AppPaths.configFile()
    
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
            val modern = File(projectDir, ".hlool-agent.json")
            val legacy = File(projectDir, ".cocacode.json")
            val configFile = when {
                modern.exists() -> modern
                legacy.exists() -> legacy
                else -> modern
            }
            if (configFile.exists()) loadConfig(configFile) ?: Config() else Config()
        }
    }

    fun getEffectiveSettings(projectDir: String) = SettingsConfig.getEffectiveSettings(projectDir)

    fun reloadSettings() = SettingsConfig.resetCache()

    fun hasSettingsFile(source: SettingsSource, projectDir: String): Boolean =
        SettingsConfig.hasSettingsFile(source, projectDir)

    fun getEnabledSourceCount(projectDir: String): Int =
        SettingsConfig.getEnabledSourceCount(projectDir)
}