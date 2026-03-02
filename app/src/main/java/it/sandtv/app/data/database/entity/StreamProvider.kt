package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single stream source for a Movie or Series
 * Multiple StreamProviders can exist for the same TMDB content (different providers/quality)
 */
@Entity(
    tableName = "stream_providers",
    foreignKeys = [
        ForeignKey(
            entity = Movie::class,
            parentColumns = ["id"],
            childColumns = ["movieId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("movieId"),
        Index("seriesId"),
        Index("playlistId"),
        Index("tmdbId")
    ]
)
data class StreamProvider(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Reference to content (one of these should be set)
    val movieId: Long? = null,
    val seriesId: Long? = null,
    
    // TMDB ID for grouping same content from different sources
    val tmdbId: Int? = null,
    
    // Source playlist
    val playlistId: Long,
    
    // Stream info
    val streamUrl: String,
    val originalName: String,      // Original name from playlist (e.g., "C'era una volta in America - Vers. Integrale")
    val category: String? = null,  // e.g., "Film Drammatici", "Film d'autore"
    
    // Quality indicators
    val quality: StreamQuality = StreamQuality.UNKNOWN,
    val language: String? = null,  // e.g., "ITA", "ENG", "GER"
    val isExtended: Boolean = false,  // Extended/Director's cut
    val isHdr: Boolean = false,
    val is4K: Boolean = false,
    
    // Provider info (from playlist)
    val providerName: String? = null,  // e.g., "GIANMARCO"
    
    // Metadata
    val addedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
