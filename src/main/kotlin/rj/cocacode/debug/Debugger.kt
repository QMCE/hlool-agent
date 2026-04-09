package rj.cocacode.debug

import rj.cocacode.utils.LogManager

object Debugger {
    private var isEnabled = false
    private val breakpoints = mutableSetOf<Int>()
    private val watchExpressions = mutableMapOf<String, () -> Any?>()
    private var stepMode = false
    
    fun enable() {
        isEnabled = true
        LogManager.logInfo("Debugger enabled")
    }
    
    fun disable() {
        isEnabled = false
        LogManager.logInfo("Debugger disabled")
    }
    
    fun isActive(): Boolean = isEnabled
    
    fun setBreakpoint(line: Int) {
        breakpoints.add(line)
        LogManager.logDebug("Breakpoint set at line $line")
    }
    
    fun removeBreakpoint(line: Int) {
        breakpoints.remove(line)
        LogManager.logDebug("Breakpoint removed from line $line")
    }
    
    fun clearBreakpoints() {
        breakpoints.clear()
    }
    
    fun getBreakpoints(): Set<Int> = breakpoints.toSet()
    
    fun hasBreakpoint(line: Int): Boolean = line in breakpoints
    
    fun addWatch(expression: String, eval: () -> Any?) {
        watchExpressions[expression] = eval
    }
    
    fun removeWatch(expression: String) {
        watchExpressions.remove(expression)
    }
    
    fun evaluateWatch(expression: String): Any? {
        return watchExpressions[expression]?.invoke()
    }
    
    fun getAllWatches(): Map<String, Any?> {
        return watchExpressions.mapValues { it.value.invoke() }
    }
    
    fun step() {
        stepMode = true
        LogManager.logDebug("Step mode enabled")
    }
    
    fun next() {
        stepMode = true
    }
    
    fun finish() {
        stepMode = false
    }
    
    fun isStepMode(): Boolean = stepMode
}

class StackTrace {
    data class Frame(
        val fileName: String,
        val methodName: String,
        val lineNumber: Int,
        val columnNumber: Int = 0
    )
    
    fun capture(currentFrame: Int = 0): List<Frame> {
        val frames = mutableListOf<Frame>()
        val elements = Thread.currentThread().stackTrace
        
        for (i in (currentFrame + 1) until elements.size) {
            val element = elements[i]
            val elementStr = element.toString()
            val parts = elementStr.split(".")
            if (parts.size >= 2) {
                frames.add(Frame(
                    fileName = parts.last(),
                    methodName = parts.dropLast(1).joinToString("."),
                    lineNumber = extractLineNumber(elementStr)
                ))
            }
        }
        
        return frames
    }
    
    private fun extractLineNumber(element: String): Int {
        val match = Regex(":(\\d+)").find(element)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
    
    fun format(frames: List<Frame>): String {
        return frames.mapIndexed { index, frame ->
            "  at $index: ${frame.methodName} (${frame.fileName}:${frame.lineNumber})"
        }.joinToString("\n")
    }
}

object BreakpointManager {
    private val breakpoints = mutableMapOf<String, MutableSet<Int>>()
    
    fun set(file: String, line: Int, condition: String? = null) {
        breakpoints.getOrPut(file) { mutableSetOf<Int>() }.add(line)
        if (condition != null) {
            ConditionalBreakpoint.set(file, line, condition)
        }
    }
    
    fun remove(file: String, line: Int) {
        breakpoints[file]?.remove(line)
        ConditionalBreakpoint.remove(file, line)
    }
    
    fun has(file: String, line: Int): Boolean {
        return breakpoints[file]?.contains(line) == true
    }
    
    fun getBreakpoints(file: String): Set<Int> {
        return breakpoints[file]?.toSet() ?: emptySet()
    }
    
    fun clear(file: String) {
        breakpoints.remove(file)
    }
    
    fun clearAll() {
        breakpoints.clear()
    }
}

object ConditionalBreakpoint {
    private val conditions = mutableMapOf<String, String>()
    
    fun set(file: String, line: Int, condition: String) {
        conditions["$file:$line"] = condition
    }
    
    fun remove(file: String, line: Int) {
        conditions.remove("$file:$line")
    }
    
    fun evaluate(file: String, line: Int, context: Map<String, () -> Any?>): Boolean {
        val condition = conditions["$file:$line"] ?: return true
        
        return try {
            val result = evaluateExpression(condition, context)
            result as? Boolean ?: true
        } catch (e: Exception) {
            LogManager.logError("Breakpoint condition error: ${e.message}")
            true
        }
    }
    
    private fun evaluateExpression(expr: String, context: Map<String, () -> Any?>): Any? {
        return context[expr]?.invoke()
    }
}

object Watchpoint {
    private val watches = mutableMapOf<String, (() -> Any?)>()
    
    fun add(name: String, eval: () -> Any?) {
        watches[name] = eval
    }
    
    fun remove(name: String) {
        watches.remove(name)
    }
    
    fun evaluate(name: String): Any? {
        return watches[name]?.invoke()
    }
    
    fun evaluateAll(): Map<String, Any?> {
        return watches.mapValues { it.value.invoke() }
    }
}