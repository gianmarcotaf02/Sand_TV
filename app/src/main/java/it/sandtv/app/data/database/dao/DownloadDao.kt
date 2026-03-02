package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Download
import it.sandtv.app.data.database.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun getDownloadsByProfile(profileId: Long): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getDownloadsByProfileList(profileId: Long): List<Download>
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(profileId: Long, status: DownloadStatus): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(status: DownloadStatus = DownloadStatus.PENDING): List<Download>
    
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPendingDownload(status: DownloadStatus = DownloadStatus.PENDING): Download?
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): Download?
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND status = :status")
    suspend fun getCompletedDownloads(profileId: Long, status: DownloadStatus = DownloadStatus.COMPLETED): List<Download>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download): Long
    
    @Update
    suspend fun update(download: Download)
    
    @Delete
    suspend fun delete(download: Download)
    
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)
    
    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, downloadedBytes: Long)
    
    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun setError(id: Long, status: DownloadStatus = DownloadStatus.FAILED, errorMessage: String)
    
    @Query("UPDATE downloads SET status = :status, localFilePath = :localPath, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, localPath: String, status: DownloadStatus = DownloadStatus.COMPLETED, completedAt: Long = System.currentTimeMillis())
}
