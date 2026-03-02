package it.sandtv.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.R
import it.sandtv.app.data.cache.ContentCache
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.util.ContentFilters
import it.sandtv.app.util.CoilImagePreloader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import android.util.Log
import it.sandtv.app.data.tmdb.TMDBService
import it.sandtv.app.data.repository.ImdbRatingsRepository
import it.sandtv.app.data.api.XtreamApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Content type for tab-specific loading
 */
enum class HomeContentType {
    MOVIES,    // Film tab - only movies
    SERIES,    // Serie TV tab - only series
    FAVORITES, // Preferiti tab - favorites
    LISTS,     // Liste tab - custom lists
    HISTORY    // Cronologia tab - watch history
}

/**
 * ViewModel for TvHomeScreen
 * Manages home screen state for the Compose TV implementation
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val watchStateDao: WatchStateDao,
    private val watchProgressDao: WatchProgressDao,
    private val favoriteDao: FavoriteDao,
    private val favoriteCategoryDao: it.sandtv.app.data.database.dao.FavoriteCategoryDao,
    private val customGroupDao: CustomGroupDao,
    private val userPreferences: UserPreferences,
    private val contentCache: ContentCache,
    private val imagePreloader: CoilImagePreloader,
    private val tmdbService: TMDBService,
    private val imdbRatingsRepository: ImdbRatingsRepository,
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao
) : ViewModel() {

    companion object {
        // Cache shuffled categories so they don't change during app usage
        // Only shuffled ONCE at first load
        private var cachedShuffledMovieCategories: List<String>? = null
        private var cachedShuffledSeriesCategories: List<String>? = null
    }

    private val _uiState = MutableStateFlow(HomeScreenState())
    val uiState: StateFlow<HomeScreenState> = _uiState.asStateFlow()
    
    // Available categories for sidebar
    private val _movieCategories = MutableStateFlow<List<String>>(emptyList())
    val movieCategories: StateFlow<List<String>> = _movieCategories.asStateFlow()
    
    private val _seriesCategories = MutableStateFlow<List<String>>(emptyList())
    val seriesCategories: StateFlow<List<String>> = _seriesCategories.asStateFlow()
    
    private var currentProfileId: Long = 1L
    private var currentContentType: HomeContentType = HomeContentType.MOVIES
    
    // Cache for tab content to avoid reloading when switching tabs
    private val cachedCarouselRows = mutableMapOf<HomeContentType, List<CarouselRow>>()
    private val cachedHeroItems = mutableMapOf<HomeContentType, Pair<List<HeroItem>, Boolean>>()
    
    // In-memory session cache for "Recently Added" content
    private var cachedRecentlyAddedMovies: List<Movie>? = null
    private var cachedRecentlyAddedSeries: List<Series>? = null
    
    // Favorite categories state
    private val _favoriteMovieCategories = MutableStateFlow<Set<String>>(emptySet())
    val favoriteMovieCategories: StateFlow<Set<String>> = _favoriteMovieCategories.asStateFlow()
    
    private val _favoriteSeriesCategories = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSeriesCategories: StateFlow<Set<String>> = _favoriteSeriesCategories.asStateFlow()


    // Hero content caching
    private var cachedMovieHeroes: List<HeroItem>? = null
    private var lastMovieHeroFetchTime: Long = 0
    private var cachedSeriesHeroes: List<HeroItem>? = null
    private var lastSeriesHeroFetchTime: Long = 0
    private val HERO_CACHE_DURATION = 30 * 60 * 1000L // 30 minutes
    
    // Saved state before entering grid mode (for back navigation)
    private var savedPreGridState: HomeScreenState? = null

    init {
        // DISABLED: Cleanup was deleting progress before movies loaded
        // cleanupOrphanedProgress()
        loadContent(HomeContentType.MOVIES)
        loadAllCategories()
        loadFavoriteCategories()
        
        // Pre-load Series heroes in background so they're ready when switching tabs
        preloadSeriesHeroes()
    }
    
    /**
     * Pre-load series heroes in background during startup
     * This ensures heroes are ready when user switches to Serie TV tab
     */
    private fun preloadSeriesHeroes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val heroResult = loadHeroItems(ContentType.SERIES)
                if (heroResult != null) {
                    cachedHeroItems[HomeContentType.SERIES] = heroResult
                    Log.d("HomeViewModel", "Pre-loaded ${heroResult.first.size} series heroes")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error pre-loading series heroes", e)
            }
        }
    }
    
    /**
     * Remove WatchProgress entries that reference movies/series that no longer exist
     * This happens when playlists are reimported and content gets new IDs
     */
    private fun cleanupOrphanedProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allProgress = watchProgressDao.getAllProgress()
                var removedCount = 0
                
                for (progress in allProgress) {
                    val exists = when (progress.contentType) {
                        ContentType.MOVIE -> movieDao.getMovieById(progress.contentId) != null
                        ContentType.SERIES, ContentType.EPISODE -> {
                            val seriesId = progress.seriesId ?: progress.contentId
                            seriesDao.getSeriesById(seriesId) != null
                        }
                        ContentType.CHANNEL -> channelDao.getChannelById(progress.contentId) != null
                    }
                    
                    if (!exists) {
                        watchProgressDao.delete(progress)
                        removedCount++
                    }
                }
                
                if (removedCount > 0) {
                    Log.d("HomeViewModel", "Cleaned up $removedCount orphaned WatchProgress entries")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error cleaning orphaned progress: ${e.message}")
            }
        }
    }
    
    /**
     * Force refresh content after returning from player
     * Clears cached data and reloads to reflect watch progress updates
     */
    fun forceRefresh() {
        Log.d("HomeViewModel", "Force refreshing content for $currentContentType")
        cachedCarouselRows.clear()
        cachedHeroItems.clear()
        loadContent(currentContentType)
    }
    
    /**
     * Mark content as watched (remove from "Continue Watching")
     * Deletes the WatchProgress entry for the given hero item and refreshes the UI
     * 
     * Note: For series, WatchProgress stores the episode with contentType=EPISODE 
     * and seriesId pointing to the series. So we need to delete by seriesId for series content.
     */
    fun markAsWatched(hero: HeroItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (hero.contentType == ContentType.MOVIE.name) {
                    // For movies: delete by contentType=MOVIE and contentId=movie.id
                    watchProgressDao.deleteProgress(currentProfileId, ContentType.MOVIE, hero.id)
                    Log.d("HomeViewModel", "Marked as watched (movie): ${hero.title} (id=${hero.id})")
                } else {
                    // For series: WatchProgress is stored with contentType=EPISODE and seriesId=series.id
                    // So we need to delete by seriesId
                    watchProgressDao.deleteProgressBySeriesId(currentProfileId, hero.id)
                    Log.d("HomeViewModel", "Marked as watched (series): ${hero.title} (seriesId=${hero.id})")
                }
                
                // Force refresh to update the UI
                withContext(Dispatchers.Main) {
                    forceRefresh()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error marking as watched: ${hero.title}", e)
            }
        }
    }
    
    /**
     * Load all categories for sidebar (filtered) with counts
     */
    private fun loadAllCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load and filter movie categories with counts
                val allMovieCategories = movieDao.getCategoriesList()
                val filteredMovies = ContentFilters.filterMovieCategories(allMovieCategories)
                val movieCategoriesWithCounts = filteredMovies.map { cat ->
                    val count = movieDao.getMovieCountByCategory(cat)
                    "$cat ($count)"
                }
                val totalMovies = movieDao.getAllMoviesCount()
                // Add "Tutti i Film" + "Aggiunti di recente" + "Film Popolari" + rest of categories
                _movieCategories.value = listOf(
                    "Tutti i Film ($totalMovies)",
                    "Aggiunti di recente",
                    "Film Popolari"
                ) + movieCategoriesWithCounts
                
                // Load and filter series categories with counts
                val allSeriesCategories = seriesDao.getCategoriesList()
                val filteredSeries = ContentFilters.filterSeriesCategories(allSeriesCategories)
                val seriesCategoriesWithCounts = filteredSeries.map { cat ->
                    val count = seriesDao.getSeriesCountByCategory(cat)
                    "$cat ($count)"
                }
                val totalSeries = seriesDao.getAllSeriesCount()
                // Add "Tutte le Serie" + "Aggiunti di recente" + "Serie Popolari" + rest of categories
                _seriesCategories.value = listOf(
                    "Tutte le Serie ($totalSeries)",
                    "Aggiunti di recente",
                    "Serie Popolari"
                ) + seriesCategoriesWithCounts
            } catch (e: Exception) {
                // Keep empty on error
            }
        }
    }

    /**
     * Load content based on content type (tab)
     * Uses cache for MOVIES and SERIES to avoid reloading when switching tabs
     */
    fun loadContent(contentType: HomeContentType = currentContentType) {
        currentContentType = contentType
        viewModelScope.launch {
            try {
                currentProfileId = userPreferences.getCurrentProfileId() ?: 1L
            
            // Check if we have cached data for this content type (only for MOVIES and SERIES)
            val cachedRows = cachedCarouselRows[contentType]
            val cachedHero = cachedHeroItems[contentType]
            
            if (cachedRows != null && (contentType == HomeContentType.MOVIES || contentType == HomeContentType.SERIES)) {
                // Use cached data - instant switch without loading
                Log.d("HomeViewModel", "Using cached content for $contentType, heroItems=${cachedHero?.first?.size ?: 0}")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        carouselRows = cachedRows,
                        heroItem = cachedRows.flatMap { r -> r.items }.firstOrNull(),
                        isGridMode = false,
                        selectedCategory = null,
                        isListsTab = false,
                        isFavoritesTab = false,
                        isHistoryTab = false,  // Reset history tab flag
                        heroItems = cachedHero?.first ?: emptyList(),
                        currentHeroIndex = 0,
                        isContinueWatchingHero = cachedHero?.second ?: false
                    )
                }
                return@launch
            }
            
            // No cache - load fresh content (fast from SQLite, no spinner needed)
            
            val rows = mutableListOf<CarouselRow>()
            
            // Run independent tasks in parallel
            val heroDeferred = async(Dispatchers.IO) {
                try {
                     when (contentType) {
                        HomeContentType.MOVIES -> loadHeroItems(ContentType.MOVIE)
                        HomeContentType.SERIES -> loadHeroItems(ContentType.SERIES)
                        else -> null
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error loading heroes for $contentType", e)
                    null
                }
            }
            
            val contentDeferred = async(Dispatchers.IO) {
                try {
                    val contentRows = mutableListOf<CarouselRow>()
                    when (contentType) {
                        HomeContentType.MOVIES -> loadMoviesContent(contentRows)
                        HomeContentType.SERIES -> loadSeriesContent(contentRows)
                        HomeContentType.FAVORITES -> loadFavoritesContent(contentRows)
                        HomeContentType.LISTS -> loadListsContent(contentRows)
                        HomeContentType.HISTORY -> loadHistoryContent(contentRows)
                    }
                    contentRows
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error loading rows for $contentType", e)
                    emptyList<CarouselRow>()
                }
            }
            
            // Wait for both results
            val heroResult = heroDeferred.await()
            val loadedRows = contentDeferred.await()
            rows.addAll(loadedRows)
            
            // Cache the loaded data for MOVIES and SERIES
            if (contentType == HomeContentType.MOVIES || contentType == HomeContentType.SERIES) {
                cachedCarouselRows[contentType] = rows.toList()
                if (heroResult != null) {
                    cachedHeroItems[contentType] = heroResult
                }
                Log.d("HomeViewModel", "Cached content for $contentType: ${rows.size} rows, ${heroResult?.first?.size ?: 0} heroes")
            }
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    carouselRows = rows,
                    heroItem = rows.flatMap { r -> r.items }.firstOrNull(),
                    isGridMode = false,  // Reset grid mode when loading tabs
                    selectedCategory = null,
                    isListsTab = contentType == HomeContentType.LISTS,

                    isFavoritesTab = contentType == HomeContentType.FAVORITES,
                    isHistoryTab = contentType == HomeContentType.HISTORY,
                    // Hero state
                    heroItems = heroResult?.first ?: emptyList(),
                    currentHeroIndex = 0,
                    isContinueWatchingHero = heroResult?.second ?: false
                )
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "CRITICAL ERROR in loadContent", e)
             _uiState.update { it.copy(isLoading = false) }
        }
    }
    }

    
    /**
     * Navigate to next hero
     */
    fun nextHero() {
        _uiState.update { state ->
            val newIndex = if (state.heroItems.isNotEmpty()) {
                (state.currentHeroIndex + 1) % state.heroItems.size
            } else 0
            state.copy(currentHeroIndex = newIndex)
        }
    }
    
    /**
     * Navigate to previous hero
     */
    fun prevHero() {
        _uiState.update { state ->
            val newIndex = if (state.heroItems.isNotEmpty()) {
                if (state.currentHeroIndex == 0) state.heroItems.size - 1
                else state.currentHeroIndex - 1
            } else 0
            state.copy(currentHeroIndex = newIndex)
        }
    }
    
    /**
     * Get a random movie or series for the "Random" button
     * Returns Pair(contentId, contentType) or null if no content available
     */
    suspend fun getRandomContent(): Pair<Long, String>? {
        return withContext(Dispatchers.IO) {
            try {
                // Get random from both movies and series based on current tab
                val randomMovie = movieDao.getRandomMovies(1).firstOrNull()
                val randomSeries = seriesDao.getRandomSeries(1).firstOrNull()
                
                // Pick one at random
                val choices = listOfNotNull(
                    randomMovie?.let { Pair(it.id, ContentType.MOVIE.name) },
                    randomSeries?.let { Pair(it.id, ContentType.SERIES.name) }
                )
                
                if (choices.isEmpty()) return@withContext null
                
                choices.random()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error getting random content", e)
                null
            }
        }
    }
    
    /**
     * Load favorite categories from database
     */
    private fun loadFavoriteCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val movieFavorites = favoriteCategoryDao.getFavoriteCategoriesByType(currentProfileId, "movies")
                _favoriteMovieCategories.value = movieFavorites.map { it.categoryName }.toSet()
                
                val seriesFavorites = favoriteCategoryDao.getFavoriteCategoriesByType(currentProfileId, "series")
                _favoriteSeriesCategories.value = seriesFavorites.map { it.categoryName }.toSet()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading favorite categories", e)
            }
        }
    }
    
    /**
     * Toggle favorite status for a category
     * @param categoryName The category name to toggle
     * @param isMovies true for movie category, false for series
     */
    fun toggleFavoriteCategory(categoryName: String, isMovies: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val categoryType = if (isMovies) "movies" else "series"
                
                // Strip the count suffix from category name (e.g., "4k UHD (377)" -> "4k UHD")
                val cleanCategoryName = categoryName
                    .replace(Regex("\\s*\\(\\d+\\)$"), "")  // Remove " (123)" at end
                    .trim()
                
                Log.d("HomeViewModel", "Toggling favorite: original='$categoryName', clean='$cleanCategoryName', type=$categoryType")
                
                val favoriteCategory = it.sandtv.app.data.database.entity.FavoriteCategory(
                    profileId = currentProfileId,
                    categoryType = categoryType,
                    categoryName = cleanCategoryName
                )
                
                val isNowFavorite = favoriteCategoryDao.toggleFavoriteCategory(favoriteCategory)
                
                // Update state with clean name
                if (isMovies) {
                    _favoriteMovieCategories.value = if (isNowFavorite) {
                        _favoriteMovieCategories.value + cleanCategoryName
                    } else {
                        _favoriteMovieCategories.value - cleanCategoryName
                    }
                } else {
                    _favoriteSeriesCategories.value = if (isNowFavorite) {
                        _favoriteSeriesCategories.value + cleanCategoryName
                    } else {
                        _favoriteSeriesCategories.value - cleanCategoryName
                    }
                }
                
                Log.d("HomeViewModel", "Category '$cleanCategoryName' (type=$categoryType, profileId=$currentProfileId) favorite status: $isNowFavorite")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error toggling favorite category", e)
            }
        }
    }
    
    /**
     * Load hero items - prioritize continue watching, fallback to popular carousels
     * Returns Pair(heroItems, isContinueWatching)
     */
    private suspend fun loadHeroItems(@Suppress("UNUSED_PARAMETER") filterType: ContentType): Pair<List<HeroItem>, Boolean>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("HomeViewModel", "loadHeroItems: Loading heroes for filterType=$filterType")
                
                // First try: Continue Watching (has priority)
                val allContinueWatching = watchProgressDao.getContinueWatching(currentProfileId, 10)
                Log.d("HomeViewModel", "loadHeroItems: profileId=$currentProfileId, allContinueWatching=${allContinueWatching.size}")
                
                val continueWatching = allContinueWatching.filter { progress ->
                    when (filterType) {
                        ContentType.MOVIE -> progress.contentType == ContentType.MOVIE
                        ContentType.SERIES -> progress.contentType in listOf(ContentType.SERIES, ContentType.EPISODE)
                        else -> false
                    }
                }
                Log.d("HomeViewModel", "loadHeroItems: filtered continueWatching=${continueWatching.size}")
                
                if (continueWatching.isNotEmpty()) {
                    // Build hero items from continue watching
                    val heroItems = continueWatching.take(5).mapNotNull { progress ->
                        buildHeroItem(progress, filterType)
                    }
                    if (heroItems.isNotEmpty()) {
                        Log.d("HomeViewModel", "loadHeroItems: built heroItems from continue watching=${heroItems.size}")
                        return@withContext Pair(heroItems, true)
                    } else {
                        Log.w("HomeViewModel", "loadHeroItems: Continue watching exists but all items failed to build, falling back to popular carousel")
                    }
                }
                
                // Second try (Series only): Recently completed episode → suggest next episode
                if (filterType == ContentType.SERIES) {
                    val recentAll = watchProgressDao.getRecentlyWatched(currentProfileId, 10)
                    val recentCompleted = recentAll.filter {
                        it.contentType in listOf(ContentType.SERIES, ContentType.EPISODE) && it.isCompleted
                    }
                    Log.d("HomeViewModel", "loadHeroItems: recentCompleted series=${recentCompleted.size}")
                    
                    for (progress in recentCompleted) {
                        val seriesId = progress.seriesId ?: continue
                        val season = progress.season ?: continue
                        val episode = progress.episode ?: continue
                        
                        // Check if all episodes of this series have been watched
                        val totalEpisodes = episodeDao.getCountBySeries(seriesId)
                        val allSeriesProgress = watchProgressDao.getRecentlyWatched(currentProfileId, 200)
                            .filter { it.seriesId == seriesId && it.isCompleted }
                            .distinctBy { Pair(it.season, it.episode) }
                        if (allSeriesProgress.size >= totalEpisodes && totalEpisodes > 0) {
                            Log.d("HomeViewModel", "loadHeroItems: Series $seriesId fully watched (${allSeriesProgress.size}/$totalEpisodes completed), skipping")
                            continue
                        }
                        
                        val nextEpisode = episodeDao.getNextEpisode(seriesId, season, episode) ?: continue
                        Log.d("HomeViewModel", "loadHeroItems: found next episode S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber} for seriesId=$seriesId")
                        
                        var series = seriesDao.getSeriesById(seriesId) ?: continue
                        
                        // Enrich series if needed
                        if (series.tmdbTrailerKey == null) {
                            try { series = tmdbService.enrichSeriesDetails(series) } catch (_: Exception) {}
                        }
                        
                        val heroItem = buildHeroItemFromSeries(
                            series,
                            resumeMinutes = null,
                            progressPercent = null,
                            resumeEpisodeSeason = nextEpisode.seasonNumber,
                            resumeEpisodeNumber = nextEpisode.episodeNumber
                        )
                        return@withContext Pair(listOf(heroItem), true)
                    }
                }
                
                // Fallback: Use popular carousels content (same source as "Film Popolari" / "Serie Popolari")
                Log.d("HomeViewModel", "loadHeroItems: No continue watching, using popular carousel for filterType=$filterType")
                
                // Check cache first
                val now = System.currentTimeMillis()
                val cachedHeroes = when (filterType) {
                    ContentType.MOVIE -> if (now - lastMovieHeroFetchTime < HERO_CACHE_DURATION) cachedMovieHeroes else null
                    ContentType.SERIES -> if (now - lastSeriesHeroFetchTime < HERO_CACHE_DURATION) cachedSeriesHeroes else null
                    else -> null
                }
                
                if (cachedHeroes != null && cachedHeroes.isNotEmpty()) {
                    Log.d("HomeViewModel", "loadHeroItems: Returning cached heroes for $filterType (${cachedHeroes.size} items)")
                    return@withContext Pair(cachedHeroes, false)
                }

                val heroItems = when (filterType) {
                    ContentType.MOVIE -> {
                        // Get from POPULAR movies carousel (same source as the carousel)
                        val popularMovies = loadPopularMovies()
                            ?.filter { movie ->
                                !ContentFilters.shouldExcludeMovieFromHero(movie.name, movie.category) &&
                                (movie.backdropUrl != null || movie.posterUrl != null)
                            } ?: emptyList()
                        
                        val candidateMovies = if (popularMovies.isNotEmpty()) {
                            Log.d("HomeViewModel", "loadHeroItems: Found ${popularMovies.size} popular movies for hero selection")
                            // Take 5 random from popular carousel
                            popularMovies.shuffled().take(5)
                        } else {
                            // Fallback to random if no popular available yet
                            Log.d("HomeViewModel", "loadHeroItems: No popular movies found, falling back to fully-enriched")
                            movieDao.getFullyEnrichedMovies(10).shuffled().take(5)
                        }

                        val finalHeroes = candidateMovies.map { movie -> 
                            // Ensure movie is enriched (has ratings and trailer) before building hero
                            val enrichedMovie = if (movie.omdbImdbRating == null || movie.tmdbTrailerKey == null) {
                                try {
                                    // 1. Ensure TMDB details (might be missing if only popular match or no trailer)
                                    val withTmdb = if (movie.tmdbId == null || movie.tmdbOverview == null || movie.tmdbTrailerKey == null) {
                                        try {
                                            tmdbService.enrichMovieDetails(movie)
                                        } catch (e: Exception) {
                                            movie
                                        }
                                    } else movie
                                    
                                    // 2. Fetch OMDB ratings
                                    val ratings = withTmdb.tmdbImdbId?.let { imdbId ->
                                        imdbRatingsRepository.getRatingsByImdbId(imdbId)
                                    } ?: imdbRatingsRepository.getRatingsByTitle(
                                        withTmdb.tmdbOriginalTitle ?: withTmdb.title,
                                        withTmdb.year
                                    )
                                    
                                    if (ratings != null) {
                                        val withRatings = withTmdb.copy(
                                            omdbImdbRating = ratings.getFormattedImdbRating(),
                                            omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                                            omdbMetacriticScore = ratings.metacriticScore,
                                            omdbAudienceScore = ratings.audienceScore,
                                            omdbLastFetchAt = System.currentTimeMillis()
                                        )
                                        movieDao.update(withRatings)
                                        withRatings
                                    } else withTmdb
                                } catch (e: Exception) {
                                    Log.e("HomeViewModel", "Error enrichment movie for hero: ${movie.name}", e)
                                    movie
                                }
                            } else movie
                            
                            buildHeroItemFromMovie(enrichedMovie) 
                        }
                        
                        if (finalHeroes.isNotEmpty()) {
                            cachedMovieHeroes = finalHeroes
                            lastMovieHeroFetchTime = System.currentTimeMillis()
                        }
                        finalHeroes
                    }
                    ContentType.SERIES -> {
                        // Get from POPULAR series carousel (same source as the carousel)
                        val popularSeries = loadPopularSeries()
                            ?.filter { series ->
                                !ContentFilters.shouldExcludeSeriesFromHero(series.name, series.category) &&
                                (series.backdropUrl != null || series.posterUrl != null)
                            } ?: emptyList()
                            
                        val candidateSeries = if (popularSeries.isNotEmpty()) {
                            Log.d("HomeViewModel", "loadHeroItems: Found ${popularSeries.size} popular series for hero selection")
                            // Take 5 random from popular carousel
                            popularSeries.shuffled().take(5)
                        } else {
                            // Fallback to random if no popular available yet
                            Log.d("HomeViewModel", "loadHeroItems: No popular series found, falling back to fully-enriched")
                            seriesDao.getFullyEnrichedSeries(10).shuffled().take(5)
                        }

                        val finalHeroes = candidateSeries.map { series -> 
                             // Ensure series is enriched (has ratings and trailer) before building hero
                            val enrichedSeries = if (series.omdbImdbRating == null || series.tmdbTrailerKey == null) {
                                try {
                                    // 1. Ensure TMDB details
                                    val withTmdb = if (series.tmdbId == null || series.tmdbOverview == null || series.tmdbTrailerKey == null) {
                                        try {
                                            tmdbService.enrichSeriesDetails(series)
                                        } catch (e: Exception) {
                                            series
                                        }
                                    } else series
                                    
                                    // 2. Fetch OMDB ratings
                                    val ratings = imdbRatingsRepository.getRatingsByTitle(
                                        withTmdb.tmdbOriginalName ?: withTmdb.title,
                                        withTmdb.year,
                                        "series"
                                    )
                                    
                                    if (ratings != null) {
                                        val withRatings = withTmdb.copy(
                                            omdbImdbRating = ratings.getFormattedImdbRating(),
                                            omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                                            omdbMetacriticScore = ratings.metacriticScore,
                                            omdbAudienceScore = ratings.audienceScore,
                                            omdbLastFetchAt = System.currentTimeMillis()
                                        )
                                        seriesDao.update(withRatings)
                                        withRatings
                                    } else withTmdb
                                } catch (e: Exception) {
                                    Log.e("HomeViewModel", "Error enrichment series for hero: ${series.name}", e)
                                    series
                                }
                            } else series

                            buildHeroItemFromSeries(enrichedSeries) 
                        }
                        
                        if (finalHeroes.isNotEmpty()) {
                            cachedSeriesHeroes = finalHeroes
                            lastSeriesHeroFetchTime = System.currentTimeMillis()
                        }
                        finalHeroes
                    }
                    else -> emptyList()
                }
                Log.d("HomeViewModel", "loadHeroItems: filtered heroItems=${heroItems.size}")
                
                Pair(heroItems, false)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading hero items: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Build HeroItem from WatchProgress (continue watching)
     */
    private suspend fun buildHeroItem(progress: WatchProgress, @Suppress("UNUSED_PARAMETER") filterType: ContentType): HeroItem? {
        val remainingMinutes = ((progress.duration - progress.position) / 60000).toInt().coerceAtLeast(1)
        val progressPercent = if (progress.duration > 0) progress.position.toFloat() / progress.duration.toFloat() else 0f
        
        Log.d("HomeViewModel", "buildHeroItem: contentType=${progress.contentType}, contentId=${progress.contentId}, seriesId=${progress.seriesId}")
        
        return when (progress.contentType) {
            ContentType.MOVIE -> {
                var movie = movieDao.getMovieById(progress.contentId)
                Log.d("HomeViewModel", "buildHeroItem: movie lookup for id=${progress.contentId}, found=${movie != null}")
                if (movie == null) return null
                
                // Ensure trailer is available
                if (movie!!.tmdbTrailerKey == null) {
                    try {
                        movie = tmdbService.enrichMovieDetails(movie!!)
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error enrichment movie for continue watching hero: ${movie?.name}", e)
                    }
                }
                
                buildHeroItemFromMovie(movie!!, remainingMinutes, progressPercent)
            }
            ContentType.SERIES, ContentType.EPISODE -> {
                val seriesId = progress.seriesId ?: progress.contentId
                var series = seriesDao.getSeriesById(seriesId)
                Log.d("HomeViewModel", "buildHeroItem: series lookup for id=$seriesId, found=${series != null}")
                if (series == null) return null
                
                // Ensure trailer is available
                if (series!!.tmdbTrailerKey == null) {
                    try {
                        series = tmdbService.enrichSeriesDetails(series!!)
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error enrichment series for continue watching hero: ${series?.name}", e)
                    }
                }
                
                // Pass season and episode info from WatchProgress
                buildHeroItemFromSeries(
                    series!!, 
                    remainingMinutes, 
                    progressPercent,
                    resumeEpisodeSeason = progress.season,
                    resumeEpisodeNumber = progress.episode
                )
            }
            else -> null
        }
    }
    
    /**
     * Build HeroItem from Movie entity
     * Loads plot from Xtream API if not available from TMDB
     */
    private suspend fun buildHeroItemFromMovie(
        movie: Movie,
        resumeMinutes: Int? = null,
        progressPercent: Float? = null
    ): HeroItem {
        // Format duration
        val durationStr = movie.tmdbRuntime?.let { mins ->
            if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        }
        
        // Try to get overview from TMDB first, if null try Xtream API
        var overview = movie.tmdbOverview
        var cast = movie.tmdbCast
        var genres = movie.tmdbGenres
        
        // If overview is still null, try to load from Xtream API (like DetailsActivity does)
        if (overview.isNullOrEmpty() && movie.xtreamStreamId != null) {
            try {
                val playlist = playlistDao.getPlaylistById(movie.playlistId)
                if (playlist?.type == "xtream" && playlist.username != null && playlist.password != null) {
                    val baseUrl = playlist.url.trimEnd('/') + "/"
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .build()
                    val api = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(OkHttpClient.Builder().build())
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                        .create(XtreamApiService::class.java)
                    
                    val vodInfo = api.getVodInfo(
                        username = playlist.username,
                        password = playlist.password,
                        vodId = movie.xtreamStreamId
                    )
                    
                    vodInfo.info?.let { info ->
                        overview = info.plot ?: overview
                        if (cast.isNullOrEmpty()) cast = info.cast
                        if (genres.isNullOrEmpty()) genres = info.genre
                    }
                    Log.d("HomeViewModel", "Loaded plot from Xtream for ${movie.name}: ${overview?.take(50)}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading Xtream VOD info for ${movie.name}", e)
            }
        }

        // Check if OMDB ratings need refresh (logic from DetailsActivity)
        var currentMovie = movie
        val hasAnyOmdbRating = movie.omdbImdbRating != null || 
            movie.omdbRottenTomatoesScore != null || 
            movie.omdbMetacriticScore != null
        val needsOmdbRefresh = movie.omdbLastFetchAt == null || 
            !hasAnyOmdbRating || 
            System.currentTimeMillis() - movie.omdbLastFetchAt!! > 7 * 24 * 60 * 60 * 1000 // 7 days cache

        if (needsOmdbRefresh) {
            try {
                Log.d("HomeViewModel", "Fetching missing OMDB ratings for hero movie: ${movie.name}")
                val ratings = imdbRatingsRepository.getRatingsWithFallbacks(
                    imdbId = movie.tmdbImdbId,
                    originalTitle = movie.name,
                    englishTitle = movie.tmdbOriginalTitle ?: movie.tmdbTitle,
                    year = movie.year,
                    type = "movie"
                )

                if (ratings != null) {
                    currentMovie = movie.copy(
                        omdbImdbRating = ratings.getFormattedImdbRating(),
                        omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                        omdbMetacriticScore = ratings.metacriticScore,
                        omdbAudienceScore = ratings.audienceScore,
                        omdbLastFetchAt = System.currentTimeMillis()
                    )
                    
                    // If OMDB didn't provide audience score OR critics score, try RT scraper
                    if (currentMovie.omdbAudienceScore == null || currentMovie.omdbRottenTomatoesScore == null) {
                        val searchTitle = currentMovie.tmdbOriginalTitle ?: currentMovie.tmdbTitle ?: movie.name
                        val rtScores = imdbRatingsRepository.fetchRtScores(
                            title = searchTitle,
                            year = currentMovie.year,
                            isMovie = true
                        )
                        if (rtScores != null) {
                             currentMovie = currentMovie.copy(
                                 omdbAudienceScore = currentMovie.omdbAudienceScore ?: rtScores.audienceScore,
                                 omdbRottenTomatoesScore = currentMovie.omdbRottenTomatoesScore ?: rtScores.criticsScore
                             )
                        }
                    }
                    
                    // Save to database asynchronously
                    viewModelScope.launch(Dispatchers.IO) {
                        movieDao.update(currentMovie)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching OMDB ratings for hero movie ${movie.name}", e)
            }
        } else if (currentMovie.omdbAudienceScore == null || currentMovie.omdbRottenTomatoesScore == null) {
            // Logic added: If ratings are fresh but RT scores are missing, try to fetch them specifically
             try {
                Log.d("HomeViewModel", "Hero movie has ratings but missing RT scores: ${movie.name}")
                val searchTitle = currentMovie.tmdbOriginalTitle ?: currentMovie.tmdbTitle ?: movie.name
                val rtScores = imdbRatingsRepository.fetchRtScores(
                    title = searchTitle,
                    year = currentMovie.year,
                    isMovie = true
                )
                if (rtScores != null) {
                    val newAudience = currentMovie.omdbAudienceScore ?: rtScores.audienceScore
                    val newCritics = currentMovie.omdbRottenTomatoesScore ?: rtScores.criticsScore
                    
                    if (newAudience != currentMovie.omdbAudienceScore || newCritics != currentMovie.omdbRottenTomatoesScore) {
                        Log.d("HomeViewModel", "Fetched missing RT scores: Audience=$newAudience%, Critics=$newCritics%")
                        currentMovie = currentMovie.copy(
                            omdbAudienceScore = newAudience,
                            omdbRottenTomatoesScore = newCritics
                        )
                        // Save to database asynchronously
                        viewModelScope.launch(Dispatchers.IO) {
                            movieDao.update(currentMovie)
                        }
                    }
                }
             } catch (e: Exception) {
                 Log.e("HomeViewModel", "Error fetching separate RT scores for ${movie.name}", e)
             }
        }
        
        return HeroItem(
            id = currentMovie.id,
            title = currentMovie.title,
            backdropUrl = currentMovie.backdropUrl ?: currentMovie.posterUrl,
            posterUrl = currentMovie.posterUrl,
            contentType = ContentType.MOVIE.name,
            overview = overview,
            cast = cast,
            imdbRating = currentMovie.omdbImdbRating,
            rottenTomatoesScore = currentMovie.omdbRottenTomatoesScore,
            audienceScore = currentMovie.omdbAudienceScore,
            metacriticScore = currentMovie.omdbMetacriticScore,
            tmdbRating = currentMovie.tmdbVoteAverage,
            resumeMinutes = resumeMinutes,
            progressPercent = progressPercent,
            year = currentMovie.year,
            duration = durationStr,
            genres = genres,
            totalDurationMinutes = currentMovie.tmdbRuntime,
            isFavorite = try {
                favoriteDao.isFavorite(currentProfileId, ContentType.MOVIE, currentMovie.id)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error checking favorite status for movie ${currentMovie.id}", e)
                false
            },
            trailerKey = currentMovie.tmdbTrailerKey
        )
    }

    
    /**
     * Build HeroItem from Series entity
     * Loads plot from Xtream API if not available from TMDB
     */
    private suspend fun buildHeroItemFromSeries(
        series: Series,
        resumeMinutes: Int? = null,
        progressPercent: Float? = null,
        resumeEpisodeSeason: Int? = null,
        resumeEpisodeNumber: Int? = null
    ): HeroItem {
        // Try to get overview from TMDB first, if null try Xtream API
        var overview = series.tmdbOverview
        var cast = series.tmdbCast
        var genres = series.tmdbGenres
        
        // If overview is still null, try to load from Xtream API (like DetailsActivity does)
        if (overview.isNullOrEmpty() && series.xtreamSeriesId != null) {
            try {
                val playlist = playlistDao.getPlaylistById(series.playlistId)
                if (playlist?.type == "xtream" && playlist.username != null && playlist.password != null) {
                    val baseUrl = playlist.url.trimEnd('/') + "/"
                    val moshi = com.squareup.moshi.Moshi.Builder()
                        .build()
                    val api = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(OkHttpClient.Builder().build())
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                        .create(XtreamApiService::class.java)
                    
                    val seriesInfo = api.getSeriesInfo(
                        username = playlist.username,
                        password = playlist.password,
                        seriesId = series.xtreamSeriesId
                    )
                    
                    seriesInfo.info?.let { info ->
                        overview = info.plot ?: overview
                        if (cast.isNullOrEmpty()) cast = info.cast
                        if (genres.isNullOrEmpty()) genres = info.genre
                    }
                    Log.d("HomeViewModel", "Loaded plot from Xtream for ${series.name}: ${overview?.take(50)}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading Xtream series info for ${series.name}", e)
            }
        }

        // Check if OMDB ratings need refresh (logic from DetailsActivity)
        var currentSeries = series
        val hasAnyOmdbRating = series.omdbImdbRating != null || 
            series.omdbRottenTomatoesScore != null || 
            series.omdbMetacriticScore != null
        val needsOmdbRefresh = series.omdbLastFetchAt == null || 
            !hasAnyOmdbRating || 
            System.currentTimeMillis() - series.omdbLastFetchAt!! > 7 * 24 * 60 * 60 * 1000 // 7 days cache

        if (needsOmdbRefresh) {
            try {
                Log.d("HomeViewModel", "Fetching missing OMDB ratings for hero series: ${series.name}")
                val ratings = imdbRatingsRepository.getRatingsWithFallbacks(
                    imdbId = null, // Can try to pass external IDs if available, but name lookup works too
                    originalTitle = series.name,
                    englishTitle = null, 
                    year = series.year,
                    type = "series"
                )

                if (ratings != null) {
                    currentSeries = series.copy(
                        omdbImdbRating = ratings.getFormattedImdbRating(),
                        omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                        omdbMetacriticScore = ratings.metacriticScore,
                        omdbAudienceScore = ratings.audienceScore,
                        omdbLastFetchAt = System.currentTimeMillis()
                    )
                    
                    // If OMDB didn't provide audience score OR critics score, try RT scraper
                    if (currentSeries.omdbAudienceScore == null || currentSeries.omdbRottenTomatoesScore == null) {
                        val rtScores = imdbRatingsRepository.fetchRtScores(
                            title = series.name,
                            year = currentSeries.year,
                            isMovie = false
                        )
                        if (rtScores != null) {
                             currentSeries = currentSeries.copy(
                                 omdbAudienceScore = currentSeries.omdbAudienceScore ?: rtScores.audienceScore,
                                 omdbRottenTomatoesScore = currentSeries.omdbRottenTomatoesScore ?: rtScores.criticsScore
                             )
                        }
                    }
                    
                    // Save to database asynchronously
                    viewModelScope.launch(Dispatchers.IO) {
                        seriesDao.update(currentSeries)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching OMDB ratings for hero series ${series.name}", e)
            }
        } else if (currentSeries.omdbAudienceScore == null || currentSeries.omdbRottenTomatoesScore == null) {
            // Logic added: If ratings are fresh but RT scores are missing, try to fetch them specifically
             try {
                Log.d("HomeViewModel", "Hero series has ratings but missing RT scores: ${series.name}")
                val rtScores = imdbRatingsRepository.fetchRtScores(
                    title = series.name,
                    year = currentSeries.year,
                    isMovie = false
                )
                if (rtScores != null) {
                    val newAudience = currentSeries.omdbAudienceScore ?: rtScores.audienceScore
                    val newCritics = currentSeries.omdbRottenTomatoesScore ?: rtScores.criticsScore
                    
                    if (newAudience != currentSeries.omdbAudienceScore || newCritics != currentSeries.omdbRottenTomatoesScore) {
                        Log.d("HomeViewModel", "Fetched missing RT scores for series: Audience=$newAudience%, Critics=$newCritics%")
                        currentSeries = currentSeries.copy(
                            omdbAudienceScore = newAudience,
                            omdbRottenTomatoesScore = newCritics
                        )
                        // Save to database asynchronously
                        viewModelScope.launch(Dispatchers.IO) {
                            seriesDao.update(currentSeries)
                        }
                    }
                }
             } catch (e: Exception) {
                 Log.e("HomeViewModel", "Error fetching separate RT scores for series ${series.name}", e)
             }
        }
        
        return HeroItem(
            id = currentSeries.id,
            title = currentSeries.title,
            backdropUrl = currentSeries.backdropUrl ?: currentSeries.posterUrl,
            posterUrl = currentSeries.posterUrl,
            contentType = ContentType.SERIES.name,
            overview = overview,
            cast = cast,
            imdbRating = currentSeries.omdbImdbRating,
            rottenTomatoesScore = currentSeries.omdbRottenTomatoesScore,
            audienceScore = currentSeries.omdbAudienceScore,
            metacriticScore = currentSeries.omdbMetacriticScore,
            tmdbRating = currentSeries.tmdbVoteAverage,
            resumeMinutes = resumeMinutes,
            progressPercent = progressPercent,
            year = currentSeries.year,
            duration = null,  // Series don't have single duration
            genres = genres,
            seasonCount = currentSeries.tmdbNumberOfSeasons ?: currentSeries.seasonCount.takeIf { it > 0 },
            isFavorite = try {
                favoriteDao.isFavorite(currentProfileId, ContentType.SERIES, currentSeries.id)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error checking favorite status for series ${currentSeries.id}", e)
                false
            },
            trailerKey = currentSeries.tmdbTrailerKey,
            resumeEpisodeSeason = resumeEpisodeSeason,
            resumeEpisodeNumber = resumeEpisodeNumber
        )
    }

    
    /**
     * Load content for HOME tab (all content)
     */
    private suspend fun loadAllContent(rows: MutableList<CarouselRow>) {
        // Load in parallel for speed
        val popularMoviesDeferred = viewModelScope.async { loadPopularMovies() }
        val popularSeriesDeferred = viewModelScope.async { loadPopularSeries() }
        val continueWatchingDeferred = viewModelScope.async { loadContinueWatching() }
        
        // Add continue watching first
        continueWatchingDeferred.await()?.let { watchStates ->
            if (watchStates.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = context.getString(R.string.continue_watching),
                    items = watchStates.mapNotNull { it.toCarouselItem() }
                ))
            }
        }
        
        // Add popular movies
        popularMoviesDeferred.await()?.let { movies ->
            if (movies.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = context.getString(R.string.popular_movies),
                    items = movies.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Add popular series
        popularSeriesDeferred.await()?.let { series ->
            if (series.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = context.getString(R.string.popular_series),
                    items = series.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Load category rows (filtered)
        loadFilteredCategoryRows(rows, includeMovies = true, includeSeries = true)
    }
    
    /**
     * Load content for MOVIES tab only
     */
    private suspend fun loadMoviesContent(rows: MutableList<CarouselRow>) {
        // Continue watching movies only
        val watchStates = loadContinueWatching()
        watchStates?.filter { it.contentType == ContentType.MOVIE }?.let { movieWatches ->
            if (movieWatches.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Continua a guardare",
                    items = movieWatches.mapNotNull { it.toCarouselItem() }
                ))
            }
        }
        
        // Recently watched movies (full watch history, including completed)
        loadRecentlyWatched(ContentType.MOVIE)?.let { recentMovies ->
            if (recentMovies.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Visti di recente",
                    items = recentMovies
                ))
            }
        }
        
        // Popular movies
        loadPopularMovies()?.let { movies ->
            if (movies.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Film popolari",
                    items = movies.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Recently added movies (filtered categories)
        loadRecentlyAddedMovies()?.let { movies ->
            if (movies.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Aggiunti di recente",
                    items = movies.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Movie category rows
        loadFilteredCategoryRows(rows, includeMovies = true, includeSeries = false)
    }
    

    
    /**
     * Load content for HISTORY tab
     * Divided into Movies, Series, Live
     */
    private suspend fun loadHistoryContent(rows: MutableList<CarouselRow>) {
        try {
            // Load comprehensive history (limit 100 items)
            val history = watchProgressDao.getRecentlyWatched(currentProfileId, 100)
            
            // 1. Movies History
            val movieHistory = history.filter { it.contentType == ContentType.MOVIE }
            if (movieHistory.isNotEmpty()) {
                val items = mutableListOf<CarouselItem>()
                for (progress in movieHistory) {
                    try {
                        val heroItem = buildHeroItem(progress, ContentType.MOVIE)
                        if (heroItem != null) {
                            items.add(heroItem.toCarouselItem())
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error building hero item for movie history: ${progress.contentId}", e)
                    }
                }
                
                if (items.isNotEmpty()) {
                    rows.add(CarouselRow(
                        title = "Film visti di recente",
                        items = items
                    ))
                }
            }
            
            // 2. Series History (deduplicate by seriesId to avoid showing same series multiple times)
            val seriesHistory = history.filter { it.contentType == ContentType.SERIES || it.contentType == ContentType.EPISODE }
            if (seriesHistory.isNotEmpty()) {
                val items = mutableListOf<CarouselItem>()
                val seenSeriesIds = mutableSetOf<Long>()
                
                for (progress in seriesHistory) {
                    try {
                        val seriesId = progress.seriesId ?: progress.contentId
                        if (seriesId in seenSeriesIds) continue
                        seenSeriesIds.add(seriesId)
                        
                        val heroItem = buildHeroItem(progress, ContentType.SERIES)
                        if (heroItem != null) {
                            items.add(heroItem.toCarouselItem())
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error building hero item for series history: ${progress.contentId}", e)
                    }
                }

                if (items.isNotEmpty()) {
                    rows.add(CarouselRow(
                        title = "Serie TV viste di recente",
                        items = items
                    ))
                }
            }
            
            // 3. Live History (Channels)
            val channelHistory = history.filter { it.contentType == ContentType.CHANNEL }
            if (channelHistory.isNotEmpty()) {
                val items = mutableListOf<CarouselItem>()
                for (progress in channelHistory) {
                    try {
                        val channel = channelDao.getChannelById(progress.contentId)
                        if (channel != null) {
                            items.add(CarouselItem(
                                id = channel.id,
                                title = channel.name,
                                posterUrl = channel.logoUrl,
                                backdropUrl = null,
                                contentType = ContentType.CHANNEL.name
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error loading channel for history: ${progress.contentId}", e)
                    }
                }
                
                if (items.isNotEmpty()) {
                    rows.add(CarouselRow(
                        title = "Canali visti di recente",
                        items = items
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error loading history content", e)
        }
    }

    /**
     * Load content for SERIES tab only
     */
    private suspend fun loadSeriesContent(rows: MutableList<CarouselRow>) {
        // Continue watching series only
        val watchStates = loadContinueWatching()
        watchStates?.filter { it.contentType == ContentType.SERIES }?.let { seriesWatches ->
            if (seriesWatches.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Continua a guardare",
                    items = seriesWatches.mapNotNull { it.toCarouselItem() }
                ))
            }
        }
        
        // Recently watched series (full watch history, including completed)
        loadRecentlyWatched(ContentType.SERIES)?.let { recentSeries ->
            if (recentSeries.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Visti di recente",
                    items = recentSeries
                ))
            }
        }
        
        // Popular series
        loadPopularSeries()?.let { series ->
            if (series.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Serie TV popolari",
                    items = series.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Recently added series (filtered categories)
        loadRecentlyAddedSeries()?.let { series ->
            if (series.isNotEmpty()) {
                rows.add(CarouselRow(
                    title = "Aggiunte di recente",
                    items = series.map { it.toCarouselItem() }
                ))
            }
        }
        
        // Series category rows
        loadFilteredCategoryRows(rows, includeMovies = false, includeSeries = true)
    }
    
    /**
     * Load content for FAVORITES tab
     */
    private suspend fun loadFavoritesContent(rows: MutableList<CarouselRow>) {
        withContext(Dispatchers.IO) {
            try {
                val favorites = favoriteDao.getFavoritesByProfileList(currentProfileId)
                
                // Collect category cards by type
                val movieCategoryCards = mutableListOf<CarouselItem>()
                val seriesCategoryCards = mutableListOf<CarouselItem>()
                val liveCategoryCards = mutableListOf<CarouselItem>()
                
                // --- Favorite Categories as Cards ---
                Log.d("HomeViewModel", "Loading favorite categories for profileId=$currentProfileId")
                
                // 1. Favorite Movie Categories -> CATEGORY_MOVIE cards
                val favMovieCats = favoriteCategoryDao.getFavoriteCategoriesByType(currentProfileId, "movies")
                Log.d("HomeViewModel", "Found ${favMovieCats.size} favorite movie categories: ${favMovieCats.map { it.categoryName }}")
                favMovieCats.forEach { cat ->
                    val movieCount = movieDao.getMoviesByCategoryList(cat.categoryName).size
                    if (movieCount > 0) {
                        movieCategoryCards.add(CarouselItem(
                            id = cat.categoryName.hashCode().toLong(),
                            title = cat.categoryName,
                            posterUrl = null,
                            backdropUrl = null,
                            contentType = "CATEGORY_MOVIE",
                            contentCount = movieCount
                        ))
                    }
                }
                
                // 2. Favorite Series Categories -> CATEGORY_SERIES cards
                val favSeriesCats = favoriteCategoryDao.getFavoriteCategoriesByType(currentProfileId, "series")
                favSeriesCats.forEach { cat ->
                    val seriesCount = seriesDao.getSeriesByCategoryList(cat.categoryName).size
                    if (seriesCount > 0) {
                        seriesCategoryCards.add(CarouselItem(
                            id = cat.categoryName.hashCode().toLong(),
                            title = cat.categoryName,
                            posterUrl = null,
                            backdropUrl = null,
                            contentType = "CATEGORY_SERIES",
                            contentCount = seriesCount
                        ))
                    }
                }
                
                // 3. Favorite Channel Categories -> CATEGORY_LIVE cards
                val favChannelCats = favoriteCategoryDao.getFavoriteCategoriesByType(currentProfileId, "channels")
                favChannelCats.forEach { cat ->
                    val channelCount = channelDao.getChannelsByCategoryList(cat.categoryName).size
                    if (channelCount > 0) {
                        liveCategoryCards.add(CarouselItem(
                            id = cat.categoryName.hashCode().toLong(),
                            title = cat.categoryName,
                            posterUrl = null,
                            backdropUrl = null,
                            contentType = "CATEGORY_LIVE",
                            contentCount = channelCount
                        ))
                    }
                }
                
                // Add "Categorie" section header if any category favorites exist
                val hasAnyCategoryFavorites = movieCategoryCards.isNotEmpty() || seriesCategoryCards.isNotEmpty() || liveCategoryCards.isNotEmpty()
                if (hasAnyCategoryFavorites) {
                    rows.add(CarouselRow(
                        title = "Categorie",
                        items = emptyList(),
                        showSeeAll = false,
                        isSectionHeader = true
                    ))
                    
                    // Add 3 separate carousels for each type
                    if (movieCategoryCards.isNotEmpty()) {
                        rows.add(CarouselRow(
                            title = "Categorie Film",
                            items = movieCategoryCards,
                            showSeeAll = false
                        ))
                    }
                    
                    if (seriesCategoryCards.isNotEmpty()) {
                        rows.add(CarouselRow(
                            title = "Categorie Serie",
                            items = seriesCategoryCards,
                            showSeeAll = false
                        ))
                    }
                    
                    if (liveCategoryCards.isNotEmpty()) {
                        rows.add(CarouselRow(
                            title = "Categorie Live",
                            items = liveCategoryCards,
                            showSeeAll = false
                        ))
                    }
                }
                
                // Now collect individual favorites by type
                val movieFavorites = favorites.filter { it.contentType == ContentType.MOVIE }
                val seriesFavorites = favorites.filter { it.contentType == ContentType.SERIES }
                val channelFavorites = favorites.filter { it.contentType == ContentType.CHANNEL }
                
                // Load movie details for favorites
                if (movieFavorites.isNotEmpty()) {
                    val movieItems = movieFavorites.mapNotNull { fav ->
                        movieDao.getMovieById(fav.contentId)?.toCarouselItem()
                    }
                    if (movieItems.isNotEmpty()) {
                        // Add "Film" section header
                        rows.add(CarouselRow(
                            title = "Film",
                            items = emptyList(),
                            showSeeAll = false,
                            isSectionHeader = true
                        ))
                        rows.add(CarouselRow(
                            title = "Film Preferiti",
                            items = movieItems
                        ))
                    }
                }
                
                // Load series details for favorites
                if (seriesFavorites.isNotEmpty()) {
                    val seriesItems = seriesFavorites.mapNotNull { fav ->
                        seriesDao.getSeriesById(fav.contentId)?.toCarouselItem()
                    }
                    if (seriesItems.isNotEmpty()) {
                        // Add "Serie TV" section header
                        rows.add(CarouselRow(
                            title = "Serie TV",
                            items = emptyList(),
                            showSeeAll = false,
                            isSectionHeader = true
                        ))
                        rows.add(CarouselRow(
                            title = "Serie TV Preferite",
                            items = seriesItems
                        ))
                    }
                }
                
                // Load channel details for favorites
                if (channelFavorites.isNotEmpty()) {
                    val channelItems = channelFavorites.mapNotNull { fav ->
                        channelDao.getChannelById(fav.contentId)?.let { 
                             CarouselItem(
                                id = it.id,
                                title = it.name,
                                posterUrl = it.logoUrl,
                                backdropUrl = null,
                                contentType = ContentType.CHANNEL.name
                            )
                        }
                    }
                    if (channelItems.isNotEmpty()) {
                        // Add "Canali" section header
                        rows.add(CarouselRow(
                            title = "Canali",
                            items = emptyList(),
                            showSeeAll = false,
                            isSectionHeader = true
                        ))
                        rows.add(CarouselRow(
                            title = "Canali Preferiti",
                            items = channelItems
                        ))
                    }
                }
                Unit // Explicit Unit to avoid if-expression error
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading favorites", e)
            }
        }
    }
    
    /**
     * Load content for LISTS tab
     */
    private suspend fun loadListsContent(rows: MutableList<CarouselRow>) {
        withContext(Dispatchers.IO) {
            try {
                val groups = customGroupDao.getGroupsForProfileList(currentProfileId)
                
                if (groups.isEmpty()) {
                    // No lists - UI will show empty state
                    return@withContext
                }
                
                // Load each group as a carousel row
                groups.forEach { group ->
                    val items = customGroupDao.getItemsForGroupList(group.id)
                    if (items.isNotEmpty()) {
                        val carouselItems = items.map { item ->
                            CarouselItem(
                                id = item.contentId,
                                title = item.title,
                                posterUrl = item.posterUrl,
                                contentType = item.contentType.name
                            )
                        }
                        rows.add(CarouselRow(
                            title = group.name,
                            items = carouselItems
                        ))
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    /**
     * Create a new custom list
     */
    fun createList(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val group = it.sandtv.app.data.database.entity.CustomGroup(
                        profileId = currentProfileId,
                        name = name
                    )
                    customGroupDao.insertGroup(group)
                    Log.d("HomeViewModel", "Created new list: $name")
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error creating list: ${e.message}")
                }
            }
        }
    }

    /**
     * Add hero item to default "Da guardare" list
     */
    fun addHeroToWatchLater(hero: HeroItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Find or create "Da guardare" list
                    val groups = customGroupDao.getGroupsForProfileList(currentProfileId)
                    var watchLaterGroup = groups.find { it.name.equals("Da guardare", ignoreCase = true) || it.name.equals("Watch Later", ignoreCase = true) }
                    
                    if (watchLaterGroup == null) {
                        val newGroup = CustomGroup(
                            profileId = currentProfileId,
                            name = "Da guardare"
                        )
                        customGroupDao.insertGroup(newGroup)
                        // Re-fetch to get the ID
                        watchLaterGroup = customGroupDao.getGroupsForProfileList(currentProfileId).find { it.name == "Da guardare" }
                    }
                    
                    if (watchLaterGroup != null) {
                        val contentType = if (hero.contentType == "MOVIE") ContentType.MOVIE else ContentType.SERIES
                        
                        // Check if already exists
                        val existing = customGroupDao.getItemsForGroupList(watchLaterGroup!!.id).find { 
                            it.contentId == hero.id && it.contentType == contentType 
                        }
                        
                        if (existing == null) {
                            val item = GroupItem(
                                groupId = watchLaterGroup!!.id,
                                contentId = hero.id,
                                contentType = contentType,
                                title = hero.title,
                                posterUrl = hero.posterUrl,
                                addedAt = System.currentTimeMillis()
                            )
                            customGroupDao.insertItem(item)
                            Log.d("HomeViewModel", "Added to Watch Later: ${hero.title}")
                        } else {
                            Log.d("HomeViewModel", "Item already in Watch Later: ${hero.title}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error adding to watch later", e)
                }
            }
        }
    }
    
    /**
     * Load popular movies from "Film Popolari" trending category
     * Category is populated weekly by LoadingActivity
     */
    private suspend fun loadPopularMovies(): List<Movie>? {
        return withContext(Dispatchers.IO) {
            try {
                // Load from trending category (populated weekly by LoadingActivity)
                val movies = movieDao.getByTrendingCategory("Film Popolari")
                
                if (movies.isEmpty()) {
                    // Start LoadingActivity to refresh if empty? Or just return empty.
                    // DO NOT fallback to random popular movies which might be unsafe
                    Log.w("HomeViewModel", "No trending movies found")
                    return@withContext emptyList()
                }
                
                Log.d("HomeViewModel", "Loaded ${movies.size} trending movies from category")
                movies.shuffled().take(10)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading popular movies", e)
                null
            }
        }
    }
    
    /**
     * Load popular series from "Serie Popolari" trending category
     * Category is populated weekly by LoadingActivity
     */
    private suspend fun loadPopularSeries(): List<Series>? {
        return withContext(Dispatchers.IO) {
            try {
                // Load from trending category (populated weekly by LoadingActivity)
                val series = seriesDao.getByTrendingCategory("Serie Popolari")
                
                if (series.isEmpty()) {
                    // DO NOT fallback to random popular series which might be unsafe
                    Log.w("HomeViewModel", "No trending series found")
                    return@withContext emptyList()
                }
                
                Log.d("HomeViewModel", "Loaded ${series.size} trending series from category")
                series.shuffled().take(10)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading popular series", e)
                null
            }
        }
    }
    
    private suspend fun loadContinueWatching(): List<ContinueWatchingData>? {
        return withContext(Dispatchers.IO) {
            try {
                // Load more items to account for duplicates that will be filtered out
                val progressList = watchProgressDao.getContinueWatching(currentProfileId, 30)
                Log.d("HomeViewModel", "WatchProgress items found: ${progressList.size}")
                
                progressList.mapNotNull { progress ->
                    // Enrich with title and poster from database
                    when (progress.contentType) {
                        ContentType.MOVIE -> {
                            val movie = movieDao.getMovieById(progress.contentId)
                            movie?.let {
                                ContinueWatchingData(
                                    contentId = progress.contentId,
                                    seriesId = null,  // Movies don't have seriesId
                                    contentType = ContentType.MOVIE,
                                    title = it.title,
                                    posterUrl = it.posterUrl,
                                    position = progress.position,
                                    duration = progress.duration,
                                    seasonNumber = null,
                                    episodeNumber = null
                                )
                            }
                        }
                        ContentType.SERIES, ContentType.EPISODE -> {
                            // Get the series ID (either from progress.seriesId or use contentId as fallback)
                            val resolvedSeriesId = progress.seriesId ?: progress.contentId
                            val series = seriesDao.getSeriesById(resolvedSeriesId)
                            series?.let {
                                ContinueWatchingData(
                                    contentId = progress.contentId,
                                    seriesId = resolvedSeriesId,  // Store series ID for deduplication and navigation
                                    contentType = ContentType.SERIES,
                                    title = it.title,
                                    posterUrl = it.posterUrl,
                                    position = progress.position,
                                    duration = progress.duration,
                                    seasonNumber = progress.season,
                                    episodeNumber = progress.episode
                                )
                            }
                        }
                        else -> null
                    }
                }
                // Deduplicate: for series, keep only the most recently watched episode (first in list since sorted by lastWatchedAt DESC)
                // Movies are deduplicated by contentId, series by seriesId
                .distinctBy { data ->
                    if (data.contentType == ContentType.SERIES && data.seriesId != null) {
                        "SERIES_${data.seriesId}"  // Group by series ID
                    } else {
                        "MOVIE_${data.contentId}"  // Movies by content ID
                    }
                }
                .take(10)  // Limit to 10 items after deduplication
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading continue watching: ${e.message}")
                null
            }
        }
    }

    /**
     * Load recently watched content (COMPLETED items only)
     * Shows content that was fully watched (or with <= 8 min remaining to exclude credits)
     */
    private suspend fun loadRecentlyWatched(filterType: ContentType? = null): List<CarouselItem>? {
        return withContext(Dispatchers.IO) {
            try {
                val watchHistory = watchProgressDao.getRecentlyWatched(currentProfileId, 50)
                Log.d("HomeViewModel", "Recently watched items found: ${watchHistory.size}")
                
                // 8 minutes in milliseconds (threshold for "completed" excluding credits)
                val completedThresholdMs = 8 * 60 * 1000L

                watchHistory
                    .filter { progress ->
                        // Only include COMPLETED content
                        // Completed = isCompleted flag OR remaining time <= 8 minutes
                        val remainingMs = progress.duration - progress.position
                        val isEffectivelyCompleted = progress.isCompleted || remainingMs <= completedThresholdMs
                        
                        if (!isEffectivelyCompleted) return@filter false
                        
                        // Filter by content type if specified
                        when (filterType) {
                            ContentType.MOVIE -> progress.contentType == ContentType.MOVIE
                            ContentType.SERIES -> progress.contentType in listOf(ContentType.SERIES, ContentType.EPISODE)
                            else -> true
                        }
                    }
                    .mapNotNull { progress ->
                        // Enrich with title and poster from database
                        when (progress.contentType) {
                            ContentType.MOVIE -> {
                                val movie = movieDao.getMovieById(progress.contentId)
                                movie?.let {
                                    CarouselItem(
                                        id = it.id,
                                        title = it.title,
                                        posterUrl = it.posterUrl,
                                        contentType = ContentType.MOVIE.name
                                    )
                                }
                            }
                            ContentType.SERIES, ContentType.EPISODE -> {
                                // Get the series (not the episode) for display
                                val seriesId = progress.seriesId ?: progress.contentId
                                val series = seriesDao.getSeriesById(seriesId)
                                series?.let {
                                    CarouselItem(
                                        id = it.id,
                                        title = it.title,
                                        posterUrl = it.posterUrl,
                                        contentType = ContentType.SERIES.name
                                    )
                                }
                            }
                            else -> null
                        }
                    }
                    .distinctBy { it.id to it.contentType } // Remove duplicates (same series from different episodes)
                    .take(15) // Limit items
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading recently watched: ${e.message}")
                null
            }
        }
    }

    /**
     * Helper data class for continue watching items
     */
    private data class ContinueWatchingData(
        val contentId: Long,
        val seriesId: Long?,  // For series, this is the parent series ID (used for navigation and deduplication)
        val contentType: ContentType,
        val title: String,
        val posterUrl: String?,
        val position: Long,
        val duration: Long,
        val seasonNumber: Int?,
        val episodeNumber: Int?
    )

    private fun ContinueWatchingData.toCarouselItem(): CarouselItem {
        val progressPercent = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
        val remaining = ((duration - position) / 60000).toInt()
        val episodeLabel = if (seasonNumber != null && episodeNumber != null) {
            "S$seasonNumber E$episodeNumber"
        } else null

        // For series, use seriesId for navigation so clicking opens the series details page
        // For movies, use contentId as usual
        val navigationId = seriesId ?: contentId

        return CarouselItem(
            id = navigationId,  // Use series ID for series, content ID for movies
            title = title,
            posterUrl = posterUrl,
            backdropUrl = null,
            contentType = contentType.name,
            progressPercent = progressPercent.coerceIn(0f, 1f),
            remainingMinutes = remaining.coerceAtLeast(0),
            episodeLabel = episodeLabel
        )
    }
    
    /**
     * Load recently added movies, excluding hidden categories
     * Uses session cache to avoid reloading
     */
    private suspend fun loadRecentlyAddedMovies(): List<Movie>? {
        // Return cache if available
        cachedRecentlyAddedMovies?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                // Filter for content added in the last 7 days
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                
                val movies = movieDao.getRecentlyAddedMovies(100)
                    .filter { movie ->
                        val category = movie.category ?: ""
                        val isRecent = movie.addedAt >= oneWeekAgo
                        !ContentFilters.shouldExcludeMovieCategory(category) && isRecent
                    }.take(15)
                // Cache result
                cachedRecentlyAddedMovies = movies
                movies
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Load recently added series, excluding hidden categories and names
     * Uses session cache to avoid reloading
     */
    private suspend fun loadRecentlyAddedSeries(): List<Series>? {
        // Return cache if available
        cachedRecentlyAddedSeries?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                // Filter for content added in the last 7 days
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                
                val series = seriesDao.getRecentlyAddedSeries(100)
                    .filter { series ->
                        val category = series.category ?: ""
                        val name = series.name
                        val isRecent = series.addedAt >= oneWeekAgo
                        !ContentFilters.shouldExcludeSeriesCategory(category) && 
                        !ContentFilters.isHiddenSeriesName(name) && 
                        isRecent
                    }.take(20)
                // Cache result
                cachedRecentlyAddedSeries = series
                series
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Load category carousel rows with filtering
     * Categories are shuffled ONCE at first load and cached for the app session
     */
    private suspend fun loadFilteredCategoryRows(
        rows: MutableList<CarouselRow>,
        includeMovies: Boolean,
        includeSeries: Boolean
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Movie categories (filtered and shuffled ONCE)
                if (includeMovies) {
                    // Only shuffle once at first load
                    if (cachedShuffledMovieCategories == null) {
                        val allMovieCategories = movieDao.getCategoriesList()
                        val filteredCategories = ContentFilters.filterMovieCategories(allMovieCategories)
                        cachedShuffledMovieCategories = filteredCategories.shuffled()
                    }
                    
                    val categoriesToShow = cachedShuffledMovieCategories!!.take(8)
                    
                    for (category in categoriesToShow) {
                        val movies = contentCache.getMoviesByCategory(category)
                            ?: movieDao.getMoviesByCategoryList(category).also {
                                contentCache.putMoviesByCategory(category, it)
                            }
                        
                        if (movies.isNotEmpty()) {
                            rows.add(CarouselRow(
                                title = ContentFilters.cleanCategoryTitle(category),
                                items = movies.shuffled().take(10).map { it.toCarouselItem() },
                                showSeeAll = true
                            ))
                        }
                    }
                }
                
                // Series categories (filtered and shuffled ONCE)
                if (includeSeries) {
                    // Only shuffle once at first load
                    if (cachedShuffledSeriesCategories == null) {
                        val allSeriesCategories = seriesDao.getCategoriesList()
                        val filteredCategories = ContentFilters.filterSeriesCategories(allSeriesCategories)
                        cachedShuffledSeriesCategories = filteredCategories.shuffled()
                    }
                    
                    val categoriesToShow = cachedShuffledSeriesCategories!!.take(8)
                    
                    for (category in categoriesToShow) {
                        val series = contentCache.getSeriesByCategory(category)
                            ?: seriesDao.getSeriesByCategoryList(category).also {
                                contentCache.putSeriesByCategory(category, it)
                            }
                        
                        if (series.isNotEmpty()) {
                            rows.add(CarouselRow(
                                title = ContentFilters.cleanCategoryTitle(category),
                                items = series.shuffled().take(10).map { it.toCarouselItem() },
                                showSeeAll = true
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    /**
     * Load content for a specific category (or all categories with filters)
     */
    fun loadCategoryContent(category: String, isMovies: Boolean) {
        viewModelScope.launch {
            // Save current state before entering grid mode (for back navigation)
            if (!_uiState.value.isGridMode) {
                savedPreGridState = _uiState.value
            }
            _uiState.update { it.copy(isLoading = true) }
            
            // Extract clean category name (remove count suffix like " (123)")
            val cleanCategory = category.replace(Regex("\\s*\\(\\d+\\)$"), "")
            
            // Get all available categories for the filter
            val allCategories = if (isMovies) {
                movieDao.getCategoriesList().filter { cat ->
                    !ContentFilters.shouldExcludeMovieCategory(cat)
                }
            } else {
                seriesDao.getCategoriesList().filter { cat ->
                    !ContentFilters.shouldExcludeSeriesCategory(cat)
                }
            }
            
            // Check if we're loading "all" content
        val isAllContent = if (isMovies) {
            cleanCategory.startsWith("Tutti i Film")
        } else {
            cleanCategory.startsWith("Tutte le Serie")
        }
        
        // Check special lists
        val isRecentlyAdded = cleanCategory.contains("Aggiunti di recente") || cleanCategory.contains("Aggiunte di recente")
        val isContinueWatching = cleanCategory.equals("Continua a guardare", ignoreCase = true)
        val isFavorites = cleanCategory.contains("Preferiti", ignoreCase = true)
        val isPopular = cleanCategory.contains("popolari", ignoreCase = true)
        
        Log.d("HomeViewModel", "loadCategoryContent: category='$category', clean='$cleanCategory', isMovies=$isMovies, isPopular=$isPopular")
        
        val items: List<CarouselItem> = if (isMovies) {
            when {
                isAllContent -> loadAllMovies()
                isRecentlyAdded -> loadRecentlyAddedMoviesForGrid()
                isContinueWatching -> {
                    // Load all continue watching movies
                    val progressList = watchProgressDao.getProgressByProfile(currentProfileId).first()
                        .filter { it.contentType == ContentType.MOVIE }
                    progressList.mapNotNull { buildHeroItem(it, ContentType.MOVIE)?.toCarouselItem() }
                }
                isFavorites -> {
                    val favs = favoriteDao.getFavoritesByType(currentProfileId, ContentType.MOVIE).first()
                    favs.mapNotNull { fav -> movieDao.getMovieById(fav.contentId)?.toCarouselItem() }
                }
                isPopular -> {
                    Log.d("HomeViewModel", "Loading POPULAR movies from trending category")
                     val result = movieDao.getByTrendingCategory("Film Popolari").map { it.toCarouselItem() }
                     Log.d("HomeViewModel", "Found ${result.size} popular movies")
                     result
                }
                else -> {
                    Log.d("HomeViewModel", "Loading GENERIC category: $cleanCategory")
                    movieDao.getMoviesByCategoryList(cleanCategory).map { it.toCarouselItem() }
                }
            }
        } else {
            when {
                isAllContent -> loadAllSeries()
                isRecentlyAdded -> loadRecentlyAddedSeriesForGrid()
                isContinueWatching -> {
                    // Load all continue watching series
                    val progressList = watchProgressDao.getProgressByProfile(currentProfileId).first()
                        .filter { it.contentType == ContentType.SERIES || it.contentType == ContentType.EPISODE }
                    progressList.mapNotNull { buildHeroItem(it, ContentType.SERIES)?.toCarouselItem() }
                }
                isFavorites -> {
                    val favs = favoriteDao.getFavoritesByType(currentProfileId, ContentType.SERIES).first()
                    favs.mapNotNull { fav -> seriesDao.getSeriesById(fav.contentId)?.toCarouselItem() }
                }
                isPopular -> {
                    seriesDao.getByTrendingCategory("Serie Popolari").map { it.toCarouselItem() }
                }
                else -> seriesDao.getSeriesByCategoryList(cleanCategory).map { it.toCarouselItem() }
            }
        }
        
        _uiState.update { 
            it.copy(
                isLoading = false,
                carouselRows = listOf(CarouselRow(title = cleanCategory, items = items, showSeeAll = false)),
                heroItem = items.firstOrNull(), // Use first item as hero backdrop in grid mode
                isGridMode = true,
                selectedCategory = cleanCategory,
                // Category filter fields
                availableCategories = if (isAllContent) allCategories else emptyList(),
                selectedCategoryFilters = if (isAllContent) allCategories.toSet() else emptySet(),  // All selected by default
                isMoviesGrid = isMovies
            )
        }
    }
}

    /**
     * Exit grid mode and restore the previous carousel state.
     * This preserves scroll position when navigating back from "Vedi tutti".
     */
    fun exitGridMode() {
        savedPreGridState?.let { savedState ->
            _uiState.update { savedState }
            savedPreGridState = null
        }
    }

    /**
 * Load content for a category trying to auto-detect if it's movies or series.
 * Useful for Favorites/Home/Search tabs where context is mixed.
 */
fun loadCategoryContentAutoDetect(category: String) {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val cleanCategory = category.replace(Regex("\\s*\\(\\d+\\)$"), "")
        
        // Special handling for special carousels
        val lowerCat = cleanCategory.lowercase()
        if (lowerCat.contains("popolari") || 
            lowerCat.contains("preferiti") || 
            lowerCat.contains("aggiunti di recente") || 
            lowerCat.contains("aggiunte di recente")) {
            
            val isMovies = lowerCat.contains("film") // "Film popolari", "Film Preferiti", "Film aggiunti..."
            val isSeries = lowerCat.contains("serie") // "Serie popolari", "Serie TV Preferite", ...
            
            if (isMovies) {
                loadCategoryContent(category, isMovies = true)
                return@launch
            } else if (isSeries) {
                loadCategoryContent(category, isMovies = false)
                return@launch
            }
            // If neither (e.g. just "Preferiti"?), fall through to DB check or default
        }
        
        // "Continua a guardare" - Try to determine based on content or default to mixed/movies
        if (lowerCat.equals("continua a guardare")) {
            // For now, default to movies, or we could support mixed if loadCategoryContent allows it
            // Let's rely on DB check below to see what we have
        }
        
        // Check if it has movies (on IO thread)
        withContext(Dispatchers.IO) {
            try {
                // For "Continua a guardare", check watch progress
                if (lowerCat.equals("continua a guardare")) {
                    val hasMovies = watchProgressDao.getProgressByProfile(currentProfileId).first().any { it.contentType == ContentType.MOVIE }
                    // Prioritize movies if present, otherwise series
                    loadCategoryContent(category, isMovies = hasMovies)
                    return@withContext
                }

                // Standard category check
                val movies = movieDao.getMoviesByCategoryList(cleanCategory)
                if (movies.isNotEmpty()) {
                    // Found movies! Load as movies
                    loadCategoryContent(category, isMovies = true)
                } else {
                    // No movies, try series (default fallback)
                    loadCategoryContent(category, isMovies = false)
                }
            } catch (e: Exception) {
                // Start error, fallback to series
                loadCategoryContent(category, isMovies = false)
            }
        }
    }
}
    
    /**
     * Toggle a category filter on/off
     */
    fun toggleCategoryFilter(category: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val newFilters = if (currentState.selectedCategoryFilters.contains(category)) {
                currentState.selectedCategoryFilters - category
            } else {
                currentState.selectedCategoryFilters + category
            }
            
            // Reload content with new filters
            applyFilters(newFilters, currentState.isMoviesGrid)
        }
    }
    
    /**
     * Select all categories
     */
    fun selectAllCategories() {
        viewModelScope.launch {
            val currentState = _uiState.value
            applyFilters(currentState.availableCategories.toSet(), currentState.isMoviesGrid)
        }
    }
    
    /**
     * Clear all category filters
     */
    fun clearCategoryFilters() {
        viewModelScope.launch {
            val currentState = _uiState.value
            applyFilters(emptySet(), currentState.isMoviesGrid)
        }
    }
    
    /**
     * Apply category filters and reload content
     */
    private suspend fun applyFilters(filters: Set<String>, isMovies: Boolean) {
        val items = if (filters.isEmpty()) {
            emptyList()
        } else if (isMovies) {
            withContext(Dispatchers.IO) {
                movieDao.getAllMoviesList().filter { movie ->
                    val category = movie.category ?: ""
                    filters.contains(category)
                }.map { it.toCarouselItem() }
            }
        } else {
            withContext(Dispatchers.IO) {
                seriesDao.getAllSeriesList().filter { series ->
                    val category = series.category ?: ""
                    filters.contains(category)
                }.map { it.toCarouselItem() }
            }
        }
        
        _uiState.update { state ->
            val title = if (isMovies) "Tutti i Film" else "Tutte le Serie TV"
            state.copy(
                carouselRows = listOf(CarouselRow(title = title, items = items, showSeeAll = false)),
                heroItem = items.firstOrNull(),
                selectedCategoryFilters = filters
            )
        }
    }
    
    private suspend fun loadAllMovies(): List<CarouselItem> {
        return withContext(Dispatchers.IO) {
            try {
                movieDao.getAllMoviesList().filter { movie ->
                    val category = movie.category ?: ""
                    !ContentFilters.shouldExcludeMovieCategory(category)
                }.map { it.toCarouselItem() }
                 .filter { it.title.isNotEmpty() } // Filter out ghost movies
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    private suspend fun loadAllSeries(): List<CarouselItem> {
        return withContext(Dispatchers.IO) {
            try {
                seriesDao.getAllSeriesList().filter { series ->
                    val category = series.category ?: ""
                    !ContentFilters.shouldExcludeSeriesCategory(category)
                }.map { it.toCarouselItem() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Load recently added movies for grid - ordered by playlistOrder (higher = added later in M3U)
     */
    private suspend fun loadRecentlyAddedMoviesForGrid(): List<CarouselItem> {
        return withContext(Dispatchers.IO) {
            try {
                movieDao.getAllMoviesList()
                    .filter { movie ->
                        val category = movie.category ?: ""
                        !ContentFilters.shouldExcludeMovieCategory(category)
                    }
                    .sortedByDescending { it.playlistOrder }  // Higher order = added later in playlist
                    .take(100)
                    .map { it.toCarouselItem() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Load recently added series for grid - ordered by playlistOrder (higher = added later in M3U)
     */
    private suspend fun loadRecentlyAddedSeriesForGrid(): List<CarouselItem> {
        return withContext(Dispatchers.IO) {
            try {
                seriesDao.getAllSeriesList()
                    .filter { series ->
                        val category = series.category ?: ""
                        !ContentFilters.shouldExcludeSeriesCategory(category)
                    }
                    .sortedByDescending { it.playlistOrder }  // Higher order = added later in playlist
                    .take(100)
                    .map { it.toCarouselItem() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    fun refreshContinueWatching() {
        viewModelScope.launch {
            val watchStates = loadContinueWatching()
            if (watchStates != null && watchStates.isNotEmpty()) {
                _uiState.update { state ->
                    val newRows = state.carouselRows.toMutableList()
                    val continueIndex = newRows.indexOfFirst { 
                        it.title.contains(context.getString(R.string.continue_watching)) ||
                        it.title.contains("Continua a guardare")
                    }
                    val newRow = CarouselRow(
                        title = "â–¶ï¸ " + context.getString(R.string.continue_watching),
                        items = watchStates.mapNotNull { it.toCarouselItem() }
                    )
                    if (continueIndex >= 0) {
                        newRows[continueIndex] = newRow
                    } else {
                        newRows.add(0, newRow)
                    }
                    state.copy(carouselRows = newRows)
                }
            }
        }
    }
    
    // Extension functions to convert entities to CarouselItem
    private fun Movie.toCarouselItem() = CarouselItem(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        contentType = "MOVIE",
        year = year ?: tmdbReleaseDate?.take(4)?.toIntOrNull(),
        rating = rating
    )
    
    private fun Series.toCarouselItem() = CarouselItem(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        contentType = "SERIES",
        year = year,
        rating = rating
    )
    
    private fun Channel.toCarouselItem() = CarouselItem(
        id = id,
        title = name,
        posterUrl = logoUrl,
        backdropUrl = null,
        contentType = "CHANNEL"
    )
    
    private fun WatchState.toCarouselItem(): CarouselItem? {
        val progressPercent = if (duration > 0) position.toFloat() / duration.toFloat() else progress
        val remaining = ((duration - position) / 60000).toInt()
        val episodeLabel = if (seasonNumber != null && episodeNumber != null) {
            "S$seasonNumber E$episodeNumber"
        } else null
        
        return CarouselItem(
            id = contentId,
            title = title ?: "Unknown",
            posterUrl = thumbnailUrl,
            backdropUrl = null,
            contentType = contentType.name,
            progressPercent = progressPercent.coerceIn(0f, 1f),
            remainingMinutes = remaining.coerceAtLeast(0),
            episodeLabel = episodeLabel
        )
    }

    private fun HeroItem.toCarouselItem() = CarouselItem(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        contentType = contentType,
        year = year,
        rating = tmdbRating ?: imdbRating?.toFloatOrNull(),
        // Continue Watching fields
        progressPercent = progressPercent,
        remainingMinutes = resumeMinutes,
        episodeLabel = null
    )

    fun toggleHeroFavorite(hero: HeroItem) {
        viewModelScope.launch {
            val contentType = if (hero.contentType == "MOVIE") ContentType.MOVIE else ContentType.SERIES
            val favorite = Favorite(
                profileId = currentProfileId,
                contentId = hero.id,
                contentType = contentType,
                title = hero.title,
                posterUrl = hero.posterUrl,
                addedAt = System.currentTimeMillis()
            )
            
            // Toggle in DB
            val isNowFavorite = favoriteDao.toggleFavorite(favorite)
            
            // Update UI state
            _uiState.update { state ->
                val updatedHeroes = state.heroItems.map { 
                    if (it.id == hero.id) it.copy(isFavorite = isNowFavorite) else it 
                }
                state.copy(heroItems = updatedHeroes)
            }
            
            // Also update the cache so switching tabs preserves the favorite state
            val heroContentType = if (hero.contentType == "MOVIE") HomeContentType.MOVIES else HomeContentType.SERIES
            cachedHeroItems[heroContentType]?.let { cached ->
                val updatedCachedHeroes = cached.first.map { 
                    if (it.id == hero.id) it.copy(isFavorite = isNowFavorite) else it 
                }
                cachedHeroItems[heroContentType] = Pair(updatedCachedHeroes, cached.second)
            }
        }
    }
}

