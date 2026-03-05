package it.sandtv.app.ui.details

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.data.api.XtreamApiService
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*
import it.sandtv.app.data.repository.ImdbRatingsRepository
import it.sandtv.app.data.repository.PlaylistRepository
import it.sandtv.app.data.tmdb.TMDBService
import it.sandtv.app.data.preferences.UserPreferences
import android.net.Uri
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.util.TitleCleaner
import it.sandtv.app.data.repository.DownloadContentManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject

/**
 * Details Activity - Shows movie/series/channel details
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class DetailsActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "DetailsActivity"
    }
    
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var favoriteDao: FavoriteDao
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var watchProgressDao: WatchProgressDao
    @Inject lateinit var customGroupDao: CustomGroupDao
    @Inject lateinit var imdbRatingsRepo: ImdbRatingsRepository
    @Inject lateinit var tmdbService: TMDBService
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var userPreferences: UserPreferences
    @androidx.media3.common.util.UnstableApi
    @Inject lateinit var downloadManager: DownloadContentManager
    
    private var contentId: Long = 0
    private var contentType: ContentType = ContentType.MOVIE
    private var streamUrl: String? = null
    private var contentTitle: String = ""
    private var currentSeriesId: Long = 0
    
    // Intent extras for instant rendering
    private var intentTitle: String = ""
    private var intentPosterUrl: String? = null
    private var intentBackdropUrl: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        contentId = intent.getLongExtra("content_id", 0)
        contentType = ContentType.valueOf(intent.getStringExtra("content_type") ?: "MOVIE")
        
        // Read extras for instant UI rendering (passed from carousel/hero)
        intentTitle = intent.getStringExtra("title") ?: ""
        intentPosterUrl = intent.getStringExtra("poster_url")
        intentBackdropUrl = intent.getStringExtra("backdrop_url")
        
        setContent {
            SandTVTheme {
                DetailsContent()
            }
        }
    }
    
    @Composable
    private fun DetailsContent() {
        // Track if content has been loaded at least once
        var hasLoadedOnce by remember { mutableStateOf(false) }
        
        // Netflix-style dissolve: entire page fades in once enriched data is ready
        val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (hasLoadedOnce) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 500,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            label = "detailFadeIn"
        )
        
        // Start with instant state from Intent data
        var state by remember { 
            mutableStateOf(DetailsState(
                title = intentTitle,
                posterUrl = intentPosterUrl,
                backdropUrl = intentBackdropUrl,
                contentType = contentType,
                isLoading = false
            )) 
        }
        
        // Load content on first composition (enriches the instant state)
        LaunchedEffect(contentId) {
            loadContent { newState ->
                state = newState
                hasLoadedOnce = true
                // Load custom lists after content loads
                loadCustomLists(newState) { lists, selectedIds ->
                    state = state.copy(customLists = lists, selectedListIds = selectedIds)
                }
            }
        }
        
        // Reload on resume (e.g., after returning from player)
        val lifecycleOwner = LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && hasLoadedOnce) {
                    // Reload to refresh episode progress
                    lifecycleScope.launch {
                        loadContent { newState ->
                            state = newState
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // Wrap DetailsScreen with fade-in alpha
        Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
        DetailsScreen(
            state = state,
            onBackClick = { finish() },
            onPlayClick = { 
                if (state.contentType == ContentType.SERIES) {
                    // If there's a next episode to watch (from nextEpisodeId), play that
                    val nextEpId = state.nextEpisodeId
                    if (nextEpId != null) {
                        // Look up in current season episodes first, then from DB
                        val episodeInSeason = state.episodes.find { it.id == nextEpId }
                        if (episodeInSeason != null) {
                            streamUrl = episodeInSeason.streamUrl
                            playContent(state, episodeInSeason)
                        } else {
                            // Episode is in a different season - look up from DAO
                            lifecycleScope.launch {
                                val episode = episodeDao.getEpisodeById(nextEpId)
                                if (episode != null) {
                                    streamUrl = episode.streamUrl
                                    playContent(state, episode)
                                }
                            }
                        }
                    } else if (state.resumeEpisodeSeason != null && state.resumeEpisodeNumber != null) {
                        // If resuming, play the episode being resumed
                        val resumeEp = state.episodes.find { 
                            it.seasonNumber == state.resumeEpisodeSeason && it.episodeNumber == state.resumeEpisodeNumber 
                        }
                        if (resumeEp != null) {
                            streamUrl = resumeEp.streamUrl
                            playContent(state, resumeEp)
                        } else {
                            playContent(state)
                        }
                    } else if (state.episodes.isNotEmpty()) {
                        // Fallback to first episode of current season
                        val firstEpisode = state.episodes.minByOrNull { it.episodeNumber }
                        if (firstEpisode != null) {
                            streamUrl = firstEpisode.streamUrl
                            playContent(state, firstEpisode)
                        } else {
                            playContent(state)
                        }
                    } else {
                        playContent(state)
                    }
                } else {
                    playContent(state)
                }
            },
            onFavoriteClick = { 
                toggleFavorite(state.isFavorite) { isFav ->
                    state = state.copy(isFavorite = isFav)
                }
            },
            onSeasonSelected = { season ->
                state = state.copy(selectedSeason = season)
                loadEpisodesForSeason(season) { episodes ->
                    state = state.copy(episodes = episodes)
                }
            },
            onEpisodeClick = { episode ->
                streamUrl = episode.streamUrl
                playContent(state, episode)
            },
            onTrailerClick = {
                state.trailerKey?.let { key -> playTrailer(key) }
            },
            // Custom lists callbacks
            onAddToList = { listId ->
                addToList(listId, state) { newListIds ->
                    state = state.copy(selectedListIds = newListIds)
                }
            },
            onRemoveFromList = { listId ->
                removeFromList(listId) { newListIds ->
                    state = state.copy(selectedListIds = newListIds)
                }
            },
            onCreateList = { name ->
                createList(name, state) { lists, listIds ->
                    state = state.copy(customLists = lists, selectedListIds = listIds)
                }
            },
            onRenameList = { listId, newName ->
                renameList(listId, newName) { lists ->
                    state = state.copy(customLists = lists)
                }
            },
            onMarkAsWatchedClick = {
                // Remove from Continue Watching
                lifecycleScope.launch {
                    watchProgressDao.deleteProgress(1L, contentType, contentId)
                    android.widget.Toast.makeText(
                        this@DetailsActivity, 
                        "Rimosso da Continua a guardare", 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    // Reload content to update UI (remove resume button)
                    loadContent { newState ->
                        state = newState
                    }
                }
            },
            // Download callbacks
            onDownloadClick = {
                if (contentType == ContentType.MOVIE) {
                    lifecycleScope.launch {
                        val success = downloadManager.downloadMovie(contentId)
                        if (success) {
                            state = state.copy(isDownloading = true, downloadProgress = 0)
                            android.widget.Toast.makeText(
                                this@DetailsActivity,
                                "Download avviato",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onDeleteDownloadClick = {
                if (contentType == ContentType.MOVIE) {
                    lifecycleScope.launch {
                        downloadManager.deleteDownload(ContentType.MOVIE, contentId)
                        state = state.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0)
                        android.widget.Toast.makeText(
                            this@DetailsActivity,
                            "Download eliminato",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDownloadEpisode = { episode ->
                // Set episode to downloading state
                val currentStates = state.episodeDownloadStates.toMutableMap()
                currentStates[episode.id] = EpisodeDownloadState(
                    isDownloaded = false,
                    isDownloading = true,
                    downloadProgress = 0
                )
                state = state.copy(episodeDownloadStates = currentStates)
                
                lifecycleScope.launch {
                    val success = downloadManager.downloadEpisode(episode.id)
                    if (success) {
                        android.widget.Toast.makeText(
                            this@DetailsActivity,
                            "Download avviato: Stagione ${episode.seasonNumber} Episodio ${episode.episodeNumber}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        
                        // Observe real-time download progress via StateFlow (updates immediately)
                        val cacheKey = "episode_${episode.id}"
                        downloadManager.downloadProgress.collect { progressMap ->
                            val progress = progressMap[cacheKey]
                            if (progress != null) {
                                val updatedStates = state.episodeDownloadStates.toMutableMap()
                                updatedStates[episode.id] = EpisodeDownloadState(
                                    isDownloaded = progress >= 100,
                                    isDownloading = progress < 100,
                                    downloadProgress = progress
                                )
                                state = state.copy(episodeDownloadStates = updatedStates)
                            } else {
                                // Download removed or completed — check DB for final state
                                val download = downloadManager.observeDownload(ContentType.EPISODE, episode.id)
                                    .first()
                                val updatedStates = state.episodeDownloadStates.toMutableMap()
                                if (download != null) {
                                    updatedStates[episode.id] = EpisodeDownloadState(
                                        isDownloaded = download.isComplete,
                                        isDownloading = !download.isComplete,
                                        downloadProgress = download.downloadProgress
                                    )
                                } else {
                                    updatedStates.remove(episode.id)
                                }
                                state = state.copy(episodeDownloadStates = updatedStates)
                            }
                        }
                    } else {
                        // Reset state on failure
                        val resetStates = state.episodeDownloadStates.toMutableMap()
                        resetStates.remove(episode.id)
                        state = state.copy(episodeDownloadStates = resetStates)
                    }
                }
            },
            onDownloadSeason = { seasonNumber ->
                lifecycleScope.launch {
                    val count = downloadManager.downloadSeason(contentId, seasonNumber)
                    android.widget.Toast.makeText(
                        this@DetailsActivity,
                        "Download avviato per $count episodi della stagione $seasonNumber",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        } // End Box fade-in wrapper
    }
    
    private fun loadContent(onStateUpdate: (DetailsState) -> Unit) {
        lifecycleScope.launch {
            when (contentType) {
                ContentType.MOVIE -> loadMovie(onStateUpdate)
                ContentType.SERIES, ContentType.EPISODE -> loadSeries(onStateUpdate)
                ContentType.CHANNEL -> loadChannel(onStateUpdate)
            }
        }
    }
    
    private suspend fun loadMovie(onStateUpdate: (DetailsState) -> Unit) {
        val movie = movieDao.getMovieById(contentId) ?: return
        
        Log.d(TAG, "Loading movie: ${movie.name}, xtreamStreamId=${movie.xtreamStreamId}")
        
        contentTitle = movie.name
        streamUrl = movie.streamUrl
        
        // Check favorite status
        val isFavorite = favoriteDao.getFavorite(1, ContentType.MOVIE, contentId) != null
        
        // Initialize state with basic info
        var overview = ""
        var cast: String? = null
        var director: String? = null
        var genre: String? = null
        var duration: String? = null
        
        // Try to load VOD info from Xtream API (this has plot, cast, director)
        val xtreamStreamId = movie.xtreamStreamId
        Log.d(TAG, "Xtream check: xtreamStreamId=$xtreamStreamId, playlistId=${movie.playlistId}")
        
        if (xtreamStreamId != null) {
            try {
                val playlist = playlistDao.getPlaylistById(movie.playlistId)
                Log.d(TAG, "Playlist: type=${playlist?.type}, username=${playlist?.username}, url=${playlist?.url}")
                
                if (playlist?.type == "xtream" && playlist.username != null && playlist.password != null) {
                    val baseUrl = playlist.url.trimEnd('/') + "/"
                    Log.d(TAG, "Calling Xtream getVodInfo with baseUrl=$baseUrl, vodId=$xtreamStreamId")
                    
                    // Use Moshi with KSP-generated adapters
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
                        vodId = xtreamStreamId
                    )
                    
                    Log.d(TAG, "Xtream VOD info: plot=${vodInfo.info?.plot?.take(50)}, cast=${vodInfo.info?.cast}, director=${vodInfo.info?.director}")
                    
                    // Use data from Xtream
                    vodInfo.info?.let { info ->
                        overview = info.plot ?: ""
                        cast = info.cast
                        director = info.director
                        genre = info.genre
                        duration = info.duration ?: info.durationSecs?.let { "${it / 60} min" }
                    }
                } else {
                    Log.d(TAG, "Playlist not valid for Xtream: type=${playlist?.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading VOD info from Xtream", e)
            }
        } else {
            Log.d(TAG, "xtreamStreamId is null, skipping Xtream API")
        }
        
        // Always enrich with TMDB to get the rating and fallback data
        val enrichedMovie = tmdbService.enrichMovieDetails(movie)
        val tmdbRating = enrichedMovie.tmdbVoteAverage
        
        // Use Xtream data if available, otherwise TMDB
        if (overview.isEmpty()) {
            overview = enrichedMovie.tmdbOverview ?: ""
        }
        cast = cast ?: enrichedMovie.tmdbCast
        director = director ?: enrichedMovie.tmdbDirector
        genre = genre ?: enrichedMovie.tmdbGenres
        duration = duration ?: enrichedMovie.tmdbRuntime?.let { "$it min" }
        
        var state = DetailsState(
            title = movie.name,  // Use original name from playlist (never modified)
            year = enrichedMovie.year?.toString() ?: "",
            overview = overview,
            genres = genre ?: "",
            duration = duration,
            director = director,
            cast = cast,
            posterUrl = movie.logoUrl ?: enrichedMovie.posterUrl,  // Playlist poster first, TMDB fallback
            backdropUrl = enrichedMovie.backdropUrl ?: movie.backdropUrl,  // TMDB backdrop first (better quality)
            contentType = ContentType.MOVIE,
            isFavorite = isFavorite,
            isLoading = false,
            tmdbRating = tmdbRating,
            // Use cached OMDB ratings immediately for instant display
            imdbRating = enrichedMovie.omdbImdbRating,
            rottenTomatoesScore = enrichedMovie.omdbRottenTomatoesScore,
            metacriticScore = enrichedMovie.omdbMetacriticScore,
            audienceScore = enrichedMovie.omdbAudienceScore,
            trailerKey = enrichedMovie.tmdbTrailerKey
        )
        
        // Load watch progress for resume button
        val watchProgress = watchProgressDao.getProgress(1L, ContentType.MOVIE, contentId)
        val resumeMinutes = watchProgress?.let { 
            val remaining = (it.duration - it.position) / 60000
            remaining.toInt().coerceAtLeast(1)
        }
        // Calculate progress fraction for progress bar
        val resumeProgress = watchProgress?.let {
            if (it.duration > 0) {
                (it.position.toFloat() / it.duration.toFloat()).coerceIn(0f, 1f)
            } else null
        }
        
        // Include resumeMinutes and resumeProgress in the state directly
        state = state.copy(resumeMinutes = resumeMinutes, resumeProgress = resumeProgress)
        
        // Check if content is downloaded
        val isDownloaded = downloadManager.isContentDownloaded(ContentType.MOVIE, contentId)
        state = state.copy(isDownloaded = isDownloaded)
        
        Log.d(TAG, "State created with cached ratings: imdb=${state.imdbRating}, rt=${state.rottenTomatoesScore}, mc=${state.metacriticScore}")
        Log.d(TAG, "TMDB rating: $tmdbRating, resumeMinutes: $resumeMinutes")
        
        onStateUpdate(state)
        
        // Check if OMDB ratings need refresh (null or older than 7 days, or all ratings still null)
        val hasAnyOmdbRating = enrichedMovie.omdbImdbRating != null || 
            enrichedMovie.omdbRottenTomatoesScore != null || 
            enrichedMovie.omdbMetacriticScore != null
        val needsOmdbRefresh = enrichedMovie.omdbLastFetchAt == null || 
            !hasAnyOmdbRating ||  // Force retry if all ratings are null (fallback logic improved)
            System.currentTimeMillis() - enrichedMovie.omdbLastFetchAt > 7 * 24 * 60 * 60 * 1000  // 7 days cache
        
        if (needsOmdbRefresh) {
            Log.d("ImdbRatings", "OMDB refresh needed! hasAnyRating=$hasAnyOmdbRating, lastFetch=${enrichedMovie.omdbLastFetchAt}")
            Log.d("ImdbRatings", "IMDB ID: ${enrichedMovie.tmdbImdbId}, English title: tmdbTitle=${enrichedMovie.tmdbTitle}, tmdbOriginalTitle=${enrichedMovie.tmdbOriginalTitle}")
            loadImdbRatings(
                title = movie.name, 
                year = movie.year, 
                englishTitle = enrichedMovie.tmdbOriginalTitle ?: enrichedMovie.tmdbTitle,
                imdbId = enrichedMovie.tmdbImdbId
            ) { ratings: ImdbRatingsRepository.RatingInfo? ->
                Log.d(TAG, "Fresh OMDB ratings: imdb=${ratings?.imdbRating}, rt=${ratings?.rottenTomatoesScore}, mc=${ratings?.metacriticScore}")
                
                // Update UI
                state = state.copy(
                    imdbRating = ratings?.getFormattedImdbRating() ?: state.imdbRating,
                    rottenTomatoesScore = ratings?.rottenTomatoesScore ?: state.rottenTomatoesScore,
                    metacriticScore = ratings?.metacriticScore ?: state.metacriticScore,
                    audienceScore = ratings?.audienceScore ?: state.audienceScore
                )
                onStateUpdate(state)
                
                // Save to database for next time (in background)
                if (ratings != null) {
                    lifecycleScope.launch {
                        movieDao.update(enrichedMovie.copy(
                            omdbImdbRating = ratings.getFormattedImdbRating(),
                            omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                            omdbMetacriticScore = ratings.metacriticScore,
                            omdbAudienceScore = ratings.audienceScore,
                            omdbLastFetchAt = System.currentTimeMillis()
                        ))
                        Log.d(TAG, "Saved OMDB ratings to database")
                    }
                }
            }
        } else {
            Log.d(TAG, "Using cached OMDB ratings (last fetch: ${enrichedMovie.omdbLastFetchAt})")
            
            // Even with cached data, if audienceScore (Popcornmeter) is missing, try to fetch it
            if (enrichedMovie.omdbAudienceScore == null) {
                Log.d(TAG, "Cached data missing audienceScore, fetching from RT...")
                lifecycleScope.launch {
                    val searchTitle = enrichedMovie.tmdbOriginalTitle ?: enrichedMovie.tmdbTitle ?: movie.name
                    val rtScores = imdbRatingsRepo.fetchRtScores(
                        title = searchTitle,
                        year = movie.year,
                        isMovie = true
                    )
                    if (rtScores != null) {
                        Log.d(TAG, "RT Scores fetched: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                        // Update state with any new scores found
                        state = state.copy(
                            audienceScore = state.audienceScore ?: rtScores.audienceScore,
                            rottenTomatoesScore = state.rottenTomatoesScore ?: rtScores.criticsScore
                        )
                        onStateUpdate(state)
                        
                        // Save to database
                        movieDao.update(enrichedMovie.copy(
                            omdbAudienceScore = enrichedMovie.omdbAudienceScore ?: rtScores.audienceScore,
                            omdbRottenTomatoesScore = enrichedMovie.omdbRottenTomatoesScore ?: rtScores.criticsScore
                        ))
                    }
                }
            }
        }
    }
    
    private suspend fun loadSeries(onStateUpdate: (DetailsState) -> Unit) {
        var series = seriesDao.getSeriesById(contentId) ?: return
        
        Log.d(TAG, "Loading series: ${series.name}, tmdbId=${series.tmdbId}")
        
        // Enrich with TMDB data if needed
        series = tmdbService.enrichSeriesDetails(series)
        
        Log.d(TAG, "After enrich series: tmdbId=${series.tmdbId}, overview=${series.tmdbOverview?.take(50)}, cast=${series.tmdbCast}")
        
        contentTitle = series.name
        currentSeriesId = series.id
        
        // Check favorite status
        val isFavorite = favoriteDao.getFavorite(1, ContentType.SERIES, contentId) != null
        
        // Load episodes on-demand
        val loadSuccess = playlistRepository.loadSeriesEpisodes(series.id)
        Log.d(TAG, "loadSeriesEpisodes result: $loadSuccess for series ${series.name} (id=${series.id}, xtreamId=${series.xtreamSeriesId})")
        
        // Get seasons
        val seasons = episodeDao.getSeasonNumbers(series.id)
        Log.d(TAG, "Seasons found: ${seasons.size} -> $seasons for series ${series.name}")
        
        // Load watch progress for all episodes FIRST to determine last watched season
        val allEpisodes = episodeDao.getEpisodesBySeriesList(series.id)
        val episodeProgressMap = mutableMapOf<Long, EpisodeProgress>()
        var lastWatchedEpisode: Episode? = null
        var lastWatchedProgress: it.sandtv.app.data.database.entity.WatchProgress? = null
        var nextEpisodeInfo: String? = null
        var nextEpisodeId: Long? = null
        var resumeMinutes: Int? = null
        var resumeProgress: Float? = null
        
        allEpisodes.forEach { ep ->
            val progress = watchProgressDao.getProgress(1L, ContentType.EPISODE, ep.id)
            if (progress != null && progress.duration > 0) {
                val progressPercent = (progress.position.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                val remainingMs = progress.duration - progress.position
                val remainingMin = (remainingMs / 60000).toInt().coerceAtLeast(1)
                // Consider completed if isCompleted flag OR remaining <= 7 minutes (credits threshold)
                val effectivelyCompleted = progress.isCompleted || remainingMs <= 7 * 60 * 1000
                
                episodeProgressMap[ep.id] = EpisodeProgress(
                    episodeId = ep.id,
                    progress = progressPercent,
                    remainingMinutes = remainingMin,
                    isCompleted = effectivelyCompleted
                )
                
                // Track the most recently watched episode
                if (lastWatchedProgress == null || progress.lastWatchedAt > lastWatchedProgress!!.lastWatchedAt) {
                    lastWatchedEpisode = ep
                    lastWatchedProgress = progress
                }
            }
        }
        
        // Determine resume state or next episode
        // Sort all episodes by season and episode number
        val sortedEpisodes = allEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        
        // Find the first episode that is NOT completed (the "next" to watch)
        val firstUnwatchedEp = sortedEpisodes.firstOrNull { ep ->
            val epProgress = episodeProgressMap[ep.id]
            epProgress == null || !epProgress.isCompleted  // not watched or not completed
        }
        
        // Check if the first unwatched has progress (in progress = resume)
        val firstUnwatchedProgress = firstUnwatchedEp?.let { episodeProgressMap[it.id] }
        
        if (firstUnwatchedProgress != null && firstUnwatchedProgress.progress > 0.01f) {
            // Episode is in progress - show resume
            resumeMinutes = firstUnwatchedProgress.remainingMinutes
            resumeProgress = firstUnwatchedProgress.progress
            lastWatchedEpisode = firstUnwatchedEp
        } else if (firstUnwatchedEp != null) {
            // There are completed episodes before this one -> show "next episode"
            val hasWatchedAny = sortedEpisodes.any { ep ->
                val epProgress = episodeProgressMap[ep.id]
                epProgress != null && epProgress.isCompleted
            }
            if (hasWatchedAny) {
                nextEpisodeInfo = "Riproduci S${firstUnwatchedEp.seasonNumber} E${firstUnwatchedEp.episodeNumber}"
                nextEpisodeId = firstUnwatchedEp.id
                // Set lastWatchedEpisode to point to the next episode's season
                lastWatchedEpisode = firstUnwatchedEp
            }
            // If no episodes watched at all, fall through to default "play first episode"
        }
        
        // Default to the season of the next/resume episode, or first season if no watch history
        val selectedSeason = lastWatchedEpisode?.seasonNumber 
            ?: seasons.firstOrNull() 
            ?: 1
        
        // Load episodes for the selected season
        val episodes = if (seasons.isNotEmpty()) {
            episodeDao.getEpisodesBySeasonList(series.id, selectedSeason)
        } else emptyList()
        
        Log.d(TAG, "Episodes found for season $selectedSeason: ${episodes.size}")
        
        // Calculate episode index to auto-scroll to (in-progress or next unwatched)
        val scrollToEpisodeIndex = if (lastWatchedEpisode != null && lastWatchedEpisode!!.seasonNumber == selectedSeason) {
            val sortedSeasonEpisodes = episodes.sortedBy { it.episodeNumber }
            sortedSeasonEpisodes.indexOfFirst { it.id == lastWatchedEpisode!!.id }.takeIf { it >= 0 }
        } else null
        Log.d(TAG, "Auto-scroll to episode index: $scrollToEpisodeIndex (target: ${lastWatchedEpisode?.let { "S${it.seasonNumber}E${it.episodeNumber}" }})")
        
        // Fetch downloaded episodes status
        val downloadedEpisodes = downloadManager.getDownloadedEpisodes(series.id)
        val episodeDownloadStates = downloadedEpisodes.associate { download ->
            download.contentId to EpisodeDownloadState(
                isDownloaded = download.isComplete,
                isDownloading = !download.isComplete,
                downloadProgress = download.downloadProgress
            )
        }
        
        Log.d(TAG, "Downloaded episodes found: ${downloadedEpisodes.size}")
        
        var state = DetailsState(
            title = series.name,
            year = series.year?.toString() ?: "",
            overview = series.tmdbOverview ?: series.plot ?: "",
            genres = series.tmdbGenres ?: series.genre ?: "",
            cast = series.tmdbCast,
            posterUrl = series.posterUrl,
            backdropUrl = series.backdropUrl,
            contentType = ContentType.SERIES,
            isFavorite = isFavorite,
            isLoading = false,
            tmdbRating = series.tmdbVoteAverage,
            seasons = seasons,
            selectedSeason = selectedSeason,
            episodes = episodes,
            episodeProgress = episodeProgressMap,
            episodeDownloadStates = episodeDownloadStates, // Pass the download states
            resumeMinutes = resumeMinutes,
            resumeProgress = resumeProgress,
            resumeEpisodeSeason = lastWatchedEpisode?.seasonNumber,
            resumeEpisodeNumber = lastWatchedEpisode?.episodeNumber,
            nextEpisodeInfo = nextEpisodeInfo,
            nextEpisodeId = nextEpisodeId,
            scrollToEpisodeIndex = scrollToEpisodeIndex,
            // Use cached OMDB ratings immediately for instant display
            imdbRating = series.omdbImdbRating,
            rottenTomatoesScore = series.omdbRottenTomatoesScore,
            metacriticScore = series.omdbMetacriticScore,
            audienceScore = series.omdbAudienceScore,
            trailerKey = series.tmdbTrailerKey
        )
        
        onStateUpdate(state)
        
        // Check if OMDB ratings need refresh (null or older than 24 hours)
        val lastFetch = series.omdbLastFetchAt
        val needsOmdbRefresh = lastFetch == null || 
            System.currentTimeMillis() - lastFetch > 24 * 60 * 60 * 1000
        
        if (needsOmdbRefresh) {
            loadImdbRatings(series.name, series.year) { ratings: ImdbRatingsRepository.RatingInfo? ->
                // Update UI
                state = state.copy(
                    imdbRating = ratings?.getFormattedImdbRating() ?: state.imdbRating,
                    rottenTomatoesScore = ratings?.rottenTomatoesScore ?: state.rottenTomatoesScore,
                    metacriticScore = ratings?.metacriticScore ?: state.metacriticScore,
                    audienceScore = ratings?.audienceScore ?: state.audienceScore
                )
                onStateUpdate(state)
                
                // Save to database for next time
                if (ratings != null) {
                    lifecycleScope.launch {
                        seriesDao.update(series.copy(
                            omdbImdbRating = ratings.getFormattedImdbRating(),
                            omdbRottenTomatoesScore = ratings.rottenTomatoesScore,
                            omdbMetacriticScore = ratings.metacriticScore,
                            omdbAudienceScore = ratings.audienceScore,
                            omdbLastFetchAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
        } else {
            // Even with cached data, if audienceScore (Popcornmeter) is missing, try to fetch it
            if (series.omdbAudienceScore == null) {
                Log.d(TAG, "Series cached data missing audienceScore, fetching from RT...")
                lifecycleScope.launch {
                    val searchTitle = series.tmdbOriginalName ?: series.tmdbName ?: series.name
                    val rtScores = imdbRatingsRepo.fetchRtScores(
                        title = searchTitle,
                        year = series.year,
                        isMovie = false
                    )
                    if (rtScores != null) {
                        Log.d(TAG, "RT scores fetched for series: Audience=${rtScores.audienceScore}%, Critics=${rtScores.criticsScore}%")
                        state = state.copy(
                            audienceScore = state.audienceScore ?: rtScores.audienceScore,
                            rottenTomatoesScore = state.rottenTomatoesScore ?: rtScores.criticsScore
                        )
                        onStateUpdate(state)
                        
                        // Save to database
                        seriesDao.update(series.copy(
                            omdbAudienceScore = series.omdbAudienceScore ?: rtScores.audienceScore,
                            omdbRottenTomatoesScore = series.omdbRottenTomatoesScore ?: rtScores.criticsScore
                        ))
                    }
                }
            }
        }
    }
    
    private suspend fun loadChannel(onStateUpdate: (DetailsState) -> Unit) {
        val channel = channelDao.getChannelById(contentId) ?: return
        
        contentTitle = channel.name
        streamUrl = channel.streamUrl
        
        // Check favorite status
        val isFavorite = favoriteDao.getFavorite(1, ContentType.CHANNEL, contentId) != null
        
        onStateUpdate(
            DetailsState(
                title = channel.name,
                year = channel.categoryName ?: "",
                posterUrl = channel.logoUrl,
                contentType = ContentType.CHANNEL,
                isFavorite = isFavorite,
                isLoading = false
            )
        )
    }
    
    private fun loadEpisodesForSeason(seasonNumber: Int, onComplete: (List<Episode>) -> Unit) {
        lifecycleScope.launch {
            val episodes = episodeDao.getEpisodesBySeasonList(currentSeriesId, seasonNumber)
            episodes.firstOrNull()?.let { streamUrl = it.streamUrl }
            onComplete(episodes)
        }
    }
    
    private fun loadImdbRatings(
        title: String, 
        year: Int?,
        englishTitle: String? = null,  // TMDB title (often English) for fallback
        imdbId: String? = null,  // IMDB ID from TMDB for direct lookup
        onComplete: (ImdbRatingsRepository.RatingInfo?) -> Unit
    ) {
        lifecycleScope.launch {
            // Use the new smart search with all fallback strategies
            var ratings = imdbRatingsRepo.getRatingsWithFallbacks(
                imdbId = imdbId,
                originalTitle = title,
                englishTitle = englishTitle,
                year = year,
                type = if (contentType == ContentType.SERIES) "series" else "movie"
            )
            
            // If OMDB didn't provide audience score or critics score, try RT scraping
            if (ratings?.audienceScore == null || ratings?.rottenTomatoesScore == null) {
                Log.d(TAG, "OMDB didn't provide complete RT scores, trying scraper...")
                val searchTitle = englishTitle ?: title
                val rtScores = imdbRatingsRepo.fetchRtScores(
                    title = searchTitle,
                    year = year,
                    isMovie = (contentType != ContentType.SERIES)
                )
                
                if (rtScores != null) {
                    val finalRating = ratings ?: ImdbRatingsRepository.RatingInfo(
                        imdbRating = null,
                        imdbVotes = null,
                        imdbId = imdbId,
                        rottenTomatoesScore = null,
                        metacriticScore = null,
                        audienceScore = null,
                        rated = null,
                        awards = null,
                        boxOffice = null
                    )
                    
                    ratings = finalRating.copy(
                        audienceScore = finalRating.audienceScore ?: rtScores.audienceScore,
                        rottenTomatoesScore = finalRating.rottenTomatoesScore ?: rtScores.criticsScore
                    )
                }
            }
            
            onComplete(ratings)
        }
    }

    
    private fun playContent(@Suppress("UNUSED_PARAMETER") state: DetailsState, episode: Episode? = null) {
        val url = episode?.streamUrl ?: streamUrl
        Log.d(TAG, "playContent called: episode=${episode?.name}, streamUrl=$url")
        
        if (url.isNullOrEmpty()) {
            Log.e(TAG, "Cannot play: stream URL is empty!")
            android.widget.Toast.makeText(this, "Errore: URL streaming mancante", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("content_id", episode?.id ?: contentId)
            putExtra("content_type", (episode?.let { ContentType.EPISODE } ?: contentType).name)
            putExtra("stream_url", url)
            // Title: series name + episode title
            // Priority: tmdbName > extract from playlist
            val displayTitle = episode?.let {
                val cleanEpTitle = TitleCleaner.getFormattedEpisodeTitle(
                    originalName = it.tmdbName ?: it.name,
                    episodeNumber = it.episodeNumber,
                    seriesName = contentTitle
                )
                
                // If the cleaner just returned "Episodio X", standard fallback
                if (cleanEpTitle == "Episodio ${it.episodeNumber}") {
                    contentTitle
                } else {
                    "$contentTitle - $cleanEpTitle"
                }
            } ?: contentTitle
            putExtra("title", displayTitle)
            episode?.let {
                putExtra("subtitle", "Stagione ${it.seasonNumber} - Episodio ${it.episodeNumber}")
                putExtra("series_id", it.seriesId)
                putExtra("season", it.seasonNumber)
                putExtra("episode", it.episodeNumber)
            }
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(intent, options.toBundle())
    }
    
    private fun toggleFavorite(currentlyFavorite: Boolean, onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            if (currentlyFavorite) {
                favoriteDao.removeFavorite(1, contentType, contentId)
            } else {
                val favorite = Favorite(
                    profileId = 1,
                    contentType = contentType,
                    contentId = contentId,
                    title = contentTitle
                )
                favoriteDao.insert(favorite)
            }
            onComplete(!currentlyFavorite)
        }
    }
    
    // ============ Custom Lists ============
    
    private fun loadCustomLists(@Suppress("UNUSED_PARAMETER") state: DetailsState, onUpdate: (List<CustomGroup>, Set<Long>) -> Unit) {
        lifecycleScope.launch {
            val profileId = 1L // TODO: Get from current profile
            val lists = customGroupDao.getGroupsForProfileList(profileId)
            
            // Find which lists contain this content
            val containingListIds = mutableSetOf<Long>()
            lists.forEach { list ->
                val items = customGroupDao.getItemsForGroupList(list.id)
                if (items.any { it.contentType == contentType && it.contentId == contentId }) {
                    containingListIds.add(list.id)
                }
            }
            
            onUpdate(lists, containingListIds)
        }
    }
    
    private fun addToList(listId: Long, state: DetailsState, onComplete: (Set<Long>) -> Unit) {
        lifecycleScope.launch {
            val item = GroupItem(
                groupId = listId,
                contentType = contentType,
                contentId = contentId,
                title = contentTitle,
                posterUrl = state.posterUrl
            )
            customGroupDao.insertItem(item)
            onComplete(state.selectedListIds + listId)
        }
    }
    
    private fun removeFromList(listId: Long, onComplete: (Set<Long>) -> Unit) {
        lifecycleScope.launch {
            val items = customGroupDao.getItemsForGroupList(listId)
            val itemToRemove = items.find { it.contentType == contentType && it.contentId == contentId }
            itemToRemove?.let { customGroupDao.deleteItem(it) }
            
            val profileId = 1L
            val lists = customGroupDao.getGroupsForProfileList(profileId)
            val containingListIds = mutableSetOf<Long>()
            lists.forEach { list ->
                val listItems = customGroupDao.getItemsForGroupList(list.id)
                if (listItems.any { it.contentType == contentType && it.contentId == contentId }) {
                    containingListIds.add(list.id)
                }
            }
            onComplete(containingListIds)
        }
    }
    
    private fun createList(name: String, state: DetailsState, onComplete: (List<CustomGroup>, Set<Long>) -> Unit) {
        lifecycleScope.launch {
            val profileId = 1L
            val group = CustomGroup(
                profileId = profileId,
                name = name
            )
            val newId = customGroupDao.insertGroup(group)
            
            // Add current content to the new list
            val item = GroupItem(
                groupId = newId,
                contentType = contentType,
                contentId = contentId,
                title = contentTitle,
                posterUrl = state.posterUrl
            )
            customGroupDao.insertItem(item)
            
            val lists = customGroupDao.getGroupsForProfileList(profileId)
            onComplete(lists, state.selectedListIds + newId)
        }
    }
    
    private fun renameList(listId: Long, newName: String, onComplete: (List<CustomGroup>) -> Unit) {
        lifecycleScope.launch {
            val group = customGroupDao.getGroupById(listId)
            group?.let {
                customGroupDao.updateGroup(it.copy(name = newName, updatedAt = System.currentTimeMillis()))
            }
            val profileId = 1L
            val lists = customGroupDao.getGroupsForProfileList(profileId)
            onComplete(lists)
        }
    }

        @Inject lateinit var trailerManager: it.sandtv.app.util.TrailerManager
    
    private fun playTrailer(trailerKey: String) {
        lifecycleScope.launch {
            trailerManager.openTrailer(this@DetailsActivity, trailerKey)
        }
    }
}
