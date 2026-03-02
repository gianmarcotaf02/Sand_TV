package it.sandtv.app.data.database.entity

/**
 * Data class for Continue Watching carousel items
 * Combines WatchProgress with display info from Movie/Series
 */
data class ContinueWatchingItem(
    val watchProgressId: Long,
    val contentType: ContentType,
    val contentId: Long,
    
    // Display info
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    
    // Progress info
    val position: Long,       // in milliseconds
    val duration: Long,       // in milliseconds
    val progressPercent: Float,
    val remainingMinutes: Int,
    
    // Series specific
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    
    val lastWatchedAt: Long
) {
    val isMovie: Boolean get() = contentType == ContentType.MOVIE
    val isSeries: Boolean get() = contentType == ContentType.EPISODE || contentType == ContentType.SERIES
    
    /**
     * Format remaining time for display: "45 min rimasti"
     */
    fun formatRemainingTime(): String {
        return when {
            remainingMinutes <= 0 -> "Pochi minuti"
            remainingMinutes == 1 -> "1 min rimasto"
            remainingMinutes < 60 -> "$remainingMinutes min rimasti"
            else -> {
                val hours = remainingMinutes / 60
                val mins = remainingMinutes % 60
                if (mins > 0) "${hours}h ${mins}min rimasti" else "${hours}h rimaste"
            }
        }
    }
}
