package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.WatchState
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchStateDao {
    
    @Query("SELECT * FROM watch_states WHERE profileId = :profileId ORDER BY lastWatchedAt DESC")
    fun getWatchStatesByProfile(profileId: Long): Flow<List<WatchState>>
    
    @Query("SELECT * FROM watch_states WHERE profileId = :profileId AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getContinueWatching(profileId: Long, limit: Int = 20): List<WatchState>
    
    @Query("SELECT * FROM watch_states WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId")
    suspend fun getWatchState(profileId: Long, contentType: ContentType, contentId: Long): WatchState?
    
    @Query("SELECT * FROM watch_states WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getRecentlyWatched(profileId: Long, limit: Int = 50): List<WatchState>
    
    // Get watch state for series (by any episode)
    @Query("SELECT * FROM watch_states WHERE profileId = :profileId AND seriesId = :seriesId ORDER BY lastWatchedAt DESC LIMIT 1")
    suspend fun getSeriesWatchState(profileId: Long, seriesId: Long): WatchState?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(watchState: WatchState): Long
    
    @Update
    suspend fun update(watchState: WatchState)
    
    @Delete
    suspend fun delete(watchState: WatchState)
    
    @Query("DELETE FROM watch_states WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId")
    suspend fun deleteWatchState(profileId: Long, contentType: ContentType, contentId: Long)
    
    @Query("DELETE FROM watch_states WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
    
    @Transaction
    suspend fun upsertWatchState(watchState: WatchState) {
        val existing = getWatchState(watchState.profileId, watchState.contentType, watchState.contentId)
        if (existing != null) {
            update(watchState.copy(id = existing.id, createdAt = existing.createdAt))
        } else {
            insert(watchState)
        }
    }
}
