package it.sandtv.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.sandtv.app.data.database.AppDatabase
import it.sandtv.app.data.database.DatabaseCheckpointManager
import it.sandtv.app.data.database.dao.*
import javax.inject.Singleton

/**
 * Hilt module for database dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Migration from version 5 to 6:
     * - Add omdbAudienceScore column to movies table
     * - Add omdbAudienceScore column to series table
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add omdbAudienceScore to movies table
            db.execSQL("ALTER TABLE movies ADD COLUMN omdbAudienceScore INTEGER DEFAULT NULL")
            // Add omdbAudienceScore to series table
            db.execSQL("ALTER TABLE series ADD COLUMN omdbAudienceScore INTEGER DEFAULT NULL")
        }
    }
    
    /**
     * Migration from version 6 to 7:
     * - Add favorite_categories table
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS favorite_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    profileId INTEGER NOT NULL,
                    categoryType TEXT NOT NULL,
                    categoryName TEXT NOT NULL,
                    addedAt INTEGER NOT NULL,
                    FOREIGN KEY (profileId) REFERENCES profiles(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_categories_profileId ON favorite_categories(profileId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_categories_profileId_categoryType_categoryName ON favorite_categories(profileId, categoryType, categoryName)")
        }
    }
    
    /**
     * Migration from version 7 to 8:
     * - Schema compatibility (no structural changes)
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No structural changes needed
        }
    }
    
    /**
     * Migration from version 8 to 9:
     * - Add tmdbTrailerKey column to movies table
     * - Add tmdbTrailerKey column to series table
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add tmdbTrailerKey to movies table
            db.execSQL("ALTER TABLE movies ADD COLUMN tmdbTrailerKey TEXT DEFAULT NULL")
            // Add tmdbTrailerKey to series table
            db.execSQL("ALTER TABLE series ADD COLUMN tmdbTrailerKey TEXT DEFAULT NULL")
        }
    }
    
    /**
     * Migration from version 9 to 10:
     * - Add team_channel_map table (for Serie A team-channel mapping)
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Migration for team_channel_map was added directly in version 10
            // No actions needed here if table already exists
        }
    }
    
    /**
     * Migration from version 10 to 11:
     * - Add trendingCategory column to movies table
     * - Add trendingCategory column to series table
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add trendingCategory to movies table
            db.execSQL("ALTER TABLE movies ADD COLUMN trendingCategory TEXT DEFAULT NULL")
            // Add trendingCategory to series table
            db.execSQL("ALTER TABLE series ADD COLUMN trendingCategory TEXT DEFAULT NULL")
        }
    }
    
    /**
     * Migration from version 11 to 12:
     * - Add downloaded_content table for offline playback
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS downloaded_content (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    contentType TEXT NOT NULL,
                    contentId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    posterUrl TEXT,
                    downloadedAt INTEGER NOT NULL,
                    downloadSize INTEGER NOT NULL DEFAULT 0,
                    cacheKey TEXT NOT NULL,
                    streamUrl TEXT NOT NULL,
                    seriesId INTEGER,
                    seriesName TEXT,
                    seasonNumber INTEGER,
                    episodeNumber INTEGER,
                    downloadProgress INTEGER NOT NULL DEFAULT 0,
                    isComplete INTEGER NOT NULL DEFAULT 0
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_content_contentType_contentId ON downloaded_content(contentType, contentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_content_seriesId ON downloaded_content(seriesId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloaded_content_cacheKey ON downloaded_content(cacheKey)")
        }
    }
    
    /**
     * Migration from version 12 to 13:
     * - Add cachedAt column to team_channel_map table
     * - Clear existing cache entries (they lack timestamps)
     */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add cachedAt column with default value of 0 (will be treated as expired)
            db.execSQL("ALTER TABLE team_channel_map ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
            // Clear existing cache so it's rebuilt with correct timestamps
            db.execSQL("DELETE FROM team_channel_map")
        }
    }
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
    
    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
    
    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()
    
    @Provides
    fun provideMovieDao(db: AppDatabase): MovieDao = db.movieDao()
    
    @Provides
    fun provideSeriesDao(db: AppDatabase): SeriesDao = db.seriesDao()
    
    @Provides
    fun provideEpisodeDao(db: AppDatabase): EpisodeDao = db.episodeDao()
    
    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    
    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    
    @Provides
    fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()
    
    @Provides
    fun provideCustomGroupDao(db: AppDatabase): CustomGroupDao = db.customGroupDao()
    
    @Provides
    fun provideWatchStateDao(db: AppDatabase): WatchStateDao = db.watchStateDao()
    
    @Provides
    fun provideTMDBCacheDao(db: AppDatabase): TMDBCacheDao = db.tmdbCacheDao()
    
    @Provides
    fun provideRecentlyWatchedDao(db: AppDatabase): RecentlyWatchedDao = db.recentlyWatchedDao()
    
    @Provides
    fun provideFavoriteCategoryDao(db: AppDatabase): FavoriteCategoryDao = db.favoriteCategoryDao()
    
    @Provides
    fun provideTeamChannelMapDao(db: AppDatabase): TeamChannelMapDao = db.teamChannelMapDao()
    
    @Provides
    fun provideDownloadedContentDao(db: AppDatabase): DownloadedContentDao = db.downloadedContentDao()
    
    @Provides
    @Singleton
    fun provideDatabaseCheckpointManager(db: AppDatabase): DatabaseCheckpointManager =
        DatabaseCheckpointManager(db)
}
