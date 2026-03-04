package it.sandtv.app.data.database.dao

import androidx.room.*
import it.sandtv.app.data.database.entity.Movie
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    
    @Query("SELECT * FROM movies WHERE isHidden = 0 ORDER BY name")
    fun getAllMovies(): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE isHidden = 0 ORDER BY name")
    suspend fun getAllMoviesList(): List<Movie>
    
    @Query("SELECT COUNT(*) FROM movies WHERE isHidden = 0")
    suspend fun getAllMoviesCount(): Int
    
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY name")
    fun getMoviesByPlaylist(playlistId: Long): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE category = :category AND isHidden = 0 ORDER BY name")
    fun getMoviesByCategory(category: String): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE category = :category AND isHidden = 0 AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL) ORDER BY name")
    suspend fun getMoviesByCategoryList(category: String): List<Movie>
    
    @Query("SELECT DISTINCT category FROM movies WHERE category IS NOT NULL AND isHidden = 0 ORDER BY category")
    fun getCategories(): Flow<List<String>>
    
    @Query("SELECT DISTINCT category FROM movies WHERE category IS NOT NULL AND isHidden = 0 ORDER BY category")
    suspend fun getCategoriesList(): List<String>
    
    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getMovieById(id: Long): Movie?
    
    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getMovieByTmdbId(tmdbId: Int): Movie?
    
    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    suspend fun searchMovies(query: String): List<Movie>
    
    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY name")
    fun searchMoviesFlow(query: String): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE category = :category AND isHidden = 0 ORDER BY name LIMIT :limit")
    fun getMoviesByCategory(category: String, limit: Int): Flow<List<Movie>>
    
    // Popular movies - sorts by TMDB popularity when available, random otherwise
    // This ensures carousel shows content even during initial TMDB enrichment
    @Query("SELECT * FROM movies WHERE isHidden = 0 AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL) ORDER BY COALESCE(tmdbPopularity, 0) DESC, RANDOM() LIMIT :limit")
    suspend fun getPopularMovies(limit: Int = 20): List<Movie>
    
    // Update only popularity score (for efficient refresh without overwriting enriched data)
    @Query("UPDATE movies SET tmdbPopularity = :popularity WHERE id = :id")
    suspend fun updatePopularityScore(id: Long, popularity: Float)
    
    @Query("SELECT * FROM movies WHERE isHidden = 0 ORDER BY COALESCE(tmdbPopularity, 0) DESC, RANDOM() LIMIT :limit")
    fun getPopularMoviesFlow(limit: Int): Flow<List<Movie>>
    
    // Recently added (only those with a poster image)
    @Query("SELECT * FROM movies WHERE isHidden = 0 AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL) ORDER BY addedAt DESC LIMIT :limit")
    suspend fun getRecentlyAddedMovies(limit: Int = 20): List<Movie>
    
    @Query("SELECT * FROM movies WHERE isHidden = 0 ORDER BY addedAt DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): Flow<List<Movie>>
    
    // Movies needing TMDB update
    @Query("SELECT * FROM movies WHERE tmdbId IS NULL AND isHidden = 0 ORDER BY addedAt DESC LIMIT :limit")
    suspend fun getMoviesNeedingTmdb(limit: Int = 50): List<Movie>
    
    // Random movies for hero banner (prioritize ones with images)
    @Query("SELECT * FROM movies WHERE isHidden = 0 AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomMovies(limit: Int = 5): List<Movie>
    
    // Get fully enriched movies for FAST hero loading (has overview AND OMDB rating = already enriched by LoadingActivity)
    @Query("""
        SELECT * FROM movies 
        WHERE isHidden = 0 
        AND tmdbOverview IS NOT NULL AND tmdbOverview != ''
        AND omdbImdbRating IS NOT NULL
        AND (logoUrl IS NOT NULL OR tmdbPosterPath IS NOT NULL OR tmdbBackdropPath IS NOT NULL)
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    suspend fun getFullyEnrichedMovies(limit: Int = 5): List<Movie>

    
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND xtreamStreamId = :xtreamStreamId LIMIT 1")
    suspend fun getByXtreamId(playlistId: Long, xtreamStreamId: Int): Movie?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: Movie): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<Movie>)
    
    @Update
    suspend fun update(movie: Movie)
    
    @Delete
    suspend fun delete(movie: Movie)
    
    @Update
    suspend fun updateList(movies: List<Movie>): Int

    @Update
    suspend fun updateMovies(movies: List<Movie>): Int
    
    @Delete
    suspend fun deleteList(movies: List<Movie>): Int
    
    /**
     * Upsert movie by xtreamStreamId to preserve Room ID
     * This is critical for WatchProgress to not become orphaned after playlist refresh
     */
    @Transaction
    suspend fun upsertByXtream(movie: Movie): Long {
        val xtreamId = movie.xtreamStreamId
        if (xtreamId != null) {
            val existing = getByXtreamId(movie.playlistId, xtreamId)
            if (existing != null) {
                // Update existing movie, keeping the same ID
                update(movie.copy(id = existing.id, 
                    // Preserve TMDB data if already enriched
                    tmdbId = existing.tmdbId ?: movie.tmdbId,
                    tmdbPosterPath = existing.tmdbPosterPath ?: movie.tmdbPosterPath,
                    tmdbBackdropPath = existing.tmdbBackdropPath ?: movie.tmdbBackdropPath,
                    tmdbTitle = existing.tmdbTitle ?: movie.tmdbTitle,
                    tmdbOriginalTitle = existing.tmdbOriginalTitle ?: movie.tmdbOriginalTitle,
                    tmdbOverview = existing.tmdbOverview ?: movie.tmdbOverview,
                    tmdbRuntime = existing.tmdbRuntime ?: movie.tmdbRuntime,
                    tmdbGenres = existing.tmdbGenres ?: movie.tmdbGenres,
                    tmdbVoteAverage = existing.tmdbVoteAverage ?: movie.tmdbVoteAverage,
                    tmdbPopularity = existing.tmdbPopularity ?: movie.tmdbPopularity,
                    tmdbCast = existing.tmdbCast ?: movie.tmdbCast,
                    tmdbDirector = existing.tmdbDirector ?: movie.tmdbDirector,
                    omdbImdbRating = existing.omdbImdbRating ?: movie.omdbImdbRating,
                    omdbRottenTomatoesScore = existing.omdbRottenTomatoesScore ?: movie.omdbRottenTomatoesScore,
                    omdbMetacriticScore = existing.omdbMetacriticScore ?: movie.omdbMetacriticScore,
                    omdbAudienceScore = existing.omdbAudienceScore ?: movie.omdbAudienceScore
                ))
                return existing.id
            }
        }
        // Insert new movie
        return insert(movie)
    }
    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
    
    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun getCountByPlaylist(playlistId: Long): Int
    
    @Query("SELECT COUNT(*) FROM movies WHERE category = :category AND isHidden = 0")
    suspend fun getMovieCountByCategory(category: String): Int
    
    // Trending categories
    @Query("SELECT * FROM movies WHERE trendingCategory = :category AND isHidden = 0 ORDER BY tmdbPopularity DESC")
    suspend fun getByTrendingCategory(category: String): List<Movie>
    
    @Query("UPDATE movies SET trendingCategory = NULL WHERE trendingCategory = :category")
    suspend fun clearTrendingCategory(category: String)
    
    // Categories with count
    @Query("""
        SELECT category as name, COUNT(*) as count 
        FROM movies 
        WHERE category IS NOT NULL AND isHidden = 0 
        GROUP BY category 
        ORDER BY category
    """)
    suspend fun getCategoriesWithCount(): List<CategoryWithCount>
}

data class CategoryWithCount(
    val name: String,
    val count: Int
)
