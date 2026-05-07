package rj.cocacode.utils

import java.time.Instant

/**
 * Logger for application logging.
 */
object Logger {
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    private var minLevel: Level = Level.INFO
    
    fun setLevel(level: Level) {
        minLevel = level
    }
    
    fun debug(message: String, vararg args: Any?) = log(Level.DEBUG, message, args)
    fun info(message: String, vararg args: Any?) = log(Level.INFO, message, args)
    fun warn(message: String, vararg args: Any?) = log(Level.WARN, message, args)
    fun error(message: String, vararg args: Any?) = log(Level.ERROR, message, args)
    
    private fun log(level: Level, message: String, args: Array<out Any?>) {
        if (level.ordinal >= minLevel.ordinal) {
            val formatted = args.fold(message) { acc, arg -> acc.replaceFirst("{}", arg?.toString() ?: "null") }
            println("[${Instant.now()}] [${level.name}] $formatted")
        }
    }
}

/**
 * LogManager - alias for Logger compatibility.
 */
object LogManager {
    fun logInfo(message: String) = Logger.info(message)
    fun logDebug(message: String) = Logger.debug(message)
    fun logWarn(message: String) = Logger.warn(message)
    fun logError(message: String, throwable: Throwable? = null) {
        Logger.error(message)
        throwable?.printStackTrace()
    }
}
