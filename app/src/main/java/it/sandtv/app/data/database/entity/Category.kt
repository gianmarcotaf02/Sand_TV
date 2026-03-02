package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Category entity for organizing content
 * Supports user customization (hiding, reordering)
 */
@Entity(
    tableName = "categories",
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
        Index("type"),
        Index(value = ["playlistId", "type", "name"], unique = true)
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    
    // Category info
    val name: String,
    val type: CategoryType,
    val externalId: String? = null, // Original ID from Xtream/M3U
    
    // User customization
    val displayOrder: Int = 0,    // User can reorder
    val isHidden: Boolean = false, // User can hide categories
    val isExpanded: Boolean = true, // Collapsed/expanded state
    
    // Display
    val iconUrl: String? = null,
    
    // Item counts
    val itemCount: Int = 0
)
