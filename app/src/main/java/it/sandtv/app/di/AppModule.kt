package it.sandtv.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.parser.ContentNameParser
import it.sandtv.app.data.parser.M3UParser
import it.sandtv.app.data.repository.DownloadContentManager
import it.sandtv.app.data.database.dao.DownloadedContentDao
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.EpisodeDao
import it.sandtv.app.data.database.dao.SeriesDao
import javax.inject.Singleton

/**
 * Hilt module for app-level dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideContentNameParser(): ContentNameParser {
        return ContentNameParser()
    }
    
    @Provides
    @Singleton
    fun provideM3UParser(contentNameParser: ContentNameParser): M3UParser {
        return M3UParser(contentNameParser)
    }
    
    @androidx.media3.common.util.UnstableApi
    @Provides
    @Singleton
    fun provideDownloadContentManager(
        @ApplicationContext context: Context,
        downloadedContentDao: DownloadedContentDao,
        movieDao: MovieDao,
        episodeDao: EpisodeDao,
        seriesDao: SeriesDao
    ): DownloadContentManager {
        return DownloadContentManager(context, downloadedContentDao, movieDao, episodeDao, seriesDao)
    }
}
