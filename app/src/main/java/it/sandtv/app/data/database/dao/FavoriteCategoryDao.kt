package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.FavoriteCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteCategoryDao {
    
    @Query("SELECT * FROM favorite_categories WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun getFavoriteCategoriesByProfile(profileId: Long): Flow<List<FavoriteCategory>>
    
    @Query("SELECT * FROM favorite_categories WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun getFavoriteCategoriesList(profileId: Long): List<FavoriteCategory>
    
    @Query("SELECT * FROM favorite_categories WHERE profileId = :profileId AND categoryType = :categoryType ORDER BY addedAt DESC")
    suspend fun getFavoriteCategoriesByType(profileId: Long, categoryType: String): List<FavoriteCategory>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_categories WHERE profileId = :profileId AND categoryType = :categoryType AND categoryName = :categoryName)")
    suspend fun isFavoriteCategory(profileId: Long, categoryType: String, categoryName: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favoriteCategory: FavoriteCategory): Long
    
    @Query("DELETE FROM favorite_categories WHERE profileId = :profileId AND categoryType = :categoryType AND categoryName = :categoryName")
    suspend fun removeFavoriteCategory(profileId: Long, categoryType: String, categoryName: String)
    
    @Query("DELETE FROM favorite_categories WHERE profileId = :profileId")
    suspend fun deleteAllForProfile(profileId: Long)
    
    @Transaction
    suspend fun toggleFavoriteCategory(favoriteCategory: FavoriteCategory): Boolean {
        val exists = isFavoriteCategory(favoriteCategory.profileId, favoriteCategory.categoryType, favoriteCategory.categoryName)
        if (exists) {
            removeFavoriteCategory(favoriteCategory.profileId, favoriteCategory.categoryType, favoriteCategory.categoryName)
            return false
        } else {
            insert(favoriteCategory)
            return true
        }
    }
}
