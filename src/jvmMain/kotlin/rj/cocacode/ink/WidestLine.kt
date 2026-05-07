package rj.cocacode.ink

fun widestLine(input: String): Int {
    var maxWidth = 0
    var start = 0
    
    while (start <= input.length) {
        val end = input.indexOf('\n', start)
        val line = if (end == -1) input.substring(start) else input.substring(start, end)
        
        maxWidth = maxOf(maxWidth, lineWidth(line))
        
        if (end == -1) break
        start = end + 1
    }
    
    return maxWidth
}

private fun lineWidth(line: String): Int {
    return stringWidth(line)
}