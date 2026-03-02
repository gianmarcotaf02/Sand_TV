package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.EPGProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EPGDao {
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :currentTime ORDER BY startTime")
    fun getUpcomingPrograms(channelId: Long, currentTime: Long = System.currentTimeMillis()): Flow<List<EPGProgram>>
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime LIMIT 1")
    suspend fun getCurrentProgram(channelId: Long, currentTime: Long = System.currentTimeMillis()): EPGProgram?
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime >= :startTime AND startTime < :endTime ORDER BY startTime")
    suspend fun getProgramsInRange(channelId: Long, startTime: Long, endTime: Long): List<EPGProgram>
    
    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelId = :channelId 
        AND startTime <= :currentTime 
        AND endTime > :currentTime
        LIMIT 1
    """)
    fun getCurrentProgramFlow(channelId: Long, currentTime: Long = System.currentTimeMillis()): Flow<EPGProgram?>
    
    @Query("""
        SELECT * FROM epg_programs 
        WHERE channelId = :channelId 
        AND startTime > :currentTime 
        ORDER BY startTime 
        LIMIT 1
    """)
    suspend fun getNextProgram(channelId: Long, currentTime: Long = System.currentTimeMillis()): EPGProgram?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: EPGProgram): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EPGProgram>)
    
    @Delete
    suspend fun delete(program: EPGProgram)
    
    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: Long)
    
    @Query("DELETE FROM epg_programs WHERE endTime < :time")
    suspend fun deleteOldPrograms(time: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()
}
