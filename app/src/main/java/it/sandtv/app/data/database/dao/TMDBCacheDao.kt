package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.TMDBCache
import it.sandtv.app.data.database.entity.TMDBMediaType

@Dao
interface TMDBCacheDao {
    
    @Query("SELECT * FROM tmdb_cache WHERE searchQuery = :query AND mediaType = :mediaType AND expiresAt > :currentTime")
    suspend fun getCachedResult(query: String, mediaType: TMDBMediaType, currentTime: Long = System.currentTimeMillis()): TMDBCache?
    
    @Query("SELECT * FROM tmdb_cache WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    suspend fun getByTmdbId(tmdbId: Int, mediaType: TMDBMediaType): TMDBCache?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TMDBCache): Long
    
    @Query("DELETE FROM tmdb_cache WHERE expiresAt < :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM tmdb_cache WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredCache(currentTime: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM tmdb_cache")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM tmdb_cache")
    suspend fun getCacheSize(): Int
    
    @Query("SELECT * FROM tmdb_cache WHERE searchQuery = :query LIMIT 1")
    suspend fun getCacheEntry(query: String): TMDBCache?
}
