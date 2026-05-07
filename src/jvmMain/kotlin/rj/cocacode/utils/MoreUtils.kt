package rj.cocacode.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    
    init {
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
    }
    
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    
    fun currentIsoString(): String = isoFormat.format(Date())
    
    fun formatTimestamp(millis: Long): String = displayFormat.format(Date(millis))
    
    fun parseIso(iso: String): Long? = try {
        isoFormat.parse(iso)?.time
    } catch (e: Exception) { null }
    
    fun elapsed(start: Long, end: Long = currentTimeMillis()): Long = end - start
    
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    fun isToday(millis: Long): Boolean {
        val today = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = millis }
        return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }
}

object StringUtils {
    fun truncate(text: String, maxLength: Int, ellipsis: String = "..."): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength - ellipsis.length) + ellipsis
        } else {
            text
        }
    }
    
    fun slugify(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }
    
    fun capitalize(text: String): String {
        return text.replaceFirstChar { it.uppercase() }
    }
    
    fun wordCount(text: String): Int {
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }
    
    fun indent(text: String, spaces: Int = 2): String {
        val indent = " ".repeat(spaces)
        return text.lines().joinToString("\n") { indent + it }
    }
}

object CollectionUtils {
    fun <T> chunk(list: List<T>, size: Int): List<List<T>> {
        return list.chunked(size)
    }
    
    fun <K, V> mergeMaps(vararg maps: Map<K, V>): Map<K, V> {
        return maps.flatMap { it.entries.toList() }.associate { it.key to it.value }
    }
    
    fun <T> distinctBy(list: List<T>, keySelector: (T) -> Any): List<T> {
        val seen = mutableSetOf<Any>()
        return list.filter {
            val key = keySelector(it)
            if (key in seen) false else { seen.add(key); true }
        }
    }
}