package rj.cocacode.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object SettingsConfig {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val sessionCache: ConcurrentMap<SettingsSource, JsonObject?> = ConcurrentHashMap()
    private var effectiveSettings: JsonObject? = null

    fun getSettingsPathForSource(source: SettingsSource, projectDir: String): String? {
        return when (source) {
            SettingsSource.USER -> {
                val configDir = getUserConfigDir()
                File(configDir, "settings.json").absolutePath
            }
            SettingsSource.PROJECT -> {
                    File(projectDir, ".cocacode/settings.json").absolutePath
                }
                SettingsSource.LOCAL -> {
                    File(projectDir, ".cocacode/settings.local.json").absolutePath
            }
            SettingsSource.FLAG -> null
            SettingsSource.POLICY -> {
                val configDir = getUserConfigDir()
                File(configDir, "managed-settings.json").absolutePath
            }
        }
    }

    private fun getUserConfigDir(): String {
        return System.getenv("COCACODE_CONFIG_DIR")
            ?: System.getenv("CLAUDE_CONFIG_DIR")
            ?: File(System.getProperty("user.home"), ".cocacode").absolutePath
    }

    fun loadSettingsForSource(source: SettingsSource, projectDir: String): JsonObject? {
        val cached = sessionCache[source]
        if (cached !== null || sessionCache.containsKey(source)) {
            return cached ?: null
        }

        val path = getSettingsPathForSource(source, projectDir) ?: return null
        val file = File(path)

        if (!file.exists()) {
            sessionCache[source] = null
            return null
        }

        return try {
            val content = file.readText()
            if (content.isBlank()) {
                sessionCache[source] = null
                return null
            }

            val parsed = json.parseToJsonElement(content)
            if (parsed is JsonObject) {
                sessionCache[source] = parsed
                parsed
            } else {
                sessionCache[source] = null
                null
            }
        } catch (e: Exception) {
            sessionCache[source] = null
            null
        }
    }

    fun getSettingsForSource(source: SettingsSource, projectDir: String): JsonObject? {
        return sessionCache.getOrPut(source) {
            loadSettingsForSource(source, projectDir)
        }
    }

    fun getEffectiveSettings(projectDir: String): JsonObject {
        effectiveSettings?.let { return it }

        val allConfigs = SettingsSource.getPriorityOrder().map { source ->
            getSettingsForSource(source, projectDir)
        }.filterNotNull()

        effectiveSettings = SettingsMerger.mergeAll(allConfigs)
        return effectiveSettings!!
    }

    fun updateSettings(source: SettingsSource, projectDir: String, updates: JsonObject): Result<Unit> {
        if (source == SettingsSource.POLICY || source == SettingsSource.FLAG) {
            return Result.failure(UnsupportedOperationException("Policy and flag sources are read-only"))
        }

        val path = getSettingsPathForSource(source, projectDir)
            ?: return Result.failure(IllegalStateException("No path for source: $source"))

        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val existing = loadSettingsForSource(source, projectDir) ?: JsonObject(emptyMap())
            val updated = SettingsMerger.merge(existing, updates)

            file.writeText(updated.toString())
            resetCache()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetCache() {
        sessionCache.clear()
        effectiveSettings = null
    }

    fun hasSettingsFile(source: SettingsSource, projectDir: String): Boolean {
        val path = getSettingsPathForSource(source, projectDir) ?: return false
        return File(path).exists()
    }

    fun getEnabledSourceCount(projectDir: String): Int {
        return SettingsSource.getPriorityOrder().count { source ->
            hasSettingsFile(source, projectDir)
        }
    }
}