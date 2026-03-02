package it.sandtv.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import it.sandtv.app.R
import java.io.File
import java.util.concurrent.Executors

/**
 * Background service for downloading video content for offline playback.
 * Uses ExoPlayer's DownloadService for reliable background downloads.
 */
@UnstableApi
class VideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val JOB_ID = 1
        
        @Volatile
        private var downloadManagerInstance: DownloadManager? = null
        @Volatile
        private var downloadCacheInstance: Cache? = null
        private var downloadNotificationHelper: DownloadNotificationHelper? = null
        
        /**
         * Set the DownloadManager instance. Must be called before starting service.
         */
        fun setDownloadManager(manager: DownloadManager) {
            downloadManagerInstance = manager
        }
        
        /**
         * Get or create notification helper
         */
        fun getDownloadNotificationHelper(context: Context): DownloadNotificationHelper {
            if (downloadNotificationHelper == null) {
                downloadNotificationHelper = DownloadNotificationHelper(context, CHANNEL_ID)
            }
            return downloadNotificationHelper!!
        }
        
        /**
         * Get or create the shared Cache instance
         */
        @Synchronized
        fun getOrCreateCache(context: Context): Cache {
            if (downloadCacheInstance == null) {
                val appContext = context.applicationContext
                val downloadDir = File(appContext.filesDir, "video_downloads")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                
                downloadCacheInstance = SimpleCache(
                    downloadDir,
                    NoOpCacheEvictor(),
                    databaseProvider
                )
            }
            return downloadCacheInstance!!
        }
        
        /**
         * Get or create DownloadManager - creates one if not already set
         */
        @Synchronized
        fun getOrCreateDownloadManager(context: Context): DownloadManager {
            if (downloadManagerInstance == null) {
                val appContext = context.applicationContext
                val databaseProvider = StandaloneDatabaseProvider(appContext)
                val downloadCache = getOrCreateCache(appContext)
                
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("SandTV/1.0")
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                
                downloadManagerInstance = DownloadManager(
                    appContext,
                    databaseProvider,
                    downloadCache,
                    dataSourceFactory,
                    Executors.newFixedThreadPool(4)
                )
            }
            return downloadManagerInstance!!
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun getDownloadManager(): DownloadManager {
        return getOrCreateDownloadManager(this)
    }
    
    override fun getScheduler(): Scheduler? {
        return if (Build.VERSION.SDK_INT >= 21) {
            PlatformScheduler(this, JOB_ID)
        } else {
            null
        }
    }
    
    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        val helper = getDownloadNotificationHelper(this)
        
        return helper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null, // No pending intent for now
            null, // Content text - will show progress
            downloads,
            notMetRequirements
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
