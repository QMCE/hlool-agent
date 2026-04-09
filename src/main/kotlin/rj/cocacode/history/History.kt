package rj.cocacode.history

import rj.cocacode.utils.generateUuid
import java.io.File

data class HistoryEntry(
    val id: String = generateUuid(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: EntryType,
    val content: String,
    val metadata: Map<String, String> = emptyMap()
)

enum class EntryType {
    QUERY, RESPONSE, COMMAND, TOOL_USE, ERROR, SYSTEM
}

class History(private val maxEntries: Int = 1000) {
    private val entries = mutableListOf<HistoryEntry>()
    private val listeners = mutableListOf<(HistoryEntry) -> Unit>()
    
    fun add(type: EntryType, content: String, metadata: Map<String, String> = emptyMap()) {
        val entry = HistoryEntry(type = type, content = content, metadata = metadata)
        entries.add(entry)
        
        if (entries.size > maxEntries) {
            entries.removeAt(0)
        }
        
        notifyListeners(entry)
    }
    
    fun query(content: String) = add(EntryType.QUERY, content)
    fun response(content: String) = add(EntryType.RESPONSE, content)
    fun command(cmd: String) = add(EntryType.COMMAND, cmd)
    fun toolUse(tool: String) = add(EntryType.TOOL_USE, tool)
    fun error(msg: String) = add(EntryType.ERROR, msg)
    fun system(msg: String) = add(EntryType.SYSTEM, msg)
    
    fun getAll(): List<HistoryEntry> = entries.toList()
    
    fun getRecent(count: Int = 10): List<HistoryEntry> = entries.takeLast(count)
    
    fun getByType(type: EntryType): List<HistoryEntry> = entries.filter { it.type == type }
    
    fun search(query: String): List<HistoryEntry> = 
        entries.filter { it.content.contains(query, ignoreCase = true) }
    
    fun clear() = entries.clear()
    
    fun size(): Int = entries.size
    
    fun addListener(listener: (HistoryEntry) -> Unit) = listeners.add(listener)
    
    private fun notifyListeners(entry: HistoryEntry) {
        listeners.forEach { it(entry) }
    }
}

object HistoryStorage {
    private val historyDir = File(System.getProperty("user.home"), ".cocacode/history")
    
    init {
        historyDir.mkdirs()
    }
    
    fun save(sessionId: String, history: History) {
        val file = File(historyDir, "$sessionId.json")
        try {
            val json = com.google.gson.Gson().toJson(history.getAll())
            file.writeText(json)
        } catch (e: Exception) {}
    }
    
    fun load(sessionId: String): List<HistoryEntry> {
        val file = File(historyDir, "$sessionId.json")
        if (!file.exists()) return emptyList()
        
        return try {
            val json = file.readText()
            val type = object : com.google.gson.reflect.TypeToken<List<HistoryEntry>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun listSessions(): List<String> {
        return historyDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
    
    fun delete(sessionId: String) {
        File(historyDir, "$sessionId.json").delete()
    }
}

class CommandHistory {
    private val history = mutableListOf<String>()
    private var position = -1
    
    fun add(command: String) {
        if (history.isEmpty() || history.last() != command) {
            history.add(command)
        }
        position = history.size
    }
    
    fun previous(): String? {
        if (position > 0) {
            position--
            return history[position]
        }
        return null
    }
    
    fun next(): String? {
        if (position < history.size - 1) {
            position++
            return history[position]
        }
        position = history.size
        return null
    }
    
    fun reset() {
        position = history.size
    }
    
    fun search(prefix: String): List<String> = 
        history.filter { it.startsWith(prefix) }
}