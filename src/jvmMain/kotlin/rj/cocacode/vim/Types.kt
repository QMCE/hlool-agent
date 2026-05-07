package rj.cocacode.vim

const val MAX_VIM_COUNT = 10000

typealias FindType = String
enum class TextObjScopeEnum { INNER, AROUND }
typealias TextObjScope = TextObjScopeEnum

enum class Operator { Delete, Change, Yank }

sealed class VimState {
  data class Insert(val insertedText: String) : VimState()
  data class Normal(val command: CommandState) : VimState()
}

sealed class CommandState {
  object Idle : CommandState()
  data class Count(val digits: String) : CommandState()
  data class Operator(val op: Operator, val count: Int) : CommandState()
  data class OperatorCount(val op: Operator, val count: Int, val digits: String) : CommandState()
  data class OperatorFind(val op: Operator, val count: Int, val find: FindType) : CommandState()
  data class OperatorTextObj(val op: Operator, val count: Int, val scope: TextObjScope) : CommandState()
  data class Find(val find: FindType, val count: Int) : CommandState()
  data class G(val count: Int) : CommandState()
  data class OperatorG(val op: Operator, val count: Int) : CommandState()
  data class Replace(val count: Int) : CommandState()
  data class Indent(val dir: Char, val count: Int) : CommandState()
}

data class LastFind(val type: FindType, val char: String)
sealed class RecordedChange {
  class Insert(val text: String) : RecordedChange()
  data class Operator(val op: Operator, val motion: String, val count: Int) : RecordedChange()
  data class OperatorTextObj(val op: Operator, val objType: String, val scope: TextObjScope, val count: Int) : RecordedChange()
  data class OperatorFind(val op: Operator, val find: FindType, val char: String, val count: Int) : RecordedChange()
  data class Replace(val ch: String, val count: Int) : RecordedChange()
  data class X(val count: Int) : RecordedChange()
  data class ToggleCase(val count: Int) : RecordedChange()
  data class Indent(val dir: Char, val count: Int) : RecordedChange()
  data class OpenLine(val direction: String) : RecordedChange()
  data class Join(val count: Int) : RecordedChange()
}

data class PersistentState(
  val lastChange: RecordedChange? = null,
  val lastFind: LastFind? = null,
  val register: String = "",
  val registerIsLinewise: Boolean = false
)

fun createInitialVimState(): VimState = VimState.Insert("")
fun createInitialPersistentState(): PersistentState = PersistentState()

val OPERATORS: Map<Char, Operator> = mapOf(
  'd' to Operator.Delete,
  'c' to Operator.Change,
  'y' to Operator.Yank
)

fun isOperatorKey(key: String): Boolean = key.length == 1 && OPERATORS.containsKey(key[0])

val SIMPLE_MOTIONS: Set<String> = setOf(
  "h",
  "l",
  "j",
  "k",
  "W",
  "B",
  "E",
  "0",
  "^",
  "$",
  "w",
  "b",
  "e"
)

val FIND_KEYS: Set<String> = setOf("f", "F", "t", "T")

val TEXT_OBJ_SCOPES: Map<String, TextObjScope> = mapOf(
  "i" to TextObjScope.INNER,
  "a" to TextObjScope.AROUND
)

fun isTextObjScopeKey(key: String): Boolean = TEXT_OBJ_SCOPES.containsKey(key)

val TEXT_OBJ_TYPES: Set<String> = setOf(
  "w",
  "W",
  "\"",
  "'",
  "`",
  "(",
  ")",
  "b",
  "[",
  "]",
  "{",
  "}",
  "B",
  "<",
  ">",
)
