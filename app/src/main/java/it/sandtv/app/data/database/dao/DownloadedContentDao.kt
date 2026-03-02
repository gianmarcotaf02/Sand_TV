package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.DownloadedContent
import kotlinx.coroutines.flow.Flow

/**
 * DAO for downloaded content operations
 */
@Dao
interface DownloadedContentDao {
    
    /**
     * Get all completed downloads, ordered by most recent first
     */
    @Query("SELECT * FROM downloaded_content WHERE isComplete = 1 ORDER BY downloadedAt DESC")
    fun getAllCompleted(): Flow<List<DownloadedContent>>
    
    /**
     * Get all downloads in progress
     */
    @Query("SELECT * FROM downloaded_content WHERE isComplete = 0 ORDER BY downloadedAt DESC")
    fun getInProgress(): Flow<List<DownloadedContent>>
    
    /**
     * Get all downloads (completed and in progress)
     */
    @Query("SELECT * FROM downloaded_content ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadedContent>>
    
    /**
     * Check if specific content is downloaded
     */
    @Query("SELECT * FROM downloaded_content WHERE contentType = :type AND contentId = :id LIMIT 1")
    suspend fun getByContent(type: ContentType, id: Long): DownloadedContent?
    
    /**
     * Check if specific content is downloaded (Flow version for UI)
     */
    @Query("SELECT * FROM downloaded_content WHERE contentType = :type AND contentId = :id LIMIT 1")
    fun observeByContent(type: ContentType, id: Long): Flow<DownloadedContent?>
    
    /**
     * Get all downloaded episodes for a series
     */
    @Query("SELECT * FROM downloaded_content WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    fun getBySeriesId(seriesId: Long): Flow<List<DownloadedContent>>
    
    /**
     * Get all downloaded episodes for a specific season
     */
    @Query("SELECT * FROM downloaded_content WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber")
    fun getBySeason(seriesId: Long, seasonNumber: Int): Flow<List<DownloadedContent>>
    
    /**
     * Get downloaded episode count for a season
     */
    @Query("SELECT COUNT(*) FROM downloaded_content WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber AND isComplete = 1")
    suspend fun getDownloadedEpisodeCount(seriesId: Long, seasonNumber: Int): Int
    
    /**
     * Check if content is downloaded (non-suspend for quick checks)
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_content WHERE contentType = :type AND contentId = :id AND isComplete = 1)")
    fun isDownloaded(type: ContentType, id: Long): Flow<Boolean>
    
    /**
     * Insert or replace a download entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadedContent): Long
    
    /**
     * Delete a download entry
     */
    @Delete
    suspend fun delete(download: DownloadedContent)
    
    /**
     * Delete by ID
     */
    @Query("DELETE FROM downloaded_content WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * Delete by content reference
     */
    @Query("DELETE FROM downloaded_content WHERE contentType = :type AND contentId = :id")
    suspend fun deleteByContent(type: ContentType, id: Long)
    
    /**
     * Delete all episodes of a season
     */
    @Query("DELETE FROM downloaded_content WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber")
    suspend fun deleteBySeason(seriesId: Long, seasonNumber: Int)
    
    /**
     * Delete all episodes of a series
     */
    @Query("DELETE FROM downloaded_content WHERE seriesId = :seriesId")
    suspend fun deleteBySeries(seriesId: Long)
    
    /**
     * Update download progress
     */
    @Query("UPDATE downloaded_content SET downloadProgress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)
    
    /**
     * Mark download as complete
     */
    @Query("UPDATE downloaded_content SET isComplete = 1, downloadProgress = 100, downloadSize = :size WHERE id = :id")
    suspend fun markComplete(id: Long, size: Long)
    
    /**
     * Get total downloaded size in bytes
     */
    @Query("SELECT COALESCE(SUM(downloadSize), 0) FROM downloaded_content WHERE isComplete = 1")
    suspend fun getTotalDownloadedSize(): Long
    
    /**
     * Get download by cache key
     */
    @Query("SELECT * FROM downloaded_content WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): DownloadedContent?
}
