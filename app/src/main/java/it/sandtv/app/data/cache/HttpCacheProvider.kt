package it.sandtv.app.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp cache configuration for HTTP-level caching
 * Works automatically for GET requests with proper cache headers
 */
@Singleton
class HttpCacheProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CACHE_SIZE = 20L * 1024 * 1024 // 20MB for HTTP responses
        private const val CACHE_DIR = "http_cache"
    }
    
    /**
     * OkHttp disk cache
     */
    val cache: Cache by lazy {
        Cache(File(context.cacheDir, CACHE_DIR), CACHE_SIZE)
    }
    
    /**
     * Interceptor that forces caching even when server doesn't set headers
     */
    val cacheInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        
        // Cache TMDB responses for 1 hour
        if (chain.request().url.host.contains("themoviedb")) {
            response.newBuilder()
                .removeHeader("Pragma")
                .removeHeader("Cache-Control")
                .header("Cache-Control", CacheControl.Builder()
                    .maxAge(1, TimeUnit.HOURS)
                    .build().toString())
                .build()
        } else {
            response
        }
    }
    
    /**
     * Interceptor for offline mode - serve stale cache when offline
     */
    val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // If offline, use cache even if stale
        if (!isNetworkAvailable()) {
            val cacheControl = CacheControl.Builder()
                .maxStale(7, TimeUnit.DAYS) // Use week-old cache if needed
                .onlyIfCached()
                .build()
            
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        
        chain.proceed(request)
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
