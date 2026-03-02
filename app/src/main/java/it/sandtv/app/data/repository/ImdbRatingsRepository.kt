package it.sandtv.app.data.repository

import android.util.Log
import it.sandtv.app.data.api.OmdbResult
import it.sandtv.app.data.api.OmdbService
import it.sandtv.app.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching IMDB ratings via OMDb API
 */
@Singleton
class ImdbRatingsRepository @Inject constructor(
    private val userPreferences: UserPreferences
) {
    // Create scraper instance internally to avoid Hilt injection issues
    private val rtScraper = RottenTomatoesScraper()
    companion object {
        private const val TAG = "ImdbRatings"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val DEFAULT_API_KEY = "85ba6cc1" // Default OMDB API key
    }
    
    private val api: OmdbService
    
    // In-memory cache
    private val ratingsCache = mutableMapOf<String, CachedRating>()
    
    data class CachedRating(
        val rating: RatingInfo,
        val timestamp: Long
    )
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        
        api = Retrofit.Builder()
            .baseUrl(OmdbService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OmdbService::class.java)
    }
    
    /**
     * Rating information from multiple sources
     */
    data class RatingInfo(
        val imdbRating: Float?,         // 8.4
        val imdbVotes: String?,         // "1,234,567"
        val imdbId: String?,
        val rottenTomatoesScore: Int?,  // 91 (percentage) - Critics score (Tomatometer)
        val audienceScore: Int?,        // 87 (percentage) - Audience score (Popcornmeter)
        val metacriticScore: Int?,      // 74
        val rated: String?,             // "PG-13", "R"
        val awards: String?,
        val boxOffice: String?
    ) {
        val hasRatings: Boolean
            get() = imdbRating != null || rottenTomatoesScore != null || metacriticScore != null
        
        fun getFormattedImdbRating(): String? {
            return imdbRating?.let { String.format("%.1f", it) }
        }
    }
    
    /**
     * Get ratings by IMDB ID
     */
    suspend fun getRatingsByImdbId(imdbId: String): RatingInfo? = withContext(Dispatchers.IO) {
        // Check cache
        ratingsCache[imdbId]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
                return@withContext cached.rating
            }
        }
        
        val apiKey = userPreferences.getOmdbApiKey() ?: DEFAULT_API_KEY
        if (apiKey.isEmpty()) {
            Log.w(TAG, "OMDb API key not configured")
            return@withContext null
        }
        
        try {
            val response = api.getByImdbId(apiKey, imdbId)
            
            if (response.isSuccessful && response.body()?.Response == "True") {
                val result = response.body()!!
                val rating = parseRatingInfo(result)
                
                // Cache result
                ratingsCache[imdbId] = CachedRating(rating, System.currentTimeMillis())
                
                return@withContext rating
            } else {
                Log.w(TAG, "OMDb error: ${response.body()?.Error}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching IMDB rating", e)
            return@withContext null
        }
    }
    
    /**
     * Get ratings by title (fallback if no IMDB ID)
     */
    suspend fun getRatingsByTitle(
        title: String,
        year: Int? = null,
        type: String? = null
    ): RatingInfo? = withContext(Dispatchers.IO) {
        val cacheKey = "$title:$year:$type"
        
        // Check cache
        ratingsCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS) {
                return@withContext cached.rating
            }
        }
        
        val apiKey = userPreferences.getOmdbApiKey() ?: DEFAULT_API_KEY
        if (apiKey.isEmpty()) {
            android.util.Log.d("ImdbRatings", "No API key available")
            return@withContext null
        }
        
        try {
            android.util.Log.d("ImdbRatings", "Calling OMDB for: title=$title, year=$year")
            val response = api.getByTitle(apiKey, title, year, type)
            
            android.util.Log.d("ImdbRatings", "OMDB response: success=${response.isSuccessful}, body.Response=${response.body()?.Response}, error=${response.body()?.Error}")
            
            if (response.isSuccessful && response.body()?.Response == "True") {
                val result = response.body()!!
                val rating = parseRatingInfo(result)
                android.util.Log.d("ImdbRatings", "Parsed: imdb=${rating.imdbRating}, rt=${rating.rottenTomatoesScore}, audience=${rating.audienceScore}, mc=${rating.metacriticScore}")
                
                // Cache by IMDB ID if available
                result.imdbID?.let { imdbId ->
                    ratingsCache[imdbId] = CachedRating(rating, System.currentTimeMillis())
                }
                ratingsCache[cacheKey] = CachedRating(rating, System.currentTimeMillis())
                
                return@withContext rating
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rating by title", e)
            return@withContext null
        }
    }
    
    /**
     * Smart search with multiple fallback strategies
     * This is the preferred method - it tries all possible ways to find ratings
     * 
     * @param imdbId IMDB ID from TMDB (most reliable if available)
     * @param originalTitle Original title from playlist (may be in any language)
     * @param englishTitle English title from TMDB (often works better with OMDB)
     * @param year Year of release
     * @param type "movie" or "series"
     */
    suspend fun getRatingsWithFallbacks(
        imdbId: String? = null,
        originalTitle: String,
        englishTitle: String? = null,
        year: Int? = null,
        type: String? = null
    ): RatingInfo? = withContext(Dispatchers.IO) {
        var ratings: RatingInfo?
        
        // Strategy 1: Try with IMDB ID (most reliable!)
        if (!imdbId.isNullOrEmpty()) {
            Log.d(TAG, "Strategy 1: IMDB ID = $imdbId")
            ratings = getRatingsByImdbId(imdbId)
            if (ratings?.hasRatings == true) {
                Log.d(TAG, "✓ Found via IMDB ID")
                return@withContext ratings
            }
        }
        
        // Clean up title
        val cleanedOriginal = cleanTitleForOmdb(originalTitle)
        
        // Strategy 2: Try with English title from TMDB (often works better)
        if (!englishTitle.isNullOrEmpty() && englishTitle.lowercase() != cleanedOriginal.lowercase()) {
            val cleanedEnglish = cleanTitleForOmdb(englishTitle)
            Log.d(TAG, "Strategy 2: English title = $cleanedEnglish, year = $year")
            ratings = getRatingsByTitle(cleanedEnglish, year, type)
            if (ratings?.hasRatings == true) {
                Log.d(TAG, "✓ Found via English title")
                return@withContext ratings
            }
            
            // Try English title without year
            if (year != null) {
                Log.d(TAG, "Strategy 2b: English title without year")
                ratings = getRatingsByTitle(cleanedEnglish, null, type)
                if (ratings?.hasRatings == true) {
                    Log.d(TAG, "✓ Found via English title (no year)")
                    return@withContext ratings
                }
            }
        }
        
        // Strategy 3: Try with original/cleaned title
        Log.d(TAG, "Strategy 3: Original title = $cleanedOriginal, year = $year")
        ratings = getRatingsByTitle(cleanedOriginal, year, type)
        if (ratings?.hasRatings == true) {
            Log.d(TAG, "✓ Found via original title")
            return@withContext ratings
        }
        
        // Strategy 4: Try original title without year
        if (year != null) {
            Log.d(TAG, "Strategy 4: Original title without year")
            ratings = getRatingsByTitle(cleanedOriginal, null, type)
            if (ratings?.hasRatings == true) {
                Log.d(TAG, "✓ Found via original title (no year)")
                return@withContext ratings
            }
        }
        
        // Strategy 5: Use OMDB Search API and take first result
        Log.d(TAG, "Strategy 5: Search API")
        ratings = searchAndGetRatings(cleanedOriginal, year, type)
            ?: englishTitle?.let { searchAndGetRatings(cleanTitleForOmdb(it), year, type) }
        if (ratings?.hasRatings == true) {
            Log.d(TAG, "✓ Found via Search API")
            return@withContext ratings
        }
        
        // Strategy 6: Try first part of title before common separators
        val titlePart = cleanedOriginal
            .split(" - ", " : ", " – ", " | ")
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.length > 2 && it != cleanedOriginal }
        
        if (titlePart != null) {
            Log.d(TAG, "Strategy 6: Title part = $titlePart")
            ratings = getRatingsByTitle(titlePart, year, type)
                ?: getRatingsByTitle(titlePart, null, type)
            if (ratings?.hasRatings == true) {
                Log.d(TAG, "✓ Found via title part")
                return@withContext ratings
            }
        }
        
        Log.d(TAG, "✗ No ratings found for: $originalTitle")
        return@withContext ratings
    }
    
    /**
     * Fetch Rotten Tomatoes scores (critics + audience) via web scraping
     * Call this separately to add missing scores to existing ratings
     */
    suspend fun fetchRtScores(
        title: String,
        year: Int? = null,
        isMovie: Boolean = true
    ): RottenTomatoesScraper.RtScores? = withContext(Dispatchers.IO) {
        return@withContext if (isMovie) {
            rtScraper.getScoresForMovie(title = title, year = year)
        } else {
            rtScraper.getScoresForSeries(title = title, year = year)
        }
    }
    
    /**
     * Search using OMDB search endpoint, then get full details of first result
     */
    private suspend fun searchAndGetRatings(
        query: String,
        year: Int?,
        type: String?
    ): RatingInfo? {
        val apiKey = userPreferences.getOmdbApiKey() ?: DEFAULT_API_KEY
        if (apiKey.isEmpty()) return null
        
        try {
            val searchResponse = api.search(apiKey, query, year, type)
            if (searchResponse.isSuccessful && searchResponse.body()?.Response == "True") {
                val firstResult = searchResponse.body()?.Search?.firstOrNull()
                if (firstResult?.imdbID != null) {
                    Log.d(TAG, "Search found: ${firstResult.Title} (${firstResult.Year}) - ${firstResult.imdbID}")
                    return getRatingsByImdbId(firstResult.imdbID)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }
        return null
    }
    
    /**
     * Clean title for OMDB search - removes quality tags, years in brackets, etc.
     */
    private fun cleanTitleForOmdb(title: String): String {
        var cleaned = title
        
        // Remove year in parentheses/brackets
        cleaned = cleaned.replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
        cleaned = cleaned.replace(Regex("""\s*\[\d{4}\]\s*"""), " ")
        
        // Remove quality tags
        val qualityTags = listOf(
            "4K", "UHD", "2160p", "FHD", "1080p", "1080i",
            "HD", "720p", "SD", "480p",
            "HDR", "HDR10", "HDR10+", "Dolby Vision", "DV",
            "HEVC", "H265", "H264", "x264", "x265",
            "WEB-DL", "WEBDL", "WEBRip", "WEBRIP",
            "BluRay", "BLU-RAY", "BLURAY", "BDRip", "BRRip",
            "DVDRip", "DVDR", "CAM", "TS", "HDTS",
            "ITA", "ENG", "MULTI", "SUB", "SUBBED", "AC3", "DTS",
            "EXTENDED", "UNRATED", "DIRECTORS CUT", "REMASTERED"
        )
        
        for (tag in qualityTags) {
            cleaned = cleaned.replace(Regex("""[\[\(]?\s*$tag\s*[\]\)]?""", RegexOption.IGNORE_CASE), " ")
        }
        
        // Remove trailing separators and extra spaces
        cleaned = cleaned.replace(Regex("""\s*[-|:]+\s*$"""), "")
        cleaned = cleaned.trim().replace(Regex("""\s{2,}"""), " ")
        
        return cleaned
    }

    
    /**
     * Parse OMDb result into RatingInfo
     */
    private fun parseRatingInfo(result: OmdbResult): RatingInfo {
        // Parse IMDB rating
        val imdbRating = result.imdbRating?.toFloatOrNull()
        
        // Parse Rotten Tomatoes Critics score (Tomatometer)
        // First try tomatoMeter field (from tomatoes=true), fallback to Ratings array
        val rtScore = result.tomatoMeter?.takeIf { it != "N/A" }?.toIntOrNull()
            ?: result.Ratings?.find { 
                it.Source == "Rotten Tomatoes" 
            }?.Value?.replace("%", "")?.toIntOrNull()
        
        // Parse Audience Score (Popcornmeter) from tomatoUserMeter
        val audienceScore = result.tomatoUserMeter?.takeIf { it != "N/A" }?.toIntOrNull()
        
        // Parse Metacritic
        val metacritic = result.Metascore?.toIntOrNull()
            ?: result.Ratings?.find { 
                it.Source == "Metacritic" 
            }?.Value?.replace("/100", "")?.toIntOrNull()
        
        return RatingInfo(
            imdbRating = imdbRating,
            imdbVotes = result.imdbVotes,
            imdbId = result.imdbID,
            rottenTomatoesScore = rtScore,
            audienceScore = audienceScore,
            metacriticScore = metacritic,
            rated = result.Rated,
            awards = result.Awards,
            boxOffice = result.BoxOffice
        )
    }
    
    /**
     * Clear ratings cache
     */
    fun clearCache() {
        ratingsCache.clear()
    }
}
