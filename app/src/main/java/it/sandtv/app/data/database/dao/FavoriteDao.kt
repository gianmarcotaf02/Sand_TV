package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.Favorite
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getFavoritesByProfile(profileId: Long): Flow<List<Favorite>>
    
    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun getFavoritesByProfileList(profileId: Long): List<Favorite>
    
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND contentType = :contentType ORDER BY addedAt DESC")
    fun getFavoritesByType(profileId: Long, contentType: ContentType): Flow<List<Favorite>>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId)")
    suspend fun isFavorite(profileId: Long, contentType: ContentType, contentId: Long): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId)")
    fun isFavoriteFlow(profileId: Long, contentType: ContentType, contentId: Long): Flow<Boolean>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite): Long
    
    @Delete
    suspend fun delete(favorite: Favorite)
    
    @Query("DELETE FROM favorites WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId")
    suspend fun removeFavorite(profileId: Long, contentType: ContentType, contentId: Long)
    
    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
    
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAllFavorites(): List<Favorite>
    
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND contentType = :contentType AND contentId = :contentId LIMIT 1")
    suspend fun getFavorite(profileId: Long, contentType: ContentType, contentId: Long): Favorite?
    
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavorite(id: Long)
    
    @Transaction
    suspend fun toggleFavorite(favorite: Favorite): Boolean {
        val exists = isFavorite(favorite.profileId, favorite.contentType, favorite.contentId)
        if (exists) {
            removeFavorite(favorite.profileId, favorite.contentType, favorite.contentId)
            return false
        } else {
            insert(favorite)
            return true
        }
    }
}
