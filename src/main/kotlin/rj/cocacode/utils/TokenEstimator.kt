package rj.cocacode.utils

import java.lang.System

data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0
) {
    fun totalTokens(): Int = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens
    
    fun contextTokens(): Int = inputTokens + cacheCreationInputTokens + cacheReadInputTokens
}

object TokenEstimator {
    private const val CHARS_PER_TOKEN = 4
    
    data class TokenUsage(
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val cacheCreationInputTokens: Int = 0,
        val cacheReadInputTokens: Int = 0
    ) {
        fun totalTokens(): Int = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens
        
        fun contextTokens(): Int = inputTokens + cacheCreationInputTokens + cacheReadInputTokens
    }
    
    fun estimateTokens(text: String): Int = text.length / CHARS_PER_TOKEN
    
    fun estimateTokensForMessages(messages: List<String>): Int {
        return messages.sumOf { estimateTokens(it) }
    }
    
    fun estimateFromUsage(usage: TokenUsage): Int {
        return usage.inputTokens + 
               usage.cacheCreationInputTokens + 
               usage.cacheReadInputTokens + 
               usage.outputTokens
    }
    
    fun getLastUsageFromMessages(messages: List<Message>): TokenUsage? {
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (msg is Message.Assistant && msg.usage != null) {
                val usage = msg.usage
                return TokenUsage(
                    inputTokens = usage.inputTokens,
                    outputTokens = usage.outputTokens,
                    cacheCreationInputTokens = usage.cacheCreationInputTokens,
                    cacheReadInputTokens = usage.cacheReadInputTokens
                )
            }
        }
        return null
    }
}

sealed class Message {
    data class User(val content: String, val timestamp: Long = java.lang.System.currentTimeMillis()) : Message()
    data class Assistant(val content: String, val usage: TokenUsage? = null, val model: String? = null) : Message()
    data class System(val content: String) : Message()
    data class Tool(val name: String, val input: String, val output: String) : Message()
}