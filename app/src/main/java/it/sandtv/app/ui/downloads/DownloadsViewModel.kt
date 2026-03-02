package it.sandtv.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sandtv.app.data.database.entity.DownloadedContent
import it.sandtv.app.data.repository.DownloadContentManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * ViewModel for Downloads screen
 */
@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadContentManager
) : ViewModel() {
    
    /**
     * All completed downloads
     */
    /**
     * All completed downloads
     */
    val completedDownloads: Flow<List<DownloadedContent>> = downloadManager.getAllDownloads()
        .distinctUntilChanged()
    
    /**
     * Downloads in progress
     */
    val inProgressDownloads: Flow<List<DownloadedContent>> = downloadManager.getDownloadsInProgress()
        .distinctUntilChanged()
    
    /**
     * Total download size in bytes
     */
    private val _totalDownloadSize = MutableStateFlow(0L)
    val totalDownloadSize: StateFlow<Long> = _totalDownloadSize
    
    init {
        loadTotalSize()
    }
    
    private fun loadTotalSize() {
        viewModelScope.launch {
            _totalDownloadSize.value = downloadManager.getTotalDownloadSize()
        }
    }
    
    /**
     * Delete a completed download
     */
    fun deleteDownload(download: DownloadedContent) {
        viewModelScope.launch {
            downloadManager.deleteDownload(download.contentType, download.contentId)
            loadTotalSize() // Refresh total size
        }
    }
    
    /**
     * Cancel an in-progress download
     */
    fun cancelDownload(download: DownloadedContent) {
        viewModelScope.launch {
            downloadManager.cancelDownload(download.contentType, download.contentId)
        }
    }
}
