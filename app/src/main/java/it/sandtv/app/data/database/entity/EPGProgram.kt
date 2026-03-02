package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * EPGProgram entity for electronic program guide data
 */
@Entity(
    tableName = "epg_programs",
    foreignKeys = [
        ForeignKey(
            entity = Channel::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("channelId"),
        Index("startTime"),
        Index("endTime"),
        Index(value = ["channelId", "startTime"])
    ]
)
data class EPGProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: Long,
    
    // EPG channel ID (from XML)
    val epgChannelId: String,
    
    // Program info
    val title: String,
    val description: String? = null,
    val startTime: Long, // Unix timestamp in milliseconds
    val endTime: Long,   // Unix timestamp in milliseconds
    
    // Additional info
    val category: String? = null,
    val iconUrl: String? = null,
    val rating: String? = null,
    val episode: String? = null, // e.g., "S01E05"
    
    // Catch-up
    val hasCatchup: Boolean = false,
    val catchupUrl: String? = null,
    
    // Metadata
    val addedAt: Long = System.currentTimeMillis()
)
