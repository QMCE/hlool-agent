package rj.cocacode.services.lsp

import kotlinx.coroutines.*
import rj.cocacode.utils.LogManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class LSPServerManager(private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
    private val servers = mutableMapOf<String, LSPServerInstance>()
    private val fileExtensions = mutableMapOf<String, String>()
    private val languageToServer = mutableMapOf<String, String>()
    
    fun initialize(configs: List<LspServerConfig>) {
        configs.forEach { config ->
            val instance = LSPServerInstance(config)
            servers[config.name] = instance
            config.fileExtensions.forEach { ext ->
                fileExtensions[ext] = config.name
            }
            config.languages.forEach { lang ->
                languageToServer[lang] = config.name
            }
        }
    }
    
    suspend fun startServer(name: String): Boolean {
        val server = servers[name] ?: return false
        return server.start()
    }
    
    suspend fun stopServer(name: String) {
        servers[name]?.stop()
    }
    
    suspend fun shutdown() {
        servers.values.forEach { it.stop() }
        servers.clear()
    }
    
    fun getServerForFile(filePath: String): LSPServerInstance? {
        val ext = filePath.substringAfterLast(".")
        val serverName = fileExtensions[ext] ?: return null
        return servers[serverName]
    }
    
    suspend fun <T> sendRequest(filePath: String, method: String, params: Map<String, Any>): T? {
        val server = getServerForFile(filePath) ?: return null
        return server.sendRequest<T>(method, params)
    }
    
    suspend fun openFile(filePath: String, content: String) {
        val server = getServerForFile(filePath) ?: return
        server.didOpen(filePath, content)
    }
    
    suspend fun changeFile(filePath: String, content: String) {
        val server = getServerForFile(filePath) ?: return
        server.didChange(filePath, content)
    }
    
    suspend fun saveFile(filePath: String) {
        val server = getServerForFile(filePath) ?: return
        server.didSave(filePath)
    }
    
    suspend fun closeFile(filePath: String) {
        val server = getServerForFile(filePath) ?: return
        server.didClose(filePath)
    }
}

data class LspServerConfig(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val fileExtensions: List<String> = emptyList(),
    val languages: List<String> = emptyList()
)

class LSPServerInstance(private val config: LspServerConfig) {
    private var process: Process? = null
    private var isRunning = false
    private var requestId = 0
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<Map<String, Any>>>()
    
    suspend fun start(): Boolean {
        return try {
            val builder = ProcessBuilder(config.command, *config.args.toTypedArray())
            config.env.forEach { builder.environment().put(it.key, it.value) }
            process = builder.start()
            
            GlobalScope.launch(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                    while (isRunning && reader.ready()) {
                        val line = reader.readLine() ?: break
                        handleMessage(line)
                    }
                }
            }
            
            isRunning = true
            initialize()
            true
        } catch (e: Exception) {
            LogManager.logError("LSP server start failed: ${e.message}")
            false
        }
    }
    
    fun stop() {
        isRunning = false
        process?.destroy()
        process = null
    }
    
    private suspend fun initialize() {
        @Suppress("UNCHECKED_CAST")
        sendRequest<Any?>("initialize", mapOf(
            "processId" to ProcessHandle.current().pid(),
            "clientInfo" to mapOf(
                "name" to "CocaCode",
                "version" to "1.0.0"
            ),
            "capabilities" to emptyMap<String, Any>()
        ))
        
        sendNotification("initialized", emptyMap())
    }
    
    suspend fun <T> sendRequest(method: String, params: Map<String, Any>): T? {
        val id = ++requestId
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to method,
            "params" to params
        )
        
        val deferred = CompletableDeferred<Map<String, Any>>()
        pendingRequests[id] = deferred
        
        sendRaw(request)
        
        return try {
            val result = deferred.await()
            @Suppress("UNCHECKED_CAST")
            result["result"] as? T
        } catch (e: Exception) {
            null
        } finally {
            pendingRequests.remove(id)
        }
    }
    
    fun sendNotification(method: String, params: Map<String, Any>) {
        val notification = mapOf(
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )
        sendRaw(notification)
    }
    
    private fun sendRaw(message: Map<String, Any>) {
        try {
            val json = com.google.gson.Gson().toJson(message)
            process?.outputStream?.write((json + "\n").toByteArray())
            process?.outputStream?.flush()
        } catch (e: Exception) {
            LogManager.logError("LSP send failed: ${e.message}")
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun handleMessage(line: String) {
        try {
            val message = com.google.gson.Gson().fromJson(line, Map::class.java)
            
            if (message.containsKey("id")) {
                val id = (message["id"] as Number).toInt()
                pendingRequests[id]?.complete(message["result"] as? Map<String, Any> ?: emptyMap())
            }
        } catch (e: Exception) {
        }
    }
    
    fun didOpen(filePath: String, content: String) {
        sendNotification("textDocument/didOpen", mapOf(
            "textDocument" to mapOf(
                "uri" to "file://$filePath",
                "languageId" to "unknown",
                "version" to 1,
                "text" to content
            )
        ))
    }
    
    fun didChange(filePath: String, content: String) {
        sendNotification("textDocument/didChange", mapOf(
            "textDocument" to mapOf("uri" to "file://$filePath"),
            "contentChanges" to listOf(mapOf("text" to content))
        ))
    }
    
    fun didSave(filePath: String) {
        sendNotification("textDocument/didSave", mapOf(
            "textDocument" to mapOf("uri" to "file://$filePath")
        ))
    }
    
    fun didClose(filePath: String) {
        sendNotification("textDocument/didClose", mapOf(
            "textDocument" to mapOf("uri" to "file://$filePath")
        ))
    }
}

object LspDefinitions {
    val TypeScript = LspServerConfig(
        name = "typescript",
        command = "typescript-language-server",
        args = listOf("--stdio"),
        fileExtensions = listOf("ts", "tsx"),
        languages = listOf("typescript", "javascript")
    )
    
    val Python = LspServerConfig(
        name = "python",
        command = "python",
        args = listOf("-m", "pylsp"),
        fileExtensions = listOf("py"),
        languages = listOf("python")
    )
    
    val Rust = LspServerConfig(
        name = "rust",
        command = "rust-analyzer",
        args = emptyList(),
        fileExtensions = listOf("rs"),
        languages = listOf("rust")
    )
    
    fun getDefaultServers(): List<LspServerConfig> = listOf(TypeScript, Python, Rust)
}