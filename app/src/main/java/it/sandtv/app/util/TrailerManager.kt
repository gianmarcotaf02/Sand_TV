package it.sandtv.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import it.sandtv.app.data.preferences.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Player preference options
 */
enum class YouTubePlayerOption(val packageName: String?, val displayName: String) {
    ALWAYS_ASK(null, "Chiedi ogni volta"),
    SMARTTUBE("org.smarttube.beta", "SmartTube"),
    YOUTUBE_TV("com.google.android.youtube.tv", "YouTube TV"),
    YOUTUBE_STANDARD("com.google.android.youtube", "YouTube")
}

/**
 * Centralized manager for opening YouTube trailers with intelligent fallback
 * 
 * Priority logic:
 * 1. Try user's preferred app first
 * 2. If not installed, try fallback apps in order
 * 3. If all fail, open chooser or show error
 */
@Singleton
class TrailerManager @Inject constructor(
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "TrailerManager"
        
        // Package names for YouTube apps
        const val PACKAGE_SMARTTUBE = "org.smarttube.beta"
        const val PACKAGE_YOUTUBE_TV = "com.google.android.youtube.tv"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
    }
    
    /**
     * Open a YouTube video/trailer
     * @param context Activity context
     * @param videoId YouTube video ID (e.g., "dQw4w9WgXcQ")
     */
    suspend fun openTrailer(context: Context, videoId: String) {
        val preferredPackage = userPreferences.getYoutubePlayerPackage()
        
        Log.d(TAG, "Opening trailer: $videoId, preferred package: $preferredPackage")
        
        when {
            // No preference set or "always ask" - show chooser
            preferredPackage.isNullOrEmpty() -> {
                openWithChooser(context, videoId)
            }
            // SmartTube preferred
            preferredPackage == PACKAGE_SMARTTUBE -> {
                openWithFallback(context, videoId, 
                    primary = PACKAGE_SMARTTUBE,
                    fallbacks = listOf(PACKAGE_YOUTUBE_TV, PACKAGE_YOUTUBE)
                )
            }
            // YouTube TV preferred
            preferredPackage == PACKAGE_YOUTUBE_TV -> {
                openWithFallback(context, videoId,
                    primary = PACKAGE_YOUTUBE_TV,
                    fallbacks = listOf(PACKAGE_SMARTTUBE, PACKAGE_YOUTUBE)
                )
            }
            // Standard YouTube preferred  
            preferredPackage == PACKAGE_YOUTUBE -> {
                openWithFallback(context, videoId,
                    primary = PACKAGE_YOUTUBE,
                    fallbacks = listOf(PACKAGE_YOUTUBE_TV, PACKAGE_SMARTTUBE)
                )
            }
            // Unknown package - try directly then fallback
            else -> {
                openWithFallback(context, videoId,
                    primary = preferredPackage,
                    fallbacks = listOf(PACKAGE_SMARTTUBE, PACKAGE_YOUTUBE_TV, PACKAGE_YOUTUBE)
                )
            }
        }
    }
    
    /**
     * Try opening with preferred app, falling back to alternatives if needed
     */
    private fun openWithFallback(
        context: Context,
        videoId: String,
        primary: String,
        fallbacks: List<String>
    ) {
        // Try primary app first
        if (tryOpenWith(context, videoId, primary)) {
            Log.d(TAG, "Opened trailer with primary: $primary")
            return
        }
        
        // Try fallbacks in order
        for (fallback in fallbacks) {
            if (tryOpenWith(context, videoId, fallback)) {
                Log.d(TAG, "Opened trailer with fallback: $fallback")
                return
            }
        }
        
        // All specific apps failed - try chooser as last resort
        Log.w(TAG, "All YouTube apps failed, trying chooser")
        openWithChooser(context, videoId)
    }
    
    /**
     * Try to open video with a specific package
     * @return true if successful, false otherwise
     */
    private fun tryOpenWith(context: Context, videoId: String, packageName: String): Boolean {
        // Check if app is installed first
        if (!isPackageInstalled(context, packageName)) {
            Log.d(TAG, "Package not installed: $packageName")
            return false
        }
        
        try {
            // Use the web URL format - works best with all YouTube apps including SmartTube
            // SmartTube and YouTube TV have issues with vnd.youtube: scheme when package is set
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Add category for better compatibility
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            
            // Check if this intent can be resolved
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo == null) {
                Log.d(TAG, "No activity found for $packageName with watch URL, trying youtu.be")
                
                // Try short URL format
                val shortIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId")).apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                context.startActivity(shortIntent)
                return true
            }
            
            context.startActivity(intent)
            return true
            
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Failed to open with $packageName: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error opening with $packageName", e)
            return false
        }
    }
    
    /**
     * Open with system chooser
     */
    private fun openWithChooser(context: Context, videoId: String) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(webIntent, "Guarda trailer").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open chooser", e)
            Toast.makeText(context, "Impossibile riprodurre trailer", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Check if a package is installed on the device
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get list of available YouTube apps on the device
     */
    fun getAvailableYouTubeApps(context: Context): List<YouTubePlayerOption> {
        val available = mutableListOf<YouTubePlayerOption>()
        
        // Always add "Ask every time" option
        available.add(YouTubePlayerOption.ALWAYS_ASK)
        
        // Check each app
        YouTubePlayerOption.entries.filter { it.packageName != null }.forEach { option ->
            if (isPackageInstalled(context, option.packageName!!)) {
                available.add(option)
            }
        }
        
        return available
    }
}
