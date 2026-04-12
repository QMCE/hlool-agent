package rj.cocacode.utils

private fun stringWidth(text: String): Int {
    return text.codePointCount(0, text.length)
}

fun truncatePathMiddle(path: String, maxLength: Int): String {
    if (stringWidth(path) <= maxLength) return path
    if (maxLength <= 0) return "…"
    if (maxLength < 5) return truncateToWidth(path, maxLength)
    val lastSlash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    val filename = if (lastSlash >= 0) path.substring(lastSlash) else path
    val directory = if (lastSlash >= 0) path.substring(0, lastSlash) else ""
    val filenameWidth = stringWidth(filename)
    if (filenameWidth >= maxLength - 1) {
        return truncateStartToWidth(path, maxLength)
    }
    val availableForDir = maxLength - 1 - filenameWidth
    if (availableForDir <= 0) {
        return truncateStartToWidth(filename, maxLength)
    }
    val truncatedDir = truncateToWidthNoEllipsis(directory, availableForDir)
    return truncatedDir + "…" + filename
}

fun truncateToWidth(text: String, maxWidth: Int): String {
    if (stringWidth(text) <= maxWidth) return text
    if (maxWidth <= 1) return "…"
    var width = 0
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val charCount = Character.charCount(cp)
        val segWidth = 1
        if (width + segWidth > maxWidth - 1) break
        sb.appendCodePoint(cp)
        width += segWidth
        i += charCount
    }
    return sb.toString() + "…"
}

private fun truncateToWidthNoEllipsis(text: String, maxWidth: Int): String {
    if (stringWidth(text) <= maxWidth) return text
    if (maxWidth <= 0) return ""
    var width = 0
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val charCount = Character.charCount(cp)
        val segWidth = 1
        if (width + segWidth > maxWidth) break
        sb.appendCodePoint(cp)
        width += segWidth
        i += charCount
    }
    return sb.toString()
}

fun truncateStartToWidth(text: String, maxWidth: Int): String {
    if (stringWidth(text) <= maxWidth) return text
    if (maxWidth <= 1) return "…"
    var i = text.length
    var startIdx = text.length
    var width = 0
    while (i > 0) {
        val cp = Character.codePointBefore(text, i)
        val charCount = Character.charCount(cp)
        if (width + 1 > maxWidth - 1) break
        width += 1
        i -= charCount
        startIdx = i
    }
    return "…" + text.substring(startIdx)
}

fun truncate(str: String, maxWidth: Int, singleLine: Boolean = false): String {
    var result = str
    if (singleLine) {
        val firstNewline = str.indexOf('\n')
        if (firstNewline != -1) {
            result = str.substring(0, firstNewline)
            return if (stringWidth(result) + 1 > maxWidth) truncateToWidth(result, maxWidth) else "$result…"
        }
    }
    if (stringWidth(result) <= maxWidth) return result
    return truncateToWidth(result, maxWidth)
}

fun wrapText(text: String, width: Int): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    var currentWidth = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val chCount = Character.charCount(cp)
        val segWidth = 1
        val seg = String(Character.toChars(cp))
        if (currentWidth + segWidth <= width) {
            current.append(seg)
            currentWidth += segWidth
        } else {
            if (current.isNotEmpty()) lines.add(current.toString())
            current = StringBuilder(seg)
            currentWidth = segWidth
        }
        i += chCount
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
}
