package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for tracking watch progress
 */
@Entity(
    tableName = "watch_progress",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["profileId", "contentType", "contentId"], unique = true)
    ]
)
data class WatchProgress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    val contentType: ContentType,
    val contentId: Long,
    val seriesId: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0, // Current position in ms
    val duration: Long = 0, // Total duration in ms
    val isCompleted: Boolean = false,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val progressPercent: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()) * 100 else 0f
}
