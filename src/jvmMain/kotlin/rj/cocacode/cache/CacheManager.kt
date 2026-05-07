package rj.cocacode.cache

import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CacheManager {
    private val cacheDir = File(System.getProperty("user.home"), ".cocacode/cache")
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()
    
    init {
        cacheDir.mkdirs()
    }
    
    fun <T> set(key: String, value: T, ttlMs: Long = 3600000) {
        memoryCache[key] = CacheEntry(value, System.currentTimeMillis() + ttlMs)
    }
    
    fun <T> get(key: String): T? {
        val entry = memoryCache[key] ?: return null
        
        if (System.currentTimeMillis() > entry.expiry) {
            memoryCache.remove(key)
            return null
        }
        
        @Suppress("UNCHECKED_CAST")
        return entry.value as? T
    }
    
    fun remove(key: String) {
        memoryCache.remove(key)
    }
    
    fun clear() {
        memoryCache.clear()
    }
    
    fun getOrSet(key: String, ttlMs: Long = 3600000, factory: () -> Any): Any {
        return get(key) ?: factory().also { set(key, it, ttlMs) }
    }
    
    fun getCacheDir(): File = cacheDir
}

data class CacheEntry<out T>(
    val value: T,
    val expiry: Long
)

object DiskCache {
    private fun getCacheFile(key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(CacheManager.getCacheDir(), "$safeKey.cache")
    }
    
    fun <T> save(key: String, value: T) {
        try {
            val file = getCacheFile(key)
            val json = com.google.gson.Gson().toJson(value)
            file.writeText(json)
        } catch (e: Exception) { }
    }
    
    fun <T> load(key: String, clazz: Class<T>): T? {
        return try {
            val file = getCacheFile(key)
            if (file.exists()) {
                val json = file.readText()
                com.google.gson.Gson().fromJson(json, clazz)
            } else null
        } catch (e: Exception) { null }
    }
    
    fun remove(key: String) {
        getCacheFile(key).delete()
    }
    
    fun clear() {
        CacheManager.getCacheDir().listFiles()?.forEach { it.delete() }
    }
}