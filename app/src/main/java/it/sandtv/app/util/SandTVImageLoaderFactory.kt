package it.sandtv.app.util

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Coil ImageLoaderFactory optimized for Android TV
 * 
 * - 25% of available RAM for memory cache (TV devices have more memory)
 * - 150MB disk cache for persistent offline images
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
            .crossfade(true)
            .build()
    }
}
