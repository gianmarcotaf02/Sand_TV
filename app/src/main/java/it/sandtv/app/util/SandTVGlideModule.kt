package it.sandtv.app.util

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

/**
 * Glide configuration for optimized image loading
 * - Aggressive caching
 * - Memory optimization for TV
 * - Preloading support
 */
@GlideModule
class SandTVGlideModule : AppGlideModule() {
    
    companion object {
        // 100MB disk cache
        private const val DISK_CACHE_SIZE = 100L * 1024 * 1024
        // 50MB memory cache (TV has more RAM)
        private const val MEMORY_CACHE_SIZE = 50L * 1024 * 1024
    }
    
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Disk cache
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE))
        
        // Memory cache
        builder.setMemoryCache(LruResourceCache(MEMORY_CACHE_SIZE))
        
        // Default options for all requests
        builder.setDefaultRequestOptions(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .format(DecodeFormat.PREFER_RGB_565) // Less memory
                .encodeFormat(Bitmap.CompressFormat.WEBP_LOSSY) // Modern WEBP format
                .encodeQuality(85)
        )
    }
    
    override fun isManifestParsingEnabled(): Boolean = false
}
