package it.sandtv.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Favorite category entity - stores user's favorite categories
 * Can be movies, series, or channel categories
 */
@Entity(
    tableName = "favorite_categories",
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
        Index(value = ["profileId", "categoryType", "categoryName"], unique = true)
    ]
)
data class FavoriteCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,
    
    // Category type: "movies", "series", "channels"
    val categoryType: String,
    
    // Category name (the actual category name from playlist)
    val categoryName: String,
    
    // Timestamp
    val addedAt: Long = System.currentTimeMillis()
)
