package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Custom user-defined group/collection
 * Examples: "Harry Potter Saga", "Marvel Movies", "Weekend Watch"
 */
@Entity(
    tableName = "custom_groups",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class CustomGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    
    val name: String,
    val description: String? = null,
    
    // Display
    val coverUrl: String? = null,  // Group cover image
    val color: String = "#8B5CF6", // Theme color
    
    // Settings
    val autoPlayNext: Boolean = true,  // Auto-play next item in group
    val loopPlayback: Boolean = false, // Loop back to start after last
    
    // Order in profile's group list
    val displayOrder: Int = 0,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
