package it.sandtv.app.ui.downloads

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.theme.SandTVTheme

/**
 * Activity for displaying and managing downloaded content
 */
@UnstableApi
@AndroidEntryPoint
class DownloadsActivity : ComponentActivity() {
    
    private val viewModel: DownloadsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SandTVTheme {
                val downloads by viewModel.completedDownloads.collectAsState(initial = emptyList())
                val inProgress by viewModel.inProgressDownloads.collectAsState(initial = emptyList())
                val totalSize by viewModel.totalDownloadSize.collectAsState()
                
                DownloadsScreen(
                    completedDownloads = downloads,
                    inProgressDownloads = inProgress,
                    totalSize = totalSize,
                    onPlayClick = { download ->
                        // Navigate to player with cached content
                        val intent = Intent(this, PlayerActivity::class.java).apply {
                            putExtra("content_type", download.contentType.name)
                            putExtra("content_id", download.contentId)
                            putExtra("stream_url", download.streamUrl)
                            putExtra("title", download.displayTitle)
                            putExtra("is_downloaded", true)
                            
                            // For episodes, include series info
                            if (download.contentType == ContentType.EPISODE) {
                                download.seriesId?.let { putExtra("series_id", it) }
                                download.seasonNumber?.let { putExtra("season", it) }
                                download.episodeNumber?.let { putExtra("episode", it) }
                            }
                        }
                        startActivity(intent)
                    },
                    onDeleteClick = { download ->
                        viewModel.deleteDownload(download)
                    },
                    onCancelClick = { download ->
                        viewModel.cancelDownload(download)
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
}
