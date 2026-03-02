package it.sandtv.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Manager for checking and downloading app updates
 * Uses Firebase Realtime Database for version info and downloads APK from GitHub Releases
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATE_NODE = "app_update"
        private const val APK_FILENAME = "sandtv_update.apk"
    }
    
    private val database = FirebaseDatabase.getInstance()
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState
    
    private var downloadedApkFile: File? = null
    private var currentDownloadUrl: String? = null
    
    /**
     * Get current installed version code
     */
    fun getInstalledVersionCode(): Int = BuildConfig.VERSION_CODE
    
    /**
     * Get current installed version name
     */
    fun getInstalledVersionName(): String = BuildConfig.VERSION_NAME
    
    /**
     * Check for available updates from Firebase Realtime Database
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates...")
            
            val updateInfo = suspendCancellableCoroutine<Pair<UpdateInfo?, String?>?> { continuation ->
                database.reference.child(UPDATE_NODE).get()
                    .addOnSuccessListener { snapshot ->
                        try {
                            if (snapshot.exists()) {
                                // Safely parse with type checks/conversions
                                val versionCodeVal = snapshot.child("version_code").value
                                val versionCode = when (versionCodeVal) {
                                    is Number -> versionCodeVal.toInt()
                                    is String -> versionCodeVal.toIntOrNull() ?: 0
                                    else -> 0
                                }
                                
                                val versionNameVal = snapshot.child("version_name").value
                                val versionName = versionNameVal?.toString() ?: ""
                                
                                val changelogVal = snapshot.child("changelog").value
                                val changelog = changelogVal?.toString() ?: ""
                                
                                val forceUpdate = snapshot.child("force_update").getValue(Boolean::class.java) ?: false
                                
                                val info = UpdateInfo(
                                    versionCode = versionCode,
                                    versionName = versionName,
                                    changelog = changelog,
                                    forceUpdate = forceUpdate
                                )
                                val downloadUrl = snapshot.child("download_url").getValue(String::class.java)
                                if (continuation.isActive) {
                                    continuation.resume(Pair(info, downloadUrl))
                                }
                            } else {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing update info: ${e.message}")
                            if (continuation.isActive) {
                                continuation.resume(Pair(null, "Errore dati: ${e.message}"))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to check for updates: ${e.message}")
                        if (continuation.isActive) {
                            continuation.resume(Pair(null, e.message))
                        }
                    }
            }
            
            if (updateInfo == null) {
                return@withContext UpdateCheckResult.Error("Errore generico verifica aggiornamenti")
            }
            
            val (info, data) = updateInfo
            
            // If info is null, data might contain error message string (hacky reuse of Pair)
            if (info == null) {
                 val errorMessage = data ?: "Dati aggiornamento non disponibili"
                 return@withContext UpdateCheckResult.Error(errorMessage)
            }
            
            val downloadUrl = data
            currentDownloadUrl = downloadUrl
            
            val installedVersion = getInstalledVersionCode()
            Log.d(TAG, "Current: $installedVersion, Available: ${info.versionCode}")
            
            if (info.isNewerThan(installedVersion)) {
                if (downloadUrl.isNullOrEmpty()) {
                    UpdateCheckResult.Error("URL download non configurato")
                } else {
                    UpdateCheckResult.UpdateAvailable(info)
                }
            } else {
                UpdateCheckResult.NoUpdateAvailable
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateCheckResult.Error(e.message ?: "Errore sconosciuto")
        }
    }
    
    /**
     * Download the APK from GitHub Releases (or any URL)
     */
    suspend fun downloadUpdate(): Boolean = withContext(Dispatchers.IO) {
        val downloadUrl = currentDownloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            _downloadState.value = DownloadState.Failed("URL download non disponibile")
            return@withContext false
        }
        
        try {
            _downloadState.value = DownloadState.Downloading(0)
            Log.d(TAG, "Starting APK download from: $downloadUrl")
            
            // Create updates directory
            val updatesDir = File(context.getExternalFilesDir(null), "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            
            // Delete old APK if exists
            val apkFile = File(updatesDir, APK_FILENAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            // Download using OkHttp
            val request = Request.Builder()
                .url(downloadUrl)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                _downloadState.value = DownloadState.Failed("Errore HTTP: ${response.code}")
                return@withContext false
            }
            
            val body = response.body ?: run {
                _downloadState.value = DownloadState.Failed("Risposta vuota dal server")
                return@withContext false
            }
            
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            FileOutputStream(apkFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            _downloadState.value = DownloadState.Downloading(progress)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Download completed: ${apkFile.absolutePath}")
            downloadedApkFile = apkFile
            _downloadState.value = DownloadState.Downloaded
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            _downloadState.value = DownloadState.Failed(e.message ?: "Errore download")
            false
        }
    }
    
    /**
     * Install the downloaded APK
     * This will show the standard Android package installer
     */
    fun installUpdate() {
        val apkFile = downloadedApkFile ?: return
        
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found")
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Installation intent started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installation", e)
        }
    }
    
    /**
     * Reset download state
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
    
    /**
     * Check if there's a downloaded APK ready to install
     */
    fun hasDownloadedUpdate(): Boolean {
        return downloadedApkFile?.exists() == true
    }
}
