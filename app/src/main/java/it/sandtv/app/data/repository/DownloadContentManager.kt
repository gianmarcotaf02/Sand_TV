package it.sandtv.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import it.sandtv.app.data.database.dao.DownloadedContentDao
import it.sandtv.app.data.database.dao.EpisodeDao
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.DownloadedContent
import it.sandtv.app.data.database.entity.Episode
import it.sandtv.app.data.database.entity.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for downloading and caching video content for offline playback.
 * Uses ExoPlayer's DownloadManager and SimpleCache for internal storage.
 * Content is NOT exported as accessible files - remains app-internal only.
 */
@UnstableApi
@Singleton
class DownloadContentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedContentDao: DownloadedContentDao,
    private val movieDao: MovieDao,
    private val episodeDao: EpisodeDao,
    private val seriesDao: SeriesDao
) {
    companion object {
        private const val TAG = "DownloadContentManager"
        private const val DOWNLOAD_CACHE_DIR = "video_downloads"
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val MAX_CACHE_SIZE = 10L * 1024 * 1024 * 1024 // 10 GB max cache
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadExecutor: Executor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)
    
    // ExoPlayer components
    private val databaseProvider = StandaloneDatabaseProvider(context)
    
    private val downloadCache: Cache by lazy {
        it.sandtv.app.service.VideoDownloadService.getOrCreateCache(context)
    }
    
    private val dataSourceFactory: DataSource.Factory by lazy {
        DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
    }
    
    /**
     * CacheDataSource factory for offline playback.
     * Use this in PlayerActivity when content is downloaded.
     */
    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // No writing when reading
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    
    private val downloadManager: DownloadManager by lazy {
        // Use the same DownloadManager from the service to avoid cache conflicts
        it.sandtv.app.service.VideoDownloadService.getOrCreateDownloadManager(context).apply {
            maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
            addListener(downloadListener)
        }
    }
    
    // Progress tracking
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress
    
    private val downloadListener = object : DownloadManager.Listener {
        // Map to track last update time for each download to throttle DB writes
        private val lastDbUpdateTime = mutableMapOf<String, Long>()
        
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            scope.launch {
                val cacheKey = download.request.id
                val progress = download.percentDownloaded.toInt()
                
                // Update progress map (Flow) - safe to update frequently as it's just a variable
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    put(cacheKey, progress)
                }
                
                // Throttling logic for Database updates
                val currentTime = System.currentTimeMillis()
                val lastUpdate = lastDbUpdateTime[cacheKey] ?: 0L
                val shouldUpdateDb = when {
                    // Always update on terminal states or state changes (effectively)
                    download.state == Download.STATE_COMPLETED || 
                    download.state == Download.STATE_FAILED || 
                    download.state == Download.STATE_STOPPED -> true
                    // Otherwise throttle to once every 1000ms
                    currentTime - lastUpdate > 1000 -> true
                    else -> false
                }
                
                if (shouldUpdateDb) {
                    lastDbUpdateTime[cacheKey] = currentTime
                    
                    // Update database
                    downloadedContentDao.getByCacheKey(cacheKey)?.let { content ->
                        when (download.state) {
                            Download.STATE_DOWNLOADING -> {
                                downloadedContentDao.updateProgress(content.id, progress)
                            }
                            Download.STATE_COMPLETED -> {
                                downloadedContentDao.markComplete(content.id, download.bytesDownloaded)
                                Log.d(TAG, "Download completed: ${content.title}")
                            }
                            Download.STATE_FAILED -> {
                                Log.e(TAG, "Download failed: ${content.title}", finalException)
                                // Keep the entry but mark as incomplete
                                downloadedContentDao.updateProgress(content.id, 0)
                            }
                            Download.STATE_STOPPED -> {
                                Log.d(TAG, "Download stopped: ${content.title}")
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
        
        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            scope.launch {
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    remove(download.request.id)
                }
            }
        }
    }
    
    /**
     * Download a movie for offline playback
     */
    suspend fun downloadMovie(movieId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val movie = movieDao.getMovieById(movieId) ?: return@withContext false
                
                val cacheKey = "movie_${movieId}"
                
                // Check if already downloaded and complete
                val existingEntry = downloadedContentDao.getByContent(ContentType.MOVIE, movieId)
                if (existingEntry != null) {
                    if (existingEntry.isComplete) {
                        Log.d(TAG, "Movie already downloaded: ${movie.title}")
                        return@withContext true
                    } else {
                        // Remove incomplete entry to restart
                        Log.d(TAG, "Removing incomplete download entry for ${movie.title}")
                        downloadedContentDao.delete(existingEntry)
                    }
                }
                
                // Create database entry
                val downloadEntry = DownloadedContent(
                    contentType = ContentType.MOVIE,
                    contentId = movieId,
                    title = movie.title,
                    posterUrl = movie.posterUrl,
                    cacheKey = cacheKey,
                    streamUrl = movie.streamUrl,
                    downloadProgress = 0,
                    isComplete = false
                )
                downloadedContentDao.insert(downloadEntry)
                
                // Start download
                startDownload(cacheKey, movie.streamUrl)
                Log.d(TAG, "Started download: ${movie.title}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading movie: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Download an episode for offline playback
     */
    suspend fun downloadEpisode(episodeId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val episode = episodeDao.getEpisodeById(episodeId) ?: return@withContext false
                val series = seriesDao.getSeriesById(episode.seriesId)
                
                val cacheKey = "episode_${episodeId}"
                
                // Check if already downloaded and complete
                val existingEntry = downloadedContentDao.getByContent(ContentType.EPISODE, episodeId)
                if (existingEntry != null) {
                    if (existingEntry.isComplete) {
                        Log.d(TAG, "Episode already downloaded: S${episode.seasonNumber}E${episode.episodeNumber}")
                        return@withContext true
                    } else {
                        // Remove incomplete entry to restart
                        Log.d(TAG, "Removing incomplete download entry for S${episode.seasonNumber}E${episode.episodeNumber}")
                        downloadedContentDao.delete(existingEntry)
                    }
                }
                
                // Create database entry
                val downloadEntry = DownloadedContent(
                    contentType = ContentType.EPISODE,
                    contentId = episodeId,
                    title = episode.title,
                    posterUrl = episode.posterUrl,
                    cacheKey = cacheKey,
                    streamUrl = episode.streamUrl,
                    seriesId = episode.seriesId,
                    seriesName = series?.title,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    downloadProgress = 0,
                    isComplete = false
                )
                downloadedContentDao.insert(downloadEntry)
                
                // Start download
                startDownload(cacheKey, episode.streamUrl)
                Log.d(TAG, "Started download: ${series?.title} S${episode.seasonNumber}E${episode.episodeNumber}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading episode: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Download all episodes of a season
     */
    suspend fun downloadSeason(seriesId: Long, seasonNumber: Int): Int {
        return withContext(Dispatchers.IO) {
            var downloadedCount = 0
            val episodes = episodeDao.getEpisodesBySeasonList(seriesId, seasonNumber)
            
            for (episode in episodes) {
                if (downloadEpisode(episode.id)) {
                    downloadedCount++
                }
            }
            
            Log.d(TAG, "Started download for $downloadedCount episodes of season $seasonNumber")
            downloadedCount
        }
    }
    
    /**
     * Download all episodes of a series
     */
    suspend fun downloadAllSeasons(seriesId: Long): Int {
        return withContext(Dispatchers.IO) {
            var downloadedCount = 0
            val episodes = episodeDao.getEpisodesBySeriesList(seriesId)
            
            for (episode in episodes) {
                if (downloadEpisode(episode.id)) {
                    downloadedCount++
                }
            }
            
            Log.d(TAG, "Started download for $downloadedCount episodes of series")
            downloadedCount
        }
    }

    /**
     * Get all downloaded episodes for a specific series
     */
    suspend fun getDownloadedEpisodes(seriesId: Long): List<DownloadedContent> {
        return withContext(Dispatchers.IO) {
            downloadedContentDao.getBySeriesId(seriesId).first()
        }
    }
    
    /**
     * Cancel an ongoing download
     */
    suspend fun cancelDownload(contentType: ContentType, contentId: Long) {
        withContext(Dispatchers.IO) {
            val download = downloadedContentDao.getByContent(contentType, contentId) ?: return@withContext
            
            downloadManager.removeDownload(download.cacheKey)
            downloadedContentDao.deleteByContent(contentType, contentId)
            
            Log.d(TAG, "Cancelled download: ${download.title}")
        }
    }
    
    /**
     * Delete a completed download
     */
    suspend fun deleteDownload(contentType: ContentType, contentId: Long) {
        withContext(Dispatchers.IO) {
            val download = downloadedContentDao.getByContent(contentType, contentId) ?: return@withContext
            
            // Remove from ExoPlayer's download manager (clears cache)
            downloadManager.removeDownload(download.cacheKey)
            
            // Remove from database
            downloadedContentDao.deleteByContent(contentType, contentId)
            
            Log.d(TAG, "Deleted download: ${download.title}")
        }
    }
    
    /**
     * Delete all downloads for a season
     */
    suspend fun deleteSeasonDownloads(seriesId: Long, seasonNumber: Int) {
        withContext(Dispatchers.IO) {
            val downloads = downloadedContentDao.getBySeason(seriesId, seasonNumber).first()
            
            for (download in downloads) {
                downloadManager.removeDownload(download.cacheKey)
            }
            
            downloadedContentDao.deleteBySeason(seriesId, seasonNumber)
            Log.d(TAG, "Deleted all downloads for season $seasonNumber")
        }
    }
    
    /**
     * Delete all downloads for a series
     */
    suspend fun deleteSeriesDownloads(seriesId: Long) {
        withContext(Dispatchers.IO) {
            val downloads = downloadedContentDao.getBySeriesId(seriesId).first()
            
            for (download in downloads) {
                downloadManager.removeDownload(download.cacheKey)
            }
            
            downloadedContentDao.deleteBySeries(seriesId)
            Log.d(TAG, "Deleted all downloads for series")
        }
    }
    
    /**
     * Check if content is downloaded and available for offline playback
     */
    fun isDownloaded(contentType: ContentType, contentId: Long): Flow<Boolean> {
        return downloadedContentDao.isDownloaded(contentType, contentId)
    }
    
    /**
     * Synchronous check if content is downloaded (for use in suspend functions)
     */
    suspend fun isContentDownloaded(contentType: ContentType, contentId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val download = downloadedContentDao.getByContent(contentType, contentId)
            download?.isComplete == true
        }
    }
    
    /**
     * Get download entry for content
     */
    fun observeDownload(contentType: ContentType, contentId: Long): Flow<DownloadedContent?> {
        return downloadedContentDao.observeByContent(contentType, contentId)
    }
    
    /**
     * Get all completed downloads
     */
    fun getAllDownloads(): Flow<List<DownloadedContent>> {
        return downloadedContentDao.getAllCompleted()
    }
    
    /**
     * Get downloads in progress
     */
    fun getDownloadsInProgress(): Flow<List<DownloadedContent>> {
        return downloadedContentDao.getInProgress()
    }
    
    /**
     * Get total size of all downloads in bytes
     */
    suspend fun getTotalDownloadSize(): Long {
        return downloadedContentDao.getTotalDownloadedSize()
    }
    
    /**
     * Check if there is cached data for a given stream
     */
    suspend fun getCachedStreamUrl(contentType: ContentType, contentId: Long): String? {
        return withContext(Dispatchers.IO) {
            val download = downloadedContentDao.getByContent(contentType, contentId)
            if (download?.isComplete == true) {
                download.streamUrl
            } else {
                null
            }
        }
    }
    
    private fun startDownload(cacheKey: String, streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()
        
        val downloadHelper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            null, // renderersFactory - use default
            dataSourceFactory
        )
        
        downloadHelper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                // Build download request with all tracks
                val downloadRequest = helper.getDownloadRequest(cacheKey, null)
                
                // Set the download manager for the service
                it.sandtv.app.service.VideoDownloadService.setDownloadManager(downloadManager)
                
                // Send download request via DownloadService for background download
                DownloadService.sendAddDownload(
                    context,
                    it.sandtv.app.service.VideoDownloadService::class.java,
                    downloadRequest,
                    /* foreground= */ false
                )
                
                Log.d(TAG, "Download prepared and started: $cacheKey")
                helper.release()
            }
            
            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                Log.e(TAG, "Error preparing download for $cacheKey: ${e.message}", e)
                
                // Fallback: try direct download for progressive streams
                val downloadRequest = DownloadRequest.Builder(cacheKey, Uri.parse(streamUrl))
                    .build()
                
                it.sandtv.app.service.VideoDownloadService.setDownloadManager(downloadManager)
                DownloadService.sendAddDownload(
                    context,
                    it.sandtv.app.service.VideoDownloadService::class.java,
                    downloadRequest,
                    /* foreground= */ false
                )
                
                Log.d(TAG, "Fallback download started: $cacheKey")
                helper.release()
            }
        })
    }
    
    /**
     * Initialize the download manager - call this on app startup
     */
    fun initialize() {
        // Access downloadManager to trigger lazy initialization
        // Resume any paused downloads
        downloadManager.resumeDownloads()
        Log.d(TAG, "DownloadContentManager initialized")
    }
    
    /**
     * Release resources - call on app termination
     */
    fun release() {
        try {
            downloadManager.release()
            downloadCache.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing download manager: ${e.message}", e)
        }
    }
}
