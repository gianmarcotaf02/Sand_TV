package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabledPlaylists(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledPlaylistsList(): List<Playlist>
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?
    
    @Query("SELECT * FROM playlists WHERE url = :url")
    suspend fun getPlaylistByUrl(url: String): Playlist?
    
    @Query("SELECT * FROM playlists WHERE name = :name")
    suspend fun getPlaylistByName(name: String): Playlist?
    
    @Query("SELECT * FROM playlists WHERE type = :type")
    suspend fun getPlaylistsByType(type: String): List<Playlist>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long
    
    @Update
    suspend fun update(playlist: Playlist)
    
    @Delete
    suspend fun delete(playlist: Playlist)
    
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("UPDATE playlists SET lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateLastUpdated(id: Long, timestamp: Long)
    
    @Query("UPDATE playlists SET channelCount = :count WHERE id = :id")
    suspend fun updateChannelCount(id: Long, count: Int)
    
    @Query("UPDATE playlists SET movieCount = :count WHERE id = :id")
    suspend fun updateMovieCount(id: Long, count: Int)
    
    @Query("UPDATE playlists SET seriesCount = :count WHERE id = :id")
    suspend fun updateSeriesCount(id: Long, count: Int)
    
    @Query("UPDATE playlists SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
    
    @Query("UPDATE playlists SET channelCount = :channels, movieCount = :movies, seriesCount = :series WHERE id = :id")
    suspend fun updateCounts(id: Long, channels: Int, movies: Int, series: Int)
}
