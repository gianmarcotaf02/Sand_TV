package it.sandtv.app.data.repository

import android.util.Log
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*
import it.sandtv.app.data.parser.ContentNameParser
import it.sandtv.app.data.parser.M3UParser
import it.sandtv.app.data.parser.XtreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for playlist management
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val contentNameParser: ContentNameParser
) {
    companion object {
        private const val TAG = "PlaylistRepo"
    }
    
    private val httpClient = OkHttpClient()
    
    /**
     * Add M3U playlist by URL
     */
    suspend fun addM3UPlaylist(name: String, url: String): Long = withContext(Dispatchers.IO) {
        Log.d(TAG, "Adding M3U playlist: $name from $url")
        
        // Create playlist entity first to get ID
        val playlist = Playlist(
            name = name,
            url = url,
            type = "m3u",
            lastUpdated = System.currentTimeMillis()
        )
        
        val playlistId = playlistDao.insert(playlist)
        
        // Download and parse M3U content
        val content = downloadContent(url)
        val parseResult = m3uParser.parseContent(content, playlistId)
        
        // Save categories
        saveCategories(playlistId, parseResult)
        
        // Save content
        saveChannels(playlistId, parseResult.channels)
        saveMovies(playlistId, parseResult.movies)
        saveSeries(playlistId, parseResult.series)
        
        // Update counts
        playlistDao.updateCounts(
            playlistId, 
            parseResult.channels.size, 
            parseResult.movies.size, 
            parseResult.series.size
        )
        
        Log.d(TAG, "Playlist added: ${parseResult.channels.size} channels, ${parseResult.movies.size} movies, ${parseResult.series.size} series")
        
        playlistId
    }
    
    /**
     * Add Xtream playlist by credentials
     */
    suspend fun addXtreamPlaylist(
        name: String,
        server: String,
        username: String,
        password: String
    ): Long = withContext(Dispatchers.IO) {
        Log.d(TAG, "Adding Xtream playlist: $name from $server")
        
        // Normalize server URL
        val baseUrl = server.trimEnd('/')
        
        // Build Xtream API URLs
        val playerApiUrl = "$baseUrl/player_api.php?username=$username&password=$password"
        
        // Verify credentials
        val authResponse = downloadContent(playerApiUrl)
        if (authResponse.contains("\"auth\":0") || authResponse.contains("Unauthorized")) {
            throw Exception("Credenziali Xtream non valide")
        }
        
        // Create playlist
        val playlist = Playlist(
            name = name,
            url = baseUrl,
            type = "xtream",
            username = username,
            password = password,
            lastUpdated = System.currentTimeMillis()
        )
        
        val playlistId = playlistDao.insert(playlist)
        
        // Load content from Xtream API
        loadXtreamContent(playlistId, baseUrl, username, password)
        
        playlistId
    }
    
    /**
     * Refresh playlist content - preserves movie/series IDs to keep WatchProgress valid
     */
    suspend fun refreshPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
        
        // Clear channels and categories (these don't have WatchProgress)
        channelDao.deleteByPlaylist(playlistId)
        categoryDao.deleteByPlaylist(playlistId)
        
        // For movies and series, DON'T delete - use upsert to preserve IDs
        
        when (playlist.type) {
            "m3u" -> {
                val content = downloadContent(playlist.url)
                val result = m3uParser.parseContent(content, playlistId)
                saveCategories(playlistId, result)
                saveChannels(playlistId, result.channels)
                // For M3U, simple delete and recreate (no stable ID like xtreamStreamId)
                movieDao.deleteByPlaylist(playlistId)
                seriesDao.deleteByPlaylist(playlistId)
                saveMovies(playlistId, result.movies)
                saveSeries(playlistId, result.series)
            }
            "xtream" -> {
                // Smart refresh - preserves IDs for WatchProgress
                refreshXtreamContent(
                    playlistId,
                    playlist.url,
                    playlist.username ?: "",
                    playlist.password ?: ""
                )
            }
        }
        
        // Update timestamp
        playlistDao.updateLastUpdated(playlistId, System.currentTimeMillis())
    }
    
    /**
     * Delete playlist and all content
     */
    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteByPlaylist(playlistId)
        movieDao.deleteByPlaylist(playlistId)
        seriesDao.deleteByPlaylist(playlistId)
        episodeDao.deleteBySeries(playlistId) // Episodes don't have playlistId directly
        categoryDao.deleteByPlaylist(playlistId)
        playlistDao.deleteById(playlistId)
    }
    
    /**
 * Load episodes for a series on-demand from Xtream API
 * Called when user opens series details page
 */
