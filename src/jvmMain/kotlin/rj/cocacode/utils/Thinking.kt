package rj.cocacode.utils

import java.util.regex.Pattern

sealed class ThinkingConfig {
    object Adaptive : ThinkingConfig()
    data class Enabled(val budgetTokens: Int) : ThinkingConfig()
    object Disabled : ThinkingConfig()
}

fun isUltrathinkEnabled(): Boolean {
    val flag = System.getenv("ULTRATHINK")
    if (flag != null) return flag.equals("true", ignoreCase = true)
    return false
}

fun hasUltrathinkKeyword(text: String): Boolean {
    val p = Pattern.compile("\\bultrathink\\b", Pattern.CASE_INSENSITIVE)
    return p.matcher(text).find()
}

data class ThinkingTriggerPosition(val word: String, val start: Int, val end: Int)

fun findThinkingTriggerPositions(text: String): List<ThinkingTriggerPosition> {
    val regex = Pattern.compile("\\bultrathink\\b", Pattern.CASE_INSENSITIVE)
    val m = regex.matcher(text)
    val positions = mutableListOf<ThinkingTriggerPosition>()
    while (m.find()) {
        val word = m.group()
        val start = m.start()
        val end = m.end()
        positions.add(ThinkingTriggerPosition(word, start, end))
    }
    return positions
}

private val RAINBOW_COLORS = arrayOf(
    "rainbow_red", "rainbow_orange", "rainbow_yellow", "rainbow_green", "rainbow_blue", "rainbow_indigo", "rainbow_violet"
)
private val RAINBOW_SHIMMER_COLORS = arrayOf(
    "rainbow_red_shimmer", "rainbow_orange_shimmer", "rainbow_yellow_shimmer", "rainbow_green_shimmer",
    "rainbow_blue_shimmer", "rainbow_indigo_shimmer", "rainbow_violet_shimmer"
)

enum class Theme {
    rainbow_red, rainbow_orange, rainbow_yellow, rainbow_green, rainbow_blue, rainbow_indigo, rainbow_violet,
    rainbow_red_shimmer, rainbow_orange_shimmer, rainbow_yellow_shimmer, rainbow_green_shimmer,
    rainbow_blue_shimmer, rainbow_indigo_shimmer, rainbow_violet_shimmer
}

fun getRainbowColor(charIndex: Int, shimmer: Boolean = false): Theme {
    val colors = if (shimmer) RAINBOW_SHIMMER_COLORS else RAINBOW_COLORS
    val idx = if (colors.isNotEmpty()) (charIndex % colors.size) else 0
    return Theme.valueOf(colors[idx])
}

fun modelSupportsThinking(model: String): Boolean {
    return true
}

fun modelSupportsAdaptiveThinking(model: String): Boolean {
    val canonical = getCanonicalName(model)
    if (canonical.contains("opus-4-6") || canonical.contains("sonnet-4-6")) return true
    if (canonical.contains("opus") || canonical.contains("sonnet") || canonical.contains("haiku")) return false
    val provider = getAPIProvider()
    return provider == "firstParty" || provider == "foundry"
}

fun shouldEnableThinkingByDefault(): Boolean {
    val maxThinking = System.getenv("MAX_THINKING_TOKENS")?.toIntOrNull()
    if (maxThinking != null) return maxThinking > 0
    val settings = getSettingsWithErrors()
    val always = settings.settings.alwaysThinkingEnabled
    return always != false
}

    private fun getCanonicalName(model: String): String = model
    private fun getAPIProvider(): String = "firstParty"
    private data class Settings(val alwaysThinkingEnabled: Boolean? = null)
    private data class SettingsWithErrors(val settings: Settings)
    private fun getSettingsWithErrors(): SettingsWithErrors = SettingsWithErrors(Settings(alwaysThinkingEnabled = true))
