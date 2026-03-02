package it.sandtv.app.data.tmdb

import android.util.Log
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.data.database.entity.Series
import it.sandtv.app.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching TMDB data and matching with local playlist content
 */
@Singleton
class TMDBService @Inject constructor(
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "TMDBService"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val API_KEY = "5f275659e4e6975d78d510255857dbf8"
        private const val LANGUAGE = "it-IT"
        private const val CACHE_DURATION_HOURS = 168L  // 7 days = 168 hours
    }
    
    // TMDB Genre IDs
    object MovieGenres {
        const val ACTION = 28
        const val ADVENTURE = 12
        const val ANIMATION = 16
        const val COMEDY = 35
        const val CRIME = 80
        const val DOCUMENTARY = 99
        const val DRAMA = 18
        const val FAMILY = 10751
        const val FANTASY = 14
        const val HISTORY = 36
        const val HORROR = 27
        const val MUSIC = 10402
        const val MYSTERY = 9648
        const val ROMANCE = 10749
        const val SCIENCE_FICTION = 878
        const val THRILLER = 53
        const val WAR = 10752
        const val WESTERN = 37
    }
    
    object TVGenres {
        const val ACTION_ADVENTURE = 10759
        const val ANIMATION = 16
        const val COMEDY = 35
        const val CRIME = 80
        const val DOCUMENTARY = 99
        const val DRAMA = 18
        const val FAMILY = 10751
        const val KIDS = 10762
        const val MYSTERY = 9648
        const val NEWS = 10763
        const val REALITY = 10764
        const val SCIFI_FANTASY = 10765
        const val SOAP = 10766
        const val TALK = 10767
        const val WAR_POLITICS = 10768
        const val WESTERN = 37
    }
    
    data class TMDBItem(
        val id: Int,
        val title: String,
        val originalTitle: String,
        val posterPath: String?,
        val backdropPath: String?,
        val overview: String?,
        val voteAverage: Float,
        val releaseDate: String?,
        val genreIds: List<Int>
    )
    
    data class CarouselRow(
        val title: String,
        val items: List<MatchedContent>
    )
    
    data class MatchedContent(
        val tmdbItem: TMDBItem,
        val localContent: Any, // Movie or Series
        val sources: List<ContentSource>
    )
    
    data class ContentSource(
        val id: Long,
        val streamUrl: String,
        val category: String,
        val quality: String?
    )
    
    /**
     * Get trending movies that exist in local playlist
     */
    suspend fun getTrendingMovies(limit: Int = 20): List<MatchedContent> = withContext(Dispatchers.IO) {
        try {
            val tmdbItems = fetchFromTMDB("$BASE_URL/trending/movie/week?api_key=$API_KEY&language=$LANGUAGE")
            matchMoviesWithLocal(tmdbItems, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending movies", e)
            emptyList()
        }
    }
    
    /**
     * Get popular movies that exist in local playlist
     */
    suspend fun getPopularMovies(limit: Int = 20): List<MatchedContent> = withContext(Dispatchers.IO) {
        try {
            val tmdbItems = fetchFromTMDB("$BASE_URL/movie/popular?api_key=$API_KEY&language=$LANGUAGE")
            matchMoviesWithLocal(tmdbItems, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular movies", e)
            emptyList()
        }
    }
    
    /**
     * Get movies by genre that exist in local playlist
     */
    suspend fun getMoviesByGenre(genreId: Int, genreName: String, limit: Int = 20): List<MatchedContent> = withContext(Dispatchers.IO) {
        try {
            val tmdbItems = fetchFromTMDB("$BASE_URL/discover/movie?api_key=$API_KEY&language=$LANGUAGE&with_genres=$genreId&sort_by=popularity.desc")
            matchMoviesWithLocal(tmdbItems, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movies by genre $genreName", e)
            emptyList()
        }
    }
    
    /**
     * Get trending TV shows that exist in local playlist
     */
    suspend fun getTrendingSeries(limit: Int = 20): List<MatchedContent> = withContext(Dispatchers.IO) {
        try {
            val tmdbItems = fetchFromTMDB("$BASE_URL/trending/tv/week?api_key=$API_KEY&language=$LANGUAGE", isTV = true)
            matchSeriesWithLocal(tmdbItems, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending series", e)
            emptyList()
        }
    }
    
    /**
     * Get popular TV shows that exist in local playlist
     */
    suspend fun getPopularSeries(limit: Int = 20): List<MatchedContent> = withContext(Dispatchers.IO) {
        try {
            val tmdbItems = fetchFromTMDB("$BASE_URL/tv/popular?api_key=$API_KEY&language=$LANGUAGE", isTV = true)
            matchSeriesWithLocal(tmdbItems, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching popular series", e)
            emptyList()
        }
    }
    
    /**
     * Refresh popular movies - updates popularity scores
     * Returns list of matched movie IDs in trending order for persistent cache
     * Uses individual lookups to avoid OOM with large playlists
     */
    suspend fun refreshPopularMovies(skipEnriched: Boolean = true): List<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing popular movies (skipEnriched=$skipEnriched)")
            val tmdbItems = fetchFromTMDB("$BASE_URL/trending/movie/week?api_key=$API_KEY&language=$LANGUAGE")
            val matchedIds = mutableListOf<Long>()
            
            for ((index, tmdb) in tmdbItems.withIndex()) {
                if (matchedIds.size >= 50) break
                
                // 1. Try to find by TMDB ID first
                var movie = movieDao.getMovieByTmdbId(tmdb.id)
                
                // Determine year from TMDB release date (hoisted for use in fallback search)
                val tmdbYear = extractYear(tmdb.releaseDate)
                
                // 2. If not found, search by title with year verification
                if (movie == null) {
                    // Search for movies with this title
                    val searchResults = movieDao.searchMovies(tmdb.title)
                    
                    movie = searchResults.firstOrNull { m ->
                        isMatch(m.name, tmdb.title, m.year, tmdbYear) || 
                        isMatch(m.name, tmdb.originalTitle, m.year, tmdbYear)
                    }
                }
                
                // 3. Try original title if still not found
                if (movie == null && tmdb.originalTitle.isNotEmpty() && tmdb.originalTitle != tmdb.title) {
                    val searchResults = movieDao.searchMovies(tmdb.originalTitle)
                    movie = searchResults.firstOrNull { m ->
                        isMatch(m.name, tmdb.originalTitle, m.year, tmdbYear)
                    }
                }
                
                if (movie != null) {
                    val popularityScore = (1000 - index).toFloat()
                    
                    // Check if already enriched (has OMDB data or trailer)
                    val isEnriched = movie.omdbLastFetchAt != null || movie.tmdbTrailerKey != null
                    
                    if (skipEnriched && isEnriched) {
                        // Only update popularity score
                        movieDao.updatePopularityScore(movie.id, popularityScore)
                    } else {
                        // Update popularity + basic TMDB data
                        val updated = movie.copy(
                            tmdbPopularity = popularityScore,
                            tmdbId = movie.tmdbId ?: tmdb.id,
                            tmdbPosterPath = movie.tmdbPosterPath ?: tmdb.posterPath,
                            tmdbBackdropPath = movie.tmdbBackdropPath ?: tmdb.backdropPath
                        )
                        movieDao.update(updated)
                    }
                    matchedIds.add(movie.id)
                }
            }
            
            Log.d(TAG, "Refreshed ${matchedIds.size} popular movies")
            matchedIds
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing popular movies", e)
            emptyList()
        }
    }
    
    /**
     * Refresh popular series - updates popularity scores
     * Returns list of matched series IDs in trending order for persistent cache
     * Uses individual lookups to avoid OOM with large playlists
     */
    suspend fun refreshPopularSeries(skipEnriched: Boolean = true): List<Long> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Refreshing popular series (skipEnriched=$skipEnriched)")
            val tmdbItems = fetchFromTMDB("$BASE_URL/trending/tv/week?api_key=$API_KEY&language=$LANGUAGE", isTV = true)
            val matchedIds = mutableListOf<Long>()
            
            for ((index, tmdb) in tmdbItems.withIndex()) {
                if (matchedIds.size >= 50) break
                
                // 1. Try to find by TMDB ID first
                var series = seriesDao.getSeriesByTmdbId(tmdb.id)
                
                // Determine year from TMDB release date (hoisted for use in fallback search)
                val tmdbYear = extractYear(tmdb.releaseDate)
                
                // 2. If not found, search by title with year verification
                if (series == null) {
                    // Search for series with this title
                    val searchResults = seriesDao.searchSeries(tmdb.title)
                    
                    series = searchResults.firstOrNull { s ->
                        val localYear = extractYearFromTitle(s.name)
                        isMatch(s.name, tmdb.title, localYear, tmdbYear) ||
                        isMatch(s.name, tmdb.originalTitle, localYear, tmdbYear)
                    }
                }
                
                // 3. Try original title if still not found
                if (series == null && tmdb.originalTitle.isNotEmpty() && tmdb.originalTitle != tmdb.title) {
                    val searchResults = seriesDao.searchSeries(tmdb.originalTitle)
                    series = searchResults.firstOrNull { s ->
                         val localYear = extractYearFromTitle(s.name)
                         isMatch(s.name, tmdb.originalTitle, localYear, tmdbYear)
                    }
                }
                
                if (series != null) {
                    val popularityScore = (1000 - index).toFloat()
                    
                    // Check if already enriched (has OMDB data or trailer)
                    val isEnriched = series.omdbLastFetchAt != null || series.tmdbTrailerKey != null
                    
                    if (skipEnriched && isEnriched) {
                        // Only update popularity score
                        seriesDao.updatePopularityScore(series.id, popularityScore)
                    } else {
                        // Update popularity + basic TMDB data
                        val updated = series.copy(
                            tmdbPopularity = popularityScore,
                            tmdbId = series.tmdbId ?: tmdb.id,
                            tmdbPosterPath = series.tmdbPosterPath ?: tmdb.posterPath,
                            tmdbBackdropPath = series.tmdbBackdropPath ?: tmdb.backdropPath
                        )
                        seriesDao.update(updated)
                    }
                    matchedIds.add(series.id)
                }
            }
            
            Log.d(TAG, "Refreshed ${matchedIds.size} popular series")
            matchedIds
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing popular series", e)
            emptyList()
        }
    }
    
    /**
     * Populate "Film Popolari" category with trending movies from TMDB
     * Matches by TMDB ID, Italian title, or English title with year verification
     * Returns count of matched movies
     */
    suspend fun populateTrendingMovies(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Populating trending movies category (fetching 3 pages)...")
            val allTmdbItems = mutableListOf<TMDBItem>()
            
            // Fetch first 3 pages (60 items)
            for (page in 1..3) {
                val items = fetchFromTMDB("$BASE_URL/trending/movie/week?api_key=$API_KEY&language=$LANGUAGE&page=$page")
                allTmdbItems.addAll(items)
            }
            
            var matchCount = 0
            
            for ((index, tmdb) in allTmdbItems.withIndex()) {
                if (matchCount >= 100) break  // Limit to 100 trending movies
                
                // 1. Try to find by TMDB ID first
                var movie = movieDao.getMovieByTmdbId(tmdb.id)
                
                // Determine year from TMDB release date
                val tmdbYear = extractYear(tmdb.releaseDate)
                
                // Verify year match if found by ID (to fix poisoned cache)
                if (movie != null) {
                    val localYear = movie.year ?: extractYearFromTitle(movie.name)
                    if (!isSameYear(localYear, tmdbYear)) {
                        Log.w(TAG, "MISMATCH on ID lookup: '${movie.name}' ($localYear) vs '${tmdb.title}' ($tmdbYear). Unlinking...")
                        // Unlink TMDB ID as it's incorrect
                        movieDao.update(movie.copy(tmdbId = null, tmdbPopularity = null))
                        movie = null
                    }
                }
                
                // 2. If not found, search by Italian title
                if (movie == null) {
                    movie = searchMovieByTitles(
                        italianTitle = tmdb.title,
                        englishTitle = tmdb.originalTitle,
                        year = tmdbYear
                    )
                }
                
                // 3. If found, update to assign trending category
                if (movie != null) {
                    // Filter out unwanted content (German, Adult, etc)
                    if (shouldExcludeContent(movie.name)) {
                        Log.d(TAG, "Skipping excluded movie: ${movie.name}")
                        continue
                    }
                    
                    val popularityScore = (1000 - index).toFloat()
                    val updated = movie.copy(
                        trendingCategory = "Film Popolari",
                        tmdbPopularity = popularityScore,
                        tmdbId = movie.tmdbId ?: tmdb.id,
                        tmdbPosterPath = movie.tmdbPosterPath ?: tmdb.posterPath,
                        tmdbBackdropPath = movie.tmdbBackdropPath ?: tmdb.backdropPath
                    )
                    movieDao.update(updated)
                    matchCount++
                    
                    Log.d(TAG, "Trending movie #$matchCount: ${movie.name}")
                }
            }
            
            Log.d(TAG, "Populated $matchCount trending movies")
            matchCount
        } catch (e: Exception) {
            Log.e(TAG, "Error populating trending movies", e)
            0
        }
    }
    
    /**
     * Populate "Serie Popolari" category with trending series from TMDB
     * Matches by TMDB ID, Italian title, or English title with year verification
     * Returns count of matched series
     */
    suspend fun populateTrendingSeries(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Populating trending series category (fetching 3 pages)...")
            val allTmdbItems = mutableListOf<TMDBItem>()
            
            // Fetch first 3 pages (60 items)
            for (page in 1..3) {
                val items = fetchFromTMDB("$BASE_URL/trending/tv/week?api_key=$API_KEY&language=$LANGUAGE&page=$page", isTV = true)
                allTmdbItems.addAll(items)
            }
            
            var matchCount = 0
            
            for ((index, tmdb) in allTmdbItems.withIndex()) {
                if (matchCount >= 100) break  // Limit to 100 trending series

                
                // 1. Try to find by TMDB ID first
                var series = seriesDao.getSeriesByTmdbId(tmdb.id)
                
                // Determine year from TMDB release date
                val tmdbYear = extractYear(tmdb.releaseDate)
                
                // Verify year match if found by ID
                if (series != null) {
                    val localYear = extractYearFromTitle(series.name)
                    if (!isSameYear(localYear, tmdbYear)) {
                        Log.w(TAG, "MISMATCH on ID lookup: '${series.name}' ($localYear) vs '${tmdb.title}' ($tmdbYear). Unlinking...")
                        seriesDao.update(series.copy(tmdbId = null, tmdbPopularity = null))
                        series = null
                    }
                }
                
                // 2. If not found, search by Italian title
                if (series == null) {
                    series = searchSeriesByTitles(
                        italianTitle = tmdb.title,
                        englishTitle = tmdb.originalTitle,
                        year = tmdbYear
                    )
                }
                
                // 3. If found, update to assign trending category
                if (series != null) {
                    // Filter out unwanted content (German, Adult, etc)
                    if (shouldExcludeContent(series.name)) {
                        Log.d(TAG, "Skipping excluded series: ${series.name}")
                        continue
                    }
                    
                    val popularityScore = (1000 - index).toFloat()
                    val updated = series.copy(
                        trendingCategory = "Serie Popolari",
                        tmdbPopularity = popularityScore,
                        tmdbId = series.tmdbId ?: tmdb.id,
                        tmdbPosterPath = series.tmdbPosterPath ?: tmdb.posterPath,
                        tmdbBackdropPath = series.tmdbBackdropPath ?: tmdb.backdropPath
                    )
                    seriesDao.update(updated)
                    matchCount++
                    
                    Log.d(TAG, "Trending series #$matchCount: ${series.name}")
                }
            }
            
            Log.d(TAG, "Populated $matchCount trending series")
            matchCount
        } catch (e: Exception) {
            Log.e(TAG, "Error populating trending series", e)
            0
        }
    }
    
    /**
     * Check if content should be excluded (German, Adult/XXX, etc)
     */
    private fun shouldExcludeContent(title: String): Boolean {
        val t = title.uppercase()
        
        // Filter XXX/Adult content
        if (t.contains("XXX") || 
            t.contains("PORN") || 
            t.contains("ADULT") || 
            t.contains("18+") ||
            t.contains("HARDCORE") ||
            t.startsWith("RED ")) {
            Log.d(TAG, "shouldExcludeContent: '$title' -> EXCLUDED (Adult filter)")
            return true
        }
        
        // Filter German content
        if (t.startsWith("DE ") || 
               t.startsWith("DE:") || 
               t.startsWith("DE-") || 
               t.startsWith("GER ") ||
               t.contains("[DE]") ||
               t.contains("(DE)") ||
               t.contains("GERMANY") ||
               t.startsWith("DE TOP")) {
            Log.d(TAG, "shouldExcludeContent: '$title' -> EXCLUDED (German filter)")
            return true
        }
        
        return false
    }
    
    /**
     * Search for a movie by Italian and English titles with year verification
     */
    private suspend fun searchMovieByTitles(
        italianTitle: String,
        englishTitle: String?,
        year: Int?
    ): Movie? {
        // Search Italian title
        val itResults = movieDao.searchMovies(italianTitle)
        var match = itResults.firstOrNull { movie ->
            val localYear = movie.year ?: extractYearFromTitle(movie.name)
            isMatch(movie.name, italianTitle, localYear, year)
        }
        
        // Fallback: search English title if different
        if (match == null && !englishTitle.isNullOrEmpty() && englishTitle != italianTitle) {
            val enResults = movieDao.searchMovies(englishTitle)
            match = enResults.firstOrNull { movie ->
                 val localYear = movie.year ?: extractYearFromTitle(movie.name)
                 isMatch(movie.name, englishTitle, localYear, year)
            }
        }
        
        return match
    }
    
    /**
     * Search for a series by Italian and English titles with year verification
     */
    private suspend fun searchSeriesByTitles(
        italianTitle: String,
        englishTitle: String?,
        year: Int?
    ): Series? {
        // Search Italian title
        val itResults = seriesDao.searchSeries(italianTitle)
        var match = itResults.firstOrNull { series ->
            val localYear = extractYearFromTitle(series.name) // series entity doesn't have year field yet, so parse it
            isMatch(series.name, italianTitle, localYear, year)
        }
        
        // Fallback: search English title if different
        if (match == null && !englishTitle.isNullOrEmpty() && englishTitle != italianTitle) {
            val enResults = seriesDao.searchSeries(englishTitle)
            match = enResults.firstOrNull { series ->
                val localYear = extractYearFromTitle(series.name)
                isMatch(series.name, englishTitle, localYear, year)
            }
        }
        
        return match
    }
    
    /**
     * Fetch data from TMDB API
     */
    private fun fetchFromTMDB(url: String, isTV: Boolean = false): List<TMDBItem> {
        val response = URL(url).readText()
        val json = JSONObject(response)
        val results = json.getJSONArray("results")
        
        return (0 until results.length()).map { i ->
            val item = results.getJSONObject(i)
            val genreIds = mutableListOf<Int>()
            val genresArray = item.optJSONArray("genre_ids")
            if (genresArray != null) {
                for (j in 0 until genresArray.length()) {
                    genreIds.add(genresArray.getInt(j))
                }
            }
            
            TMDBItem(
                id = item.getInt("id"),
                title = item.optString(if (isTV) "name" else "title", ""),
                originalTitle = item.optString(if (isTV) "original_name" else "original_title", ""),
                posterPath = item.optString("poster_path", "").takeIf { it.isNotEmpty() },
                backdropPath = item.optString("backdrop_path", "").takeIf { it.isNotEmpty() },
                overview = item.optString("overview", "").takeIf { it.isNotEmpty() },
                voteAverage = item.optDouble("vote_average", 0.0).toFloat(),
                releaseDate = item.optString(if (isTV) "first_air_date" else "release_date", "").takeIf { it.isNotEmpty() },
                genreIds = genreIds
            )
        }
    }
    
    /**
     * Match TMDB items with local movies
     * Uses individual lookups to avoid loading all movies into memory (OOM fix)
     */
    private suspend fun matchMoviesWithLocal(tmdbItems: List<TMDBItem>, limit: Int): List<MatchedContent> {
        val matched = mutableListOf<MatchedContent>()
        
        for (tmdb in tmdbItems) {
            if (matched.size >= limit) break
            
            // 1. Try to find by TMDB ID first (fastest and most accurate)
            var movie = movieDao.getMovieByTmdbId(tmdb.id)
            
            // Determine year from TMDB release date (hoisted for use in fallback search)
            val tmdbYear = extractYear(tmdb.releaseDate)
            
            // 2. If not found, search by title with year verification
            if (movie == null) {
                // Search for movies with this title
                val searchResults = movieDao.searchMovies(tmdb.title)
                
                movie = searchResults.firstOrNull { m ->
                    val localYear = m.year ?: extractYearFromTitle(m.name)
                    isMatch(m.name, tmdb.title, localYear, tmdbYear) || 
                    isMatch(m.name, tmdb.originalTitle, localYear, tmdbYear)
                }
            }
            
            // 3. If still not found, try original title if different
            if (movie == null && tmdb.originalTitle.isNotEmpty() && tmdb.originalTitle != tmdb.title) {
                val searchResults = movieDao.searchMovies(tmdb.originalTitle)
                movie = searchResults.firstOrNull { m ->
                    val localYear = m.year ?: extractYearFromTitle(m.name)
                    isMatch(m.name, tmdb.originalTitle, localYear, tmdbYear)
                }
            }
            
            if (movie != null) {
                val sources = listOf(
                    ContentSource(
                        id = movie.id,
                        streamUrl = movie.streamUrl,
                        category = movie.category ?: "Sconosciuta",
                        quality = detectQuality(movie.streamUrl, movie.name)
                    )
                )
                
                matched.add(MatchedContent(
                    tmdbItem = tmdb,
                    localContent = movie,
                    sources = sources
                ))
                
                // Update popularity score immediately
                val popularityScore = (1000 - matched.size).toFloat()
                movieDao.updatePopularityScore(movie.id, popularityScore)
            }
        }
        
        return matched
    }
    
    /**
     * Match TMDB items with local series
     * Uses individual lookups to avoid loading all series into memory (OOM fix)
     */
    private suspend fun matchSeriesWithLocal(tmdbItems: List<TMDBItem>, limit: Int): List<MatchedContent> {
        val matched = mutableListOf<MatchedContent>()
        
        for (tmdb in tmdbItems) {
            if (matched.size >= limit) break
            
            // 1. Try to find by TMDB ID first (fastest and most accurate)
            var series = seriesDao.getSeriesByTmdbId(tmdb.id)
            
            // Determine year from TMDB release date (hoisted for use in fallback search)
            val tmdbYear = extractYear(tmdb.releaseDate)
            
            // 2. If not found, search by title with year verification
            if (series == null) {
                // Search for series with this title
                val searchResults = seriesDao.searchSeries(tmdb.title)
                
                series = searchResults.firstOrNull { s ->
                    val localYear = extractYearFromTitle(s.name)
                    isMatch(s.name, tmdb.title, localYear, tmdbYear) ||
                    isMatch(s.name, tmdb.originalTitle, localYear, tmdbYear)
                }
            }
            
            // 3. If still not found, try original title if different
            if (series == null && tmdb.originalTitle.isNotEmpty() && tmdb.originalTitle != tmdb.title) {
                val searchResults = seriesDao.searchSeries(tmdb.originalTitle)
                series = searchResults.firstOrNull { s ->
                     val localYear = extractYearFromTitle(s.name)
                     isMatch(s.name, tmdb.originalTitle, localYear, tmdbYear)
                }
            }
            
            if (series != null) {
                val sources = listOf(
                    ContentSource(
                        id = series.id,
                        streamUrl = "", // Series don't have direct stream URL
                        category = series.category ?: "Sconosciuta",
                        quality = null
                    )
                )
                
                matched.add(MatchedContent(
                    tmdbItem = tmdb,
                    localContent = series,
                    sources = sources
                ))
                
                // Update popularity score immediately
                val popularityScore = (1000 - matched.size).toFloat()
                seriesDao.updatePopularityScore(series.id, popularityScore)
            }
        }
        
        return matched
    }
    
    /**
     * Normalize title for fuzzy matching
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9àèéìòù]"), "")
            .replace("à", "a")
            .replace("è", "e")
            .replace("é", "e")
            .replace("ì", "i")
            .replace("ò", "o")
            .replace("ù", "u")
    }
    
    /**
     * Detect quality from stream URL or title
     */
    private fun detectQuality(url: String, title: String): String? {
        val combined = "$url $title".lowercase()
        return when {
            "4k" in combined || "2160" in combined -> "4K"
            "1080" in combined || "fhd" in combined -> "FHD"
            "720" in combined || "hd" in combined -> "HD"
            "480" in combined || "sd" in combined -> "SD"
            else -> null
        }
    }
    
    /**
     * Clean movie/series title for TMDB search
     * Removes year tags, quality tags, and common IPTV provider suffixes
     */
    private fun cleanTitleForSearch(title: String): String {
        var cleaned = title
        
        // Remove year in parentheses: (2024), (2025), etc.
        cleaned = cleaned.replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
        
        // Remove year in brackets: [2024], [2025], etc.
        cleaned = cleaned.replace(Regex("""\s*\[\d{4}\]\s*"""), " ")
        
        // Remove quality tags (case insensitive)
        val qualityTags = listOf(
            "4K", "UHD", "2160p", "2160",
            "FHD", "1080p", "1080i", "1080",
            "HD", "720p", "720",
            "SD", "480p", "480",
            "HDR", "HDR10", "HDR10+", "DOLBY VISION", "DV",
            "HEVC", "H265", "H.265", "x265",
            "H264", "H.264", "x264",
            "WEB-DL", "WEBDL", "WEB", "WEBRIP",
            "BLURAY", "BLU-RAY", "BDRIP", "BRRIP",
            "DVDRIP", "DVDSCR",
            "CAM", "TS", "TC", "HDTS",
            "ITA", "ENG", "MULTI", "SUB",
            "AC3", "DTS", "AAC", "ATMOS"
        )
        
        for (tag in qualityTags) {
            // Remove tag ONLY as whole word (with optional surrounding brackets/parentheses)
            // Uses word boundary \b to prevent matching inside words like "vita" → "v"
            cleaned = cleaned.replace(Regex("""(?i)\b$tag\b|\[$tag\]|\($tag\)"""), " ")
        }
        
        // Remove common separators at the end: - | : 
        cleaned = cleaned.replace(Regex("""\s*[-|:]+\s*$"""), "")
        
        // Remove trailing/leading whitespace and normalize multiple spaces
        cleaned = cleaned.trim().replace(Regex("""\s{2,}"""), " ")
        
        Log.d(TAG, "Cleaned title: '$title' -> '$cleaned'")
        return cleaned
    }
    
    /**
     * Clean movie/series title for display
     * Same as cleanTitleForSearch but also capitalizes properly
     */
    private fun cleanTitleForDisplay(title: String): String {
        // First do the standard cleaning
        val cleaned = cleanTitleForSearch(title)
        
        // Capitalize first letter of each word
        return cleaned.split(" ").joinToString(" ") { word ->
            if (word.length > 1) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word.uppercase()
            }
        }
    }
    
    /**
     * Check if cache needs refresh
     */
    suspend fun needsCacheRefresh(): Boolean {
        val lastUpdate = userPreferences.getTmdbLastUpdate()
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)
        return hoursSinceUpdate >= CACHE_DURATION_HOURS
    }
    
    /**
     * Update cache timestamp
     */
    suspend fun updateCacheTimestamp() {
        userPreferences.setTmdbLastUpdate(System.currentTimeMillis())
    }
    
    /**
     * Enrich movie with TMDB details (plot, cast, director, runtime)
     * Called on-demand when opening movie details
     * Uses multiple search strategies for better matching
     */
    suspend fun enrichMovieDetails(movie: Movie): Movie = withContext(Dispatchers.IO) {
        Log.d(TAG, "===== ENRICH START: '${movie.name}' =====")
        Log.d(TAG, "Movie state: tmdbId=${movie.tmdbId}, tmdbVoteAverage=${movie.tmdbVoteAverage}, tmdbOriginalTitle=${movie.tmdbOriginalTitle}, tmdbLastFetchAt=${movie.tmdbLastFetchAt}")
        
        // Skip if already enriched recently AND has TMDB data AND has original title (for OMDB fallback) AND has trailer key
        val hasRealData = movie.tmdbId != null && movie.tmdbOverview != null && movie.tmdbOriginalTitle != null && movie.tmdbTrailerKey != null
        if (hasRealData && movie.tmdbLastFetchAt != null && 
            System.currentTimeMillis() - movie.tmdbLastFetchAt < 7 * 24 * 60 * 60 * 1000) {  // 7 days cache
            Log.d(TAG, "SKIP: Movie '${movie.name}' already enriched, returning cached (rating=${movie.tmdbVoteAverage})")
            // Return fresh data from DB to ensure all fields are populated (in case movie passed is stale)
            val freshFromDb = movieDao.getMovieById(movie.id)
            if (freshFromDb != null && freshFromDb.tmdbVoteAverage != null) {
                Log.d(TAG, "Returning fresh DB data with rating=${freshFromDb.tmdbVoteAverage}")
                return@withContext freshFromDb
            }
            return@withContext movie
        }
        
        try {
            // Clean title for TMDB search - remove year tags, quality suffixes, etc.
            val cleanedTitle = cleanTitleForSearch(movie.name)
            Log.d(TAG, "Cleaned title: '$cleanedTitle' (original: '${movie.name}')")
            
            // Try to extract year from original title if not already set
            val yearFromTitle = Regex("""\\((\\d{4})\\)""").find(movie.name)?.groupValues?.get(1)?.toIntOrNull()
            val year = movie.year ?: yearFromTitle
            
            // Try multiple search strategies
            var tmdbId: Int? = searchMovieOnTMDB(cleanedTitle, year, "it-IT")
            
            // Strategy 2: If no results, try without year
            if (tmdbId == null && year != null) {
                Log.d(TAG, "TMDB: No results with year, trying without year...")
                tmdbId = searchMovieOnTMDB(cleanedTitle, null, "it-IT")
            }
            
            // Strategy 3: Try with English language (for Italian titles that might match English)
            if (tmdbId == null) {
                Log.d(TAG, "TMDB: No Italian results, trying English language...")
                tmdbId = searchMovieOnTMDB(cleanedTitle, year, "en-US")
                if (tmdbId == null && year != null) {
                    tmdbId = searchMovieOnTMDB(cleanedTitle, null, "en-US")
                }
            }
            
            // Strategy 4: Try common Italian-to-English title translations
            if (tmdbId == null) {
                val englishTitle = tryTranslateCommonItalianTitles(cleanedTitle)
                if (englishTitle != null && englishTitle != cleanedTitle) {
                    Log.d(TAG, "TMDB: Trying translated title: '$englishTitle'")
                    tmdbId = searchMovieOnTMDB(englishTitle, year, "en-US")
                    if (tmdbId == null && year != null) {
                        tmdbId = searchMovieOnTMDB(englishTitle, null, "en-US")
                    }
                }
            }
            
            // Strategy 5: Try removing articles and common prefixes
            if (tmdbId == null) {
                val simplifiedTitle = removeArticlesAndPrefixes(cleanedTitle)
                if (simplifiedTitle != cleanedTitle) {
                    Log.d(TAG, "TMDB: Trying simplified title: '$simplifiedTitle'")
                    tmdbId = searchMovieOnTMDB(simplifiedTitle, year, "it-IT")
                    if (tmdbId == null) {
                        tmdbId = searchMovieOnTMDB(simplifiedTitle, null, "en-US")
                    }
                }
            }
            
            // Strategy 6: Try individual words (for cases like "John Rambo" -> "Rambo")
            if (tmdbId == null) {
                val words = cleanedTitle.split(" ").filter { it.length > 3 }
                if (words.size >= 2) {
                    // Try last word first (often the main title)
                    val lastWord = words.last()
                    Log.d(TAG, "TMDB: Trying last word: '$lastWord'")
                    tmdbId = searchMovieOnTMDB(lastWord, year, "en-US")
                    
                    // Try first word if last didn't work
                    if (tmdbId == null && words.first() != lastWord) {
                        val firstWord = words.first()
                        Log.d(TAG, "TMDB: Trying first word: '$firstWord'")
                        tmdbId = searchMovieOnTMDB(firstWord, year, "en-US")
                    }
                }
            }
            
            if (tmdbId == null) {
                Log.d(TAG, "No TMDB results for: '${movie.name}' after all strategies")
                return@withContext movie
            }
            
            // Fetch full movie details including credits (in Italian)
            Log.d(TAG, "Fetching full details for tmdbId=$tmdbId")
            val enrichedMovie = fetchMovieDetails(movie, tmdbId)
            Log.d(TAG, "ENRICHED RESULT: tmdbVoteAverage=${enrichedMovie.tmdbVoteAverage}")
            
            // Save to database
            movieDao.update(enrichedMovie)
            
            Log.d(TAG, "===== ENRICH END: ${movie.name} - SUCCESS (id=$tmdbId, rating=${enrichedMovie.tmdbVoteAverage}) =====")
            enrichedMovie
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching movie: ${movie.name}", e)
            movie
        }
    }
    
    /**
     * Search for a movie on TMDB and return its ID
     */
    private fun searchMovieOnTMDB(title: String, year: Int?, language: String): Int? {
        try {
            val yearParam = year?.let { "&year=$it" } ?: ""
            val searchUrl = "$BASE_URL/search/movie?api_key=$API_KEY&language=$language&query=${
                java.net.URLEncoder.encode(title, "UTF-8")
            }$yearParam"
            
            Log.d(TAG, "TMDB search: '$title' year=$year lang=$language")
            
            val searchResponse = URL(searchUrl).readText()
            val searchJson = JSONObject(searchResponse)
            val results = searchJson.optJSONArray("results")
            
            if (results != null && results.length() > 0) {
                val firstResult = results.getJSONObject(0)
                val resultTitle = firstResult.optString("title", "")
                val resultOriginalTitle = firstResult.optString("original_title", "")
                Log.d(TAG, "TMDB found: '$resultTitle' (original: '$resultOriginalTitle')")
                return firstResult.getInt("id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDB search error for '$title'", e)
        }
        return null
    }
    
    /**
     * Fetch full movie details from TMDB
     */
    private fun fetchMovieDetails(movie: Movie, tmdbId: Int): Movie {
        val detailsUrl = "$BASE_URL/movie/$tmdbId?api_key=$API_KEY&language=$LANGUAGE&append_to_response=credits,external_ids,videos&include_video_language=it,en,null"
        Log.d(TAG, "Fetching: $detailsUrl")
        val detailsResponse = URL(detailsUrl).readText()
        val details = JSONObject(detailsResponse)
        
        // Extract detailed info
        val overview = details.optString("overview", "").takeIf { it.isNotBlank() }
        val runtime = details.optInt("runtime", 0).takeIf { it > 0 }
        val backdropPath = details.optString("backdrop_path", "").takeIf { it.isNotEmpty() }
        val posterPath = details.optString("poster_path", "").takeIf { it.isNotEmpty() }
        val voteAverage = details.optDouble("vote_average", 0.0).toFloat()
        val releaseDate = details.optString("release_date", "").takeIf { it.isNotEmpty() }
        val extractedYear = releaseDate?.take(4)?.toIntOrNull()
        
        Log.d(TAG, "FETCH DETAILS: tmdbId=$tmdbId, voteAverage=$voteAverage, voteAverage>0=${voteAverage > 0}")
        
        // Extract genres
        val genresArray = details.optJSONArray("genres")
        val genres = if (genresArray != null) {
            (0 until genresArray.length()).mapNotNull { 
                genresArray.getJSONObject(it).optString("name", "").takeIf { name -> name.isNotEmpty() }
            }.joinToString(", ")
        } else null
        
        // Extract cast (top 5)
        val credits = details.optJSONObject("credits")
        val castArray = credits?.optJSONArray("cast")
        val cast = if (castArray != null) {
            (0 until minOf(5, castArray.length())).mapNotNull { 
                castArray.getJSONObject(it).optString("name", "").takeIf { name -> name.isNotEmpty() }
            }.joinToString(", ")
        } else null
        
        // Extract director
        val crewArray = credits?.optJSONArray("crew")
        var director: String? = null
        if (crewArray != null) {
            for (i in 0 until crewArray.length()) {
                val crew = crewArray.getJSONObject(i)
                if (crew.optString("job") == "Director") {
                    director = crew.optString("name", "").takeIf { it.isNotEmpty() }
                    break
                }
            }
        }
        
        val externalIds = details.optJSONObject("external_ids")
        val imdbId = externalIds?.optString("imdb_id", "")?.takeIf { it.isNotBlank() }
        Log.d(TAG, "External IDs: imdb_id=$imdbId")
        
        // Get TMDB title (for reference only - DO NOT modify movie.name)
        val tmdbTitle = details.optString("title", "").takeIf { it.isNotBlank() }
        val tmdbOriginalTitle = details.optString("original_title", "").takeIf { it.isNotBlank() }
        
        Log.d(TAG, "TMDB titles: tmdbTitle='$tmdbTitle', originalTitle='$tmdbOriginalTitle'")
        
        if (tmdbTitle == null) {
            Log.w(TAG, "WARNING: tmdbTitle is NULL for id=$tmdbId. Movie.title will fallback to name.")
        }

        // Extract Trailer
        val videos = details.optJSONObject("videos")
        val videosArray = videos?.optJSONArray("results")
        var trailerKey: String? = null
        
        if (videosArray != null) {
            for (i in 0 until videosArray.length()) {
                val video = videosArray.getJSONObject(i)
                val site = video.optString("site", "")
                val type = video.optString("type", "")
                
                if (site.equals("YouTube", ignoreCase = true) && type.equals("Trailer", ignoreCase = true)) {
                    trailerKey = video.optString("key")
                    break
                }
            }
        }
        
        Log.d(TAG, "Trailer check: key=$trailerKey")
        
        // Update movie entity (keep original name unchanged!)
        return movie.copy(
            tmdbId = tmdbId,
            tmdbTitle = tmdbTitle,
            tmdbOriginalTitle = tmdbOriginalTitle,  // For OMDB fallback
            tmdbImdbId = imdbId,  // For direct OMDB lookup by ID
            tmdbTrailerKey = trailerKey,
            tmdbOverview = overview,
            tmdbRuntime = runtime,
            tmdbCast = cast,
            tmdbDirector = director,
            tmdbGenres = genres,
            tmdbBackdropPath = backdropPath,
            tmdbPosterPath = posterPath ?: movie.tmdbPosterPath,
            tmdbVoteAverage = voteAverage.takeIf { it > 0 },
            year = movie.year ?: extractedYear,
            tmdbLastFetchAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Try to translate common Italian movie title patterns to English
     */
    private fun tryTranslateCommonItalianTitles(italianTitle: String): String? {
        val translations = mapOf(
            "il grande gatsby" to "the great gatsby",
            "il padrino" to "the godfather",
            "il signore degli anelli" to "the lord of the rings",
            "guerre stellari" to "star wars",
            "la bella e la bestia" to "beauty and the beast",
            "il re leone" to "the lion king",
            "la sirenetta" to "the little mermaid",
            "pinocchio" to "pinocchio",
            "cenerentola" to "cinderella",
            "la bella addormentata" to "sleeping beauty",
            "frozen" to "frozen",
            "avatar" to "avatar",
            "titanic" to "titanic",
            "matrix" to "the matrix",
            "inception" to "inception",
            "interstellar" to "interstellar"
        )
        
        val lowerTitle = italianTitle.lowercase()
        
        // Check for exact matches
        translations[lowerTitle]?.let { return it }
        
        // Check for partial matches (e.g., "Il Grande Gatsby (2013)" contains "il grande gatsby")
        for ((italian, english) in translations) {
            if (lowerTitle.contains(italian)) {
                return lowerTitle.replace(italian, english)
            }
        }
        
        return null
    }
    
    /**
     * Remove common Italian and English articles and prefixes
     */
    private fun removeArticlesAndPrefixes(title: String): String {
        val articlesAndPrefixes = listOf(
            "il ", "lo ", "la ", "i ", "gli ", "le ", "l'", "un ", "uno ", "una ",
            "the ", "a ", "an ",
            "di ", "del ", "della ", "dei ", "delle ", "dello ",
            "of ", "in ", "on "
        )
        
        var result = title.lowercase()
        for (article in articlesAndPrefixes) {
            if (result.startsWith(article)) {
                result = result.removePrefix(article)
                break
            }
        }
        
        return result.trim().replaceFirstChar { it.uppercase() }
    }
    
    /**
     * Enrich series with TMDB details (plot, cast, genres, rating)
     * Called on-demand when opening series details
     * Uses multiple search strategies for better matching
     */
    suspend fun enrichSeriesDetails(series: Series): Series = withContext(Dispatchers.IO) {
        // Skip if already enriched recently AND has TMDB data AND has trailer key
        val hasRealData = series.tmdbId != null && series.tmdbOverview != null && series.tmdbTrailerKey != null
        if (hasRealData && series.tmdbLastFetchAt != null && 
            System.currentTimeMillis() - series.tmdbLastFetchAt < 24 * 60 * 60 * 1000) {
            Log.d(TAG, "Series '${series.name}' already enriched with data, skipping")
            // Return fresh data from DB to ensure all fields are populated
            val freshFromDb = seriesDao.getSeriesById(series.id)
            if (freshFromDb != null && freshFromDb.tmdbVoteAverage != null) {
                Log.d(TAG, "Returning fresh DB series data with rating=${freshFromDb.tmdbVoteAverage}")
                return@withContext freshFromDb
            }
            return@withContext series
        }
        
        try {
            // Clean title for TMDB search
            val cleanedTitle = cleanTitleForSearch(series.name)
            
            // Try to extract year from first air date or name
            val yearFromName = Regex("""\\((\\d{4})\\)""").find(series.name)?.groupValues?.get(1)?.toIntOrNull()
            val year = series.year ?: yearFromName
            
            // Try multiple search strategies
            var tmdbId: Int? = searchSeriesOnTMDB(cleanedTitle, year, "it-IT")
            
            // Strategy 2: If no results, try without year
            if (tmdbId == null && year != null) {
                Log.d(TAG, "TMDB TV: No results with year, trying without year...")
                tmdbId = searchSeriesOnTMDB(cleanedTitle, null, "it-IT")
            }
            
            // Strategy 3: Try with English language
            if (tmdbId == null) {
                Log.d(TAG, "TMDB TV: No Italian results, trying English language...")
                tmdbId = searchSeriesOnTMDB(cleanedTitle, year, "en-US")
                if (tmdbId == null && year != null) {
                    tmdbId = searchSeriesOnTMDB(cleanedTitle, null, "en-US")
                }
            }
            
            // Strategy 4: Try common Italian-to-English title translations
            if (tmdbId == null) {
                val englishTitle = tryTranslateCommonItalianTitles(cleanedTitle)
                if (englishTitle != null && englishTitle != cleanedTitle) {
                    Log.d(TAG, "TMDB TV: Trying translated title: '$englishTitle'")
                    tmdbId = searchSeriesOnTMDB(englishTitle, year, "en-US")
                    if (tmdbId == null && year != null) {
                        tmdbId = searchSeriesOnTMDB(englishTitle, null, "en-US")
                    }
                }
            }
            
            // Strategy 5: Try removing articles and common prefixes
            if (tmdbId == null) {
                val simplifiedTitle = removeArticlesAndPrefixes(cleanedTitle)
                if (simplifiedTitle != cleanedTitle) {
                    Log.d(TAG, "TMDB TV: Trying simplified title: '$simplifiedTitle'")
                    tmdbId = searchSeriesOnTMDB(simplifiedTitle, year, "it-IT")
                    if (tmdbId == null) {
                        tmdbId = searchSeriesOnTMDB(simplifiedTitle, null, "en-US")
                    }
                }
            }
            
            // Strategy 6: Try individual words (for cases like "John Rambo" -> "Rambo")
            if (tmdbId == null) {
                val words = cleanedTitle.split(" ").filter { it.length > 3 }
                if (words.size >= 2) {
                    val lastWord = words.last()
                    Log.d(TAG, "TMDB TV: Trying last word: '$lastWord'")
                    tmdbId = searchSeriesOnTMDB(lastWord, year, "en-US")
                    
                    if (tmdbId == null && words.first() != lastWord) {
                        val firstWord = words.first()
                        Log.d(TAG, "TMDB TV: Trying first word: '$firstWord'")
                        tmdbId = searchSeriesOnTMDB(firstWord, year, "en-US")
                    }
                }
            }
            
            if (tmdbId == null) {
                Log.d(TAG, "No TMDB results for series: '${series.name}' after all strategies")
                return@withContext series
            }
            
            // Fetch full series details (in Italian)
            val enrichedSeries = fetchSeriesDetails(series, tmdbId)
            
            // Save to database
            seriesDao.update(enrichedSeries)
            
            Log.d(TAG, "Enriched series: ${series.name} with TMDB data (id=$tmdbId)")
            enrichedSeries
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enriching series: ${series.name}", e)
            series
        }
    }
    
    /**
     * Search for a TV series on TMDB and return its ID
     */
    private fun searchSeriesOnTMDB(title: String, year: Int?, language: String): Int? {
        try {
            val yearParam = year?.let { "&first_air_date_year=$it" } ?: ""
            val searchUrl = "$BASE_URL/search/tv?api_key=$API_KEY&language=$language&query=${
                java.net.URLEncoder.encode(title, "UTF-8")
            }$yearParam"
            
            Log.d(TAG, "TMDB TV search: '$title' year=$year lang=$language")
            
            val searchResponse = URL(searchUrl).readText()
            val searchJson = JSONObject(searchResponse)
            val results = searchJson.optJSONArray("results")
            
            if (results != null && results.length() > 0) {
                val firstResult = results.getJSONObject(0)
                val resultTitle = firstResult.optString("name", "")
                val resultOriginalTitle = firstResult.optString("original_name", "")
                Log.d(TAG, "TMDB TV found: '$resultTitle' (original: '$resultOriginalTitle')")
                return firstResult.getInt("id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TMDB TV search error for '$title'", e)
        }
        return null
    }
    
    /**
     * Fetch full TV series details from TMDB
     */
    private fun fetchSeriesDetails(series: Series, tmdbId: Int): Series {
        val detailsUrl = "$BASE_URL/tv/$tmdbId?api_key=$API_KEY&language=$LANGUAGE&append_to_response=credits,videos&include_video_language=it,en,null"
        val detailsResponse = URL(detailsUrl).readText()
        val details = JSONObject(detailsResponse)
        
        // Extract detailed info
        val overview = details.optString("overview", "").takeIf { it.isNotBlank() }
        val firstAirDate = details.optString("first_air_date", "").takeIf { it.isNotEmpty() }
        val backdropPath = details.optString("backdrop_path", "").takeIf { it.isNotEmpty() }
        val posterPath = details.optString("poster_path", "").takeIf { it.isNotEmpty() }
        val voteAverage = details.optDouble("vote_average", 0.0).toFloat()
        val numberOfSeasons = details.optInt("number_of_seasons", 0)
        val numberOfEpisodes = details.optInt("number_of_episodes", 0)
        val status = details.optString("status", "").takeIf { it.isNotEmpty() }
        @Suppress("UNUSED_VARIABLE") // extractedYear kept for future use
        val extractedYear = firstAirDate?.take(4)?.toIntOrNull()
        
        // Extract genres
        val genresArray = details.optJSONArray("genres")
        val genres = if (genresArray != null) {
            (0 until genresArray.length()).mapNotNull { 
                genresArray.getJSONObject(it).optString("name", "").takeIf { name -> name.isNotEmpty() }
            }.joinToString(", ")
        } else null
        
        // Extract Trailer
        val videos = details.optJSONObject("videos")
        val videosArray = videos?.optJSONArray("results")
        var trailerKey: String? = null
        
        if (videosArray != null) {
            for (i in 0 until videosArray.length()) {
                val video = videosArray.getJSONObject(i)
                val site = video.optString("site", "")
                val type = video.optString("type", "")
                
                if (site.equals("YouTube", ignoreCase = true) && type.equals("Trailer", ignoreCase = true)) {
                    trailerKey = video.optString("key")
                    break
                }
            }
        }
        
        // Extract cast (top 5)
        val credits = details.optJSONObject("credits")
        val castArray = credits?.optJSONArray("cast")
        val cast = if (castArray != null) {
            (0 until minOf(5, castArray.length())).mapNotNull { 
                castArray.getJSONObject(it).optString("name", "").takeIf { name -> name.isNotEmpty() }
            }.joinToString(", ")
        } else null
        
        // Update series entity
        return series.copy(
            tmdbId = tmdbId,
            tmdbTrailerKey = trailerKey,
            tmdbOverview = overview,
            tmdbFirstAirDate = firstAirDate,
            tmdbCast = cast,
            tmdbGenres = genres,
            tmdbBackdropPath = backdropPath,
            tmdbPosterPath = posterPath ?: series.tmdbPosterPath,
            tmdbVoteAverage = voteAverage.takeIf { it > 0 },
            tmdbNumberOfSeasons = numberOfSeasons.takeIf { it > 0 },
            tmdbNumberOfEpisodes = numberOfEpisodes.takeIf { it > 0 },
            tmdbStatus = status,
            tmdbLastFetchAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Helper to extract year from date string (YYYY-MM-DD)
     */
    private fun extractYear(dateString: String?): Int? {
        if (dateString.isNullOrEmpty()) return null
        return try {
            dateString.take(4).toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Helper to check if years match with tolerance (+/- 1 year)
     * Returns true if either year is null (permissive) or if they are close
     */
    /**
     * Unified matching logic that combines title similarity and year verification.
     * - EXACT title match: Year check is PERMISSIVE (missing year is OK).
     * - FUZZY/CONTAINS title match: Year check is STRICT (years MUST match to avoid false positives).
     */
    private fun isMatch(localTitle: String, tmdbTitle: String, localYear: Int?, tmdbYear: Int?): Boolean {
        val nLocal = normalizeTitle(localTitle)
        val nTmdb = normalizeTitle(tmdbTitle)
        
        // 1. EXACT match -> Year can be permissive
        if (nLocal == nTmdb && nLocal.length > 2) {
             val match = isSameYear(localYear, tmdbYear)
             if (match) Log.d(TAG, "MATCH EXACT: '$localTitle' == '$tmdbTitle' (Years: L=$localYear T=$tmdbYear)")
             return match
        }
        
        // 2. CONTAINS match
        if (nLocal.length > 3 && nTmdb.length > 3) {
            if (nLocal.contains(nTmdb) || nTmdb.contains(nLocal)) {
                // If titles are very similar (e.g. starts with), allow permissive year check
                if (nLocal.startsWith(nTmdb) || nTmdb.startsWith(nLocal)) {
                     val match = isSameYear(localYear, tmdbYear)
                     if (match) Log.d(TAG, "MATCH START: '$localTitle' ~= '$tmdbTitle' (Years: L=$localYear T=$tmdbYear)")
                     return match
                }
                // Otherwise require strict year check to avoid false positives
                val match = isSameYearStrict(localYear, tmdbYear)
                if (match) Log.d(TAG, "MATCH STRICT: '$localTitle' ~= '$tmdbTitle' (Years: L=$localYear T=$tmdbYear)")
                return match
            }
        }
        
        return false
    }

    /**
     * Helper to check if years match with tolerance (+/- 1 year).
     * PERMISSIVE: Returns true if EITHER year is missing.
     */
    private fun isSameYear(year1: Int?, year2: Int?): Boolean {
        if (year1 == null || year2 == null) return true 
        if (year1 == 0 || year2 == 0) return true
        return kotlin.math.abs(year1 - year2) <= 1
    }

    /**
     * Helper to check if years match with tolerance (+/- 1 year).
     * STRICT: Returns false if ANY year is missing.
     */
    private fun isSameYearStrict(year1: Int?, year2: Int?): Boolean {
        if (year1 == null || year2 == null) return false
        if (year1 == 0 || year2 == 0) return false
        return kotlin.math.abs(year1 - year2) <= 1
    }
    
    /**
     * Helper to extract year from title string (e.g. "Movie Name (2023)")
     * Looks for 4 digit number in parentheses or brackets, or just a 4 digit year
     */
    private fun extractYearFromTitle(title: String?): Int? {
        if (title.isNullOrEmpty()) return null
        // Match (2023), [2023], or just space 2023 at end of string
        val regex = Regex("[(\\[ ](\\d{4})[)\\]]?$")
        val match = regex.find(title)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
