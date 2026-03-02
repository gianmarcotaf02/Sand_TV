package it.sandtv.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import it.sandtv.app.data.database.dao.*
import it.sandtv.app.data.database.entity.*

/**
 * Main Room Database for SandTV
 */
@Database(
    entities = [
        Profile::class,
        Playlist::class,
        Channel::class,
        Movie::class,
        Series::class,
        Episode::class,
        Category::class,
        Favorite::class,
        FavoriteCategory::class,
        WatchProgress::class,
        WatchState::class,
        TMDBCache::class,
        CustomGroup::class,
        GroupItem::class,
        RecentlyWatchedChannel::class,
        TeamChannelMap::class,
        DownloadedContent::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    // DAOs
    abstract fun profileDao(): ProfileDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun watchStateDao(): WatchStateDao
    abstract fun tmdbCacheDao(): TMDBCacheDao
    abstract fun customGroupDao(): CustomGroupDao
    abstract fun recentlyWatchedDao(): RecentlyWatchedDao
    abstract fun favoriteCategoryDao(): FavoriteCategoryDao
    abstract fun teamChannelMapDao(): TeamChannelMapDao
    abstract fun downloadedContentDao(): DownloadedContentDao
    
    companion object {
        const val DATABASE_NAME = "sandtv_database"
    }
}
