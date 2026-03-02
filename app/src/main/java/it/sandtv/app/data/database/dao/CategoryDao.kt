package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Category
import it.sandtv.app.data.database.entity.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    
    @Query("SELECT * FROM categories WHERE type = :type AND isHidden = 0 ORDER BY displayOrder, name")
    fun getCategoriesByType(type: CategoryType): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE type = :type AND isHidden = 0 ORDER BY displayOrder, name")
    suspend fun getCategoriesByTypeList(type: CategoryType): List<Category>
    
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type AND isHidden = 0 ORDER BY displayOrder, name")
    suspend fun getCategoriesByPlaylistAndType(playlistId: Long, type: CategoryType): List<Category>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)
    
    @Update
    suspend fun update(category: Category)
    
    @Delete
    suspend fun delete(category: Category)
    
    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
    
    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteByPlaylistAndType(playlistId: Long, type: CategoryType)
    
    @Query("UPDATE categories SET itemCount = :count WHERE id = :id")
    suspend fun updateItemCount(id: Long, count: Int)
}
