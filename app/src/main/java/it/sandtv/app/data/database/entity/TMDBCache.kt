package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TMDBCache entity for caching TMDB search results
 */
@Entity(
    tableName = "tmdb_cache",
    indices = [
        Index("searchQuery"),
        Index("tmdbId"),
        Index("mediaType"),
        Index(value = ["searchQuery", "mediaType"], unique = true)
    ]
)
data class TMDBCache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Search query
    val searchQuery: String,
    val mediaType: TMDBMediaType,
    
    // Result
    val tmdbId: Int,
    val title: String,
    val originalTitle: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Float? = null,
    val popularity: Float? = null,
    
    // Cache metadata
    val confidence: Float = 0f, // Match confidence 0.0 to 1.0
    val fetchedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // 7 days
)

enum class TMDBMediaType {
    MOVIE,
    TV
}
