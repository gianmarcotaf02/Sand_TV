package it.sandtv.app

import android.app.Application
import android.content.ComponentCallbacks2
import dagger.hilt.android.HiltAndroidApp
import it.sandtv.app.data.database.DatabaseCheckpointManager
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.ui.theme.AccentColor
import it.sandtv.app.ui.theme.SandTVColors
import javax.inject.Inject

/**
 * Main Application class for SandTV
 * 
 * Handles:
 * - Immediate accent color loading from SharedPreferences (sync) for instant theming
 * - Periodic database checkpoint to prevent data loss on sudden shutdown
 * - Memory pressure callbacks to save data before system kills the app
 */
@HiltAndroidApp
class SandTVApplication : Application() {
    
    @Inject
    lateinit var checkpointManager: DatabaseCheckpointManager
    
    @Inject
    lateinit var userPreferences: UserPreferences
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("SandTVDebug", "SandTVApplication onCreate STARTED")
        
        // Load accent color IMMEDIATELY from SharedPreferences (sync)
        // This ensures the theme is correct from the very first screen (LoadingActivity)
        val accentColorId = userPreferences.getAccentColorSync()
        SandTVColors.updateAccent(AccentColor.fromId(accentColorId))
        android.util.Log.d("SandTVDebug", "App Accent Color applied: $accentColorId")
        
        // Start periodic WAL checkpoint (every 30 seconds)
        // This ensures data is written to disk and not lost on sudden TV shutdown
        // FIXME: Disabling this temporarily as it seems to cause lock contention and ANRs
        // checkpointManager.startPeriodicCheckpoint()
        
        android.util.Log.d("SandTVDebug", "SandTVApplication onCreate COMPLETED")
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        // Save data when system is running low on memory
        // These callbacks often precede app termination
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                checkpointManager.forceCheckpoint()
            }
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        checkpointManager.onLowMemory()
    }
    
    override fun onTerminate() {
        // checkpointManager.stopPeriodicCheckpoint()
        checkpointManager.forceCheckpoint()
        super.onTerminate()
    }
}
