package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Profile entity representing a user profile linked to a playlist
 * Each profile has its own watch history, favorites, and preferences
 */
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("playlistId")]
)
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    
    // Avatar (index for predefined avatars or custom color)
    val avatarIndex: Int = 0,
    val avatarColor: String = "#8B5CF6", // Default purple
    
    // Associated playlist
    val playlistId: Long? = null,
    
    // Auto-start settings
    val isDefault: Boolean = false,      // This profile starts automatically
    val isLastUsed: Boolean = false,     // Track last used for "use last profile" setting
    
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
