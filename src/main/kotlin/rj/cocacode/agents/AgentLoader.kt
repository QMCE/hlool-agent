package rj.cocacode.agents

import rj.cocacode.utils.generateUuid

data class AgentDefinition(
    val id: String = generateUuid(),
    val name: String,
    val description: String,
    val color: String = "#007acc",
    val systemPrompt: String = "",
    val tools: List<String> = emptyList(),
    val model: String? = null,
    val enabled: Boolean = true
)

object AgentLoader {
    private val agents = mutableMapOf<String, AgentDefinition>()
    private val agentDirs = mutableListOf<String>()
    
    fun addAgentDir(path: String) {
        agentDirs.add(path)
    }
    
    fun loadAgents(): List<AgentDefinition> {
        agents.clear()
        
        loadDefaultAgents()
        
        return agents.values.toList()
    }
    
    private fun loadDefaultAgents() {
        val defaultAgents = listOf(
            AgentDefinition(
                name = "default",
                description = "Default agent for general tasks",
                color = "#007acc"
            ),
            AgentDefinition(
                name = "general",
                description = "General purpose assistant",
                color = "#28a745"
            ),
            AgentDefinition(
                name = "code",
                description = "Specialized in code review and refactoring",
                color = "#6f42c1"
            ),
            AgentDefinition(
                name = "debug",
                description = "Helps debug and fix issues",
                color = "#dc3545"
            )
        )
        
        defaultAgents.forEach { agents[it.name] = it }
    }
    
    fun getAgent(name: String): AgentDefinition? = agents[name]
    
    fun getAllAgents(): List<AgentDefinition> = agents.values.toList()
    
    fun getEnabledAgents(): List<AgentDefinition> = agents.values.filter { it.enabled }
    
    fun registerAgent(agent: AgentDefinition) {
        agents[agent.name] = agent
    }
    
    fun unregisterAgent(name: String) {
        agents.remove(name)
    }
    
    fun enableAgent(name: String) {
        agents[name]?.let { agents[name] = it.copy(enabled = true) }
    }
    
    fun disableAgent(name: String) {
        agents[name]?.let { agents[name] = it.copy(enabled = false) }
    }
}

object AgentExecutor {
    suspend fun executeAgent(
        agent: AgentDefinition,
        prompt: String,
        context: Map<String, Any> = emptyMap()
    ): AgentResult {
        val fullPrompt = buildPrompt(agent, prompt, context)
        
        val engine = rj.cocacode.engine.QueryEngineManager.getEngine()
        val response = engine.processQuery(fullPrompt)
        
        return when (response) {
            is rj.cocacode.engine.QueryResponse.Success -> {
                AgentResult.Success(response.response.content)
            }
            is rj.cocacode.engine.QueryResponse.Error -> {
                AgentResult.Error(response.message)
            }
        }
    }
    
    private fun buildPrompt(agent: AgentDefinition, prompt: String, context: Map<String, Any>): String {
        return buildString {
            appendLine(agent.systemPrompt)
            appendLine()
            if (context.isNotEmpty()) {
                appendLine("Context:")
                for ((key, value) in context) {
                    appendLine("$key: $value")
                }
                appendLine()
            }
            appendLine("Task: $prompt")
        }
    }
}

sealed class AgentResult {
    data class Success(val output: String) : AgentResult()
    data class Error(val message: String) : AgentResult()
}

class AgentManager {
    private var currentAgent: AgentDefinition? = null
    
    fun setCurrentAgent(agent: AgentDefinition) {
        currentAgent = agent
    }
    
    fun getCurrentAgent(): AgentDefinition? = currentAgent
    
    fun switchAgent(name: String): Boolean {
        val agent = AgentLoader.getAgent(name)
        if (agent != null && agent.enabled) {
            currentAgent = agent
            return true
        }
        return false
    }
}