suspend fun loadSeriesEpisodes(seriesId: Long): Boolean = withContext(Dispatchers.IO) {
    val series = seriesDao.getSeriesById(seriesId) ?: run {
        Log.w(TAG, "loadSeriesEpisodes: Series not found for id=$seriesId")
        return@withContext false
    }
    val xtreamSeriesId = series.xtreamSeriesId ?: run {
        Log.w(TAG, "loadSeriesEpisodes: xtreamSeriesId is null for ${series.name}")
        return@withContext false
    }
    
    // Check if episodes already loaded
    val existingCount = episodeDao.getCountBySeries(seriesId)
    if (existingCount > 0) {
        Log.d(TAG, "Episodes already loaded for series $seriesId ($existingCount episodes)")
        return@withContext true
    }
    
    val playlist = playlistDao.getPlaylistById(series.playlistId) ?: run {
        Log.w(TAG, "loadSeriesEpisodes: Playlist not found for playlistId=${series.playlistId}")
        return@withContext false
    }
    if (playlist.type != "xtream") {
        Log.w(TAG, "loadSeriesEpisodes: Playlist type is ${playlist.type}, not xtream")
        return@withContext false
    }
    
    val baseUrl = playlist.url.trimEnd('/')
    val username = playlist.username ?: run {
        Log.w(TAG, "loadSeriesEpisodes: username is null")
        return@withContext false
    }
    val password = playlist.password ?: run {
        Log.w(TAG, "loadSeriesEpisodes: password is null")
        return@withContext false
    }
    
    try {
        Log.d(TAG, "Loading episodes for series ${series.name} (xtreamId=$xtreamSeriesId)")
        
        val apiUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$xtreamSeriesId"
        Log.d(TAG, "API URL: $apiUrl")
        
        val response = downloadContent(apiUrl)
        Log.d(TAG, "API response length: ${response.length} chars")
        Log.d(TAG, "API response preview: ${response.take(500)}")
        
        // Parse JSON response
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val adapter = moshi.adapter(it.sandtv.app.data.api.XtreamSeriesInfo::class.java)
        val seriesInfo = adapter.fromJson(response)
        
        if (seriesInfo == null) {
            Log.w(TAG, "loadSeriesEpisodes: Failed to parse JSON response")
            return@withContext false
        }
        
        Log.d(TAG, "Parsed seriesInfo: episodes map has ${seriesInfo.episodes?.size ?: 0} seasons")
        
        // Extract episodes from all seasons
        val episodeEntities = mutableListOf<Episode>()
        
        seriesInfo.episodes?.forEach seasons@{ (seasonKey, episodeList) ->
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            Log.d(TAG, "Processing season $seasonNum with ${episodeList.size} episodes")
            
            episodeList.forEach episodes@{ ep ->
                val episodeNum = ep.episodeNum ?: run {
                    Log.w(TAG, "Skipping episode: episodeNum is null")
                    return@episodes
                }
                val episodeId = ep.id?.toIntOrNull() ?: run {
                    Log.w(TAG, "Skipping episode $episodeNum: id is null or not a number (ep.id=${ep.id})")
                    return@episodes
                }
                val extension = ep.containerExtension ?: "mp4"
                val streamUrl = "$baseUrl/series/$username/$password/$episodeId.$extension"
                
                Log.d(TAG, "Episode S${seasonNum}E$episodeNum: id=$episodeId, url=$streamUrl")
                
                episodeEntities.add(Episode(
                    seriesId = seriesId,
                    name = ep.title ?: "Episode $episodeNum",
                    streamUrl = streamUrl,
                    seasonNumber = seasonNum,
                    episodeNumber = episodeNum,
                    xtreamEpisodeId = episodeId,
                    containerExtension = extension,
                    tmdbStillPath = ep.info?.movieImage,
                    tmdbOverview = ep.info?.plot,
                    duration = ep.info?.durationSecs?.toLong()
                ))
            }
        }
        
        Log.d(TAG, "Total episodes parsed: ${episodeEntities.size}")
        
        if (episodeEntities.isNotEmpty()) {
            episodeDao.insertAll(episodeEntities)
            Log.d(TAG, "Saved ${episodeEntities.size} episodes for series ${series.name}")
        } else {
            Log.w(TAG, "No episodes found in API response for ${series.name}")
        }
        
        return@withContext episodeEntities.isNotEmpty()
        
    } catch (e: Exception) {
        Log.e(TAG, "Error loading episodes for series ${series.name}", e)
        return@withContext false
    }
}
    
    // ========== Private helpers ==========
    
    private suspend fun downloadContent(url: String): String {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            response.body?.string() ?: ""
        }
    }
    
    private suspend fun loadXtreamContent(
        playlistId: Long,
        baseUrl: String,
        username: String,
        password: String
    ) {
        val apiBase = "$baseUrl/player_api.php?username=$username&password=$password"
        
        try {
            // Load live categories and streams
            Log.d(TAG, "Loading live streams...")
            val liveCatsJson = downloadContent("$apiBase&action=get_live_categories")
            val liveStreamsJson = downloadContent("$apiBase&action=get_live_streams")
            
            val liveCategories = xtreamParser.parseLiveCategories(liveCatsJson)
            val liveStreams = xtreamParser.parseLiveStreams(liveStreamsJson)
            
            Log.d(TAG, "Parsed ${liveCategories.size} live categories, ${liveStreams.size} live streams")
            
            // Save live categories
            val liveCategoryEntities = liveCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = cat.name,
                    externalId = cat.id,
                    type = CategoryType.LIVE_TV
                )
            }
            categoryDao.insertAll(liveCategoryEntities)
            
            // Map category IDs to names for lookup
            val liveCategoryMap = liveCategories.associate { it.id to it.name }
            
            // Save live channels
            val channelEntities = liveStreams.map { stream ->
                Channel(
                    playlistId = playlistId,
                    name = stream.name,
                    streamUrl = "$baseUrl/live/$username/$password/${stream.id}.ts",
                    logoUrl = stream.logo,
                    category = liveCategoryMap[stream.categoryId] ?: "Uncategorized",
                    categoryId = stream.categoryId,
                    xtreamStreamId = stream.id,
                    xtreamEpgChannelId = stream.epgId,
                    hasCatchup = stream.hasArchive > 0
                )
            }
            channelDao.insertAll(channelEntities)
            
            // Load VOD (movies)
            Log.d(TAG, "Loading VOD streams...")
            val vodCatsJson = downloadContent("$apiBase&action=get_vod_categories")
            val vodStreamsJson = downloadContent("$apiBase&action=get_vod_streams")
            
            val vodCategories = xtreamParser.parseVodCategories(vodCatsJson)
            val vodStreams = xtreamParser.parseVodStreams(vodStreamsJson)
            
            Log.d(TAG, "Parsed ${vodCategories.size} VOD categories, ${vodStreams.size} VOD streams")
            
            // Save VOD categories (with normalized names - remove "Film" prefix)
            val vodCategoryEntities = vodCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = contentNameParser.normalizeMovieCategory(cat.name),
                    externalId = cat.id,
                    type = CategoryType.MOVIE
                )
            }
            categoryDao.insertAll(vodCategoryEntities)
            
            // Map uses normalized names too
            val vodCategoryMap = vodCategories.associate { it.id to contentNameParser.normalizeMovieCategory(it.name) }
            
            // Save movies - with playlistOrder based on added timestamp (or fallback to index)
            val movieEntities = vodStreams.mapIndexed { index, vod ->
                Movie(
                    playlistId = playlistId,
                    name = vod.name,
                    streamUrl = "$baseUrl/movie/$username/$password/${vod.id}.${vod.extension ?: "mp4"}",
                    logoUrl = vod.poster,
                    category = vodCategoryMap[vod.categoryId] ?: "Uncategorized",
                    categoryId = vod.categoryId,
                    xtreamStreamId = vod.id,
                    containerExtension = vod.extension,
                    year = vod.year?.toIntOrNull(),
                    playlistOrder = (vod.added ?: index.toLong()).toInt()  // Use added timestamp, fallback to index
                )
            }
            movieDao.insertAll(movieEntities)
            
            // Load series
            Log.d(TAG, "Loading series...")
            val seriesCatsJson = downloadContent("$apiBase&action=get_series_categories")
            val seriesListJson = downloadContent("$apiBase&action=get_series")
            
            val seriesCategories = xtreamParser.parseSeriesCategories(seriesCatsJson)
            val seriesList = xtreamParser.parseSeries(seriesListJson)
            
            Log.d(TAG, "Parsed ${seriesCategories.size} series categories, ${seriesList.size} series")
            
            // Save series categories (with normalized names - remove "Programmi" prefix)
            val seriesCategoryEntities = seriesCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = contentNameParser.normalizeSeriesCategory(cat.name),
                    externalId = cat.id,
                    type = CategoryType.SERIES
                )
            }
            categoryDao.insertAll(seriesCategoryEntities)
            
            // Map uses normalized names too
            val seriesCategoryMap = seriesCategories.associate { it.id to contentNameParser.normalizeSeriesCategory(it.name) }
            
            // Save series (without episodes for now - episodes loaded on demand)
            // Filter out test/placeholder series
            val filteredSeriesList = seriesList.filter { series -> 
                !series.name.equals("Test Serie", ignoreCase = true) &&
                !series.name.equals("Test Series", ignoreCase = true)
            }
            val seriesEntities = filteredSeriesList.mapIndexed { index, series ->
                Series(
                    playlistId = playlistId,
                    name = series.name,
                    logoUrl = series.poster,
                    category = seriesCategoryMap[series.categoryId] ?: "Uncategorized",
                    categoryId = series.categoryId,
                    xtreamSeriesId = series.id,
                    playlistOrder = (series.added ?: index.toLong()).toInt()  // Use added timestamp, fallback to index
                )
            }
            seriesDao.insertAll(seriesEntities)
            
            // Update playlist counts
            playlistDao.updateCounts(
                playlistId,
                channelEntities.size,
                movieEntities.size,
                seriesEntities.size
            )
            
            Log.d(TAG, "Xtream content loaded successfully: ${channelEntities.size} channels, ${movieEntities.size} movies, ${seriesEntities.size} series")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Xtream content", e)
            throw Exception("Errore nel caricamento dei contenuti Xtream: ${e.message}")
        }
    }
    
    /**
     * Refresh Xtream content - FAST VERSION using batch operations
     * Note: This deletes and recreates movies/series. WatchProgress uses xtreamStreamId for matching.
     */
    /**
     * Refresh Xtream content - SMART SYNC using diff strategy
     * Preserves existing IDs so WatchProgress remains valid
     */
    private suspend fun refreshXtreamContent(
        playlistId: Long,
        baseUrl: String,
        username: String,
        password: String
    ) {
        val apiBase = "$baseUrl/player_api.php?username=$username&password=$password"
        
        try {
            // === LIVE CHANNELS ===
            // Live channels don't have WatchProgress usually, so simple replacement is generally OK,
            // but for consistency we could improve. For now, sticking to replace for channels unless critical.
            // Actually, keep it simple for channels as user specifically mentioned "continue watching" 
            // which is VOD feature. Channels might be in favorites though.
            
            Log.d(TAG, "Refreshing live streams...")
            val liveCatsJson = downloadContent("$apiBase&action=get_live_categories")
            val liveStreamsJson = downloadContent("$apiBase&action=get_live_streams")
            
            val liveCategories = xtreamParser.parseLiveCategories(liveCatsJson)
            val liveStreams = xtreamParser.parseLiveStreams(liveStreamsJson)
            
            // For channels, we still use full replace for now as per previous implementation 
            // unless we want to preservefavorites ID. But favorites use content ID/Name usually.
            // Let's safe-replace categories first
            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.LIVE_TV)
            
            val liveCategoryEntities = liveCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = cat.name,
                    externalId = cat.id,
                    type = CategoryType.LIVE_TV
                )
            }
            categoryDao.insertAll(liveCategoryEntities)
            
            val liveCategoryMap = liveCategories.associate { it.id to it.name }
            
            // Safe replace channels
            channelDao.deleteByPlaylist(playlistId)
            val channelEntities = liveStreams.map { stream ->
                Channel(
                    playlistId = playlistId,
                    name = stream.name,
                    streamUrl = "$baseUrl/live/$username/$password/${stream.id}.ts",
                    logoUrl = stream.logo,
                    category = liveCategoryMap[stream.categoryId] ?: "Uncategorized",
                    categoryId = stream.categoryId,
                    xtreamStreamId = stream.id,
                    xtreamEpgChannelId = stream.epgId,
                    hasCatchup = stream.hasArchive > 0
                )
            }
            channelDao.insertAll(channelEntities)
            
            
            // === VOD MOVIES (CRITICAL FOR HISTORY) ===
            Log.d(TAG, "Refreshing VOD streams (smart sync)...")
            
            // 1. Fetch current movies in DB to map: xtreamId -> Movie
            // We use a helper query to get just what we need or load all
            // Since we're syncing, loading all for playlist is acceptable
            val currentMovies = movieDao.getAllMoviesList().filter { it.playlistId == playlistId }
            val currentMovieMap = currentMovies.associateBy { it.xtreamStreamId }
            
            // 2. Load new data from API
            val vodCatsJson = downloadContent("$apiBase&action=get_vod_categories")
            val vodStreamsJson = downloadContent("$apiBase&action=get_vod_streams")
            
            val vodCategories = xtreamParser.parseVodCategories(vodCatsJson)
            val vodStreams = xtreamParser.parseVodStreams(vodStreamsJson)
            
            // Update Categories (these are safe to replace as they link by Name usually or we can update)
            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.MOVIE)
            val vodCategoryEntities = vodCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = contentNameParser.normalizeMovieCategory(cat.name),
                    externalId = cat.id,
                    type = CategoryType.MOVIE
                )
            }
            categoryDao.insertAll(vodCategoryEntities)
            
            val vodCategoryMap = vodCategories.associate { it.id to contentNameParser.normalizeMovieCategory(it.name) }
            
            // 3. Diff & prepare batch operations
            val moviesToInsert = mutableListOf<Movie>()
            val moviesToUpdate = mutableListOf<Movie>()
            val moviesToDelete = mutableListOf<Movie>()
            
            // Track which existing IDs we've seen to detect deletions
            val seenXtreamIds = mutableSetOf<Int>()
            
            vodStreams.forEachIndexed { index, vod ->
                val xtreamId = vod.id
                if (xtreamId != null) {
                    seenXtreamIds.add(xtreamId)
                    
                    val existing = currentMovieMap[xtreamId]
                    val categoryName = vodCategoryMap[vod.categoryId] ?: "Uncategorized"
                    val streamUrl = "$baseUrl/movie/$username/$password/${vod.id}.${vod.extension ?: "mp4"}"
                    val playlistOrder = (vod.added ?: index.toLong()).toInt() // Use added timestamp
                    
                    if (existing != null) {
                        // Check if meaningful data changed, or just unconditional update
                        // Use copy to update fields but KEEP THE ID
                        val updated = existing.copy(
                            name = vod.name,
                            streamUrl = streamUrl,
                            logoUrl = vod.poster,
                            category = categoryName,
                            categoryId = vod.categoryId,
                            containerExtension = vod.extension,
                            year = vod.year?.toIntOrNull(),
                            playlistOrder = playlistOrder,
                            // Preserve TMDB/OMDB data automatically since we're copying 'existing'
                            tmdbLastFetchAt = existing.tmdbLastFetchAt, 
                            omdbLastFetchAt = existing.omdbLastFetchAt
                        )
                        moviesToUpdate.add(updated)
                    } else {
                        // New movie
                        moviesToInsert.add(Movie(
                            playlistId = playlistId,
                            name = vod.name,
                            streamUrl = streamUrl,
                            logoUrl = vod.poster,
                            category = categoryName,
                            categoryId = vod.categoryId,
                            xtreamStreamId = vod.id,
                            containerExtension = vod.extension,
                            year = vod.year?.toIntOrNull(),
                            playlistOrder = playlistOrder
                        ))
                    }
                }
            }
            
            // Find deleted movies
            currentMovies.forEach { movie ->
                if (movie.xtreamStreamId != null && !seenXtreamIds.contains(movie.xtreamStreamId)) {
                    moviesToDelete.add(movie)
                }
            }
            
            // 4. Batch Execute
            if (moviesToDelete.isNotEmpty()) {
                Log.d(TAG, "Deleting ${moviesToDelete.size} removed movies")
                movieDao.deleteList(moviesToDelete)
            }
            if (moviesToUpdate.isNotEmpty()) {
                Log.d(TAG, "Updating ${moviesToUpdate.size} existing movies")
                movieDao.updateList(moviesToUpdate)
            }
            if (moviesToInsert.isNotEmpty()) {
                Log.d(TAG, "Inserting ${moviesToInsert.size} new movies")
                movieDao.insertAll(moviesToInsert)
            }
            
            
            // === TV SERIES (CRITICAL FOR HISTORY) ===
            Log.d(TAG, "Refreshing Series (smart sync)...")
            
            // 1. Fetch current series
            val currentSeries = seriesDao.getAllSeriesList().filter { it.playlistId == playlistId }
            val currentSeriesMap = currentSeries.associateBy { it.xtreamSeriesId }
            
            // 2. Load new data
            val seriesCatsJson = downloadContent("$apiBase&action=get_series_categories")
            val seriesListJson = downloadContent("$apiBase&action=get_series")
            
            val seriesCategories = xtreamParser.parseSeriesCategories(seriesCatsJson)
            val seriesList = xtreamParser.parseSeries(seriesListJson)
            
            // Update Categories
            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.SERIES)
            val seriesCategoryEntities = seriesCategories.map { cat ->
                Category(
                    playlistId = playlistId,
                    name = contentNameParser.normalizeSeriesCategory(cat.name),
                    externalId = cat.id,
                    type = CategoryType.SERIES
                )
            }
            categoryDao.insertAll(seriesCategoryEntities)
            
            val seriesCategoryMap = seriesCategories.associate { it.id to contentNameParser.normalizeSeriesCategory(it.name) }
            
            // 3. Diff & prepare operations
            val seriesToInsert = mutableListOf<Series>()
            val seriesToUpdate = mutableListOf<Series>()
            val seriesToDelete = mutableListOf<Series>()
            val seenSeriesIds = mutableSetOf<Int>()
            
            val filteredNewSeries = seriesList.filter { series -> 
                !series.name.equals("Test Serie", ignoreCase = true) &&
                !series.name.equals("Test Series", ignoreCase = true)
            }
            
            filteredNewSeries.forEachIndexed { index, ser ->
                val xtreamId = ser.id
                if (xtreamId != null) {
                    seenSeriesIds.add(xtreamId)
                    
                    val existing = currentSeriesMap[xtreamId]
                    val categoryName = seriesCategoryMap[ser.categoryId] ?: "Uncategorized"
                    val playlistOrder = (ser.added ?: index.toLong()).toInt()
                    
                    if (existing != null) {
                        val updated = existing.copy(
                            name = ser.name,
                            logoUrl = ser.poster,
                            category = categoryName,
                            categoryId = ser.categoryId,
                            playlistOrder = playlistOrder
                            // Preserve TMDB/OMDB data automatically
                        )
                        seriesToUpdate.add(updated)
                    } else {
                        seriesToInsert.add(Series(
                            playlistId = playlistId,
                            name = ser.name,
                            logoUrl = ser.poster,
                            category = categoryName,
                            categoryId = ser.categoryId,
                            xtreamSeriesId = ser.id,
                            playlistOrder = playlistOrder
                        ))
                    }
                }
            }
            
            currentSeries.forEach { series ->
                if (series.xtreamSeriesId != null && !seenSeriesIds.contains(series.xtreamSeriesId)) {
                    seriesToDelete.add(series)
                }
            }
            
            // 4. Batch Execute
            if (seriesToDelete.isNotEmpty()) {
                Log.d(TAG, "Deleting ${seriesToDelete.size} removed series")
                seriesDao.deleteList(seriesToDelete)
            }
            if (seriesToUpdate.isNotEmpty()) {
                Log.d(TAG, "Updating ${seriesToUpdate.size} existing series")
                seriesDao.updateList(seriesToUpdate)
            }
            if (seriesToInsert.isNotEmpty()) {
                Log.d(TAG, "Inserting ${seriesToInsert.size} new series")
                seriesDao.insertAll(seriesToInsert)
            }
            
            // Update playlist counts
            val finalChannelCount = channelEntities.size
            val finalMovieCount = currentMovies.size - moviesToDelete.size + moviesToInsert.size
            val finalSeriesCount = currentSeries.size - seriesToDelete.size + seriesToInsert.size
            
            playlistDao.updateCounts(playlistId, finalChannelCount, finalMovieCount, finalSeriesCount)
            
            Log.d(TAG, "Xtream smart refresh completed. M: $finalMovieCount, S: $finalSeriesCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Xtream content", e)
            throw Exception("Errore nel refresh contenuti Xtream: ${e.message}")
        }
    }
    
    private suspend fun saveCategories(playlistId: Long, result: M3UParser.ParseResult) {
        val categories = mutableListOf<Category>()
        
        result.channels.map { it.category }.distinct().forEach { name ->
            categories.add(Category(
                playlistId = playlistId,
                name = name,
                type = CategoryType.LIVE_TV
            ))
        }
        
        result.movies.map { it.category }.distinct().forEach { name ->
            categories.add(Category(
                playlistId = playlistId,
                name = name,
                type = CategoryType.MOVIE
            ))
        }
        
        result.series.map { it.category }.distinct().forEach { name ->
            categories.add(Category(
                playlistId = playlistId,
                name = name,
                type = CategoryType.SERIES
            ))
        }
        
        categoryDao.insertAll(categories)
    }
    
    private suspend fun saveChannels(playlistId: Long, channels: List<M3UParser.ParsedChannel>) {
        val entities = channels.map { entry ->
            Channel(
                playlistId = playlistId,
                name = entry.name,
                streamUrl = entry.streamUrl,
                logoUrl = entry.logoUrl,
                category = entry.category,
                xtreamEpgChannelId = entry.epgId
            )
        }
        channelDao.insertAll(entities)
    }
    
    private suspend fun saveMovies(playlistId: Long, movies: List<M3UParser.ParsedMovie>) {
        val entities = movies.map { entry ->
            Movie(
                playlistId = playlistId,
                name = entry.originalName,
                streamUrl = entry.streamUrl,
                logoUrl = entry.logoUrl,
                category = entry.category,
                year = entry.year,
                playlistOrder = entry.playlistOrder  // Track position in M3U file
            )
        }
        movieDao.insertAll(entities)
    }
    
    private suspend fun saveSeries(playlistId: Long, series: List<M3UParser.ParsedSeries>) {
        // Group by series name first
        val groupedSeries = series.groupBy { it.cleanName }
        
        groupedSeries.forEach { (seriesName, episodes) ->
            val firstEp = episodes.first()
            // Use max playlistOrder from episodes (latest added episode determines series order)
            val maxOrder = episodes.maxOf { it.playlistOrder }
            val seriesEntity = Series(
                playlistId = playlistId,
                name = seriesName,
                logoUrl = firstEp.logoUrl,
                category = firstEp.category,
                playlistOrder = maxOrder
            )
            val seriesId = seriesDao.insert(seriesEntity)
            
            // Save episodes
            val episodeEntities = episodes.mapNotNull { ep ->
                if (ep.season != null && ep.episode != null) {
                    Episode(
                        seriesId = seriesId,
                        name = ep.originalName,
                        streamUrl = ep.streamUrl,
                        seasonNumber = ep.season,
                        episodeNumber = ep.episode
                    )
                } else null
            }
            episodeDao.insertAll(episodeEntities)
        }
    }
}
