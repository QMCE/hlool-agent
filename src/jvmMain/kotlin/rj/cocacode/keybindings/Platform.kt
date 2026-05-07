package rj.cocacode.keybindings

object Platform {
    fun getPlatform(): String {
        return try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> "macos"
                os.contains("windows") -> "windows"
                os.contains("linux") -> "linux"
                else -> "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}

fun getPlatform(): String = Platform.getPlatform()
