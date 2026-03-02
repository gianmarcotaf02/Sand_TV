package it.sandtv.app.data.cache

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent disk cache for API responses and metadata
 * Uses file system with efficient read/write operations
 * 
 * Features:
 * - TTL-based expiration
 * - Max size limit with LRU eviction
 * - Corruption-safe writes
 * - Thread-safe operations
 */
@Singleton
class DiskCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    
    companion object {
        private const val TAG = "DiskCache"
        private const val CACHE_DIR = "api_cache"
        private const val MAX_CACHE_SIZE_MB = 50L // 50MB max
        private const val MAX_CACHE_SIZE_BYTES = MAX_CACHE_SIZE_MB * 1024 * 1024
        
        // TTL in milliseconds
        const val TTL_SHORT = 5 * 60 * 1000L          // 5 minutes
        const val TTL_MEDIUM = 60 * 60 * 1000L        // 1 hour
        const val TTL_LONG = 24 * 60 * 60 * 1000L     // 1 day
        const val TTL_WEEK = 7 * 24 * 60 * 60 * 1000L // 1 week
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    }
    
    private val mutex = Mutex()
    
    /**
     * Store data with TTL
     */
    suspend fun <T> put(
        key: String,
        data: T,
        clazz: Class<T>,
        ttlMs: Long = TTL_LONG
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                val entry = CacheEntry(
                    data = moshi.adapter(clazz).toJson(data),
                    timestamp = System.currentTimeMillis(),
                    ttlMs = ttlMs
                )
                
                val file = getFileForKey(key)
                val tempFile = File(file.path + ".tmp")
                
                // Write to temp file first (atomic write)
                tempFile.writeText(moshi.adapter(CacheEntry::class.java).toJson(entry))
                tempFile.renameTo(file)
                
                // Check cache size periodically
                checkCacheSize()
                
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache: $key", e)
            false
        }
    }
    
    /**
     * Get cached data if valid
     */
    suspend fun <T> get(key: String, clazz: Class<T>): T? = withContext(Dispatchers.IO) {
        try {
            val file = getFileForKey(key)
            if (!file.exists()) return@withContext null
            
            val entryJson = file.readText()
            val entry = moshi.adapter(CacheEntry::class.java).fromJson(entryJson)
                ?: return@withContext null
            
            // Check if expired
            if (isExpired(entry)) {
                file.delete()
                return@withContext null
            }
            
            moshi.adapter(clazz).fromJson(entry.data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cache: $key", e)
            null
        }
    }
    
    /**
     * Get cached data even if expired (for offline mode)
     */
    suspend fun <T> getStale(key: String, clazz: Class<T>): Pair<T?, Boolean>? = withContext(Dispatchers.IO) {
        try {
            val file = getFileForKey(key)
            if (!file.exists()) return@withContext null
            
            val entryJson = file.readText()
            val entry = moshi.adapter(CacheEntry::class.java).fromJson(entryJson)
                ?: return@withContext null
            
            val data = moshi.adapter(clazz).fromJson(entry.data)
            val isStale = isExpired(entry)
            
            Pair(data, isStale)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read stale cache: $key", e)
            null
        }
    }
    
    /**
     * Check if cached data exists and is valid
     */
    suspend fun isValid(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getFileForKey(key)
            if (!file.exists()) return@withContext false
            
            val entryJson = file.readText()
            val entry = moshi.adapter(CacheEntry::class.java).fromJson(entryJson)
                ?: return@withContext false
            
            !isExpired(entry)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Remove specific cache entry
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        getFileForKey(key).delete()
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
    
    /**
     * Get current cache size in bytes
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Get cache size formatted as string
     */
    suspend fun getCacheSizeFormatted(): String {
        val bytes = getCacheSize()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    private fun getFileForKey(key: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, hash)
    }
    
    private fun isExpired(entry: CacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp > entry.ttlMs
    }
    
    private suspend fun checkCacheSize() {
        val size = getCacheSize()
        if (size > MAX_CACHE_SIZE_BYTES) {
            evictOldest(size - MAX_CACHE_SIZE_BYTES * 80 / 100) // Evict to 80%
        }
    }
    
    private suspend fun evictOldest(bytesToFree: Long) = withContext(Dispatchers.IO) {
        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
        
        var freedBytes = 0L
        for (file in files) {
            if (freedBytes >= bytesToFree) break
            freedBytes += file.length()
            file.delete()
        }
        
        Log.d(TAG, "Evicted ${freedBytes / 1024} KB from cache")
    }
    
    private data class CacheEntry(
        val data: String,
        val timestamp: Long,
        val ttlMs: Long
    )
}
