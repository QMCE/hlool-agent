package rj.cocacode.permissions

/**
 * Permission mode types for the tool permission system.
 * 
 * These modes control how the system handles tool permission requests:
 * - default: Standard permission prompting
 * - plan: Plan mode with limited permissions
 * - acceptEdits: Accept all file edits without prompting
 * - bypassPermissions: Skip all permission checks
 * - dontAsk: Auto-deny all permission requests
 * - auto: Auto mode using AI classifier for decisions
 * - bubble: Special bubble mode
 */
enum class PermissionMode {
    /** Standard permission prompting mode */
    DEFAULT,
    
    /** Plan mode with limited permissions */
    PLAN,
    
    /** Accept all file edits without prompting */
    ACCEPT_EDITS,
    
    /** Skip all permission checks */
    BYPASS_PERMISSIONS,
    
    /** Auto-deny all permission requests */
    DONT_ASK,
    
    /** Auto mode using AI classifier for decisions */
    AUTO,
    
    /** Special bubble mode */
    BUBBLE;

    companion object {
        /**
         * Get PermissionMode from string value.
         * Returns DEFAULT if the string doesn't match any mode.
         */
        fun fromString(str: String): PermissionMode {
            return when (str.lowercase()) {
                "default" -> DEFAULT
                "plan" -> PLAN
                "acceptedits", "accept_edits" -> ACCEPT_EDITS
                "bypasspermissions", "bypass_permissions" -> BYPASS_PERMISSIONS
                "dontask", "dont_ask" -> DONT_ASK
                "auto" -> AUTO
                "bubble" -> BUBBLE
                else -> DEFAULT
            }
        }
    }
}

/**
 * Mode color keys for UI display
 */
enum class ModeColorKey {
    TEXT,
    PLAN_MODE,
    PERMISSION,
    AUTO_ACCEPT,
    ERROR,
    WARNING
}

/**
 * Configuration for a permission mode
 */
data class PermissionModeConfig(
    val title: String,
    val shortTitle: String,
    val symbol: String,
    val color: ModeColorKey,
    val external: ExternalPermissionMode
)

/**
 * External permission modes (user-addressable)
 */
enum class ExternalPermissionMode {
    ACCEPT_EDITS,
    BYPASS_PERMISSIONS,
    DEFAULT,
    DONT_ASK,
    PLAN
}

/**
 * Get the configuration for a permission mode.
 */
fun getModeConfig(mode: PermissionMode): PermissionModeConfig {
    return when (mode) {
        PermissionMode.DEFAULT -> PermissionModeConfig(
            title = "Default",
            shortTitle = "Default",
            symbol = "",
            color = ModeColorKey.TEXT,
            external = ExternalPermissionMode.DEFAULT
        )
        PermissionMode.PLAN -> PermissionModeConfig(
            title = "Plan Mode",
            shortTitle = "Plan",
            symbol = "⏸",
            color = ModeColorKey.PLAN_MODE,
            external = ExternalPermissionMode.PLAN
        )
        PermissionMode.ACCEPT_EDITS -> PermissionModeConfig(
            title = "Accept edits",
            shortTitle = "Accept",
            symbol = "⏵⏵",
            color = ModeColorKey.AUTO_ACCEPT,
            external = ExternalPermissionMode.ACCEPT_EDITS
        )
        PermissionMode.BYPASS_PERMISSIONS -> PermissionModeConfig(
            title = "Bypass Permissions",
            shortTitle = "Bypass",
            symbol = "⏵⏵",
            color = ModeColorKey.ERROR,
            external = ExternalPermissionMode.BYPASS_PERMISSIONS
        )
        PermissionMode.DONT_ASK -> PermissionModeConfig(
            title = "Don't Ask",
            shortTitle = "DontAsk",
            symbol = "⏵⏵",
            color = ModeColorKey.ERROR,
            external = ExternalPermissionMode.DONT_ASK
        )
        PermissionMode.AUTO -> PermissionModeConfig(
            title = "Auto mode",
            shortTitle = "Auto",
            symbol = "⏵⏵",
            color = ModeColorKey.WARNING,
            external = ExternalPermissionMode.DEFAULT
        )
        PermissionMode.BUBBLE -> PermissionModeConfig(
            title = "Bubble",
            shortTitle = "Bubble",
            symbol = "⏵⏵",
            color = ModeColorKey.WARNING,
            external = ExternalPermissionMode.DEFAULT
        )
    }
}

/**
 * Check if a PermissionMode is an ExternalPermissionMode.
 * AUTO and BUBBLE are internal-only modes.
 */
fun isExternalPermissionMode(mode: PermissionMode): Boolean {
    return mode != PermissionMode.AUTO && mode != PermissionMode.BUBBLE
}

/**
 * Convert to external permission mode.
 */
fun toExternalPermissionMode(mode: PermissionMode): ExternalPermissionMode {
    return getModeConfig(mode).external
}

/**
 * Get the title for a permission mode.
 */
fun permissionModeTitle(mode: PermissionMode): String {
    return getModeConfig(mode).title
}

/**
 * Check if a mode is the default mode.
 */
fun isDefaultMode(mode: PermissionMode?): Boolean {
    return mode == null || mode == PermissionMode.DEFAULT
}

/**
 * Get the short title for a permission mode.
 */
fun permissionModeShortTitle(mode: PermissionMode): String {
    return getModeConfig(mode).shortTitle
}

/**
 * Get the symbol for a permission mode.
 */
fun permissionModeSymbol(mode: PermissionMode): String {
    return getModeConfig(mode).symbol
}

/**
 * Get the color key for a permission mode.
 */
fun getModeColor(mode: PermissionMode): ModeColorKey {
    return getModeConfig(mode).color
}
