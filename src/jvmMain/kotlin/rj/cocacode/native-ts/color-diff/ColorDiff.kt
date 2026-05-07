package rj.cocacode.nativets.colordiff

data class Hunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>
)

data class SyntaxTheme(val theme: String, val source: String?)

class ColorDiff(
    private val hunk: Hunk,
    private val firstLine: String?,
    private val filePath: String,
    private val prefixContent: String? = null
) {
    fun render(themeName: String, width: Int, dim: Boolean): List<String>? {
        return null
    }
}

class ColorFile(private val code: String, private val filePath: String) {
    fun render(themeName: String, width: Int, dim: Boolean): List<String>? {
        return null
    }
}

fun getSyntaxTheme(themeName: String): SyntaxTheme {
    val defaultTheme = when {
        themeName.contains("ansi") -> "ansi"
        themeName.contains("dark") -> "Monokai Extended"
        else -> "GitHub"
    }
    return SyntaxTheme(defaultTheme, null)
}

fun getNativeModule(): Any? {
    return null
}