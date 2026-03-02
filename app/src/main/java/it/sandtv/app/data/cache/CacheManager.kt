package it.sandtv.app.data.cache

import it.sandtv.app.data.database.dao.TMDBCacheDao
import it.sandtv.app.data.database.entity.TMDBCache
import it.sandtv.app.data.database.entity.TMDBMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified cache manager that coordinates all caching layers:
 * 1. Memory cache (instant, volatile)
 * 2. Room database (structured data, persistent)
 * 3. Disk cache (API responses, persistent)
 * 4. Glide (images, persistent)
 * 
 * Implements stale-while-revalidate pattern for optimal UX
 */
@Singleton
class CacheManager @Inject constructor(
    private val contentCache: ContentCache,
    private val diskCache: DiskCache,
    private val networkMonitor: NetworkMonitor,
    private val tmdbCacheDao: TMDBCacheDao
) {
    
    companion object {
        // Cache keys
        fun tmdbMovieKey(tmdbId: Int) = "tmdb_movie_$tmdbId"
        fun tmdbTvKey(tmdbId: Int) = "tmdb_tv_$tmdbId"
        fun tmdbSearchKey(query: String) = "tmdb_search_${query.lowercase().trim()}"
        fun epgKey(channelId: Long, date: String) = "epg_${channelId}_$date"
    }
    
    /**
     * Get TMDB movie details with multi-layer caching
     * Returns cached data immediately, refreshes in background if stale
     */
    suspend fun <T> getCachedOrFetch(
        key: String,
        clazz: Class<T>,
        ttl: Long = DiskCache.TTL_LONG,
        fetchBlock: suspend () -> T?
    ): T? = withContext(Dispatchers.IO) {
        
        // 1. Try disk cache first
        val cached = diskCache.getStale(key, clazz)
        
        if (cached != null) {
            val (data, isStale) = cached
            
            if (!isStale) {
                // Fresh data, return immediately
                return@withContext data
            }
            
            // Stale data - return it but try to refresh if online
            if (networkMonitor.isOnline.value) {
                try {
                    val fresh = fetchBlock()
                    if (fresh != null) {
                        diskCache.put(key, fresh, clazz, ttl)
                        return@withContext fresh
                    }
                } catch (e: Exception) {
                    // Network error, return stale data
                }
            }
            
            // Return stale data (better than nothing)
            return@withContext data
        }
        
        // 2. No cache, fetch if online
        if (!networkMonitor.isOnline.value) {
            return@withContext null
        }
        
        try {
            val data = fetchBlock()
            if (data != null) {
                diskCache.put(key, data, clazz, ttl)
            }
            data
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get TMDB data from Room cache (structured)
     */
    suspend fun getTMDBFromRoom(tmdbId: Int, mediaType: TMDBMediaType): TMDBCache? {
        return tmdbCacheDao.getByTmdbId(tmdbId, mediaType)
    }
    
    /**
     * Save TMDB data to Room cache
     */
    suspend fun saveTMDBToRoom(
        query: String,
        tmdbId: Int,
        mediaType: TMDBMediaType,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        overview: String?,
        voteAverage: Float?,
        releaseDate: String?
    ) {
        val entry = TMDBCache(
            searchQuery = query,
            tmdbId = tmdbId,
            mediaType = mediaType,
            title = title,
            posterPath = posterPath,
            backdropPath = backdropPath,
            overview = overview,
            voteAverage = voteAverage,
            releaseDate = releaseDate
        )
        tmdbCacheDao.insert(entry)
    }
    
    /**
     * Clear all caches
     */
    suspend fun clearAllCaches() {
        contentCache.invalidateAll()
        diskCache.clearAll()
        tmdbCacheDao.deleteExpired()
    }
    
    /**
     * Get total cache size
     */
    suspend fun getTotalCacheSize(): String {
        val diskSize = diskCache.getCacheSize()
        val mbSize = diskSize / (1024 * 1024)
        return "$mbSize MB"
    }
    
    /**
     * Check if device is offline
     */
    fun isOffline(): Boolean = !networkMonitor.isOnline.value
}
