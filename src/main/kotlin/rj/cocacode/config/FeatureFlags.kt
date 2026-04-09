package rj.cocacode.config

import java.io.File

object FeatureFlags {
    private val flags = mutableMapOf<String, Boolean>()
    private val overrides = mutableMapOf<String, Boolean?>()
    
    fun init() {
        flags["STREAMLINED_OUTPUT"] = false
        flags["PROACTIVE"] = false
        flags["KAIROS"] = false
        flags["BRIDGE_MODE"] = false
        flags["VOICE_MODE"] = false
        flags["CCR_AUTO_CONNECT"] = false
        flags["DOWNLOAD_USER_SETTINGS"] = true
        flags["EXTRACT_MEMORIES"] = false
        flags["TEAMMEM"] = false
        flags["WORKFLOW_SCRIPTS"] = false
    }
    
    fun isEnabled(name: String): Boolean {
        return overrides[name] ?: flags[name] ?: false
    }
    
    fun enable(name: String) {
        flags[name] = true
    }
    
    fun disable(name: String) {
        flags[name] = false
    }
    
    fun setOverride(name: String, value: Boolean?) {
        overrides[name] = value
    }
    
    fun resetOverrides() {
        overrides.clear()
    }
    
    fun getAllFlags(): Map<String, Boolean> = flags.toMap()
    
    fun getEnabledFlags(): Map<String, Boolean> = flags.filter { it.value }
}

object EnvironmentConfig {
    private val envVars = mutableMapOf<String, String?>()
    
    init {
        System.getenv().forEach { (key, value) ->
            envVars[key] = value
        }
    }
    
    fun get(key: String): String? = envVars[key]
    
    fun get(key: String, default: String): String = envVars[key] ?: default
    
    fun getInt(key: String): Int? = envVars[key]?.toIntOrNull()
    
    fun getInt(key: String, default: Int): Int = getInt(key) ?: default
    
    fun getBool(key: String): Boolean = envVars[key]?.toBoolean() ?: false
    
    fun set(key: String, value: String?) {
        envVars[key] = value
    }
    
    fun isSet(key: String): Boolean = envVars[key] != null
    
    fun getPrefix(prefix: String): Map<String, String> = 
        envVars.filter { it.key.startsWith(prefix) && it.value != null }
            .mapValues { it.value!! }
}

object ConfigLoader {
    data class AppConfig(
        val version: String = "1.0.0",
        val apiUrl: String = "https://api.anthropic.com",
        val logLevel: String = "INFO",
        val maxMemory: Long = 1024 * 1024 * 1024,
        val enableTelemetry: Boolean = true
    )
    
    fun load(configFile: File = File(System.getProperty("user.home"), ".cocacode/config.json")): AppConfig {
        if (!configFile.exists()) return AppConfig()
        
        return try {
            val json = configFile.readText()
            parseConfig(json)
        } catch (e: Exception) {
            AppConfig()
        }
    }
    
    private fun parseConfig(json: String): AppConfig {
        val map = com.google.gson.Gson().fromJson(json, Map::class.java)
        
        return AppConfig(
            version = map["version"] as? String ?: "1.0.0",
            apiUrl = map["apiUrl"] as? String ?: "https://api.anthropic.com",
            logLevel = map["logLevel"] as? String ?: "INFO",
            maxMemory = (map["maxMemory"] as? Number)?.toLong() ?: 1024 * 1024 * 1024,
            enableTelemetry = map["enableTelemetry"] as? Boolean ?: true
        )
    }
    
    fun save(config: AppConfig, configFile: File = File(System.getProperty("user.home"), ".cocacode/config.json")) {
        configFile.parentFile?.mkdirs()
        
        val map = mapOf(
            "version" to config.version,
            "apiUrl" to config.apiUrl,
            "logLevel" to config.logLevel,
            "maxMemory" to config.maxMemory,
            "enableTelemetry" to config.enableTelemetry
        )
        
        val json = com.google.gson.Gson().toJson(map)
        configFile.writeText(json)
    }
}

object PropertiesConfig {
    private val properties = mutableMapOf<String, String>()
    
    fun load(file: File) {
        if (!file.exists()) return
        
        file.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    properties[parts[0].trim()] = parts[1].trim()
                }
            }
        }
    }
    
    fun get(key: String): String? = properties[key]
    
    fun get(key: String, default: String): String = properties[key] ?: default
    
    fun set(key: String, value: String) {
        properties[key] = value
    }
    
    fun save(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(properties.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}