package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    fun getEpisodesBySeries(seriesId: Long): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    suspend fun getEpisodesBySeriesList(seriesId: Long): List<Episode>
    
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber")
    fun getEpisodesBySeason(seriesId: Long, seasonNumber: Int): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber")
    suspend fun getEpisodesBySeasonList(seriesId: Long, seasonNumber: Int): List<Episode>
    
    @Query("SELECT DISTINCT seasonNumber FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber")
    suspend fun getSeasonNumbers(seriesId: Long): List<Int>
    
    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): Episode?
    
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun getEpisode(seriesId: Long, seasonNumber: Int, episodeNumber: Int): Episode?
    
    // Get next episode after the given one
    @Query("""
        SELECT * FROM episodes 
        WHERE seriesId = :seriesId 
        AND (seasonNumber > :seasonNumber OR (seasonNumber = :seasonNumber AND episodeNumber > :episodeNumber))
        ORDER BY seasonNumber, episodeNumber
        LIMIT 1
    """)
    suspend fun getNextEpisode(seriesId: Long, seasonNumber: Int, episodeNumber: Int): Episode?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: Episode): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<Episode>)
    
    @Update
    suspend fun update(episode: Episode)
    
    @Delete
    suspend fun delete(episode: Episode)
    
    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteBySeries(seriesId: Long)
    
    @Query("DELETE FROM episodes WHERE seriesId IN (:seriesIds)")
    suspend fun deleteBySeriesIds(seriesIds: List<Long>)
    
    @Query("SELECT COUNT(*) FROM episodes WHERE seriesId = :seriesId")
    suspend fun getCountBySeries(seriesId: Long): Int
    
    // Get last episode of a specific season (for previous episode navigation)
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season ORDER BY episodeNumber DESC LIMIT 1")
    suspend fun getLastEpisodeOfSeason(seriesId: Long, season: Int): Episode?
    
    // Get first episode of a series (S1E1 or lowest available)
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber LIMIT 1")
    suspend fun getFirstEpisodeForSeries(seriesId: Long): Episode?
}
