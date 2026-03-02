package it.sandtv.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.R
import it.sandtv.app.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for showing app notifications
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_GENERAL = "sandtv_general"
        const val CHANNEL_NEW_CONTENT = "sandtv_new_content"
        const val CHANNEL_DOWNLOADS = "sandtv_downloads"
        
        const val NOTIFICATION_NEW_CONTENT = 1001
        const val NOTIFICATION_DOWNLOAD_PROGRESS = 1002
        const val NOTIFICATION_SYNC_COMPLETE = 1003
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // General channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_general_desc)
            }
            
            // New content channel
            val newContentChannel = NotificationChannel(
                CHANNEL_NEW_CONTENT,
                context.getString(R.string.notification_channel_new_content),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_new_content_desc)
            }
            
            // Downloads channel
            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
            }
            
            manager.createNotificationChannels(listOf(
                generalChannel,
                newContentChannel,
                downloadsChannel
            ))
        }
    }
    
    /**
     * Show notification for new content
     */
    fun showNewContentNotification(count: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_CONTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_new_content_title))
            .setContentText(context.getString(R.string.notification_new_content_text, count))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_NEW_CONTENT, notification)
    }
    
    /**
     * Show sync complete notification
     */
    fun showSyncCompleteNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_sync_complete))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_SYNC_COMPLETE, notification)
    }
    
    /**
     * Show download progress notification
     */
    fun showDownloadProgressNotification(title: String, progress: Int, max: Int = 100): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_downloading))
            .setContentText(title)
            .setProgress(max, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
    }
    
    /**
     * Update download progress
     */
    fun updateDownloadProgress(title: String, progress: Int) {
        val notification = showDownloadProgressNotification(title, progress).build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_DOWNLOAD_PROGRESS, notification)
    }
    
    /**
     * Cancel download notification
     */
    fun cancelDownloadNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_DOWNLOAD_PROGRESS)
    }
}
