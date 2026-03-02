package it.sandtv.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.sandtv.app.data.database.dao.PlaylistDao
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.repository.EpgRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker for periodic EPG updates
 */
@HiltWorker
class EpgUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val epgRepository: EpgRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EpgUpdateWorker"
        const val WORK_NAME = "epg_update_work"
        
        /**
         * Schedule EPG update based on interval setting
         */
        fun schedule(context: Context, interval: String) {
            val workManager = WorkManager.getInstance(context)
            
            // Cancel any existing work
            workManager.cancelUniqueWork(WORK_NAME)
            
            // Don't schedule if interval is "startup" (handled by LoadingActivity)
            if (interval == "startup") {
                Log.d(TAG, "EPG updates set to startup only, not scheduling periodic work")
                return
            }
            
            val repeatIntervalHours = when (interval) {
                "3h" -> 3L
                "6h" -> 6L
                "12h" -> 12L
                "24h" -> 24L
                "3d" -> 72L
                "weekly" -> 168L // 7 * 24
                else -> 6L
            }
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = PeriodicWorkRequestBuilder<EpgUpdateWorker>(
                repeatIntervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Log.d(TAG, "Scheduled EPG update every $repeatIntervalHours hours")
        }
        
        /**
         * Cancel scheduled EPG updates
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled EPG update work")
        }
        
        /**
         * Run EPG update immediately (one-time)
         */
        fun runNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Enqueued immediate EPG update")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting EPG update work")
        
        return try {
            val playlists = playlistDao.getAllPlaylists().first()
            
            for (playlist in playlists) {
                try {
                    if (playlist.type == "xtream" && 
                        !playlist.username.isNullOrEmpty() && 
                        !playlist.password.isNullOrEmpty()) {
                        // Xtream EPG
                        epgRepository.loadEpgFromXtream(
                            baseUrl = playlist.url,
                            username = playlist.username,
                            password = playlist.password
                        )
                    } else if (!playlist.epgUrl.isNullOrEmpty()) {
                        // XMLTV EPG
                        epgRepository.loadEpgFromUrl(playlist.epgUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load EPG for ${playlist.name}", e)
                }
            }
            
            // Update last update timestamp
            userPreferences.setEpgLastUpdate(System.currentTimeMillis())
            
            Log.d(TAG, "EPG update completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "EPG update failed", e)
            Result.retry()
        }
    }
}
