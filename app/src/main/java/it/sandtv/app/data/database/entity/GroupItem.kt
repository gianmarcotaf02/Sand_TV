package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item in a custom group with ordering support
 * Links movies/series/channels to groups with custom order
 */
@Entity(
    tableName = "group_items",
    foreignKeys = [
        ForeignKey(
            entity = CustomGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("groupId"),
        Index(value = ["groupId", "contentType", "contentId"], unique = true)
    ]
)
data class GroupItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    
    // Content reference
    val contentType: ContentType,  // MOVIE, EPISODE, CHANNEL
    val contentId: Long,
    
    // Display order within group (user can drag to reorder)
    val displayOrder: Int = 0,
    
    // Cached info for performance
    val title: String,
    val subtitle: String? = null,  // e.g., "Capitolo 1" or "S01E01"
    val posterUrl: String? = null,
    val streamUrl: String? = null,
    
    // Watch state
    val isWatched: Boolean = false,
    
    val addedAt: Long = System.currentTimeMillis()
)
