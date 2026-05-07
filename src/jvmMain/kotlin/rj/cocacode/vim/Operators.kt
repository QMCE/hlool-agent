package rj.cocacode.vim

class OperatorContext

fun executeOperatorMotion(op: Operator, motion: String, count: Int, ctx: OperatorContext) {}

fun executeOperatorFind(op: Operator, findType: FindType, char: String, count: Int, ctx: OperatorContext) {}

fun executeOperatorTextObj(op: Operator, scope: TextObjScope, objType: String, count: Int, ctx: OperatorContext) {}

fun executeLineOp(op: Operator, count: Int, ctx: OperatorContext) {}

fun executeX(count: Int, ctx: OperatorContext) {}

fun executeReplace(char: String, count: Int, ctx: OperatorContext) {}

fun executeToggleCase(count: Int, ctx: OperatorContext) {}

fun executeJoin(count: Int, ctx: OperatorContext) {}

fun executePaste(after: Boolean, count: Int, ctx: OperatorContext) {}

fun executeIndent(dir: Char, count: Int, ctx: OperatorContext) {}

fun executeOpenLine(direction: String, ctx: OperatorContext) {}
