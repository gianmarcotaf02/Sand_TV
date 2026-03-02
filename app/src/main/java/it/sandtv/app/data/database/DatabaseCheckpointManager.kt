package it.sandtv.app.data.database

import android.util.Log
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager that periodically checkpoints the SQLite WAL (Write-Ahead Log)
 * to ensure data is written to disk and not lost on unexpected shutdown.
 * 
 * On Android TV / Fire Stick, when the TV is turned off suddenly,
 * data in the WAL that hasn't been checkpointed may be lost.
 */
@Singleton
class DatabaseCheckpointManager @Inject constructor(
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "DBCheckpoint"
        private const val CHECKPOINT_INTERVAL_MS = 30_000L // 30 seconds
    }
    
    private var checkpointJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Start periodic checkpoint timer
     */
    fun startPeriodicCheckpoint() {
        if (checkpointJob?.isActive == true) return
        
        checkpointJob = scope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                forceCheckpoint()
            }
        }
        Log.d(TAG, "Started periodic WAL checkpoint (every ${CHECKPOINT_INTERVAL_MS / 1000}s)")
    }
    
    /**
     * Stop periodic checkpoint timer
     */
    fun stopPeriodicCheckpoint() {
        checkpointJob?.cancel()
        checkpointJob = null
        Log.d(TAG, "Stopped periodic WAL checkpoint")
    }
    
    /**
     * Force an immediate WAL checkpoint to write all pending data to disk.
     * TRUNCATE mode clears the WAL file after checkpointing for maximum durability.
     */
    fun forceCheckpoint() {
        // Run on IO thread to avoid blocking Main Thread (e.g. when called from onTrimMemory)
        scope.launch {
            try {
                // WAL Checkpoint is heavy/blocking - never run on Main
                if (database.isOpen) {
                    database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                    Log.d(TAG, "WAL checkpoint completed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "WAL checkpoint failed: ${e.message}")
            }
        }
    }
    
    /**
     * Called when system is low on memory - ensures data is saved
     */
    fun onLowMemory() {
        scope.launch {
            forceCheckpoint()
            Log.d(TAG, "Checkpoint on low memory")
        }
    }
}
