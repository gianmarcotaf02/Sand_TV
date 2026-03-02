package it.sandtv.app.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import it.sandtv.app.data.api.TMDBApiService
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.data.database.entity.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preloads images for smooth scrolling experience
 * Runs in background to prefetch upcoming content
 */
@Singleton
class ImagePreloader @Inject constructor() {
    
    /**
     * Preload posters for a list of movies
     */
    suspend fun preloadMoviePosters(context: Context, movies: List<Movie>) = withContext(Dispatchers.IO) {
        movies.take(20).forEach { movie ->
            val posterUrl = TMDBApiService.getPosterUrl(movie.tmdbPosterPath) ?: movie.logoUrl
            posterUrl?.let { url ->
                try {
                    Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .priority(Priority.LOW)
                        .preload(176, 264) // Card dimensions
                } catch (e: Exception) {
                    // Ignore preload failures
                }
            }
        }
    }
    
    /**
     * Preload posters for a list of series
     */
    suspend fun preloadSeriesPosters(context: Context, seriesList: List<Series>) = withContext(Dispatchers.IO) {
        seriesList.take(20).forEach { series ->
            val posterUrl = TMDBApiService.getPosterUrl(series.tmdbPosterPath) ?: series.logoUrl
            posterUrl?.let { url ->
                try {
                    Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .priority(Priority.LOW)
                        .preload(176, 264)
                } catch (e: Exception) {
                    // Ignore preload failures
                }
            }
        }
    }
    
    /**
     * Preload logos for a list of channels
     */
    suspend fun preloadChannelLogos(context: Context, channels: List<Channel>) = withContext(Dispatchers.IO) {
        channels.take(30).forEach { channel ->
            channel.logoUrl?.let { url ->
                try {
                    Glide.with(context)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .priority(Priority.LOW)
                        .preload(176, 132)
                } catch (e: Exception) {
                    // Ignore preload failures
                }
            }
        }
    }
    
    /**
     * Preload backdrop for details page
     */
    suspend fun preloadBackdrop(context: Context, backdropPath: String?) = withContext(Dispatchers.IO) {
        val url = TMDBApiService.getBackdropUrl(backdropPath) ?: return@withContext
        try {
            Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
                .preload(1280, 720)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
