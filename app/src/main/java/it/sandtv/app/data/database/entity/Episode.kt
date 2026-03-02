package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Episode entity representing a single episode of a series
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("seriesId"),
        Index(value = ["seriesId", "seasonNumber", "episodeNumber"], unique = true)
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Long,
    
    // Episode info
    val name: String,
    val streamUrl: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    
    // Xtream specific
    val xtreamEpisodeId: Int? = null,
    val containerExtension: String? = null,
    
    // TMDB enriched data
    val tmdbEpisodeId: Int? = null,
    val tmdbName: String? = null,
    val tmdbOverview: String? = null,
    val tmdbStillPath: String? = null,
    val tmdbAirDate: String? = null,
    val tmdbVoteAverage: Float? = null,
    val tmdbRuntime: Int? = null, // in minutes
    
    // Stream info
    val duration: Long? = null, // in seconds
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolution: String? = null,
    
    // Metadata
    val addedAt: Long = System.currentTimeMillis()
) {
    // Convenience properties for UI
    val season: Int get() = seasonNumber
    val episode: Int get() = episodeNumber
    val posterUrl: String? get() = tmdbStillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val title: String get() = tmdbName ?: name
}
