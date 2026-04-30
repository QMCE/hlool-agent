package rj.cocacode.tools

import rj.cocacode.types.Tool

/**
 * ToolRegistry for managing available tools.
 */
object ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun get(name: String): Tool? = tools[name]
    
    fun all(): List<Tool> = tools.values.toList()
    
    fun clear() = tools.clear()
}
