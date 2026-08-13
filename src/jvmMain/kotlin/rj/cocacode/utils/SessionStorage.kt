package rj.cocacode.utils

import com.google.gson.Gson
import rj.cocacode.types.Message
import rj.cocacode.types.MessageType
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object SessionStorage {
    private val sessionsDir = rj.cocacode.utils.AppPaths.sessionsDir()
    private val sessionCache = ConcurrentHashMap<String, SessionData>()
    private val gson = Gson()

    init {
        sessionsDir.mkdirs()
    }

    fun saveSession(
        sessionId: String,
        messages: List<Message>,
        title: String? = null,
        titleGenerated: Boolean = false,
        wireMessages: List<rj.cocacode.services.api.WireMessage>? = null
    ) {
        val existing = loadSession(sessionId)
        val now = System.currentTimeMillis()
        val data = SessionData(
            id = sessionId,
            messages = messages,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            title = title ?: existing?.title,
            titleGenerated = titleGenerated || (existing?.titleGenerated == true),
            wireMessages = wireMessages ?: existing?.wireMessages
        )

        try {
            File(sessionsDir, "$sessionId.json").writeText(gson.toJson(data))
            sessionCache[sessionId] = data
        } catch (e: Exception) {
            LogManager.logError("Failed to save session: ${e.message}")
        }
    }

    /** Update title fields without rewriting message history. */
    fun updateTitle(sessionId: String, title: String, generated: Boolean) {
        val existing = loadSession(sessionId) ?: return
        val data = existing.copy(
            title = title,
            titleGenerated = generated,
            updatedAt = System.currentTimeMillis()
        )
        try {
            File(sessionsDir, "$sessionId.json").writeText(gson.toJson(data))
            sessionCache[sessionId] = data
        } catch (e: Exception) {
            LogManager.logError("Failed to update session title: ${e.message}")
        }
    }

    fun loadSession(sessionId: String): SessionData? {
        sessionCache[sessionId]?.let { return it }

        val sessionFile = File(sessionsDir, "$sessionId.json")
        if (!sessionFile.exists()) return null

        return try {
            val data = gson.fromJson(sessionFile.readText(), SessionData::class.java)
            sessionCache[sessionId] = data
            data
        } catch (e: Exception) {
            LogManager.logError("Failed to load session: ${e.message}")
            null
        }
    }

    fun deleteSession(sessionId: String) {
        File(sessionsDir, "$sessionId.json").delete()
        sessionCache.remove(sessionId)
    }

    fun listSessions(): List<SessionInfo> {
        return sessionsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val data = gson.fromJson(file.readText(), SessionData::class.java)
                    SessionInfo(
                        id = data.id,
                        createdAt = data.createdAt,
                        updatedAt = data.updatedAt,
                        messageCount = data.messages.size,
                        title = resolveDisplayTitle(data)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun getSessionDir(): File = sessionsDir

    fun getLastSessionId(): String? = listSessions().firstOrNull()?.id

    fun loadLastSession(): SessionData? {
        val id = getLastSessionId() ?: return null
        return loadSession(id)
    }

    /** Prefer stored title; else first user message; else Untitled. */
    private fun resolveDisplayTitle(data: SessionData): String {
        data.title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val firstUser = data.messages.firstOrNull { it.type == MessageType.USER }?.content
        return SessionTitle.deriveTitle(firstUser ?: "") ?: "Untitled"
    }
}

data class SessionData(
    val id: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String? = null,
    val titleGenerated: Boolean = false,
    /** Full API transcript (thinking + tool_calls + tool results). */
    val wireMessages: List<rj.cocacode.services.api.WireMessage>? = null
)

data class SessionInfo(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val title: String
)
