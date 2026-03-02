package it.sandtv.app.data.database

import androidx.room.TypeConverter
import it.sandtv.app.data.database.entity.CategoryType
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.FavoriteType
import it.sandtv.app.data.database.entity.StreamQuality

/**
 * Type converters for Room database
 */
class Converters {
    
    // StreamQuality
    @TypeConverter
    fun fromStreamQuality(value: StreamQuality): String = value.name
    
    @TypeConverter
    fun toStreamQuality(value: String): StreamQuality = 
        StreamQuality.valueOf(value)
    
    // CategoryType
    @TypeConverter
    fun fromCategoryType(value: CategoryType): String = value.name
    
    @TypeConverter
    fun toCategoryType(value: String): CategoryType = 
        CategoryType.valueOf(value)
    
    // FavoriteType
    @TypeConverter
    fun fromFavoriteType(value: FavoriteType): String = value.name
    
    @TypeConverter
    fun toFavoriteType(value: String): FavoriteType = 
        FavoriteType.valueOf(value)
    
    // ContentType
    @TypeConverter
    fun fromContentType(value: ContentType): String = value.name
    
    @TypeConverter
    fun toContentType(value: String): ContentType = 
        ContentType.valueOf(value)
    
    // List<String>
    @TypeConverter
    fun fromStringList(value: List<String>?): String? = 
        value?.joinToString(",")
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? = 
        value?.split(",")?.filter { it.isNotEmpty() }
}
