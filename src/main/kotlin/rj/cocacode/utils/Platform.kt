package rj.cocacode.utils

enum class Platform {
    MACOS, WINDOWS, WSL, LINUX, UNKNOWN
}

val SUPPORTED_PLATFORMS = listOf(Platform.MACOS, Platform.WSL)

fun getPlatform(): Platform {
    return when (System.getProperty("os.name")) {
        "Mac OS X" -> Platform.MACOS
        else -> when (System.getProperty("os.name")) {
            "Linux" -> Platform.LINUX
            else -> Platform.UNKNOWN
        }
    }
}

data class LinuxDistroInfo(
    val linuxDistroId: String? = null,
    val linuxDistroVersion: String? = null,
    val linuxKernel: String? = null
)

private val VCS_MARKERS = listOf(
    ".git" to "git",
    ".hg" to "mercurial",
    ".svn" to "svn",
    ".p4config" to "perforce",
    ".tf" to "tfs",
    ".tfvc" to "tfs",
    ".jj" to "jujutsu",
    ".sl" to "sapling"
)

fun detectVcs(dir: String? = null): List<String> {
    val detected = mutableSetOf<String>()
    
    System.getenv("P4PORT")?.let { detected.add("perforce") }
    
    val targetDir = dir ?: System.getProperty("user.dir")
    try {
        java.io.File(targetDir).list()?.let { entries ->
            for ((marker, vcs) in VCS_MARKERS) {
                if (entries.contains(marker)) {
                    detected.add(vcs)
                }
            }
        }
    } catch (_: Exception) { }
    
    return detected.toList()
}