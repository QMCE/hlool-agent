package rj.cocacode.utils

import rj.cocacode.constants.Product
import java.io.File

/**
 * Resolves config / data directories for Hlool Agent.
 *
 * Preference order for the home dir:
 *  1. `HLOOL_CONFIG_DIR` / `COCACODE_CONFIG_DIR` / `CLAUDE_CONFIG_DIR`
 *  2. `~/.hlool-agent` (create if missing)
 *
 * When reading the first time, if `~/.hlool-agent/config.json` is absent but
 * a legacy `~/.cocacode/config.json` exists, that file is used (and copied
 * into the new home on save).
 */
object AppPaths {
    fun configHome(): File {
        val override = env("HLOOL_CONFIG_DIR")
            ?: env("COCACODE_CONFIG_DIR")
            ?: env("CLAUDE_CONFIG_DIR")
        if (override != null) return File(override).also { it.mkdirs() }

        val home = System.getProperty("user.home") ?: "."
        val primary = File(home, Product.CONFIG_DIR_NAME)
        if (!primary.exists()) primary.mkdirs()
        return primary
    }

    fun legacyConfigHome(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(home, Product.LEGACY_CONFIG_DIR_NAME)
    }

    fun configFile(): File = File(configHome(), "config.json")

    /** Prefer the new config; fall back to legacy CocaCode config if present. */
    fun resolveConfigFileForRead(): File {
        val primary = configFile()
        if (primary.exists()) return primary
        val legacy = File(legacyConfigHome(), "config.json")
        if (legacy.exists()) return legacy
        return primary
    }

    fun sessionsDir(): File = File(configHome(), "sessions").also { it.mkdirs() }

    fun historyDir(): File = File(configHome(), "history").also { it.mkdirs() }

    fun projectSettingsDir(projectDir: String): File =
        File(projectDir, Product.CONFIG_DIR_NAME)

    private fun env(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
}
