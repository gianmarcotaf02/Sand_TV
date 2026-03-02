package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Series
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    
    @Query("SELECT * FROM series WHERE isHidden = 0 ORDER BY name")
    fun getAllSeries(): Flow<List<Series>>
    
    @Query("SELECT * FROM series WHERE isHidden = 0 ORDER BY name")
    suspend fun getAllSeriesList(): List<Series>
    
    @Query("SELECT COUNT(*) FROM series WHERE isHidden = 0")
    suspend fun getAllSeriesCount(): Int
    
    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY name")
    fun getSeriesByPlaylist(playlistId: Long): Flow<List<Series>>
    
    @Query("SELECT * FROM series WHERE category = :category AND isHidden = 0 ORDER BY name")
    fun getSeriesByCategory(category: String): Flow<List<Series>>
    
    @Query("SELECT * FROM series WHERE category = :category AND isHidden = 0 ORDER BY name")
    suspend fun getSeriesByCategoryList(category: String): List<Series>
    
    @Query("SELECT DISTINCT category FROM series WHERE category IS NOT NULL AND isHidden = 0 ORDER BY category")
    fun getCategories(): Flow<List<String>>
    
    @Query("SELECT DISTINCT category FROM series WHERE category IS NOT NULL AND isHidden = 0 ORDER BY category")
    suspend fun getCategoriesList(): List<String>
    
    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getSeriesById(id: Long): Series?
    
    @Query("SELECT * FROM series WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getSeriesByTmdbId(tmdbId: Int): Series?
    
    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    suspend fun searchSeries(query: String): List<Series>
    
    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    fun searchSeriesFlow(query: String): Flow<List<Series>>
    
    @Query("SELECT * FROM series WHERE category = :category AND isHidden = 0 ORDER BY name LIMIT :limit")
    fun getSeriesByCategory(category: String, limit: Int): Flow<List<Series>>
    
    // Popular series - sorts by TMDB popularity when available, random otherwise
    // This ensures carousel shows content even during initial TMDB enrichment
    @Query("SELECT * FROM series WHERE isHidden = 0 ORDER BY COALESCE(tmdbPopularity, 0) DESC, RANDOM() LIMIT :limit")
    suspend fun getPopularSeries(limit: Int = 20): List<Series>
    
    // Update only popularity score (for efficient refresh without overwriting enriched data)
    @Query("UPDATE series SET tmdbPopularity = :popularity WHERE id = :id")
    suspend fun updatePopularityScore(id: Long, popularity: Float)
    
    @Query("SELECT * FROM series WHERE isHidden = 0 ORDER BY COALESCE(tmdbPopularity, 0) DESC, RANDOM() LIMIT :limit")
    fun getPopularSeriesFlow(limit: Int): Flow<List<Series>>
    
    // Recently added
    @Query("SELECT * FROM series WHERE isHidden = 0 ORDER BY addedAt DESC LIMIT :limit")
    suspend fun getRecentlyAddedSeries(limit: Int = 20): List<Series>
    
    // Series needing TMDB update
    @Query("SELECT * FROM series WHERE tmdbId IS NULL AND isHidden = 0 ORDER BY addedAt DESC LIMIT :limit")
    suspend fun getSeriesNeedingTmdb(limit: Int = 50): List<Series>
    
    // Random series for hero banner (prioritize ones with images)
    @Query("SELECT * FROM series WHERE isHidden = 0 AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSeries(limit: Int = 5): List<Series>
    
    // Get fully enriched series for FAST hero loading (has overview AND OMDB rating = already enriched by LoadingActivity)
    @Query("""
        SELECT * FROM series 
        WHERE isHidden = 0 
        AND tmdbOverview IS NOT NULL AND tmdbOverview != ''
        AND omdbImdbRating IS NOT NULL
        AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL OR tmdbBackdropPath IS NOT NULL)
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getFullyEnrichedSeries(limit: Int = 5): List<Series>

    
    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND xtreamSeriesId = :xtreamSeriesId LIMIT 1")
    suspend fun getByXtreamId(playlistId: Long, xtreamSeriesId: Int): Series?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(series: Series): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<Series>)
    
    @Update
    suspend fun update(series: Series)
    
    @Delete
    suspend fun delete(series: Series)
    
    @Update
    suspend fun updateList(series: List<Series>): Int
    
    @Delete
    suspend fun deleteList(series: List<Series>): Int

    @Update
    suspend fun updateSeries(series: List<Series>): Int
    
    /**
     * Upsert series by xtreamSeriesId to preserve Room ID
     * This is critical for WatchProgress to not become orphaned after playlist refresh
     */
    @Transaction
    suspend fun upsertByXtream(series: Series): Long {
        val xtreamId = series.xtreamSeriesId
        if (xtreamId != null) {
            val existing = getByXtreamId(series.playlistId, xtreamId)
            if (existing != null) {
                // Update existing series, keeping the same ID
                update(series.copy(id = existing.id,
                    // Preserve TMDB data if already enriched
                    tmdbId = existing.tmdbId ?: series.tmdbId,
                    tmdbPosterPath = existing.tmdbPosterPath ?: series.tmdbPosterPath,
                    tmdbBackdropPath = existing.tmdbBackdropPath ?: series.tmdbBackdropPath,
                    tmdbName = existing.tmdbName ?: series.tmdbName,
                    tmdbOriginalName = existing.tmdbOriginalName ?: series.tmdbOriginalName,
                    tmdbOverview = existing.tmdbOverview ?: series.tmdbOverview,
                    tmdbGenres = existing.tmdbGenres ?: series.tmdbGenres,
                    tmdbVoteAverage = existing.tmdbVoteAverage ?: series.tmdbVoteAverage,
                    tmdbPopularity = existing.tmdbPopularity ?: series.tmdbPopularity,
                    tmdbCast = existing.tmdbCast ?: series.tmdbCast,
                    omdbImdbRating = existing.omdbImdbRating ?: series.omdbImdbRating,
                    omdbRottenTomatoesScore = existing.omdbRottenTomatoesScore ?: series.omdbRottenTomatoesScore,
                    omdbMetacriticScore = existing.omdbMetacriticScore ?: series.omdbMetacriticScore,
                    omdbAudienceScore = existing.omdbAudienceScore ?: series.omdbAudienceScore
                ))
                return existing.id
            }
        }
        // Insert new series
        return insert(series)
    }
    
    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
    
    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun getCountByPlaylist(playlistId: Long): Int
    
    @Query("SELECT COUNT(*) FROM series WHERE category = :category AND isHidden = 0")
    suspend fun getSeriesCountByCategory(category: String): Int
    
    // Trending categories
    @Query("SELECT * FROM series WHERE trendingCategory = :category AND isHidden = 0 ORDER BY tmdbPopularity DESC")
    suspend fun getByTrendingCategory(category: String): List<Series>
    
    @Query("UPDATE series SET trendingCategory = NULL WHERE trendingCategory = :category")
    suspend fun clearTrendingCategory(category: String)
    
    // Categories with count
    @Query("""
        SELECT category as name, COUNT(*) as count 
        FROM series 
        WHERE category IS NOT NULL AND isHidden = 0 
        GROUP BY category 
        ORDER BY category
    """)
    suspend fun getCategoriesWithCount(): List<SeriesCategoryWithCount>
}

data class SeriesCategoryWithCount(
    val name: String,
    val count: Int
)
