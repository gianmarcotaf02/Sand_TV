package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.WatchProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY lastWatchedAt DESC")
    fun getProgressByProfile(profileId: Long): Flow<List<WatchProgress>>
    
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getContinueWatching(profileId: Long, limit: Int = 20): List<WatchProgress>
    
    // Movies only - for FilmActivity carousel
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentType = 'MOVIE' AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getContinueWatchingMovies(profileId: Long, limit: Int = 20): List<WatchProgress>
    
    // Series/Episodes - for SeriesActivity carousel
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentType IN ('SERIES', 'EPISODE') AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getContinueWatchingSeries(profileId: Long, limit: Int = 20): List<WatchProgress>
    
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId")
    suspend fun getProgress(profileId: Long, contentType: ContentType, contentId: Long): WatchProgress?
    
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getRecentlyWatched(profileId: Long, limit: Int = 50): List<WatchProgress>
    
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND seriesId = :seriesId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getSeriesProgress(profileId: Long, seriesId: Long): WatchProgress?
    
    @Query("SELECT * FROM watch_progress")
    suspend fun getAllProgress(): List<WatchProgress>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: WatchProgress): Long
    
    @Update
    suspend fun update(progress: WatchProgress)
    
    @Delete
    suspend fun delete(progress: WatchProgress)
    
    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId")
    suspend fun deleteProgress(profileId: Long, contentType: ContentType, contentId: Long)
    
    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun deleteProgressBySeriesId(profileId: Long, seriesId: Long)
    
    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
    
    @Transaction
    suspend fun upsert(progress: WatchProgress) {
        val existing = getProgress(progress.profileId, progress.contentType, progress.contentId)
        if (existing != null) {
            update(progress.copy(id = existing.id, createdAt = existing.createdAt))
        } else {
            insert(progress)
        }
    }
}
