package it.sandtv.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB API Service interface
 * Uses the user-provided API key from settings
 */
interface TMDBApiService {
    
    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
        
        fun getPosterUrl(path: String?, size: String = "w500"): String? {
            return path?.let { "$IMAGE_BASE_URL$size$it" }
        }
        
        fun getBackdropUrl(path: String?, size: String = "w1280"): String? {
            return path?.let { "$IMAGE_BASE_URL$size$it" }
        }
    }
    
    /**
     * Multi-search for movies and TV shows
     */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TMDBSearchResponse
    
    /**
     * Search for movies only
     */
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1,
        @Query("year") year: Int? = null
    ): TMDBMovieSearchResponse
    
    /**
     * Search for TV shows only
     */
    @GET("search/tv")
    suspend fun searchTV(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): TMDBTVSearchResponse
    
    /**
     * Get movie details
     */
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "it-IT",
        @Query("append_to_response") appendToResponse: String = "credits,external_ids"
    ): TMDBMovieDetails
    
    /**
     * Get TV show details
     */
    @GET("tv/{tv_id}")
    suspend fun getTVDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "it-IT",
        @Query("append_to_response") appendToResponse: String = "credits"
    ): TMDBTVDetails
    
    /**
     * Get TV season details
     */
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "it-IT"
    ): TMDBSeasonDetails
}

// Response models

@JsonClass(generateAdapter = true)
data class TMDBSearchResponse(
    val page: Int,
    val results: List<TMDBSearchResult>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)

@JsonClass(generateAdapter = true)
data class TMDBSearchResult(
    val id: Int,
    @Json(name = "media_type") val mediaType: String?, // "movie" or "tv"
    val title: String?, // For movies
    val name: String?, // For TV
    @Json(name = "original_title") val originalTitle: String?,
    @Json(name = "original_name") val originalName: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?, // For movies
    @Json(name = "first_air_date") val firstAirDate: String?, // For TV
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "vote_count") val voteCount: Int?,
    val popularity: Float?,
    @Json(name = "genre_ids") val genreIds: List<Int>?
) {
    val displayTitle: String get() = title ?: name ?: ""
    val displayOriginalTitle: String get() = originalTitle ?: originalName ?: ""
    val displayReleaseDate: String? get() = releaseDate ?: firstAirDate
}

@JsonClass(generateAdapter = true)
data class TMDBMovieSearchResponse(
    val page: Int,
    val results: List<TMDBMovieResult>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)

@JsonClass(generateAdapter = true)
data class TMDBMovieResult(
    val id: Int,
    val title: String?,
    @Json(name = "original_title") val originalTitle: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "vote_count") val voteCount: Int?,
    val popularity: Float?,
    @Json(name = "genre_ids") val genreIds: List<Int>?
)

@JsonClass(generateAdapter = true)
data class TMDBTVSearchResponse(
    val page: Int,
    val results: List<TMDBTVResult>,
    @Json(name = "total_pages") val totalPages: Int,
    @Json(name = "total_results") val totalResults: Int
)

@JsonClass(generateAdapter = true)
data class TMDBTVResult(
    val id: Int,
    val name: String?,
    @Json(name = "original_name") val originalName: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "vote_count") val voteCount: Int?,
    val popularity: Float?,
    @Json(name = "genre_ids") val genreIds: List<Int>?
)

@JsonClass(generateAdapter = true)
data class TMDBMovieDetails(
    val id: Int,
    val title: String?,
    @Json(name = "original_title") val originalTitle: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "vote_count") val voteCount: Int?,
    val popularity: Float?,
    val runtime: Int?,
    val genres: List<TMDBGenre>?,
    val credits: TMDBCredits?,
    @Json(name = "external_ids") val externalIds: TMDBExternalIds?
)

@JsonClass(generateAdapter = true)
data class TMDBExternalIds(
    @Json(name = "imdb_id") val imdbId: String?,
    @Json(name = "facebook_id") val facebookId: String?,
    @Json(name = "instagram_id") val instagramId: String?,
    @Json(name = "twitter_id") val twitterId: String?
)

@JsonClass(generateAdapter = true)
data class TMDBTVDetails(
    val id: Int,
    val name: String?,
    @Json(name = "original_name") val originalName: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    @Json(name = "vote_count") val voteCount: Int?,
    val popularity: Float?,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int?,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int?,
    val genres: List<TMDBGenre>?,
    val status: String?,
    val credits: TMDBCredits?
)

@JsonClass(generateAdapter = true)
data class TMDBSeasonDetails(
    val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "air_date") val airDate: String?,
    val episodes: List<TMDBEpisode>?
)

@JsonClass(generateAdapter = true)
data class TMDBEpisode(
    val id: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    @Json(name = "still_path") val stillPath: String?,
    @Json(name = "air_date") val airDate: String?,
    @Json(name = "vote_average") val voteAverage: Float?,
    val runtime: Int?
)

@JsonClass(generateAdapter = true)
data class TMDBGenre(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TMDBCredits(
    val cast: List<TMDBCast>?,
    val crew: List<TMDBCrew>?
)

@JsonClass(generateAdapter = true)
data class TMDBCast(
    val id: Int,
    val name: String,
    val character: String?,
    @Json(name = "profile_path") val profilePath: String?,
    val order: Int?
)

@JsonClass(generateAdapter = true)
data class TMDBCrew(
    val id: Int,
    val name: String,
    val job: String?,
    val department: String?,
    @Json(name = "profile_path") val profilePath: String?
)
