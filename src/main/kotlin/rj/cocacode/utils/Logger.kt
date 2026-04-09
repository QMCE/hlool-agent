package rj.cocacode.utils

import org.slf4j.LoggerFactory

object Logger {
    private val logger = LoggerFactory.getLogger("CocaCode")
    
    fun info(message: String) = logger.info(message)
    
    fun warn(message: String) = logger.warn(message)
    
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) logger.error(message, throwable)
        else logger.error(message)
    }
    
    fun debug(message: String) = logger.debug(message)
    
    fun trace(message: String) = logger.trace(message)
}

object LogManager {
    private val inMemoryLogs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 100
    
    data class LogEntry(
        val level: LogLevel,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class LogLevel { DEBUG, INFO, WARN, ERROR }
    
    fun addLog(level: LogLevel, message: String) {
        if (inMemoryLogs.size >= MAX_LOGS) {
            inMemoryLogs.removeAt(0)
        }
        inMemoryLogs.add(LogEntry(level, message))
    }
    
    fun getLogs(): List<LogEntry> = inMemoryLogs.toList()
    
    fun clearLogs() = inMemoryLogs.clear()
    
    fun logError(error: String) {
        addLog(LogLevel.ERROR, error)
        Logger.error(error)
    }
    
    fun logInfo(message: String) {
        addLog(LogLevel.INFO, message)
        Logger.info(message)
    }
    
    fun logWarn(message: String) {
        addLog(LogLevel.WARN, message)
        Logger.warn(message)
    }
    
    fun logDebug(message: String) {
        addLog(LogLevel.DEBUG, message)
        Logger.debug(message)
    }
}