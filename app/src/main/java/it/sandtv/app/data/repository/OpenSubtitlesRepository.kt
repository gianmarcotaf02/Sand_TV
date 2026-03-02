package it.sandtv.app.data.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.data.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenSubtitles Repository
 * Handles authentication, search, and download of subtitles
 */
@Singleton
class OpenSubtitlesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OpenSubtitles"
        private const val PREFS_NAME = "opensubtitles_secure"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"  // Stored securely encrypted
        private const val KEY_USER_ID = "user_id"
        private const val KEY_VIP = "is_vip"
        private const val KEY_REMAINING = "remaining_downloads"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        
        private const val CACHE_DIR = "subtitles"
        private const val MAX_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
    }
    
    private val api: OpenSubtitlesService
    private val encryptedPrefs by lazy { createEncryptedPrefs() }
    
    // Rate limiting
    private var lastRequestTime = 0L
    private val minRequestInterval = 1000L // 1 second between requests
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Api-Key", OpenSubtitlesService.API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "SandTV v1.0")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        api = Retrofit.Builder()
            .baseUrl(OpenSubtitlesService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenSubtitlesService::class.java)
    }
    
    private fun createEncryptedPrefs() = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    // ========== Authentication ==========
    
    sealed class AuthResult {
        data class Success(val username: String, val remainingDownloads: Int) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
    
    suspend fun login(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()
            
            val response = api.login(LoginRequest(username, password))
            
            if (response.isSuccessful && response.body()?.token != null) {
                val body = response.body()!!
                val token = body.token!!
                
                // Save credentials including password for auto-refresh
                encryptedPrefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)  // Saved securely for auto-refresh
                    .putInt(KEY_USER_ID, body.user?.user_id ?: 0)
                    .putBoolean(KEY_VIP, body.user?.vip ?: false)
                    .putInt(KEY_REMAINING, body.user?.allowed_downloads ?: 0)
                    .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + 24 * 60 * 60 * 1000) // 24h
                    .apply()
                
                AuthResult.Success(
                    username = username,
                    remainingDownloads = body.user?.allowed_downloads ?: 0
                )
            } else {
                val error = response.errorBody()?.string() ?: "Credenziali errate"
                AuthResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            AuthResult.Error("Connessione fallita: ${e.message}")
        }
    }
    
    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext true
            api.logout("Bearer $token")
            clearCredentials()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            clearCredentials()
            true
        }
    }
    
    fun isAuthenticated(): Boolean {
        val token = encryptedPrefs.getString(KEY_TOKEN, null)
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        // Consider authenticated if we have credentials (can auto-refresh token)
        val hasCredentials = encryptedPrefs.getString(KEY_PASSWORD, null) != null
        return token != null && (System.currentTimeMillis() < expiry || hasCredentials)
    }
    
    /**
     * Check if token needs refresh and refresh it automatically
     * Call this before making API requests
     */
    private suspend fun ensureValidToken(): Boolean {
        val token = encryptedPrefs.getString(KEY_TOKEN, null)
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        // Token still valid
        if (token != null && System.currentTimeMillis() < expiry) {
            return true
        }
        
        // Try to refresh using saved credentials
        val username = encryptedPrefs.getString(KEY_USERNAME, null)
        val password = encryptedPrefs.getString(KEY_PASSWORD, null)
        
        if (username != null && password != null) {
            Log.d(TAG, "Token expired, auto-refreshing...")
            val result = loginInternal(username, password)
            return result is AuthResult.Success
        }
        
        return false
    }
    
    /**
     * Internal login without saving password (used for refresh)
     */
    private suspend fun loginInternal(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()
            
            val response = api.login(LoginRequest(username, password))
            
            if (response.isSuccessful && response.body()?.token != null) {
                val body = response.body()!!
                val token = body.token!!
                
                // Update token and expiry
                encryptedPrefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putInt(KEY_USER_ID, body.user?.user_id ?: 0)
                    .putBoolean(KEY_VIP, body.user?.vip ?: false)
                    .putInt(KEY_REMAINING, body.user?.allowed_downloads ?: 0)
                    .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                    .apply()
                
                Log.d(TAG, "Token refreshed successfully")
                AuthResult.Success(
                    username = username,
                    remainingDownloads = body.user?.allowed_downloads ?: 0
                )
            } else {
                AuthResult.Error("Auto-refresh failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            AuthResult.Error("Refresh failed: ${e.message}")
        }
    }
    
    fun getUsername(): String? = encryptedPrefs.getString(KEY_USERNAME, null)
    
    fun getRemainingDownloads(): Int = encryptedPrefs.getInt(KEY_REMAINING, 0)
    
    private fun getToken(): String? = encryptedPrefs.getString(KEY_TOKEN, null)
    
    private fun clearCredentials() {
        encryptedPrefs.edit().clear().apply()
    }
    
    // ========== Subtitle Search ==========
    
    sealed class SearchResult {
        data class Success(val subtitles: List<SubtitleInfo>) : SearchResult()
        data class Error(val message: String) : SearchResult()
    }
    
    data class SubtitleInfo(
        val id: String,
        val fileId: Int,
        val language: String,
        val languageCode: String,
        val rating: Double,
        val downloadCount: Int,
        val release: String?,
        val fileName: String?,
        val isHearingImpaired: Boolean,
        val isHD: Boolean,
        val uploaderName: String?
    )
    
    suspend fun searchSubtitles(
        query: String? = null,
        imdbId: String? = null,
        tmdbId: Int? = null,
        language: String = "it,en",
        type: String? = null,
        season: Int? = null,
        episode: Int? = null
    ): SearchResult = withContext(Dispatchers.IO) {
        try {
            // Auto-refresh token if needed
            if (!ensureValidToken()) {
                return@withContext SearchResult.Error("Non autenticato")
            }
            
            val token = getToken() ?: return@withContext SearchResult.Error("Non autenticato")
            
            enforceRateLimit()
            
            val response = api.searchSubtitles(
                token = "Bearer $token",
                query = query,
                imdbId = imdbId,
                tmdbId = tmdbId,
                languages = language,
                type = type,
                seasonNumber = season,
                episodeNumber = episode
            )
            
            if (response.isSuccessful) {
                val results = response.body()?.data?.mapNotNull { result ->
                    val attr = result.attributes
                    val file = attr.files.firstOrNull() ?: return@mapNotNull null
                    
                    SubtitleInfo(
                        id = result.id,
                        fileId = file.file_id,
                        language = getLanguageName(attr.language),
                        languageCode = attr.language,
                        rating = attr.ratings,
                        downloadCount = attr.download_count,
                        release = attr.release,
                        fileName = file.file_name,
                        isHearingImpaired = attr.hearing_impaired,
                        isHD = attr.hd,
                        uploaderName = attr.uploader?.name
                    )
                } ?: emptyList()
                
                SearchResult.Success(results)
            } else {
                val error = response.errorBody()?.string() ?: "Ricerca fallita"
                SearchResult.Error(error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            SearchResult.Error("Errore di connessione: ${e.message}")
        }
    }
    
    // ========== Subtitle Download ==========
    
    sealed class DownloadResult {
        data class Success(val filePath: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
        object LimitReached : DownloadResult()
    }
    
    suspend fun downloadSubtitle(
        fileId: Int,
        contentTitle: String
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // Auto-refresh token if needed
            if (!ensureValidToken()) {
                return@withContext DownloadResult.Error("Non autenticato")
            }
            
            val token = getToken() ?: return@withContext DownloadResult.Error("Non autenticato")
            
            enforceRateLimit()
            
            // Get download link
            val response = api.downloadSubtitle(
                token = "Bearer $token",
                request = DownloadRequest(file_id = fileId, sub_format = "srt")
            )
            
            if (response.isSuccessful) {
                val body = response.body()!!
                
                // Update remaining downloads
                body.remaining?.let {
                    encryptedPrefs.edit().putInt(KEY_REMAINING, it).apply()
                }
                
                // Check if limit reached
                if (body.remaining == 0 && body.link == null) {
                    return@withContext DownloadResult.LimitReached
                }
                
                val downloadUrl = body.link ?: return@withContext DownloadResult.Error("Link non disponibile")
                
                // Download file
                val fileName = sanitizeFileName(contentTitle) + "_${fileId}.srt"
                val cacheDir = File(context.cacheDir, CACHE_DIR)
                cacheDir.mkdirs()
                
                // Clean cache if too large
                cleanCacheIfNeeded(cacheDir)
                
                val outputFile = File(cacheDir, fileName)
                
                URL(downloadUrl).openStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        // Check if gzipped
                        if (downloadUrl.endsWith(".gz")) {
                            GZIPInputStream(input).copyTo(output)
                        } else {
                            input.copyTo(output)
                        }
                    }
                }
                
                DownloadResult.Success(outputFile.absolutePath)
            } else {
                val errorBody = response.errorBody()?.string()
                if (errorBody?.contains("download limit") == true || response.code() == 429) {
                    DownloadResult.LimitReached
                } else {
                    DownloadResult.Error(errorBody ?: "Download fallito")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            DownloadResult.Error("Errore download: ${e.message}")
        }
    }
    
    // ========== Cache Management ==========
    
    fun getCachedSubtitle(contentTitle: String): File? {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        val sanitized = sanitizeFileName(contentTitle)
        return cacheDir.listFiles()?.find { it.name.startsWith(sanitized) }
    }
    
    fun getCachedSubtitles(): List<File> {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        return cacheDir.listFiles()?.toList() ?: emptyList()
    }
    
    private fun cleanCacheIfNeeded(cacheDir: File) {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        
        if (totalSize > MAX_CACHE_SIZE) {
            // Delete oldest files first
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (totalSize > MAX_CACHE_SIZE * 0.7) {
                    totalSize -= file.length()
                    file.delete()
                }
            }
        }
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
    }
    
    // ========== Helpers ==========
    
    private suspend fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < minRequestInterval) {
            kotlinx.coroutines.delay(minRequestInterval - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()
    }
    
    private fun getLanguageName(code: String): String {
        return when (code.lowercase()) {
            "it" -> "Italiano"
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "pt" -> "Português"
            "ru" -> "Русский"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "zh" -> "中文"
            else -> code.uppercase()
        }
    }
}
