package it.sandtv.app.data.repository

import it.sandtv.app.data.api.TMDBApiService
import it.sandtv.app.data.api.TMDBMovieDetails
import it.sandtv.app.data.api.TMDBSearchResult
import it.sandtv.app.data.api.TMDBTVDetails
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.dao.TMDBCacheDao
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.data.database.entity.Series
import it.sandtv.app.data.database.entity.TMDBCache
import it.sandtv.app.data.database.entity.TMDBMediaType
import it.sandtv.app.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for TMDB data with caching
 */
@Singleton
class TMDBRepository @Inject constructor(
    private val tmdbApi: TMDBApiService,
    private val tmdbCacheDao: TMDBCacheDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val userPreferences: UserPreferences
) {
    
    /**
     * Search for a movie by title and update the Movie entity with TMDB data
     */
    suspend fun enrichMovie(movie: Movie): Movie? = withContext(Dispatchers.IO) {
        val apiKey = userPreferences.getTmdbApiKey() ?: return@withContext null
        
        // Check cache first
        val cachedResult = tmdbCacheDao.getCachedResult(movie.name, TMDBMediaType.MOVIE)
        if (cachedResult != null) {
            val updatedMovie = movie.copy(
                tmdbId = cachedResult.tmdbId,
                tmdbPosterPath = cachedResult.posterPath,
                tmdbBackdropPath = cachedResult.backdropPath,
                tmdbTitle = cachedResult.title,
                tmdbOriginalTitle = cachedResult.originalTitle,
                tmdbOverview = cachedResult.overview,
                tmdbReleaseDate = cachedResult.releaseDate,
                tmdbVoteAverage = cachedResult.voteAverage,
                tmdbPopularity = cachedResult.popularity,
                tmdbLastFetchAt = System.currentTimeMillis()
            )
            movieDao.update(updatedMovie)
            return@withContext updatedMovie
        }
        
        // Search TMDB
        try {
            val searchQuery = cleanTitle(movie.name)
            val response = tmdbApi.searchMovies(
                apiKey = apiKey,
                query = searchQuery,
                year = movie.year
            )
            
            val bestMatch = response.results.firstOrNull() ?: return@withContext null
            
            // Get full details
            val details = tmdbApi.getMovieDetails(bestMatch.id, apiKey)
            
            // Cache the result
            cacheResult(searchQuery, TMDBMediaType.MOVIE, bestMatch.id, details)
            
            // Update movie
            val updatedMovie = movie.enrichWithTMDB(details)
            movieDao.update(updatedMovie)
            
            return@withContext updatedMovie
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Search for a series by title and update the Series entity with TMDB data
     */
    suspend fun enrichSeries(series: Series): Series? = withContext(Dispatchers.IO) {
        val apiKey = userPreferences.getTmdbApiKey() ?: return@withContext null
        
        // Check cache first
        val cachedResult = tmdbCacheDao.getCachedResult(series.name, TMDBMediaType.TV)
        if (cachedResult != null) {
            val updatedSeries = series.copy(
                tmdbId = cachedResult.tmdbId,
                tmdbPosterPath = cachedResult.posterPath,
                tmdbBackdropPath = cachedResult.backdropPath,
                tmdbName = cachedResult.title,
                tmdbOriginalName = cachedResult.originalTitle,
                tmdbOverview = cachedResult.overview,
                tmdbFirstAirDate = cachedResult.releaseDate,
                tmdbVoteAverage = cachedResult.voteAverage,
                tmdbPopularity = cachedResult.popularity,
                tmdbLastFetchAt = System.currentTimeMillis()
            )
            seriesDao.update(updatedSeries)
            return@withContext updatedSeries
        }
        
        try {
            val searchQuery = cleanTitle(series.name)
            val response = tmdbApi.searchTV(
                apiKey = apiKey,
                query = searchQuery
            )
            
            val bestMatch = response.results.firstOrNull() ?: return@withContext null
            
            // Get full details
            val details = tmdbApi.getTVDetails(bestMatch.id, apiKey)
            
            // Cache the result
            cacheResultTV(searchQuery, details.id, details)
            
            // Update series
            val updatedSeries = series.enrichWithTMDB(details)
            seriesDao.update(updatedSeries)
            
            return@withContext updatedSeries
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Search TMDB for any content type
     */
    suspend fun searchMulti(query: String): List<TMDBSearchResult> = withContext(Dispatchers.IO) {
        val apiKey = userPreferences.getTmdbApiKey() ?: return@withContext emptyList()
        
        try {
            val response = tmdbApi.searchMulti(apiKey, query)
            return@withContext response.results.filter { it.mediaType in listOf("movie", "tv") }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
    
    /**
     * Get movie details by TMDB ID
     */
    suspend fun getMovieDetails(tmdbId: Int): TMDBMovieDetails? = withContext(Dispatchers.IO) {
        val apiKey = userPreferences.getTmdbApiKey() ?: return@withContext null
        
        try {
            return@withContext tmdbApi.getMovieDetails(tmdbId, apiKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Get TV show details by TMDB ID
     */
    suspend fun getTVDetails(tmdbId: Int): TMDBTVDetails? = withContext(Dispatchers.IO) {
        val apiKey = userPreferences.getTmdbApiKey() ?: return@withContext null
        
        try {
            return@withContext tmdbApi.getTVDetails(tmdbId, apiKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    /**
     * Batch enrich movies (with rate limiting)
     */
    suspend fun enrichMoviesBatch(movies: List<Movie>, delayMs: Long = 250): List<Movie> {
        return movies.mapNotNull { movie ->
            if (movie.tmdbId == null) {
                kotlinx.coroutines.delay(delayMs)
                enrichMovie(movie)
            } else {
                movie
            }
        }
    }
    
    /**
     * Clean title for better search results
     */
    private fun cleanTitle(title: String): String {
        return title
            .replace("""\(\d{4}\)""".toRegex(), "") // Remove year in parentheses
            .replace("""\[.*?]""".toRegex(), "") // Remove brackets
            .replace("""[._-]+""".toRegex(), " ") // Replace separators with spaces
            .replace("""\s+""".toRegex(), " ") // Normalize whitespace
            .trim()
    }
    
    private suspend fun cacheResult(
        query: String,
        mediaType: TMDBMediaType,
        tmdbId: Int,
        details: TMDBMovieDetails
    ) {
        val cache = TMDBCache(
            searchQuery = query,
            mediaType = mediaType,
            tmdbId = tmdbId,
            title = details.title ?: "",
            originalTitle = details.originalTitle,
            posterPath = details.posterPath,
            backdropPath = details.backdropPath,
            overview = details.overview,
            releaseDate = details.releaseDate,
            voteAverage = details.voteAverage,
            popularity = details.popularity,
            confidence = 1.0f
        )
        tmdbCacheDao.insert(cache)
    }
    
    private suspend fun cacheResultTV(
        query: String,
        tmdbId: Int,
        details: TMDBTVDetails
    ) {
        val cache = TMDBCache(
            searchQuery = query,
            mediaType = TMDBMediaType.TV,
            tmdbId = tmdbId,
            title = details.name ?: "",
            originalTitle = details.originalName,
            posterPath = details.posterPath,
            backdropPath = details.backdropPath,
            overview = details.overview,
            releaseDate = details.firstAirDate,
            voteAverage = details.voteAverage,
            popularity = details.popularity,
            confidence = 1.0f
        )
        tmdbCacheDao.insert(cache)
    }
    
    private fun Movie.enrichWithTMDB(details: TMDBMovieDetails) = copy(
        tmdbId = details.id,
        tmdbPosterPath = details.posterPath,
        tmdbBackdropPath = details.backdropPath,
        tmdbTitle = details.title,
        tmdbOriginalTitle = details.originalTitle,
        tmdbOverview = details.overview,
        tmdbReleaseDate = details.releaseDate,
        tmdbVoteAverage = details.voteAverage,
        tmdbVoteCount = details.voteCount,
        tmdbPopularity = details.popularity,
        tmdbGenres = details.genres?.joinToString(",") { it.name },
        tmdbRuntime = details.runtime,
        tmdbCast = details.credits?.cast?.take(10)?.joinToString(",") { it.name },
        tmdbDirector = details.credits?.crew?.find { it.job == "Director" }?.name,
        tmdbLastFetchAt = System.currentTimeMillis()
    )
    
    private fun Series.enrichWithTMDB(details: TMDBTVDetails) = copy(
        tmdbId = details.id,
        tmdbPosterPath = details.posterPath,
        tmdbBackdropPath = details.backdropPath,
        tmdbName = details.name,
        tmdbOriginalName = details.originalName,
        tmdbOverview = details.overview,
        tmdbFirstAirDate = details.firstAirDate,
        tmdbVoteAverage = details.voteAverage,
        tmdbVoteCount = details.voteCount,
        tmdbPopularity = details.popularity,
        tmdbGenres = details.genres?.joinToString(",") { it.name },
        tmdbNumberOfSeasons = details.numberOfSeasons,
        tmdbNumberOfEpisodes = details.numberOfEpisodes,
        tmdbCast = details.credits?.cast?.take(10)?.joinToString(",") { it.name },
        tmdbStatus = details.status,
        tmdbLastFetchAt = System.currentTimeMillis()
    )
}
