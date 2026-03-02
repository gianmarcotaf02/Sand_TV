package it.sandtv.app.update

/**
 * Data class representing update information from Firebase
 */
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false
) {
    /**
     * Check if this update is newer than the installed version
     */
    fun isNewerThan(installedVersionCode: Int): Boolean {
        return versionCode > installedVersionCode
    }
}

/**
 * Sealed class representing update check result
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    data object NoUpdateAvailable : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Sealed class representing download state
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Downloaded : DownloadState()
    data class Failed(val error: String) : DownloadState()
}
