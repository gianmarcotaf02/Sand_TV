package it.sandtv.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service for scraping Rotten Tomatoes Popcornmeter (audience score)
 * since OMDB doesn't provide it.
 * 
 * Note: This is for personal use only. Web scraping may violate RT's ToS.
 */
class RottenTomatoesScraper {
    
    companion object {
        private const val TAG = "RTScraper"
        private const val BASE_URL = "https://www.rottentomatoes.com"
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    // Data class to hold both scores
    data class RtScores(
        val criticsScore: Int?,
        val audienceScore: Int?
    )

    // In-memory cache
    private val scoresCache = mutableMapOf<String, CachedScore>()
    
    data class CachedScore(
        val scores: RtScores?,
        val timestamp: Long
    )
    
    /**
     * Get Rotten Tomatoes scores for a movie
     * @param imdbId IMDB ID to search for
     * @param title Movie title (fallback for URL construction)
     * @param year Release year
     * @return RtScores object or null if not found
     */
    suspend fun getScoresForMovie(
        imdbId: String? = null,
        title: String,
        year: Int? = null
    ): RtScores? = withContext(Dispatchers.IO) {
        val cacheKey = imdbId ?: "$title:$year"
        
        // Check cache
        scoresCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
                Log.d(TAG, "Cache hit for movie: $title -> Critics: ${cached.scores?.criticsScore}%, Audience: ${cached.scores?.audienceScore}%")
                return@withContext cached.scores
            }
        }
        
