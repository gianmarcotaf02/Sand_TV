package it.sandtv.app.data.cache

import android.util.LruCache
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.data.database.entity.Series
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for frequently accessed content
 * Reduces database queries and improves UI responsiveness
 */
@Singleton
class ContentCache @Inject constructor() {
    
    companion object {
        private const val CHANNEL_CACHE_SIZE = 500
        private const val MOVIE_CACHE_SIZE = 300
        private const val SERIES_CACHE_SIZE = 200
        private const val CATEGORY_CACHE_SIZE = 50
    }
    
    // LRU Caches for fast access
    private val channelCache = LruCache<Long, Channel>(CHANNEL_CACHE_SIZE)
    private val movieCache = LruCache<Long, Movie>(MOVIE_CACHE_SIZE)
    private val seriesCache = LruCache<Long, Series>(SERIES_CACHE_SIZE)
    
    // Category lists cache
    private val channelsByCategoryCache = LruCache<String, List<Channel>>(CATEGORY_CACHE_SIZE)
    private val moviesByCategoryCache = LruCache<String, List<Movie>>(CATEGORY_CACHE_SIZE)
    private val seriesByCategoryCache = LruCache<String, List<Series>>(CATEGORY_CACHE_SIZE)
    
    // Timestamp for cache invalidation
    private var lastInvalidation = System.currentTimeMillis()
    
    // Channels
    fun getChannel(id: Long): Channel? = channelCache.get(id)
    
    fun putChannel(channel: Channel) {
        channelCache.put(channel.id, channel)
    }
    
    fun putChannels(channels: List<Channel>) {
        channels.forEach { channelCache.put(it.id, it) }
    }
    
    fun getChannelsByCategory(category: String): List<Channel>? = channelsByCategoryCache.get(category)
    
    fun putChannelsByCategory(category: String, channels: List<Channel>) {
        channelsByCategoryCache.put(category, channels)
        channels.forEach { channelCache.put(it.id, it) }
    }
    
    // Movies
    fun getMovie(id: Long): Movie? = movieCache.get(id)
    
    fun putMovie(movie: Movie) {
        movieCache.put(movie.id, movie)
    }
    
    fun putMovies(movies: List<Movie>) {
        movies.forEach { movieCache.put(it.id, it) }
    }
    
    fun getMoviesByCategory(category: String): List<Movie>? = moviesByCategoryCache.get(category)
    
    fun putMoviesByCategory(category: String, movies: List<Movie>) {
        moviesByCategoryCache.put(category, movies)
        movies.forEach { movieCache.put(it.id, it) }
    }
    
    // Series
    fun getSeries(id: Long): Series? = seriesCache.get(id)
    
    fun putSeries(series: Series) {
        seriesCache.put(series.id, series)
    }
    
    fun putSeriesList(seriesList: List<Series>) {
        seriesList.forEach { seriesCache.put(it.id, it) }
    }
    
    fun getSeriesByCategory(category: String): List<Series>? = seriesByCategoryCache.get(category)
    
    fun putSeriesByCategory(category: String, seriesList: List<Series>) {
        seriesByCategoryCache.put(category, seriesList)
        seriesList.forEach { seriesCache.put(it.id, it) }
    }
    
    // Invalidation
    fun invalidateAll() {
        channelCache.evictAll()
        movieCache.evictAll()
        seriesCache.evictAll()
        channelsByCategoryCache.evictAll()
        moviesByCategoryCache.evictAll()
        seriesByCategoryCache.evictAll()
        lastInvalidation = System.currentTimeMillis()
    }
    
    fun invalidateChannels() {
        channelCache.evictAll()
        channelsByCategoryCache.evictAll()
    }
    
    fun invalidateMovies() {
        movieCache.evictAll()
        moviesByCategoryCache.evictAll()
    }
    
    fun invalidateSeries() {
        seriesCache.evictAll()
        seriesByCategoryCache.evictAll()
    }
    
    fun isStale(maxAgeMs: Long = 5 * 60 * 1000): Boolean {
        return System.currentTimeMillis() - lastInvalidation > maxAgeMs
    }
}
