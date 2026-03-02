package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Channel entity representing a live TV channel
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("category"),
        Index("name")
    ]
)
data class Channel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    
    // Basic info
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    
    // Xtream specific
    val xtreamStreamId: Int? = null,
    val xtreamEpgChannelId: String? = null,
    
    // Stream info
    val streamType: StreamType = StreamType.LIVE,
    val containerExtension: String? = null,
    
    // Catch-up/Timeshift support
    val hasCatchup: Boolean = false,
    val catchupDays: Int = 0,
    val catchupSource: String? = null,
    
    // Display order
    val displayOrder: Int = 0,
    
    // Metadata
    val isHidden: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    // Convenience properties for UI
    val categoryName: String? get() = category
    val epgChannelId: String? get() = xtreamEpgChannelId
    val title: String get() = name
}

enum class StreamType {
    LIVE,
    MOVIE,
    SERIES
}
