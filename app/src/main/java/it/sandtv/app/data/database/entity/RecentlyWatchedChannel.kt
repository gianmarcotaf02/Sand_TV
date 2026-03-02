package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Recently watched channel - tracks when a live channel was last viewed
 * Used for "Visti di recente" category in Live section
 * Entries expire after 24 hours (rolling window per channel)
 */
@Entity(tableName = "recently_watched_channels")
data class RecentlyWatchedChannel(
    @PrimaryKey
    val channelId: Long,
    val watchedAt: Long  // Timestamp when channel was watched
)
