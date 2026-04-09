package rj.cocacode.ink

fun wrapAnsi(input: String, columns: Int, options: WrapAnsiOptions? = null): String {
    return input
}

data class WrapAnsiOptions(
    val hard: Boolean = false,
    val wordWrap: Boolean = true,
    val trim: Boolean = false
)