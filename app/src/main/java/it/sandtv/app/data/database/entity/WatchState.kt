package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * WatchState entity for tracking playback position (resume functionality)
 */
@Entity(
    tableName = "watch_states",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "contentType", "contentId"], unique = true)
    ]
)
data class WatchState(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    
    // Content reference
    val contentType: ContentType,
    val contentId: Long, // ID of Channel, Movie, or Episode
    
    // Playback state
    val position: Long = 0, // in milliseconds
    val duration: Long = 0, // in milliseconds
    val progress: Float = 0f, // 0.0 to 1.0
    val isCompleted: Boolean = false,
    
    // For series tracking
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    
    // Display info
    val title: String? = null,
    val thumbnailUrl: String? = null,
    
    // Timestamps
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
