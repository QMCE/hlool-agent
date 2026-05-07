package rj.cocacode.vim

interface Cursor

fun resolveMotion(key: String, cursor: Cursor, count: Int): Cursor {
  return cursor
}

fun isInclusiveMotion(key: String): Boolean = true

fun isLinewiseMotion(key: String): Boolean = false
