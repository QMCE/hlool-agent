package rj.cocacode.ink

fun wrapText(text: String, maxWidth: Int, wrapType: String): String {
    if (wrapType == "wrap") {
        return wrapAnsi(text, maxWidth, WrapAnsiOptions(hard = true, trim = false))
    }
    if (wrapType == "wrap-trim") {
        return wrapAnsi(text, maxWidth, WrapAnsiOptions(hard = true, trim = true))
    }
    if (wrapType.startsWith("truncate")) {
        return truncate(text, maxWidth, if (wrapType == "truncate-middle") "middle" else if (wrapType == "truncate-start") "start" else "end")
    }
    return text
}

private fun truncate(text: String, columns: Int, position: String): String {
    if (columns < 1) return ""
    if (columns == 1) return "…"
    
    val length = stringWidth(text)
    if (length <= columns) return text
    
    return when (position) {
        "start" -> "…" + text.takeLast(columns - 1)
        "middle" -> {
            val half = columns / 2
            val firstHalf = text.take(half)
            val secondHalf = text.takeLast(columns - half - 1)
            firstHalf + "…" + secondHalf
        }
        else -> text.take(columns - 1) + "…"
    }
}