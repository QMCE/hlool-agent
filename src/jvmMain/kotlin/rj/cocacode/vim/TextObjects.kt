package rj.cocacode.vim

data class TextObjectRange(val start: Int, val end: Int)

fun findTextObject(text: String, offset: Int, objectType: String, isInner: Boolean): TextObjectRange? {
  return null
}
