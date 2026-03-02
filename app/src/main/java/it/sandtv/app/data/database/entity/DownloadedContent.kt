package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking downloaded content (movies and episodes)
 * Content is cached internally using ExoPlayer's cache system,
 * not exported as accessible files.
 */
@Entity(tableName = "downloaded_content")
data class DownloadedContent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Type of content: MOVIE or EPISODE */
    val contentType: ContentType,
    
    /** Reference to movie.id or episode.id */
    val contentId: Long,
    
    /** Display title */
    val title: String,
    
    /** Poster image URL for display */
    val posterUrl: String? = null,
    
    /** Timestamp when download was initiated */
    val downloadedAt: Long = System.currentTimeMillis(),
    
    /** Download size in bytes */
    val downloadSize: Long = 0,
    
    /** Unique key for ExoPlayer cache lookup */
    val cacheKey: String,
    
    /** Stream URL for playback */
    val streamUrl: String,
    
    // Series-specific fields (for episodes only)
    
    /** Series ID for episodes */
    val seriesId: Long? = null,
    
    /** Series name for display */
    val seriesName: String? = null,
    
    /** Season number for episodes */
    val seasonNumber: Int? = null,
    
    /** Episode number for episodes */
    val episodeNumber: Int? = null,
    
    /** Download progress 0-100 */
    val downloadProgress: Int = 0,
    
    /** Whether download is complete and ready for offline playback */
    val isComplete: Boolean = false
) {
    /**
     * Display subtitle for UI (e.g., "S1E4" for episodes)
     */
    val subtitle: String?
        get() = if (seasonNumber != null && episodeNumber != null) {
            "S${seasonNumber}E${episodeNumber}"
        } else null
    
    /**
     * Full display title including series name for episodes
     */
    val displayTitle: String
        get() = if (seriesName != null && seasonNumber != null && episodeNumber != null) {
            "$seriesName - S${seasonNumber}E${episodeNumber}"
        } else title
}
