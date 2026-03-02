package it.sandtv.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*
import it.sandtv.app.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for backup and restore functionality
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val watchProgressDao: WatchProgressDao,
    private val customGroupDao: CustomGroupDao,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "BackupRepository"
        private const val BACKUP_VERSION = 1
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()
    
    /**
     * Create backup of all user data
     */
    suspend fun createBackup(): BackupData = withContext(Dispatchers.IO) {
        Log.d(TAG, "Creating backup...")
        
        BackupData(
            version = BACKUP_VERSION,
            createdAt = System.currentTimeMillis(),
            profiles = profileDao.getAllProfiles().first(),
            playlists = playlistDao.getAllPlaylists().first().map { playlist ->
                PlaylistBackup(
                    name = playlist.name,
                    url = playlist.url,
                    type = playlist.type,
                    username = playlist.username,
                    password = playlist.password,
                    epgUrl = playlist.epgUrl
                )
            },
            favorites = favoriteDao.getAllFavorites(),
            watchProgress = watchProgressDao.getAllProgress(),
            customGroups = customGroupDao.getAllGroups().first(),
            preferences = PreferencesBackup(
                omdbApiKey = userPreferences.getOmdbApiKey(),
                autoPlayNext = userPreferences.getAutoPlayNext(),
                subtitleLanguage = userPreferences.getSubtitleLanguage()
            )
        )
    }
    
    /**
     * Export backup to JSON file
     */
    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = createBackup()
            val json = gson.toJson(backup)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            
            Log.d(TAG, "Backup exported successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup", e)
            false
        }
    }
    
    /**
     * Export backup to internal storage (returns file path)
     */
    suspend fun exportBackupToFile(): File? = withContext(Dispatchers.IO) {
        try {
            val backup = createBackup()
            val json = gson.toJson(backup)
            
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "sandtv_backup_$dateStr.json"
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            
            val file = File(backupDir, fileName)
            file.writeText(json)
            
            Log.d(TAG, "Backup saved to: ${file.absolutePath}")
            file
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving backup", e)
            null
        }
    }
    
    /**
     * Import backup from URI
     */
    suspend fun importBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return@withContext BackupResult.Error("Impossibile leggere il file")
            
            val backup = gson.fromJson(json, BackupData::class.java)
            
            applyBackup(backup)
            
            BackupResult.Success(
                profileCount = backup.profiles.size,
                playlistCount = backup.playlists.size,
                favoriteCount = backup.favorites.size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error importing backup", e)
            BackupResult.Error(e.message ?: "Errore sconosciuto")
        }
    }
    
    /**
     * Apply backup data to database
     */
    private suspend fun applyBackup(backup: BackupData) {
        // Restore profiles
        backup.profiles.forEach { profile ->
            val existing = profileDao.getProfileByName(profile.name)
            if (existing == null) {
                profileDao.insert(profile.copy(id = 0))
            }
        }
        
        // Restore playlists
        backup.playlists.forEach { playlistBackup ->
            val existing = playlistDao.getPlaylistByUrl(playlistBackup.url)
            if (existing == null) {
                playlistDao.insert(Playlist(
                    name = playlistBackup.name,
                    url = playlistBackup.url,
                    type = playlistBackup.type,
                    username = playlistBackup.username,
                    password = playlistBackup.password,
                    epgUrl = playlistBackup.epgUrl,
                    lastUpdated = System.currentTimeMillis()
                ))
            }
        }
        
        // Restore favorites
        backup.favorites.forEach { favorite ->
            val existing = favoriteDao.getFavorite(favorite.profileId, favorite.contentType, favorite.contentId)
            if (existing == null) {
                favoriteDao.insert(favorite.copy(id = 0))
            }
        }
        
        // Restore watch progress
        backup.watchProgress.forEach { progress ->
            watchProgressDao.upsert(progress)
        }
        
        // Restore custom groups
        backup.customGroups.forEach { group ->
            val existing = customGroupDao.getGroupByName(group.profileId, group.name)
            if (existing == null) {
                customGroupDao.insert(group.copy(id = 0))
            }
        }
        
        // Restore preferences
        backup.preferences?.let { prefs ->
            prefs.omdbApiKey?.let { userPreferences.setOmdbApiKey(it) }
            prefs.autoPlayNext?.let { userPreferences.setAutoPlayNext(it) }
            prefs.subtitleLanguage?.let { userPreferences.setSubtitleLanguage(it) }
        }
        
        Log.d(TAG, "Backup applied successfully")
    }
    
    /**
     * Get list of local backups
     */
    suspend fun getLocalBackups(): List<File> = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups")
        backupDir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    /**
     * Delete a local backup file
     */
    suspend fun deleteBackup(file: File): Boolean = withContext(Dispatchers.IO) {
        file.delete()
    }
}

// Data classes
data class BackupData(
    val version: Int,
    val createdAt: Long,
    val profiles: List<Profile>,
    val playlists: List<PlaylistBackup>,
    val favorites: List<Favorite>,
    val watchProgress: List<WatchProgress>,
    val customGroups: List<CustomGroup>,
    val preferences: PreferencesBackup?
)

data class PlaylistBackup(
    val name: String,
    val url: String,
    val type: String,
    val username: String?,
    val password: String?,
    val epgUrl: String?
)

data class PreferencesBackup(
    val omdbApiKey: String?,
    val autoPlayNext: Boolean?,
    val subtitleLanguage: String?
)

sealed class BackupResult {
    data class Success(
        val profileCount: Int,
        val playlistCount: Int,
        val favoriteCount: Int
    ) : BackupResult()
    
    data class Error(val message: String) : BackupResult()
}
