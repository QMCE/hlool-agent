package rj.cocacode.config

/**
 * Feature flags for enabling/disabling features.
 */
object FeatureFlags {
    var MCP_ENABLED = true
    var STREAMING_ENABLED = true
    var TOOL_USE_ENABLED = true
    var AUTO_COMPACT_ENABLED = true
    var MEMORY_ENABLED = false
    var SKILLS_ENABLED = false

    fun isEnabled(flag: String): Boolean = when (flag) {
        "mcp" -> MCP_ENABLED
        "streaming" -> STREAMING_ENABLED
        "tool_use" -> TOOL_USE_ENABLED
        "auto_compact" -> AUTO_COMPACT_ENABLED
        "memory" -> MEMORY_ENABLED
        "skills" -> SKILLS_ENABLED
        else -> false
    }
}
