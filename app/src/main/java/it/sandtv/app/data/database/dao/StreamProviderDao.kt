package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.StreamProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamProviderDao {
    
    /**
     * Get all providers for a movie
     */
    @Query("SELECT * FROM stream_providers WHERE movieId = :movieId ORDER BY quality DESC, lastUsedAt DESC")
    fun getProvidersForMovie(movieId: Long): Flow<List<StreamProvider>>
    
    @Query("SELECT * FROM stream_providers WHERE movieId = :movieId ORDER BY quality DESC, lastUsedAt DESC")
    suspend fun getProvidersForMovieList(movieId: Long): List<StreamProvider>
    
    /**
     * Get all providers for a series
     */
    @Query("SELECT * FROM stream_providers WHERE seriesId = :seriesId ORDER BY quality DESC, lastUsedAt DESC")
    fun getProvidersForSeries(seriesId: Long): Flow<List<StreamProvider>>
    
    @Query("SELECT * FROM stream_providers WHERE seriesId = :seriesId ORDER BY quality DESC, lastUsedAt DESC")
    suspend fun getProvidersForSeriesList(seriesId: Long): List<StreamProvider>
    
    /**
     * Get all providers by TMDB ID (for grouping same content)
     */
    @Query("SELECT * FROM stream_providers WHERE tmdbId = :tmdbId ORDER BY quality DESC, lastUsedAt DESC")
    suspend fun getProvidersByTmdbId(tmdbId: Int): List<StreamProvider>
    
    /**
     * Count providers for a movie
     */
    @Query("SELECT COUNT(*) FROM stream_providers WHERE movieId = :movieId")
    suspend fun countProvidersForMovie(movieId: Long): Int
    
    /**
     * Get best quality provider
     */
    @Query("SELECT * FROM stream_providers WHERE movieId = :movieId ORDER BY quality DESC, lastUsedAt DESC LIMIT 1")
    suspend fun getBestProviderForMovie(movieId: Long): StreamProvider?
    
    /**
     * Update last used time
     */
    @Query("UPDATE stream_providers SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: StreamProvider): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(providers: List<StreamProvider>)
    
    @Update
    suspend fun update(provider: StreamProvider)
    
    @Delete
    suspend fun delete(provider: StreamProvider)
    
    @Query("DELETE FROM stream_providers WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
    
    @Query("DELETE FROM stream_providers WHERE movieId = :movieId")
    suspend fun deleteByMovie(movieId: Long)
}
