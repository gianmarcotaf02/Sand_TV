package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Favorite entity for profile-specific favorites
 */
@Entity(
    tableName = "favorites",
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
data class Favorite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    
    // Content reference
    val contentType: ContentType,
    val contentId: Long, // ID of Channel, Movie, or Series
    
    // Display info (cached for performance)
    val title: String,
    val posterUrl: String? = null,
    val category: String? = null,
    
    // Timestamp
    val addedAt: Long = System.currentTimeMillis()
)
