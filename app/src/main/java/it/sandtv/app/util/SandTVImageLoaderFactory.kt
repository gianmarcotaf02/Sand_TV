package it.sandtv.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Coil ImageLoaderFactory optimized for Android TV
 * 
 * - 25% of available RAM for memory cache (TV devices have more memory)
 * - 150MB disk cache for persistent offline images
 * - RGB_565 bitmap config (half memory vs ARGB_8888, fine for posters/backdrops)
 * - No crossfade by default (instant display for cached images)
 */
class SandTVImageLoaderFactory(private val context: Context) : ImageLoaderFactory {
    
    override fun newImageLoader(): ImageLoader {
        val memoryPercent = 0.25  // 25% of available RAM
        val diskCacheSize = 150L * 1024 * 1024 // 150 MB

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true) // Half memory for non-transparent images
            .crossfade(false)   // No crossfade by default (instant for cached)
            .respectCacheHeaders(false) // Ignore server cache headers, use our policy
            .build()
    }
}
