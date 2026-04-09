package rj.cocacode.services.compact

import rj.cocacode.state.AppStateManager
import rj.cocacode.types.Message
import rj.cocacode.utils.TokenEstimator

object Compactor {
    data class CompactOptions(
        val maxMessages: Int = 50,
        val preserveFirstN: Int = 5,
        val preserveLastN: Int = 10,
        val summarizeThreshold: Int = 100
    )
    
    data class CompactResult(
        val messages: List<Message>,
        val removedCount: Int,
        val summary: String?
    )
    
    fun compact(messages: List<Message>, options: CompactOptions = CompactOptions()): CompactResult {
        if (messages.size <= options.maxMessages) {
            return CompactResult(messages, 0, null)
        }
        
        val first = messages.take(options.preserveFirstN)
        val last = messages.takeLast(options.preserveLastN)
        val middle = messages.drop(options.preserveFirstN).dropLast(options.preserveLastN)
        
        val summarized = if (middle.isNotEmpty()) {
            listOf(createSummaryMessage(middle))
        } else {
            emptyList()
        }
        
        val compacted = first + summarized + last
        
        return CompactResult(
            messages = compacted,
            removedCount = messages.size - compacted.size,
            summary = "Compacted ${messages.size - compacted.size} messages"
        )
    }
    
    private fun createSummaryMessage(removed: List<Message>): Message {
        val totalTokens = removed.sumOf { TokenEstimator.estimateTokens(it.content) }
        
        return Message(
            id = rj.cocacode.utils.generateUuid(),
            type = rj.cocacode.types.MessageType.SYSTEM,
            content = "[Compacted $removed.size messages, ~${totalTokens} tokens]"
        )
    }
    
    fun shouldCompact(messages: List<Message>): Boolean {
        return messages.size > 100
    }
    
    fun getMemoryUsage(messages: List<Message>): MemoryUsage {
        val totalChars = messages.sumOf { it.content.length }
        val estimatedTokens = TokenEstimator.estimateTokens(messages.joinToString { it.content })
        
        return MemoryUsage(
            messageCount = messages.size,
            totalCharacters = totalChars,
            estimatedTokens = estimatedTokens
        )
    }
}

data class MemoryUsage(
    val messageCount: Int,
    val totalCharacters: Int,
    val estimatedTokens: Int
)

object MemoryManager {
    private var maxMemory: Long = 100L * 1024L * 1024L
    
    fun setMaxMemory(bytes: Long) {
        maxMemory = bytes
    }
    
    fun getMaxMemory(): Long = maxMemory
    
    fun getCurrentMemory(): Long {
        val runtime = java.lang.Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    fun isMemoryPressure(): Boolean {
        return getCurrentMemory() > maxMemory * 0.8
    }
    
    fun forceGC() {
        System.gc()
    }
}