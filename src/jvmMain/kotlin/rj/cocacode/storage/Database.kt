package rj.cocacode.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object Database {
    private var connection: Connection? = null
    
    fun connect(path: String = "${System.getProperty("user.home")}/.cocacode/data.db") {
        val dbFile = File(path)
        dbFile.parentFile?.mkdirs()
        
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$path")
        
        initializeTables()
    }
    
    private fun initializeTables() {
        connection?.createStatement()?.use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    created_at INTEGER,
                    updated_at INTEGER,
                    metadata TEXT
                )
            """.trimIndent())
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT,
                    type TEXT,
                    content TEXT,
                    timestamp INTEGER,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
            """.trimIndent())
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT
                )
            """.trimIndent())
        }
    }
    
    fun execute(sql: String, vararg params: Any): Boolean {
        return try {
            connection?.prepareStatement(sql)?.use { ps ->
                params.forEachIndexed { index, param ->
                    ps.setObject(index + 1, param)
                }
                ps.execute()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    fun query(sql: String, vararg params: Any): List<Map<String, Any>> {
        return try {
            connection?.prepareStatement(sql)?.use { ps ->
                params.forEachIndexed { index, param ->
                    ps.setObject(index + 1, param)
                }
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<Map<String, Any>>()
                    val metaData = rs.metaData
                    
                    while (rs.next()) {
                        val row = mutableMapOf<String, Any>()
                        for (i in 1..metaData.columnCount) {
                            row[metaData.getColumnName(i)] = rs.getObject(i)
                        }
                        results.add(row)
                    }
                    results
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun close() {
        connection?.close()
        connection = null
    }
}

object KeyValueStore {
    private val store = mutableMapOf<String, String>()
    
    fun set(key: String, value: String) {
        store[key] = value
    }
    
    fun get(key: String): String? = store[key]
    
    fun get(key: String, default: String): String = store[key] ?: default
    
    fun remove(key: String) {
        store.remove(key)
    }
    
    fun clear() {
        store.clear()
    }
    
    fun keys(): Set<String> = store.keys.toSet()
    
    fun size(): Int = store.size
    
    fun loadFromFile(file: File) {
        if (!file.exists()) return
        
        file.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                store[parts[0]] = parts[1]
            }
        }
    }
    
    fun saveToFile(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(store.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}

class DocumentStore {
    private val documents = mutableMapOf<String, Document>()
    
    data class Document(
        val id: String,
        val content: String,
        val metadata: Map<String, String> = emptyMap(),
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )
    
    fun save(id: String, content: String, metadata: Map<String, String> = emptyMap()) {
        val existing = documents[id]
        documents[id] = Document(
            id = id,
            content = content,
            metadata = metadata,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    fun get(id: String): Document? = documents[id]
    
    fun delete(id: String) {
        documents.remove(id)
    }
    
    fun search(query: String): List<Document> {
        val lowerQuery = query.lowercase()
        return documents.values.filter { 
            it.content.lowercase().contains(lowerQuery) ||
            it.metadata.values.any { v -> v.lowercase().contains(lowerQuery) }
        }
    }
    
    fun all(): Collection<Document> = documents.values
    
    fun clear() = documents.clear()
}