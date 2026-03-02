package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    
    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY displayOrder, name")
    fun getAllChannels(): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY displayOrder, name")
    suspend fun getAllChannelsList(): List<Channel>
    
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY displayOrder, name")
    fun getChannelsByPlaylist(playlistId: Long): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE category = :category AND isHidden = 0 ORDER BY displayOrder, name")
    fun getChannelsByCategory(category: String): Flow<List<Channel>>
    
    @Query("SELECT * FROM channels WHERE category = :category AND isHidden = 0 ORDER BY displayOrder, name")
    suspend fun getChannelsByCategoryList(category: String): List<Channel>
    
    @Query("SELECT category FROM channels WHERE category IS NOT NULL AND isHidden = 0 GROUP BY category ORDER BY MIN(id)")
    fun getCategories(): Flow<List<String>>
    
    @Query("SELECT category FROM channels WHERE category IS NOT NULL AND isHidden = 0 GROUP BY category ORDER BY MIN(id)")
    suspend fun getCategoriesList(): List<String>
    
    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannelById(id: Long): Channel?
    
    @Query("SELECT * FROM channels WHERE id IN (:ids) AND isHidden = 0")
    suspend fun getChannelsByIds(ids: List<Long>): List<Channel>
    
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    suspend fun searchChannels(query: String): List<Channel>
    
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    fun searchChannelsFlow(query: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE (name LIKE '%' || :query1 || '%' OR name LIKE '%' || :query2 || '%') AND isHidden = 0 ORDER BY name")
    suspend fun searchChannelsDual(query1: String, query2: String): List<Channel>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: Channel): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<Channel>)
    
    @Update
    suspend fun update(channel: Channel)
    
    @Delete
    suspend fun delete(channel: Channel)
    
    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
    
    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getCountByPlaylist(playlistId: Long): Int
    
    @Query("UPDATE channels SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)
    
    @Query("""
        SELECT category as name, COUNT(*) as count 
        FROM channels 
        WHERE category IS NOT NULL AND isHidden = 0 
        GROUP BY category 
        ORDER BY MIN(id)
    """)
    suspend fun getCategoriesWithCount(): List<CategoryWithCount>
}


