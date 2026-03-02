package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.CustomGroup
import it.sandtv.app.data.database.entity.GroupItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomGroupDao {
    
    // ========== Groups ==========
    
    @Query("SELECT * FROM custom_groups WHERE profileId = :profileId ORDER BY displayOrder")
    fun getGroupsForProfile(profileId: Long): Flow<List<CustomGroup>>
    
    @Query("SELECT * FROM custom_groups WHERE profileId = :profileId ORDER BY displayOrder")
    suspend fun getGroupsForProfileList(profileId: Long): List<CustomGroup>
    
    @Query("SELECT * FROM custom_groups ORDER BY profileId, displayOrder")
    fun getAllGroups(): Flow<List<CustomGroup>>
    
    @Query("SELECT * FROM custom_groups WHERE profileId = :profileId AND name = :name LIMIT 1")
    suspend fun getGroupByName(profileId: Long, name: String): CustomGroup?
    
    @Query("SELECT * FROM custom_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): CustomGroup?
    
    @Query("SELECT * FROM custom_groups WHERE id = :groupId")
    fun getGroupByIdFlow(groupId: Long): Flow<CustomGroup?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CustomGroup): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: CustomGroup): Long
    
    @Update
    suspend fun updateGroup(group: CustomGroup)
    
    @Delete
    suspend fun deleteGroup(group: CustomGroup)
    
    @Query("UPDATE custom_groups SET displayOrder = :order WHERE id = :groupId")
    suspend fun updateGroupOrder(groupId: Long, order: Int)
    
    // ========== Group Items ==========
    
    @Query("SELECT * FROM group_items WHERE groupId = :groupId ORDER BY displayOrder")
    fun getItemsForGroup(groupId: Long): Flow<List<GroupItem>>
    
    @Query("SELECT * FROM group_items WHERE groupId = :groupId ORDER BY displayOrder")
    suspend fun getItemsForGroupList(groupId: Long): List<GroupItem>
    
    @Query("SELECT * FROM group_items WHERE id = :itemId")
    suspend fun getItemById(itemId: Long): GroupItem?
    
    @Query("SELECT COUNT(*) FROM group_items WHERE groupId = :groupId")
    suspend fun getItemCount(groupId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: GroupItem): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<GroupItem>)
    
    @Update
    suspend fun updateItem(item: GroupItem)
    
    @Delete
    suspend fun deleteItem(item: GroupItem)
    
    @Query("DELETE FROM group_items WHERE groupId = :groupId")
    suspend fun deleteAllItemsInGroup(groupId: Long)
    
    @Query("UPDATE group_items SET displayOrder = :order WHERE id = :itemId")
    suspend fun updateItemOrder(itemId: Long, order: Int)
    
    @Query("UPDATE group_items SET isWatched = :watched WHERE id = :itemId")
    suspend fun updateItemWatched(itemId: Long, watched: Boolean)
    
    // ========== Next Item Logic ==========
    
    /**
     * Get next item in group after current order
     * Used for auto-play next
     */
    @Query("""
        SELECT * FROM group_items 
        WHERE groupId = :groupId AND displayOrder > :currentOrder 
        ORDER BY displayOrder ASC 
        LIMIT 1
    """)
    suspend fun getNextItem(groupId: Long, currentOrder: Int): GroupItem?
    
    /**
     * Get previous item in group before current order
     * Used for navigating to previous episode
     */
    @Query("""
        SELECT * FROM group_items 
        WHERE groupId = :groupId AND displayOrder < :currentOrder 
        ORDER BY displayOrder DESC 
        LIMIT 1
    """)
    suspend fun getPreviousItem(groupId: Long, currentOrder: Int): GroupItem?
    
    /**
     * Get first item in group (for loop playback)
     */
    @Query("SELECT * FROM group_items WHERE groupId = :groupId ORDER BY displayOrder ASC LIMIT 1")
    suspend fun getFirstItem(groupId: Long): GroupItem?
    
    /**
     * Get current item's position info
     */
    @Query("""
        SELECT gi.*, 
               (SELECT COUNT(*) FROM group_items WHERE groupId = gi.groupId) as totalItems
        FROM group_items gi
        WHERE gi.groupId = :groupId AND gi.contentId = :contentId
    """)
    suspend fun getItemPosition(groupId: Long, contentId: Long): GroupItem?
}
