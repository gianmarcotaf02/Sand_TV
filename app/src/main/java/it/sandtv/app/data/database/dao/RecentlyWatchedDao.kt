package it.sandtv.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.sandtv.app.data.database.entity.RecentlyWatchedChannel

/**
 * DAO for recently watched channels
 * Supports "Visti di recente" category in Live section
 */
@Dao
interface RecentlyWatchedDao {
    
    /**
     * Insert or update watch timestamp for a channel
     * Uses REPLACE strategy so same channelId gets updated timestamp
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: RecentlyWatchedChannel)
    
    /**
     * Get channel IDs watched since the given timestamp
     * Returns ordered by most recent first
     */
    @Query("SELECT channelId FROM recently_watched_channels WHERE watchedAt >= :sinceTimestamp ORDER BY watchedAt DESC")
    suspend fun getRecentChannelIds(sinceTimestamp: Long): List<Long>
    
    /**
     * Get count of recently watched channels (for showing badge or empty state)
     */
    @Query("SELECT COUNT(*) FROM recently_watched_channels WHERE watchedAt >= :sinceTimestamp")
    suspend fun getRecentCount(sinceTimestamp: Long): Int
    
    /**
     * Cleanup old entries to prevent table from growing indefinitely
     */
    @Query("DELETE FROM recently_watched_channels WHERE watchedAt < :olderThan")
    suspend fun cleanupOlderThan(olderThan: Long)
    
    /**
     * Clear all recently watched entries
     */
    @Query("DELETE FROM recently_watched_channels")
    suspend fun clearAll()
}