        try {
            val slug = createSlug(title)
            val url = "$BASE_URL/m/$slug"
            Log.d(TAG, "Fetching movie: $url")
            
            val scores = fetchScores(url)
            
            // Cache result
            scoresCache[cacheKey] = CachedScore(scores, System.currentTimeMillis())
            
            Log.d(TAG, "Scores for '$title': Critics: ${scores?.criticsScore}%, Audience: ${scores?.audienceScore}%")
            scores
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scores for movie: $title", e)
            null
        }
    }
    
    /**
     * Get Rotten Tomatoes scores for a TV series
     * @param imdbId IMDB ID to search for
     * @param title Series title
     * @param year Release year
     * @return RtScores object or null if not found
     */
    suspend fun getScoresForSeries(
        imdbId: String? = null,
        title: String,
        year: Int? = null
    ): RtScores? = withContext(Dispatchers.IO) {
        val cacheKey = imdbId ?: "$title:$year:tv"
        
        // Check cache
        scoresCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
                Log.d(TAG, "Cache hit for series: $title -> Critics: ${cached.scores?.criticsScore}%, Audience: ${cached.scores?.audienceScore}%")
                return@withContext cached.scores
            }
        }
        
        try {
            val slug = createSlug(title)
            val url = "$BASE_URL/tv/$slug"
            Log.d(TAG, "Fetching series: $url")
            
            val scores = fetchScores(url)
            
            // Cache result
            scoresCache[cacheKey] = CachedScore(scores, System.currentTimeMillis())
            
            Log.d(TAG, "Scores for series '$title': Critics: ${scores?.criticsScore}%, Audience: ${scores?.audienceScore}%")
            scores
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching scores for series: $title", e)
            null
        }
    }
    
    /**
     * Fetch and parse Scores from RT page HTML
     */
    private fun fetchScores(url: String): RtScores? {
        Log.d(TAG, "=== FETCHING SCORES ===")
        Log.d(TAG, "URL: $url")
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching RT: ${e.message}")
            return null
        }
        
        Log.d(TAG, "HTTP response code: ${response.code}")
        
        if (!response.isSuccessful) {
            Log.w(TAG, "HTTP ${response.code} for $url")
            return null
        }
        
        val html = response.body?.string()
        if (html == null) {
            Log.e(TAG, "HTML body is null!")
            return null
        }
        
        // Parse Audience Score (Popcornmeter)
        val audienceScore = extractScore(html, "audienceScore")
        
        // Parse Critics Score (Tomatometer)
        val criticsScore = extractScore(html, "criticsScore")
        
        if (audienceScore == null && criticsScore == null) {
            Log.w(TAG, "✗ Could not find any scores in HTML for $url")
            return null
        }

        return RtScores(criticsScore, audienceScore)
    }

    private fun extractScore(html: String, slotName: String): Int? {
        // RT uses hyphenated slot names like "audience-score" not "audienceScore"
        val hyphenatedName = when (slotName) {
            "audienceScore" -> "audience-score"
            "criticsScore" -> "critics-score"
            else -> slotName
        }
        
        // Pattern 1: slot="audience-score"...>83%< (with hyphen)
        val slotHyphenPattern = """slot="$hyphenatedName"[\s\S]*?>(\d+)%?<""".toRegex()
        val slotHyphenMatch = slotHyphenPattern.find(html)
        slotHyphenMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
            Log.d(TAG, "✓ Found $slotName via slot pattern (hyphenated): $it%")
            return it
        }
        
        // Pattern 2: slot="audienceScore"...>90%< (camelCase, legacy)
        val slotCamelPattern = """slot="$slotName"[\s\S]*?>(\d+)%?<""".toRegex()
        val slotCamelMatch = slotCamelPattern.find(html)
        slotCamelMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
            Log.d(TAG, "✓ Found $slotName via slot pattern (camelCase): $it%")
            return it
        }
        
        // Pattern 3: JSON data
        // "audienceScore": { "score": 90 }
        val jsonPattern = """"$slotName":\s*\{\s*"score":\s*(\d+)""".toRegex()
        val jsonMatch = jsonPattern.find(html)
        jsonMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
            Log.d(TAG, "✓ Found $slotName via JSON pattern: $it%")
            return it
        }
        
        // Pattern 4: TV series specific - look for data-audiencescore attribute
        if (slotName == "audienceScore") {
            // Try: data-audiencescore="83"
            val dataAttrPattern = """data-audiencescore="(\d+)"""".toRegex(RegexOption.IGNORE_CASE)
            val dataAttrMatch = dataAttrPattern.find(html)
            dataAttrMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                Log.d(TAG, "✓ Found $slotName via data-audiencescore attribute: $it%")
                return it
            }
            
            // Try: rt-text with audience-score slot directly
            val rtTextPattern = """<rt-text[^>]*slot="audience-score"[^>]*>(\d{1,3})%?</rt-text>""".toRegex(RegexOption.IGNORE_CASE)
            val rtTextMatch = rtTextPattern.find(html)
            rtTextMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                Log.d(TAG, "✓ Found $slotName via rt-text element: $it%")
                return it
            }
            
            // Try: audience-score percentage display
            val audiencePercentPattern = """(?:audience|popcornmeter|avgpopcornmeter)[\s\S]{0,100}?>(\d{1,3})%<""".toRegex(RegexOption.IGNORE_CASE)
            val audiencePercentMatch = audiencePercentPattern.find(html)
            audiencePercentMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 0..100) {
                    Log.d(TAG, "✓ Found $slotName via audience percentage pattern: $it%")
                    return it
                }
            }
            
            // Fallback: Generic pattern
            val fallbackPattern = """audienceScore[\s\S]{0,200}?>(\d{1,3})%?<""".toRegex(RegexOption.IGNORE_CASE)
            val fallbackMatch = fallbackPattern.find(html)
            fallbackMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                if (it in 0..100) {
                    Log.d(TAG, "✓ Found $slotName via fallback pattern: $it%")
                    return it
                }
            }
        }

        Log.d(TAG, "✗ Could not find $slotName with any pattern")
        return null
    }

    /**
     * Create RT URL slug from title
     * Example: "One Battle After Another" -> "one_battle_after_another"
     */
    private fun createSlug(title: String): String {
        return title
            .lowercase()
            // Remove common articles at the start
            .replace(Regex("^(the|a|an)\\s+"), "")
            // Remove year in parentheses
            .replace(Regex("\\s*\\(\\d{4}\\)"), "")
            // Remove special characters except alphanumeric and spaces
            .replace(Regex("[^a-z0-9\\s]"), "")
            // Replace spaces with underscores
            .replace(Regex("\\s+"), "_")
            // Remove leading/trailing underscores
            .trim('_')
    }
    
    /**
     * Clear the cache
     */
    fun clearCache() {
        scoresCache.clear()
    }
}
