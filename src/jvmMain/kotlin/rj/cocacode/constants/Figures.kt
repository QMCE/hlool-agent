package rj.cocacode.constants

import rj.cocacode.utils.getPlatform
import rj.cocacode.utils.Platform

object Figures {
    val BLACK_CIRCLE = if (getPlatform() == Platform.MACOS) "⏺" else "●"
    const val BULLET_OPERATOR = "∙"
    const val TEARDROP_ASTERISK = "✻"
    const val UP_ARROW = "↑"
    const val DOWN_ARROW = "↓"
    const val LIGHTNING_BOLT = "↯"
    const val EFFORT_LOW = "○"
    const val EFFORT_MEDIUM = "◐"
    const val EFFORT_HIGH = "●"
    const val EFFORT_MAX = "◉"
    const val PLAY_ICON = "▶"
    const val PAUSE_ICON = "⏸"
    const val REFRESH_ARROW = "↻"
    const val CHANNEL_ARROW = "←"
    const val INJECTED_ARROW = "→"
    const val FORK_GLYPH = "⑂"
    const val DIAMOND_OPEN = "◇"
    const val DIAMOND_FILLED = "◆"
    const val REFERENCE_MARK = "※"
    const val FLAG_ICON = "⚑"
    const val BLOCKQUOTE_BAR = "▎"
    const val HEAVY_HORIZONTAL = "━"
    
    val BRIDGE_SPINNER_FRAMES = listOf("·|·", "·/·", "·—·", "·\\·")
    const val BRIDGE_READY_INDICATOR = "·✓·"
    const val BRIDGE_FAILED_INDICATOR = "×"
}