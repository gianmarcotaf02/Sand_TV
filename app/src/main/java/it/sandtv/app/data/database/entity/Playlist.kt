package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Playlist entity representing an M3U, Xtream, or other playlist source
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String, // "m3u", "xtream", "ftp", "webdav"
    val url: String, // Base URL or M3U URL
    
    // For Xtream Codes
    val username: String? = null,
    val password: String? = null,
    
    // EPG URL
    val epgUrl: String? = null,
    
    // Metadata
    val isEnabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
