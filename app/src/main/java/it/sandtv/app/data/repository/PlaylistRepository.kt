package it.sandtv.app.data.repository

import android.util.Log
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*
import it.sandtv.app.data.parser.ContentNameParser
import it.sandtv.app.data.parser.M3UParser
import it.sandtv.app.data.parser.XtreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
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
        
        val playlist = Playlist(
            name = name,
            url = url,
            type = "m3u",
            lastUpdated = System.currentTimeMillis()
        )
        
        val playlistId = playlistDao.insert(playlist)
        val content = downloadContent(url)
        val parseResult = m3uParser.parseContent(content, playlistId)
        
        saveCategories(playlistId, parseResult)
        saveChannels(playlistId, parseResult.channels)
        saveMovies(playlistId, parseResult.movies)
        saveSeries(playlistId, parseResult.series)
        
        playlistDao.updateCounts(
            playlistId, 
            parseResult.channels.size, 
            parseResult.movies.size, 
            parseResult.series.size
        )
        
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
        val baseUrl = server.trimEnd('/')
        val playerApiUrl = "$baseUrl/player_api.php?username=$username&password=$password"
        val authResponse = downloadContent(playerApiUrl)
        if (authResponse.contains("\"auth\":0") || authResponse.contains("Unauthorized")) {
            throw Exception("Credenziali Xtream non valide")
        }
        
        val playlist = Playlist(
            name = name,
            url = baseUrl,
            type = "xtream",
            username = username,
            password = password,
            lastUpdated = System.currentTimeMillis()
        )
        
        val playlistId = playlistDao.insert(playlist)
        loadXtreamContent(playlistId, baseUrl, username, password)
        
        playlistId
    }
    
    /**
     * Refresh playlist content - preserves movie/series IDs to keep WatchProgress valid
     */
    suspend fun refreshPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext
        
        channelDao.deleteByPlaylist(playlistId)
        categoryDao.deleteByPlaylist(playlistId)
        
        when (playlist.type) {
            "m3u" -> {
                val content = downloadContent(playlist.url)
                val result = m3uParser.parseContent(content, playlistId)
                saveCategories(playlistId, result)
                saveChannels(playlistId, result.channels)
                movieDao.deleteByPlaylist(playlistId)
                seriesDao.deleteByPlaylist(playlistId)
                saveMovies(playlistId, result.movies)
                saveSeries(playlistId, result.series)
            }
            "xtream" -> {
                refreshXtreamContent(
                    playlistId,
                    playlist.url,
                    playlist.username ?: "",
                    playlist.password ?: ""
                )
            }
        }
        
        playlistDao.updateLastUpdated(playlistId, System.currentTimeMillis())
    }
    
    /**
     * Delete playlist and all content
     */
    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        val seriesInPlaylist = seriesDao.getAllSeriesList().filter { it.playlistId == playlistId }
        if (seriesInPlaylist.isNotEmpty()) {
            episodeDao.deleteBySeriesIds(seriesInPlaylist.map { it.id })
        }
        channelDao.deleteByPlaylist(playlistId)
        movieDao.deleteByPlaylist(playlistId)
        seriesDao.deleteByPlaylist(playlistId)
        categoryDao.deleteByPlaylist(playlistId)
        playlistDao.deleteById(playlistId)
    }
    
    /**
     * Load episodes for a series on-demand from Xtream API
     */
    suspend fun loadSeriesEpisodes(seriesId: Long, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val series = seriesDao.getSeriesById(seriesId) ?: return@withContext false
        val xtreamSeriesId = series.xtreamSeriesId ?: return@withContext false
        
        val existingCount = episodeDao.getCountBySeries(seriesId)
        if (existingCount > 0 && !forceRefresh) return@withContext true
        if (forceRefresh && existingCount > 0) episodeDao.deleteBySeries(seriesId)
        
        val playlist = playlistDao.getPlaylistById(series.playlistId) ?: return@withContext false
        if (playlist.type != "xtream") return@withContext false
        
        val baseUrl = playlist.url.trimEnd('/')
        val username = playlist.username ?: return@withContext false
        val password = playlist.password ?: return@withContext false
        
        try {
            val apiUrl = "$baseUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$xtreamSeriesId"
            val response = downloadContent(apiUrl)
            val seriesInfoResult = xtreamParser.parseSeriesInfo(response)
            if (seriesInfoResult == null) return@withContext false
            
            // Save info back to Series DB
            val info = seriesInfoResult.info
            if (info != null) {
                seriesDao.update(series.copy(
                    xtreamPlot = info.plot ?: series.xtreamPlot,
                    xtreamCast = info.cast ?: series.xtreamCast,
                    xtreamDirector = info.director ?: series.xtreamDirector,
                    xtreamGenre = info.genre ?: series.xtreamGenre,
                    xtreamRating = info.rating ?: series.xtreamRating
                ))
            }
            
            val episodeEntities = mutableListOf<Episode>()
            seriesInfoResult.episodes?.forEach { (seasonKey, episodeList) ->
                val seasonNum = seasonKey.toIntOrNull() ?: 1
                episodeList.forEach { ep ->
                    val episodeNum = ep.episodeNum.takeIf { it > 0 } ?: return@forEach
                    val episodeId = ep.id.toIntOrNull() ?: return@forEach
                    val extension = ep.extension ?: "mp4"
                    val streamUrl = "$baseUrl/series/$username/$password/$episodeId.$extension"
                    
                    episodeEntities.add(Episode(
                        seriesId = seriesId,
                        name = ep.title ?: "Episode $episodeNum",
                        streamUrl = streamUrl,
                        seasonNumber = seasonNum,
                        episodeNumber = episodeNum,
                        xtreamEpisodeId = episodeId,
                        containerExtension = extension,
                        tmdbStillPath = ep.info?.image,
                        tmdbOverview = ep.info?.plot,
                        duration = ep.info?.durationSecs?.toLong()
                    ))
                }
            }
            
            if (episodeEntities.isNotEmpty()) {
                episodeDao.insertAll(episodeEntities)
                val latest = episodeEntities.maxByOrNull { it.episodeNumber * 100 + it.seasonNumber }
                if (latest != null) {
                    if (series.latestEpisodeSeason != latest.seasonNumber || 
                        series.latestEpisodeNumber != latest.episodeNumber) {
                        seriesDao.update(series.copy(
                            latestEpisodeSeason = latest.seasonNumber,
                            latestEpisodeNumber = latest.episodeNumber,
                            latestEpisodeAddedAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
            return@withContext episodeEntities.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading episodes for series ${series.name}", e)
            return@withContext false
        }
    }
    
    private suspend fun downloadContent(url: String): String {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: ""
        }
    }
    
    private suspend fun loadXtreamContent(
        playlistId: Long,
        baseUrl: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val apiBase = "$baseUrl/player_api.php?username=$username&password=$password"
        try {
            val liveCatsDeferred = async { downloadContent("$apiBase&action=get_live_categories") }
            val liveStreamsDeferred = async { downloadContent("$apiBase&action=get_live_streams") }
            val vodCatsDeferred = async { downloadContent("$apiBase&action=get_vod_categories") }
            val vodStreamsDeferred = async { downloadContent("$apiBase&action=get_vod_streams") }
            val seriesCatsDeferred = async { downloadContent("$apiBase&action=get_series_categories") }
            val seriesListDeferred = async { downloadContent("$apiBase&action=get_series") }
            
            val liveCatsJson = liveCatsDeferred.await()
            val liveStreamsJson = liveStreamsDeferred.await()
            val vodCatsJson = vodCatsDeferred.await()
            val vodStreamsJson = vodStreamsDeferred.await()
            val seriesCatsJson = seriesCatsDeferred.await()
            val seriesListJson = seriesListDeferred.await()
            
            val liveCategories = xtreamParser.parseLiveCategories(liveCatsJson)
            val liveStreams = xtreamParser.parseLiveStreams(liveStreamsJson)
            val vodCategories = xtreamParser.parseVodCategories(vodCatsJson)
            val vodStreams = xtreamParser.parseVodStreams(vodStreamsJson)
            val seriesCategories = xtreamParser.parseSeriesCategories(seriesCatsJson)
            val seriesList = xtreamParser.parseSeries(seriesListJson)
            
            val liveCategoryMap = liveCategories.associate { it.id to it.name }
            categoryDao.insertAll(liveCategories.map { Category(playlistId = playlistId, name = it.name, type = CategoryType.LIVE_TV, externalId = it.id) })
            channelDao.insertAll(liveStreams.map { stream ->
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
            })
            
            val vodCategoryMap = vodCategories.associate { it.id to contentNameParser.normalizeMovieCategory(it.name) }
            categoryDao.insertAll(vodCategories.map { Category(playlistId = playlistId, name = contentNameParser.normalizeMovieCategory(it.name), type = CategoryType.MOVIE, externalId = it.id) })
            movieDao.insertAll(vodStreams.mapIndexed { index, vod ->
                Movie(
                    playlistId = playlistId,
                    name = vod.name,
                    streamUrl = "$baseUrl/movie/$username/$password/${vod.id}.${vod.extension ?: "mp4"}",
                    logoUrl = vod.poster,
                    xtreamBackdropUrl = vod.backdrop,
                    category = vodCategoryMap[vod.categoryId] ?: "Uncategorized",
                    categoryId = vod.categoryId,
                    xtreamStreamId = vod.id,
                    containerExtension = vod.extension,
                    year = vod.year?.toIntOrNull(),
                    xtreamRating = vod.rating,
                    playlistOrder = (vod.added ?: index.toLong()).toInt()
                )
            })
            
            val seriesCategoryMap = seriesCategories.associate { it.id to contentNameParser.normalizeSeriesCategory(it.name) }
            categoryDao.insertAll(seriesCategories.map { Category(playlistId = playlistId, name = contentNameParser.normalizeSeriesCategory(it.name), type = CategoryType.SERIES, externalId = it.id) })
            val filteredSeries = seriesList.filter { !it.name.equals("Test Serie", true) && !it.name.equals("Test Series", true) }
            seriesDao.insertAll(filteredSeries.mapIndexed { index, ser ->
                Series(
                    playlistId = playlistId,
                    name = ser.name,
                    logoUrl = ser.poster,
                    xtreamBackdropUrl = ser.backdrop,
                    category = seriesCategoryMap[ser.categoryId] ?: "Uncategorized",
                    categoryId = ser.categoryId,
                    xtreamSeriesId = ser.id,
                    xtreamRating = ser.rating,
                    xtreamPlot = ser.plot,
                    xtreamCast = ser.cast,
                    xtreamDirector = ser.director,
                    xtreamGenre = ser.genre,
                    playlistOrder = (ser.added ?: index.toLong()).toInt()
                )
            })
            
            playlistDao.updateCounts(playlistId, liveStreams.size, vodStreams.size, filteredSeries.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Xtream content", e)
            throw Exception("Errore nel caricamento dei contenuti Xtream: ${e.message}")
        }
    }
    
    private suspend fun refreshXtreamContent(
        playlistId: Long,
        baseUrl: String,
        username: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        val apiBase = "$baseUrl/player_api.php?username=$username&password=$password"
        try {
            val liveCatsDeferred = async { downloadContent("$apiBase&action=get_live_categories") }
            val liveStreamsDeferred = async { downloadContent("$apiBase&action=get_live_streams") }
            val vodCatsDeferred = async { downloadContent("$apiBase&action=get_vod_categories") }
            val vodStreamsDeferred = async { downloadContent("$apiBase&action=get_vod_streams") }
            val seriesCatsDeferred = async { downloadContent("$apiBase&action=get_series_categories") }
            val seriesListDeferred = async { downloadContent("$apiBase&action=get_series") }
            
            val liveCatsJson = liveCatsDeferred.await()
            val liveStreamsJson = liveStreamsDeferred.await()
            val vodCatsJson = vodCatsDeferred.await()
            val vodStreamsJson = vodStreamsDeferred.await()
            val seriesCatsJson = seriesCatsDeferred.await()
            val seriesListJson = seriesListDeferred.await()
            
            val liveCategories = xtreamParser.parseLiveCategories(liveCatsJson)
            val liveStreams = xtreamParser.parseLiveStreams(liveStreamsJson)
            val vodCategories = xtreamParser.parseVodCategories(vodCatsJson)
            val vodStreams = xtreamParser.parseVodStreams(vodStreamsJson)
            val seriesCategories = xtreamParser.parseSeriesCategories(seriesCatsJson)
            val seriesList = xtreamParser.parseSeries(seriesListJson)

            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.LIVE_TV)
            val liveCategoryMap = liveCategories.associate { it.id to it.name }
            categoryDao.insertAll(liveCategories.map { Category(playlistId = playlistId, name = it.name, type = CategoryType.LIVE_TV, externalId = it.id) })
            channelDao.deleteByPlaylist(playlistId)
            channelDao.insertAll(liveStreams.map { stream ->
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
            })

            val currentMovies = movieDao.getAllMoviesList().filter { it.playlistId == playlistId }
            val currentMovieMap = currentMovies.associateBy { it.xtreamStreamId }
            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.MOVIE)
            val vodCategoryMap = vodCategories.associate { it.id to contentNameParser.normalizeMovieCategory(it.name) }
            categoryDao.insertAll(vodCategories.map { Category(playlistId = playlistId, name = contentNameParser.normalizeMovieCategory(it.name), type = CategoryType.MOVIE, externalId = it.id) })
            
            val moviesToInsert = mutableListOf<Movie>()
            val moviesToUpdate = mutableListOf<Movie>()
            val moviesToDelete = mutableListOf<Movie>()
            val seenXtreamIds = mutableSetOf<Int>()

            vodStreams.forEachIndexed { index, vod ->
                val xtreamId = vod.id
                if (xtreamId != null) {
                    seenXtreamIds.add(xtreamId)
                    val existing = currentMovieMap[xtreamId]
                    val categoryName = vodCategoryMap[vod.categoryId] ?: "Uncategorized"
                    val streamUrl = "$baseUrl/movie/$username/$password/${vod.id}.${vod.extension ?: "mp4"}"
                    val playlistOrder = (vod.added ?: index.toLong()).toInt()
                    
                    if (existing != null) {
                        if (existing.name != vod.name || existing.logoUrl != vod.poster || existing.category != categoryName || existing.streamUrl != streamUrl) {
                            moviesToUpdate.add(existing.copy(
                                name = vod.name, streamUrl = streamUrl, logoUrl = vod.poster, xtreamBackdropUrl = vod.backdrop,
                                category = categoryName, categoryId = vod.categoryId, containerExtension = vod.extension,
                                year = vod.year?.toIntOrNull(), xtreamRating = vod.rating, playlistOrder = playlistOrder
                            ))
                        }
                    } else {
                        moviesToInsert.add(Movie(
                            playlistId = playlistId, name = vod.name, streamUrl = streamUrl, logoUrl = vod.poster, xtreamBackdropUrl = vod.backdrop,
                            category = categoryName, categoryId = vod.categoryId, xtreamStreamId = vod.id,
                            containerExtension = vod.extension, year = vod.year?.toIntOrNull(), xtreamRating = vod.rating, playlistOrder = playlistOrder
                        ))
                    }
                }
            }
            currentMovies.forEach { if (it.xtreamStreamId != null && !seenXtreamIds.contains(it.xtreamStreamId)) moviesToDelete.add(it) }
            movieDao.deleteList(moviesToDelete)
            movieDao.updateList(moviesToUpdate)
            movieDao.insertAll(moviesToInsert)

            val currentSeries = seriesDao.getAllSeriesList().filter { it.playlistId == playlistId }
            val currentSeriesMap = currentSeries.associateBy { it.xtreamSeriesId }
            categoryDao.deleteByPlaylistAndType(playlistId, CategoryType.SERIES)
            val seriesCategoryMap = seriesCategories.associate { it.id to contentNameParser.normalizeSeriesCategory(it.name) }
            categoryDao.insertAll(seriesCategories.map { Category(playlistId = playlistId, name = contentNameParser.normalizeSeriesCategory(it.name), type = CategoryType.SERIES, externalId = it.id) })
            
            val seriesToInsert = mutableListOf<Series>()
            val seriesToUpdate = mutableListOf<Series>()
            val seriesToDelete = mutableListOf<Series>()
            val seenSeriesIds = mutableSetOf<Int>()

            val filteredNewSeries = seriesList.filter { !it.name.equals("Test Serie", true) && !it.name.equals("Test Series", true) }
            filteredNewSeries.forEachIndexed { index, ser ->
                val xtreamId = ser.id
                if (xtreamId != null) {
                    seenSeriesIds.add(xtreamId)
                    val existing = currentSeriesMap[xtreamId]
                    val categoryName = seriesCategoryMap[ser.categoryId] ?: "Uncategorized"
                    val playlistOrder = (ser.added ?: index.toLong()).toInt()
                    
                    if (existing != null) {
                        if (existing.name != ser.name || existing.logoUrl != ser.poster || existing.category != categoryName) {
                            seriesToUpdate.add(existing.copy(
                                name = ser.name, logoUrl = ser.poster, xtreamBackdropUrl = ser.backdrop,
                                category = categoryName, categoryId = ser.categoryId, xtreamRating = ser.rating, 
                                xtreamPlot = ser.plot, xtreamCast = ser.cast, xtreamDirector = ser.director, xtreamGenre = ser.genre,
                                playlistOrder = playlistOrder
                            ))
                        } else {
                            seriesToUpdate.add(existing.copy(
                                xtreamRating = ser.rating, xtreamPlot = ser.plot, xtreamCast = ser.cast, 
                                xtreamDirector = ser.director, xtreamGenre = ser.genre, playlistOrder = playlistOrder
                            ))
                        }
                    } else {
                        seriesToInsert.add(Series(
                            playlistId = playlistId, name = ser.name, logoUrl = ser.poster, xtreamBackdropUrl = ser.backdrop,
                            category = categoryName, categoryId = ser.categoryId, xtreamSeriesId = ser.id, 
                            xtreamRating = ser.rating, xtreamPlot = ser.plot, xtreamCast = ser.cast, 
                            xtreamDirector = ser.director, xtreamGenre = ser.genre, playlistOrder = playlistOrder
                        ))
                    }
                }
            }
            currentSeries.forEach { if (it.xtreamSeriesId != null && !seenSeriesIds.contains(it.xtreamSeriesId)) seriesToDelete.add(it) }
            seriesToDelete.forEach { episodeDao.deleteBySeries(it.id) }
            seriesDao.deleteList(seriesToDelete)
            seriesDao.updateList(seriesToUpdate)
            seriesDao.insertAll(seriesToInsert)

            playlistDao.updateCounts(playlistId, liveStreams.size, currentMovies.size - moviesToDelete.size + moviesToInsert.size, currentSeries.size - seriesToDelete.size + seriesToInsert.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Xtream content", e)
            throw Exception("Errore nel refresh contenuti Xtream: ${e.message}")
        }
    }
    
    private suspend fun saveCategories(playlistId: Long, result: M3UParser.ParseResult) {
        val categories = mutableListOf<Category>()
        result.channels.map { it.category }.distinct().forEach { name ->
            categories.add(Category(playlistId = playlistId, name = name, type = CategoryType.LIVE_TV))
        }
        result.movies.map { it.category }.distinct().forEach { name ->
            categories.add(Category(playlistId = playlistId, name = name, type = CategoryType.MOVIE))
        }
        result.series.map { it.category }.distinct().forEach { name ->
            categories.add(Category(playlistId = playlistId, name = name, type = CategoryType.SERIES))
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
                playlistOrder = entry.playlistOrder
            )
        }
        movieDao.insertAll(entities)
    }
    
    private suspend fun saveSeries(playlistId: Long, series: List<M3UParser.ParsedSeries>) {
        val groupedSeries = series.groupBy { it.cleanName }
        groupedSeries.forEach { (seriesName, episodes) ->
            val firstEp = episodes.first()
            val maxOrder = episodes.maxOf { it.playlistOrder }
            val seriesEntity = Series(
                playlistId = playlistId,
                name = seriesName,
                logoUrl = firstEp.logoUrl,
                category = firstEp.category,
                playlistOrder = maxOrder
            )
            val seriesId = seriesDao.insert(seriesEntity)
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
