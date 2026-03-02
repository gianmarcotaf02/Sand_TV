package it.sandtv.app.ui.loading

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.PlaylistDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.repository.EpgRepository
import it.sandtv.app.data.repository.ImdbRatingsRepository
import it.sandtv.app.data.repository.PlaylistRepository
import it.sandtv.app.data.repository.TMDBRepository
import it.sandtv.app.data.tmdb.TMDBService
import it.sandtv.app.ui.MainActivity
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.util.ContentFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Loading Activity - Shows sync progress after setup
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class LoadingActivity : ComponentActivity() {
    
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var tmdbRepository: TMDBRepository
    @Inject lateinit var epgRepository: EpgRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var tmdbService: TMDBService
    @Inject lateinit var imdbRatingsRepository: ImdbRatingsRepository
    @Inject lateinit var watchProgressDao: it.sandtv.app.data.database.dao.WatchProgressDao
    @Inject lateinit var teamChannelMapDao: it.sandtv.app.data.database.dao.TeamChannelMapDao

    
    private var profileId: Long = 1L
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        profileId = intent.getLongExtra("profile_id", 1L)
        val forceRefresh = intent.getBooleanExtra("force_refresh", false)
        
        setContent {
            SandTVTheme {
                LoadingContent(forceRefresh = forceRefresh)
            }
        }
    }
    
    @Composable
    private fun LoadingContent(forceRefresh: Boolean) {
        var loadingState by remember { 
            mutableStateOf(
                LoadingState(
                    status = getString(R.string.loading),
                    detail = "",
                    progress = 0,
                    showProgress = false
                )
            ) 
        }
        
        // Start loading on first composition
        LaunchedEffect(Unit) {
            startLoading(forceRefresh) { state ->
                loadingState = state
            }
        }
        
        LoadingScreen(
            statusText = loadingState.status,
            detailText = loadingState.detail,
            progress = loadingState.progress,
            showProgressBar = loadingState.showProgress
        )
    }
    
    private fun startLoading(
        forceRefresh: Boolean = false,
        onStateUpdate: (LoadingState) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val playlists = playlistDao.getAllPlaylists().first()
                
                if (playlists.isEmpty()) {
                    // No playlists - go to setup
                    goToSetup()
                    return@launch
                }
                
                // Check if auto-update is enabled and get interval
                val autoUpdateEnabled = userPreferences.getPlaylistAutoUpdate()
                val updateIntervalHours = userPreferences.getPlaylistUpdateIntervalHours()
                val intervalMs = updateIntervalHours * 60 * 60 * 1000L
                val now = System.currentTimeMillis()
                
                var progress = 0
                val totalSteps = playlists.size
                var syncedCount = 0
                var skippedCount = 0
                
                // Show initial progress
                onStateUpdate(
                    LoadingState(
                        status = getString(R.string.loading),
                        detail = "Inizializzazione...",
                        progress = 5,
                        showProgress = true
                    )
                )
                
                // One-time migration: Clear team-channel cache to rebuild with correct logic
                if (!userPreferences.isTeamChannelCacheCleared()) {
                    withContext(Dispatchers.IO) {
                        teamChannelMapDao.clearAll()
                        android.util.Log.d("LoadingActivity", "Team-channel cache cleared (one-time migration)")
                    }
                    userPreferences.setTeamChannelCacheCleared()
                }
                
                delay(300) // Brief delay to show initial state
                
                // Phase 1: Playlist sync (0-30%)
                playlists.forEachIndexed { index, playlist ->
                    val timeSinceUpdate = now - playlist.lastUpdated
                    val needsUpdate = forceRefresh || 
                                     (autoUpdateEnabled && timeSinceUpdate > intervalMs)
                    
                    // Debug logging to verify update logic
                    val hoursInterval = updateIntervalHours
                    val hoursSinceUpdate = timeSinceUpdate / (60 * 60 * 1000)
                    android.util.Log.d("LoadingActivity", 
                        "Playlist '${playlist.name}' check: interval=${hoursInterval}h, timeSince=${hoursSinceUpdate}h, " +
                        "autoUpdate=$autoUpdateEnabled, forceRefresh=$forceRefresh, needsUpdate=$needsUpdate")
                    
                    // Calculate progress within phase 1 (5-30%)
                    val phaseProgress = 5 + ((index + 1) * 25 / totalSteps)
                    
                    if (needsUpdate) {
                        // Sync playlist
                        onStateUpdate(
                            LoadingState(
                                status = getString(R.string.loading_syncing),
                                detail = playlist.name,
                                progress = phaseProgress,
                                showProgress = true
                            )
                        )
                        
                        try {
                            playlistRepository.refreshPlaylist(playlist.id)
                            syncedCount++
                            android.util.Log.d("LoadingActivity", "Synced playlist: ${playlist.name}")
                        } catch (e: Exception) {
                            android.util.Log.e("LoadingActivity", "Failed to sync ${playlist.name}", e)
                        }
                    } else {
                        // Skip - use cached data
                        val hoursAgo = timeSinceUpdate / (60 * 60 * 1000)
                        onStateUpdate(
                            LoadingState(
                                status = getString(R.string.loading_using_cache),
                                detail = "${playlist.name} (${hoursAgo}h fa)",
                                progress = phaseProgress,
                                showProgress = true
                            )
                        )
                        skippedCount++
                        android.util.Log.d("SandTVDebug", 
                            "Using cache for ${playlist.name}, last updated ${hoursAgo}h ago")
                        delay(400) // Longer delay for visual feedback when using cache
                    }
                    
                    progress++
                }
                
                // Phase 2: EPG (30-50%)
                onStateUpdate(
                    LoadingState(
                        status = "Caricamento guida TV...",
                        detail = "",
                        progress = 35,
                        showProgress = true
                    )
                )
                
                // === EPG Loading with caching ===
                loadEpgIfNeeded(playlists, forceRefresh, onStateUpdate)
                
                onStateUpdate(
                    LoadingState(
                        status = "Guida TV pronta",
                        detail = "",
                        progress = 50,
                        showProgress = true
                    )
                )
                delay(200)
                
                // Phase 2.5: TMDB Trending categories refresh (50-55%)
                refreshTrendingCategoriesIfNeeded(onStateUpdate)
                
                // Phase 3: Hero enrichment (55-95%)
                // Pre-load hero banners so they're ready when home screen appears
                enrichHeroContent(onStateUpdate)
                
                // Phase 4: Complete (100%)
                onStateUpdate(
                    LoadingState(
                        status = getString(R.string.loading_complete),
                        detail = "Tutto pronto!",
                        progress = 100,
                        showProgress = true,
                        isComplete = true
                    )
                )
                delay(600)

                
                goToMain()

                
            } catch (e: Exception) {
                onStateUpdate(
                    LoadingState(
                        status = getString(R.string.loading_error),
                        detail = e.message ?: "",
                        progress = 0,
                        showProgress = false,
                        hasError = true
                    )
                )
                delay(2000)
                goToMain()
            }
        }
    }
    
    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("profile_id", profileId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    
    private fun goToSetup() {
        val intent = Intent(this, it.sandtv.app.ui.setup.SetupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    
    /**
     * Load EPG data if needed based on cache age and user preferences
     */
    private suspend fun loadEpgIfNeeded(
        playlists: List<it.sandtv.app.data.database.entity.Playlist>,
        forceRefresh: Boolean,
        onStateUpdate: (LoadingState) -> Unit
    ) {
        try {
            val epgLastUpdate = userPreferences.getEpgLastUpdate()
            val epgInterval = userPreferences.getEpgUpdateInterval()
            val now = System.currentTimeMillis()
            
            // Calculate interval in milliseconds
            val intervalMs = when (epgInterval) {
                "startup" -> 0L // Always update on startup
                "3h" -> 3 * 60 * 60 * 1000L
                "6h" -> 6 * 60 * 60 * 1000L
                "12h" -> 12 * 60 * 60 * 1000L
                "24h" -> 24 * 60 * 60 * 1000L
                "3d" -> 3 * 24 * 60 * 60 * 1000L
                "weekly" -> 7 * 24 * 60 * 60 * 1000L
                else -> 24 * 60 * 60 * 1000L // Default: 24h
            }
            
            val timeSinceUpdate = now - epgLastUpdate
            val needsUpdate = forceRefresh || 
                              (epgInterval == "startup") ||
                              (intervalMs > 0 && timeSinceUpdate > intervalMs)
            
            if (!needsUpdate) {
                val hoursAgo = timeSinceUpdate / (60 * 60 * 1000)
                android.util.Log.d("LoadingActivity", "EPG cache still valid (updated ${hoursAgo}h ago)")
                return
            }
            
            onStateUpdate(
                LoadingState(
                    status = "Caricamento guida TV...",
                    detail = "",
                    progress = 40,  // Fixed: was 92, causing progress to jump back
                    showProgress = true
                )
            )
            
            // Load EPG for each Xtream playlist with TIMEOUT to prevent hanging
            for (playlist in playlists) {
                try {
                    if (playlist.type == "xtream" && 
                        !playlist.username.isNullOrEmpty() && 
                        !playlist.password.isNullOrEmpty()) {
                        
                        android.util.Log.d("LoadingActivity", "Loading EPG for: ${playlist.name}")
                        
                        // Use timeout to prevent indefinite hang (30 seconds max)
                        val result = withTimeoutOrNull(30_000L) {
                            withContext(Dispatchers.IO) {
                                epgRepository.loadEpgFromXtream(
                                    baseUrl = playlist.url,
                                    username = playlist.username,
                                    password = playlist.password
                                )
                            }
                        }
                        
                        if (result == null) {
                            android.util.Log.w("LoadingActivity", "EPG loading timed out for ${playlist.name}")
                        }
                        
                    } else if (!playlist.epgUrl.isNullOrEmpty()) {
                        // Timeout for EPG URL loading too
                        val result = withTimeoutOrNull(30_000L) {
                            withContext(Dispatchers.IO) {
                                epgRepository.loadEpgFromUrl(playlist.epgUrl)
                            }
                        }
                        if (result == null) {
                            android.util.Log.w("LoadingActivity", "EPG URL loading timed out")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LoadingActivity", "Error loading EPG for ${playlist.name}", e)
                    // Continue with other playlists, don't block
                }
            }
            
            // Update last EPG update timestamp
            userPreferences.setEpgLastUpdate(now)
            android.util.Log.d("LoadingActivity", "EPG loaded and cached")
            
        } catch (e: Exception) {
            android.util.Log.e("LoadingActivity", "Error loading EPG", e)
            // Don't block app startup if EPG fails
        }
    }
    
    /**
     * Enrich hero content - prioritizes continue watching, falls back to random
     * This runs during loading so heroes are ready when user reaches home screen
     */
    private suspend fun enrichHeroContent(onStateUpdate: (LoadingState) -> Unit) {
        try {
            withContext(Dispatchers.IO) {
                // === MOVIES ===
                val movieContinueWatching = watchProgressDao.getContinueWatchingMovies(profileId, 5)
                val hasContinueWatchingMovies = movieContinueWatching.isNotEmpty()
                
                android.util.Log.d("LoadingActivity", 
                    "Continue watching movies: ${movieContinueWatching.size}, profileId=$profileId")
                
                if (hasContinueWatchingMovies) {
                    // Continue watching items are already enriched from previous views - no API calls needed
                    android.util.Log.d("LoadingActivity", 
                        "Skipping movie enrichment - using cached continue watching data")
                    
                    withContext(Dispatchers.Main) {
                        onStateUpdate(
                            LoadingState(
                                status = "Contenuti già pronti!",
                                detail = "Film in continua a guardare",
                                progress = 75,
                                showProgress = true
                            )
                        )
                    }
                } else {
                    // No continue watching - random 5 from "Film Popolari" trending category
                    val trendingMovies = movieDao.getByTrendingCategory("Film Popolari")
                    val popularMovies = if (trendingMovies.isNotEmpty()) {
                        // Random 5 from trending category
                        trendingMovies
                            .filter { movie ->
                                !ContentFilters.shouldExcludeMovieFromHero(movie.name, movie.category) &&
                                (movie.logoUrl != null || movie.tmdbPosterPath != null)
                            }
                            .shuffled()
                            .take(5)
                    } else {
                        // Fallback to random if trending not populated yet
                        movieDao.getRandomMovies(30)
                            .filter { movie ->
                                !ContentFilters.shouldExcludeMovieFromHero(movie.name, movie.category) &&
                                (movie.logoUrl != null || movie.tmdbPosterPath != null)
                            }
                            .take(5)
                    }
                    
                    android.util.Log.d("LoadingActivity", "Enriching ${popularMovies.size} trending/popular movies for hero")
                    
                    popularMovies.forEachIndexed { index, movie ->
                        val movieProgress = 55 + ((index + 1) * 20 / popularMovies.size.coerceAtLeast(1))
                        
                        withContext(Dispatchers.Main) {
                            onStateUpdate(
                                LoadingState(
                                    status = "Preparazione contenuti in evidenza...",
                                    detail = movie.name,
                                    progress = movieProgress,
                                    showProgress = true
                                )
                            )
                        }
                        
                        try {
                            val enrichedMovie = tmdbService.enrichMovieDetails(movie)
                            
                            if (enrichedMovie.omdbImdbRating == null) {
                                val ratings = imdbRatingsRepository.getRatingsWithFallbacks(
                                    imdbId = enrichedMovie.tmdbImdbId,
                                    originalTitle = movie.name,
                                    englishTitle = enrichedMovie.tmdbOriginalTitle ?: enrichedMovie.tmdbTitle,
                                    year = enrichedMovie.year,
                                    type = "movie"
                                )
                                
                                if (ratings != null) {
                                    var withRatings = enrichedMovie.copy(
                                        omdbImdbRating = ratings.getFormattedImdbRating(),
                                        omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                                        omdbMetacriticScore = ratings.metacriticScore,
                                        omdbAudienceScore = ratings.audienceScore,
                                        omdbLastFetchAt = System.currentTimeMillis()
                                    )
                                    
                                    // If OMDB didn't provide audience score, try RT scraper
                                    if (withRatings.omdbAudienceScore == null) {
                                        android.util.Log.d("LoadingActivity", "OMDB missing audienceScore for hero ${movie.name}, fetching from RT...")
                                        val searchTitle = withRatings.tmdbOriginalTitle ?: withRatings.tmdbTitle ?: movie.name
                                        val rtScores = imdbRatingsRepository.fetchRtScores(
                                            title = searchTitle,
                                            year = withRatings.year,
                                            isMovie = true
                                        )
                                        if (rtScores != null) {
                                            android.util.Log.d("LoadingActivity", "RT scores fetched for hero: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                                            withRatings = withRatings.copy(
                                                omdbAudienceScore = withRatings.omdbAudienceScore ?: rtScores.audienceScore,
                                                omdbRottenTomatoesScore = withRatings.omdbRottenTomatoesScore ?: rtScores.criticsScore
                                            )
                                        }
                                    }
                                    
                                    movieDao.update(withRatings)
                                }
                            } else if (enrichedMovie.omdbAudienceScore == null || enrichedMovie.omdbRottenTomatoesScore == null) {
                                // Already enriched but missing RT scores - try to fetch them
                                android.util.Log.d("LoadingActivity", "Hero missing RT scores: ${movie.name}")
                                val searchTitle = enrichedMovie.tmdbOriginalTitle ?: enrichedMovie.tmdbTitle ?: movie.name
                                val rtScores = imdbRatingsRepository.fetchRtScores(
                                    title = searchTitle,
                                    year = enrichedMovie.year,
                                    isMovie = true
                                )
                                if (rtScores != null) {
                                    android.util.Log.d("LoadingActivity", "RT scores updated for hero: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                                    movieDao.update(enrichedMovie.copy(
                                        omdbAudienceScore = enrichedMovie.omdbAudienceScore ?: rtScores.audienceScore,
                                        omdbRottenTomatoesScore = enrichedMovie.omdbRottenTomatoesScore ?: rtScores.criticsScore
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("LoadingActivity", "Error enriching movie: ${movie.name}", e)
                        }
                    }
                }

                // === SERIES ===
                val seriesContinueWatching = watchProgressDao.getContinueWatchingSeries(profileId, 5)
                val hasContinueWatchingSeries = seriesContinueWatching.isNotEmpty()
                
                android.util.Log.d("LoadingActivity", 
                    "Continue watching series: ${seriesContinueWatching.size}")
                
                if (hasContinueWatchingSeries) {
                    // Continue watching items are already enriched - no API calls needed
                    android.util.Log.d("LoadingActivity", 
                        "Skipping series enrichment - using cached continue watching data")
                    
                    withContext(Dispatchers.Main) {
                        onStateUpdate(
                            LoadingState(
                                status = "Contenuti già pronti!",
                                detail = "Serie TV in continua a guardare",
                                progress = 95,
                                showProgress = true
                            )
                        )
                    }
                } else {
                    // No continue watching - random 5 from "Serie Popolari" trending category
                    val trendingSeries = seriesDao.getByTrendingCategory("Serie Popolari")
                    val popularSeries = if (trendingSeries.isNotEmpty()) {
                        // Random 5 from trending category
                        trendingSeries
                            .filter { series ->
                                !ContentFilters.shouldExcludeSeriesFromHero(series.name, series.category) &&
                                (series.logoUrl != null || series.tmdbPosterPath != null)
                            }
                            .shuffled()
                            .take(5)
                    } else {
                        // Fallback to random if trending not populated yet
                        seriesDao.getRandomSeries(30)
                            .filter { series ->
                                !ContentFilters.shouldExcludeSeriesFromHero(series.name, series.category) &&
                                (series.logoUrl != null || series.tmdbPosterPath != null)
                            }
                            .take(5)
                    }
                    
                    android.util.Log.d("LoadingActivity", "Enriching ${popularSeries.size} trending/popular series for hero")
                    
                    popularSeries.forEachIndexed { index, series ->
                        val seriesProgress = 75 + ((index + 1) * 20 / popularSeries.size.coerceAtLeast(1))
                        
                        withContext(Dispatchers.Main) {
                            onStateUpdate(
                                LoadingState(
                                    status = "Preparazione serie TV...",
                                    detail = series.name,
                                    progress = seriesProgress,
                                    showProgress = true
                                )
                            )
                        }
                        
                        try {
                            val enrichedSeries = tmdbService.enrichSeriesDetails(series)
                            
                            if (enrichedSeries.omdbImdbRating == null) {
                                val ratings = imdbRatingsRepository.getRatingsWithFallbacks(
                                    imdbId = null,
                                    originalTitle = series.name,
                                    englishTitle = enrichedSeries.tmdbOriginalName ?: enrichedSeries.tmdbName,
                                    year = enrichedSeries.year,
                                    type = "series"
                                )
                                
                                if (ratings != null) {
                                    var withRatings = enrichedSeries.copy(
                                        omdbImdbRating = ratings.getFormattedImdbRating(),
                                        omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                                        omdbMetacriticScore = ratings.metacriticScore,
                                        omdbAudienceScore = ratings.audienceScore,
                                        omdbLastFetchAt = System.currentTimeMillis()
                                    )
                                    
                                    // If OMDB didn't provide audience score OR critics score, try RT scraper
                                    if (withRatings.omdbAudienceScore == null || withRatings.omdbRottenTomatoesScore == null) {
                                        android.util.Log.d("LoadingActivity", "OMDB missing RT scores for series hero ${series.name}, fetching from RT...")
                                        val searchTitle = withRatings.tmdbOriginalName ?: withRatings.tmdbName ?: series.name
                                        val rtScores = imdbRatingsRepository.fetchRtScores(
                                            title = searchTitle,
                                            year = withRatings.year,
                                            isMovie = false
                                        )
                                        if (rtScores != null) {
                                            android.util.Log.d("LoadingActivity", "RT scores fetched for series hero: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                                            withRatings = withRatings.copy(
                                                omdbAudienceScore = withRatings.omdbAudienceScore ?: rtScores.audienceScore,
                                                omdbRottenTomatoesScore = withRatings.omdbRottenTomatoesScore ?: rtScores.criticsScore
                                            )
                                        }
                                    }
                                    
                                    seriesDao.update(withRatings)
                                }
                            } else if (enrichedSeries.omdbAudienceScore == null || enrichedSeries.omdbRottenTomatoesScore == null) {
                                // Already enriched but missing RT scores - try to fetch them
                                android.util.Log.d("LoadingActivity", "Series hero missing RT scores: ${series.name}")
                                val searchTitle = enrichedSeries.tmdbOriginalName ?: enrichedSeries.tmdbName ?: series.name
                                val rtScores = imdbRatingsRepository.fetchRtScores(
                                    title = searchTitle,
                                    year = enrichedSeries.year,
                                    isMovie = false
                                )
                                if (rtScores != null) {
                                    android.util.Log.d("LoadingActivity", "RT scores updated for series hero: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                                    seriesDao.update(enrichedSeries.copy(
                                        omdbAudienceScore = enrichedSeries.omdbAudienceScore ?: rtScores.audienceScore,
                                        omdbRottenTomatoesScore = enrichedSeries.omdbRottenTomatoesScore ?: rtScores.criticsScore
                                    ))
                                }
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("LoadingActivity", "Error enriching series: ${series.name}", e)
                        }
                    }
                }
            }
            
            android.util.Log.d("LoadingActivity", "Hero content enrichment complete")
            
        } catch (e: Exception) {
            android.util.Log.e("LoadingActivity", "Error enriching hero content", e)
        }
    }
    
    /**
     * Refresh TMDB trending categories if cache has expired (7 days)
     * Clears old trending assignments and populates with fresh trending data
     */
    private suspend fun refreshTrendingCategoriesIfNeeded(onStateUpdate: (LoadingState) -> Unit) {
        try {
            val lastUpdate = userPreferences.getTmdbPopularLastUpdate()
            val oneWeekMs = 7 * 24 * 60 * 60 * 1000L
            
            // FORCE REFRESH: Disabled in production
            val forceRefresh = false
            val needsUpdate = forceRefresh || (System.currentTimeMillis() - lastUpdate) > oneWeekMs
            
            if (!needsUpdate) {
                android.util.Log.d("LoadingActivity", "Trending categories cache still valid, skipping refresh")
                return
            }
            
            android.util.Log.d("LoadingActivity", "Trending categories: Refreshing...")
            
            withContext(Dispatchers.Main) {
                onStateUpdate(
                    LoadingState(
                        status = "Aggiornamento contenuti trending...",
                        detail = "",
                        progress = 52,
                        showProgress = true
                    )
                )
            }
            
            withContext(Dispatchers.IO) {
                // Clear old trending categories
                movieDao.clearTrendingCategory("Film Popolari")
                seriesDao.clearTrendingCategory("Serie Popolari")
                
                android.util.Log.d("LoadingActivity", "Cleared old trending categories")
                
                // Populate new trending from TMDB
                val movieCount = tmdbService.populateTrendingMovies()
                val seriesCount = tmdbService.populateTrendingSeries()
                
                android.util.Log.d("LoadingActivity", "Trending populated: $movieCount movies, $seriesCount series")
            }
            
            // Update timestamp
            userPreferences.setTmdbPopularLastUpdate(System.currentTimeMillis())
            
            withContext(Dispatchers.Main) {
                onStateUpdate(
                    LoadingState(
                        status = "Contenuti trending aggiornati",
                        detail = "",
                        progress = 55,
                        showProgress = true
                    )
                )
            }
            delay(200)
            
        } catch (e: Exception) {
            android.util.Log.e("LoadingActivity", "Error refreshing trending categories", e)
            // Don't block app startup if trending refresh fails
        }
    }
}



