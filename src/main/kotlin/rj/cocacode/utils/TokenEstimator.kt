package rj.cocacode.utils

import rj.cocacode.types.Message

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
    
    fun estimateTokens(text: String): Int = text.length / CHARS_PER_TOKEN
    
    fun estimateTokensForMessages(messages: List<String>): Int {
        return messages.sumOf { text -> this.estimateTokens(text) }
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
            // Use Message from rj.cocacode.types
            return null
        }
        return null
    }
}