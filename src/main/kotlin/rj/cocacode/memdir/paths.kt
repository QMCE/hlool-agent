package rj.cocacode.memdir

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MemoryPathUtils {
    private const val AUTO_MEM_DIRNAME = "memory"
    private const val AUTO_MEM_ENTRYPOINT_NAME = "MEMORY.md"

    fun isAutoMemoryEnabled(): Boolean {
        val envVal = System.getenv("CLAUDE_CODE_DISABLE_AUTO_MEMORY")
        if (envVal != null) {
            val v = envVal.lowercase()
            if (v == "1" || v == "true" || v == "yes" || v == "on") return false
        }
        return true
    }

    fun getMemoryBaseDir(): String {
        val remote = System.getenv("CLAUDE_CODE_REMOTE_MEMORY_DIR")
        if (!remote.isNullOrEmpty()) return remote
        return getClaudeConfigHomeDir()
    }

    fun getClaudeConfigHomeDir(): String {
        val home = System.getProperty("user.home") ?: ""
        return File(home, ".claude").absolutePath
    }

    private val _autoMemPath: String by lazy {
        val override = getAutoMemPathOverride()
        if (override != null) return@lazy override
        val base = getAutoMemBase()
        val path = Paths.get(base, "projects", sanitizePath(getAutoMemBase()), AUTO_MEM_DIRNAME).toAbsolutePath().toString()
        path
    }

    fun getAutoMemPath(): String = _autoMemPath

    fun getAutoMemEntrypoint(): String {
        return Paths.get(getAutoMemPath(), AUTO_MEM_ENTRYPOINT_NAME).toString()
    }

    fun getAutoMemDailyLogPath(date: LocalDate = LocalDate.now()): String {
        val yyyy = date.year.toString()
        val mm = date.monthValue.toString().padStart(2, '0')
        val dd = date.dayOfMonth.toString().padStart(2, '0')
        return Paths.get(getAutoMemPath(), "logs", yyyy, mm, "${yyyy}-${mm}-${dd}.md").toString()
    }

    fun getAutoMemPathOverride(): String? {
        val v = System.getenv("CLAUDE_COWORK_MEMORY_PATH_OVERRIDE") ?: return null
        val p = Paths.get(v)
        return if (p.isAbsolute) v else null
    }

    fun getAutoMemPathSetting(): String? {
        return null
    }

    fun hasAutoMemPathOverride(): Boolean = getAutoMemPathOverride() != null

    fun getAutoMemBase(): String {
        return getProjectRoot()
    }

    fun getProjectRoot(): String {
        return System.getProperty("user.dir") ?: "."
    }

    fun isAutoMemPath(absolutePath: String): Boolean {
        val normalized = Paths.get(absolutePath).normalize().toString()
        return normalized.startsWith(getAutoMemPath())
    }

    private fun sanitizePath(p: String): String {
        // naive sanitation: remove trailing separators
        var s = p
        while (s.endsWith(File.separator)) s = s.dropLast(1)
        return s
    }
}
