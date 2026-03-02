package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Movie entity representing a VOD movie
 */
@Entity(
    tableName = "movies",
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
        Index("category"),
        Index("name"),
        Index("tmdbId")
    ]
)
data class Movie(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    
    // Basic info from playlist
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val trendingCategory: String? = null,  // "Film Popolari" for trending movies, updated weekly
    
    // Xtream specific
    val xtreamStreamId: Int? = null,
    val containerExtension: String? = null,
    
    // TMDB enriched data
    val tmdbId: Int? = null,
    val tmdbPosterPath: String? = null,
    val tmdbBackdropPath: String? = null,
    val tmdbTitle: String? = null,
    val tmdbOriginalTitle: String? = null,
    val tmdbOverview: String? = null,
    val tmdbReleaseDate: String? = null,
    val tmdbVoteAverage: Float? = null,
    val tmdbVoteCount: Int? = null,
    val tmdbPopularity: Float? = null,
    val tmdbGenres: String? = null, // JSON array of genre names
    val tmdbRuntime: Int? = null, // in minutes
    val tmdbCast: String? = null, // JSON array of cast names
    val tmdbDirector: String? = null,
    val tmdbImdbId: String? = null, // IMDB ID from TMDB external_ids
    val tmdbTrailerKey: String? = null, // YouTube video key
    
    // Stream info
    val duration: Long? = null, // in seconds
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null,
    
    // Metadata
    val year: Int? = null,
    val isHidden: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val tmdbLastFetchAt: Long? = null,
    val playlistOrder: Int = 0,  // Position in M3U file (higher = added later by provider)
    
    // OMDB cached ratings
    val omdbImdbRating: String? = null,
    val omdbRottenTomatoesScore: Int? = null,
    val omdbMetacriticScore: Int? = null,
    val omdbAudienceScore: Int? = null,  // Popcornmeter (tomatoUserMeter)
    val omdbLastFetchAt: Long? = null
) {
    // Convenience properties for UI
    val posterUrl: String? get() = logoUrl ?: tmdbPosterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = tmdbBackdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val rating: Float? get() = tmdbVoteAverage
    val plot: String? get() = tmdbOverview
    val genre: String? get() = tmdbGenres
    val imdbId: String? get() = tmdbImdbId
    val title: String get() = tmdbTitle?.takeIf { it.isNotEmpty() } ?: name
}
