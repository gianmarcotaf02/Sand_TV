package it.sandtv.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.sandtv.app.data.database.dao.PlaylistDao
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker for syncing playlists and EPG data
 * Supports customizable sync intervals from user preferences
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val playlistDao: PlaylistDao,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "SyncWorker"
        
        const val SYNC_TYPE_KEY = "sync_type"
        const val SYNC_TYPE_PLAYLIST = "playlist"
        const val SYNC_TYPE_EPG = "epg"
        const val SYNC_TYPE_ALL = "all"
        
        private const val WORK_NAME_PLAYLIST = "playlist_sync"
        private const val WORK_NAME_EPG = "epg_sync"
        
        /**
         * Schedule periodic playlist sync with custom interval
         */
        fun schedulePlaylistSync(context: Context, intervalHours: Long = 6) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(SYNC_TYPE_KEY to SYNC_TYPE_PLAYLIST))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PLAYLIST,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            
            Log.d(TAG, "Scheduled playlist sync every $intervalHours hours")
        }
        
        /**
         * Schedule periodic EPG sync with custom interval
         */
        fun scheduleEPGSync(context: Context, intervalHours: Long = 12) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(SYNC_TYPE_KEY to SYNC_TYPE_EPG))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_EPG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
            
            Log.d(TAG, "Scheduled EPG sync every $intervalHours hours")
        }
        
        /**
         * Update sync schedules based on user preferences
         */
        suspend fun updateSchedules(context: Context, userPreferences: UserPreferences) {
            val playlistInterval = userPreferences.getPlaylistUpdateIntervalHours().toLong()
            val epgInterval = userPreferences.getEpgUpdateIntervalHours().toLong()
            
            if (userPreferences.getPlaylistAutoUpdate()) {
                schedulePlaylistSync(context, playlistInterval)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PLAYLIST)
            }
            
            if (userPreferences.getEpgAutoUpdateFlow().first()) {
                scheduleEPGSync(context, epgInterval)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_EPG)
            }
        }
        
        /**
         * Cancel all scheduled syncs
         */
        fun cancelAllSyncs(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PLAYLIST)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_EPG)
        }
        
        /**
         * Trigger immediate sync
         */
        fun syncNow(context: Context, syncType: String = SYNC_TYPE_ALL) {
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SYNC_TYPE_KEY to syncType))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Triggered immediate sync: $syncType")
        }
    }
    
    override suspend fun doWork(): Result {
        val syncType = inputData.getString(SYNC_TYPE_KEY) ?: SYNC_TYPE_ALL
        
        Log.d(TAG, "Starting sync: $syncType")
        
        return try {
            when (syncType) {
                SYNC_TYPE_PLAYLIST -> syncPlaylists()
                SYNC_TYPE_EPG -> syncEPG()
                SYNC_TYPE_ALL -> {
                    syncPlaylists()
                    syncEPG()
                }
                else -> syncPlaylists()
            }
            
            Log.d(TAG, "Sync completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun syncPlaylists() {
        val playlists = playlistDao.getEnabledPlaylistsList()
        
        for (playlist in playlists) {
            try {
                Log.d(TAG, "Syncing playlist: ${playlist.name}")
                playlistRepository.refreshPlaylist(playlist.id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync playlist: ${playlist.name}", e)
            }
        }
    }
    
    private suspend fun syncEPG() {
        // EPG sync implementation
        val playlists = playlistDao.getEnabledPlaylistsList()
        
        for (playlist in playlists) {
            playlist.epgUrl?.let { _ ->
                try {
                    Log.d(TAG, "Syncing EPG for: ${playlist.name}")
                    // EPG parsing would go here
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync EPG: ${playlist.name}", e)
                }
            }
        }
    }
}
