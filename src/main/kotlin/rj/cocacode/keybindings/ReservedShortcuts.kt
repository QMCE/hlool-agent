package rj.cocacode.keybindings

data class ReservedShortcut(
    val key: String,
    val reason: String,
    val severity: String
)

val NON_REBINDABLE = listOf(
    ReservedShortcut(
        key = "ctrl+c",
        reason = "Cannot be rebound - used for interrupt/exit (hardcoded)",
        severity = "error"
    ),
    ReservedShortcut(
        key = "ctrl+d",
        reason = "Cannot be rebound - used for exit (hardcoded)",
        severity = "error"
    ),
    ReservedShortcut(
        key = "ctrl+m",
        reason = "Cannot be rebound - identical to Enter in terminals (both send CR)",
        severity = "error"
    )
)

val TERMINAL_RESERVED = listOf(
    ReservedShortcut(
        key = "ctrl+z",
        reason = "Unix process suspend (SIGTSTP)",
        severity = "warning"
    ),
    ReservedShortcut(
        key = "ctrl+\\",
        reason = "Terminal quit signal (SIGQUIT)",
        severity = "error"
    )
)

val MACOS_RESERVED = listOf(
    ReservedShortcut(key = "cmd+c", reason = "macOS system copy", severity = "error"),
    ReservedShortcut(key = "cmd+v", reason = "macOS system paste", severity = "error"),
    ReservedShortcut(key = "cmd+x", reason = "macOS system cut", severity = "error"),
    ReservedShortcut(key = "cmd+q", reason = "macOS quit application", severity = "error"),
    ReservedShortcut(key = "cmd+w", reason = "macOS close window/tab", severity = "error"),
    ReservedShortcut(key = "cmd+tab", reason = "macOS app switcher", severity = "error"),
    ReservedShortcut(key = "cmd+space", reason = "macOS Spotlight", severity = "error")
)

fun getReservedShortcuts(): List<ReservedShortcut> {
    val platform = Platform.getPlatform()
    val reserved = NON_REBINDABLE + TERMINAL_RESERVED
    return if (platform == "macos") {
        reserved + MACOS_RESERVED
    } else {
        reserved
    }
}

fun normalizeKeyForComparison(key: String): String {
    return key.trim().split(Regex("\\s+")).joinToString(" ") { normalizeStep(it) }
}

private fun normalizeStep(step: String): String {
    val parts = step.split("+")
    val modifiers = mutableListOf<String>()
    var mainKey = ""
    
    for (part in parts) {
        val lower = part.trim().lowercase()
        when (lower) {
            "ctrl", "control" -> modifiers.add("ctrl")
            "alt", "opt", "option" -> modifiers.add("alt")
            "meta" -> modifiers.add("meta")
            "cmd", "command" -> modifiers.add("cmd")
            "shift" -> modifiers.add("shift")
            else -> mainKey = lower
        }
    }
    
    modifiers.sort()
    return (modifiers + mainKey).joinToString("+")
}
