package it.sandtv.app.util

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image preloader using Coil (same cache as UI rendering)
 * 
 * Enqueues non-blocking preload requests so images are already
 * in Coil's memory/disk cache when the UI needs them.
 */
@Singleton
class CoilImagePreloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imageLoader: ImageLoader by lazy {
        ImageLoader(context) // Uses the app-level factory via ImageLoaderFactory
    }

    /**
     * Preload a list of image URLs into Coil's cache.
     * Non-blocking — fires and forgets.
     */
    fun preloadImages(urls: List<String?>, targetSize: Size = Size.ORIGINAL) {
        urls.filterNotNull().filter { it.isNotBlank() }.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(targetSize)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
            imageLoader.enqueue(request)
        }
    }

    /**
     * Preload poster images from a carousel (first N items)
     */
    fun preloadCarouselPosters(posterUrls: List<String?>, count: Int = 20) {
        preloadImages(
            urls = posterUrls.take(count),
            targetSize = Size(390, 585)  // 3x of 130x195 card size for sharp rendering
        )
    }

    /**
     * Preload a single backdrop image (for detail view anticipation)
     */
    fun preloadBackdrop(backdropUrl: String?) {
        if (backdropUrl != null) {
            preloadImages(
                urls = listOf(backdropUrl),
                targetSize = Size(1280, 720)
            )
        }
    }

    /**
     * Preload poster + backdrop for a specific item (before detail view navigation)
     */
    fun preloadDetailImages(posterUrl: String?, backdropUrl: String?) {
        preloadBackdrop(backdropUrl)
        if (posterUrl != null) {
            preloadImages(
                urls = listOf(posterUrl),
                targetSize = Size(450, 675) // Detail poster size (150x225 @ 3x)
            )
        }
    }
}
