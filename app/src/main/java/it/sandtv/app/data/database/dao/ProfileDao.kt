package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    
    @Query("SELECT * FROM profiles ORDER BY lastUsedAt DESC")
    fun getAllProfiles(): Flow<List<Profile>>
    
    @Query("SELECT * FROM profiles ORDER BY lastUsedAt DESC")
    suspend fun getAllProfilesList(): List<Profile>
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): Profile?
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    fun getProfileByIdFlow(id: Long): Flow<Profile?>
    
    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): Profile?
    
    @Query("SELECT * FROM profiles WHERE isLastUsed = 1 LIMIT 1")
    suspend fun getLastUsedProfile(): Profile?
    
    @Query("SELECT * FROM profiles WHERE playlistId = :playlistId")
    suspend fun getProfileByPlaylistId(playlistId: Long): Profile?
    
    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getProfileByName(name: String): Profile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: Profile): Long
    
    @Update
    suspend fun update(profile: Profile)
    
    @Delete
    suspend fun delete(profile: Profile)
    
    @Query("UPDATE profiles SET lastUsedAt = :timestamp, isLastUsed = 1 WHERE id = :profileId")
    suspend fun updateLastUsed(profileId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE profiles SET isLastUsed = 0")
    suspend fun clearLastUsed()
    
    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefaultProfile()
    
    @Query("UPDATE profiles SET isDefault = 1 WHERE id = :profileId")
    suspend fun setDefaultProfile(profileId: Long)
    
    @Transaction
    suspend fun setAsDefault(profileId: Long) {
        clearDefaultProfile()
        setDefaultProfile(profileId)
    }
    
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}
