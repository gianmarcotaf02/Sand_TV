package it.sandtv.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.sandtv.app.data.database.entity.TeamChannelMap

@Dao
interface TeamChannelMapDao {
    @Query("SELECT * FROM team_channel_map WHERE teamName = :teamName")
    suspend fun getMapForTeam(teamName: String): TeamChannelMap?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(map: TeamChannelMap)
    
    @Query("DELETE FROM team_channel_map")
    suspend fun clearAll()
}
