package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Download entity for offline content management
 */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId"),
        Index("status"),
        Index(value = ["profileId", "contentType", "contentId"], unique = true)
    ]
)
data class Download(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    
    // Content reference
    val contentType: ContentType,
    val contentId: Long, // ID of Movie or Episode
    
    // Download info
    val title: String,
    val streamUrl: String,
    val localFilePath: String? = null,
    val posterUrl: String? = null,
    
    // Status
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
    
    // For series
    val seriesId: Long? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
