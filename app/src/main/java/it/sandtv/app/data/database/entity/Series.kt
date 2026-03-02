package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Series entity representing a TV series
 */
@Entity(
    tableName = "series",
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
data class Series(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    
    // Basic info from playlist
    val name: String,
    val logoUrl: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val trendingCategory: String? = null,  // "Serie Popolari" for trending series, updated weekly
    
    // Xtream specific
    val xtreamSeriesId: Int? = null,
    
    // TMDB enriched data
    val tmdbId: Int? = null,
    val tmdbPosterPath: String? = null,
    val tmdbBackdropPath: String? = null,
    val tmdbName: String? = null,
    val tmdbOriginalName: String? = null,
    val tmdbOverview: String? = null,
    val tmdbFirstAirDate: String? = null,
    val tmdbVoteAverage: Float? = null,
    val tmdbVoteCount: Int? = null,
    val tmdbPopularity: Float? = null,
    val tmdbGenres: String? = null, // JSON array of genre names
    val tmdbNumberOfSeasons: Int? = null,
    val tmdbNumberOfEpisodes: Int? = null,
    val tmdbCast: String? = null, // JSON array of cast names
    val tmdbStatus: String? = null, // "Returning Series", "Ended", etc.
    val tmdbTrailerKey: String? = null, // YouTube video key
    
    // Metadata
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
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
    val year: Int? get() = tmdbFirstAirDate?.take(4)?.toIntOrNull()
    val imdbId: String? get() = null
    val title: String get() = tmdbName?.takeIf { it.isNotEmpty() } ?: name
}
