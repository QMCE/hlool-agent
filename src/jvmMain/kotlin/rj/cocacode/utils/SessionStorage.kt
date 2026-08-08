package rj.cocacode.utils

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object SessionStorage {
    private val sessionsDir = File(System.getProperty("user.home"), ".cocacode/sessions")
    private val sessionCache = ConcurrentHashMap<String, SessionData>()
    
    init {
        sessionsDir.mkdirs()
    }
    
    fun saveSession(sessionId: String, messages: List<rj.cocacode.types.Message>) {
        val sessionFile = File(sessionsDir, "$sessionId.json")
        
        val data = SessionData(
            id = sessionId,
            messages = messages,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        try {
            val json = com.google.gson.Gson().toJson(data)
            sessionFile.writeText(json)
            sessionCache[sessionId] = data
        } catch (e: Exception) {
            LogManager.logError("Failed to save session: ${e.message}")
        }
    }
    
    fun loadSession(sessionId: String): SessionData? {
        sessionCache[sessionId]?.let { return it }
        
        val sessionFile = File(sessionsDir, "$sessionId.json")
        if (!sessionFile.exists()) return null
        
        return try {
            val json = sessionFile.readText()
            val data = com.google.gson.Gson().fromJson(json, SessionData::class.java)
            sessionCache[sessionId] = data
            data
        } catch (e: Exception) {
            LogManager.logError("Failed to load session: ${e.message}")
            null
        }
    }
    
    fun deleteSession(sessionId: String) {
        val sessionFile = File(sessionsDir, "$sessionId.json")
        sessionFile.delete()
        sessionCache.remove(sessionId)
    }
    
    fun listSessions(): List<SessionInfo> {
        return sessionsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val data = com.google.gson.Gson().fromJson(file.readText(), SessionData::class.java)
                    SessionInfo(data.id, data.createdAt, data.updatedAt, data.messages.size)
                } catch (e: Exception) { null }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }
    
    fun getSessionDir(): File = sessionsDir

    /** The most recently updated session id, or null if none exist. */
    fun getLastSessionId(): String? = listSessions().firstOrNull()?.id

    /** Messages for the most recent session, or null if none exist. */
    fun loadLastSession(): SessionData? {
        val id = getLastSessionId() ?: return null
        return loadSession(id)
    }
}

data class SessionData(
    val id: String,
    val messages: List<rj.cocacode.types.Message>,
    val createdAt: Long,
    val updatedAt: Long
)

data class SessionInfo(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int
)