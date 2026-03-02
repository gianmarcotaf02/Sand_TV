package it.sandtv.app.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OMDb API Service (Open Movie Database)
 * Provides IMDB ratings and additional movie info
 * Free tier: 1,000 requests/day
 * Get API key at: https://www.omdbapi.com/apikey.aspx
 */
interface OmdbService {
    
    companion object {
        const val BASE_URL = "https://www.omdbapi.com/"
    }
    
    /**
     * Get movie/series by IMDB ID
     * tomatoes=true includes Rotten Tomatoes extended data (tomatoMeter, tomatoUserMeter, etc.)
     */
    @GET("/")
    suspend fun getByImdbId(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String,
        @Query("plot") plot: String = "short",  // "short" or "full"
        @Query("tomatoes") tomatoes: Boolean = true  // Include RT extended data
    ): Response<OmdbResult>
    
    /**
     * Get movie/series by title
     * tomatoes=true includes Rotten Tomatoes extended data
     */
    @GET("/")
    suspend fun getByTitle(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: Int? = null,
        @Query("type") type: String? = null, // "movie", "series", "episode"
        @Query("plot") plot: String = "short",
        @Query("tomatoes") tomatoes: Boolean = true  // Include RT extended data
    ): Response<OmdbResult>
    
    /**
     * Search movies/series
     */
    @GET("/")
    suspend fun search(
        @Query("apikey") apiKey: String,
        @Query("s") query: String,
        @Query("y") year: Int? = null,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1
    ): Response<OmdbSearchResponse>
}

// ========== Response Models ==========

data class OmdbResult(
    val Title: String?,
    val Year: String?,
    val Rated: String?,           // "PG-13", "R", etc.
    val Released: String?,
    val Runtime: String?,         // "148 min"
    val Genre: String?,           // "Action, Adventure, Sci-Fi"
    val Director: String?,
    val Writer: String?,
    val Actors: String?,
    val Plot: String?,
    val Language: String?,
    val Country: String?,
    val Awards: String?,
    val Poster: String?,
    val Ratings: List<OmdbRating>?,
    val Metascore: String?,       // "74"
    val imdbRating: String?,      // "8.4"
    val imdbVotes: String?,       // "1,234,567"
    val imdbID: String?,          // "tt1234567"
    val Type: String?,            // "movie", "series", "episode"
    val DVD: String?,
    val BoxOffice: String?,
    val Production: String?,
    val Website: String?,
    val Response: String?,        // "True" or "False"
    val Error: String?,           // Error message if Response is "False"
    
    // Series specific
    val totalSeasons: String?,
    
    // Episode specific
    val Season: String?,
    val Episode: String?,
    val seriesID: String?,
    
    // Rotten Tomatoes extended data (requires tomatoes=true)
    val tomatoMeter: String?,      // Critics score "91" or "N/A"
    val tomatoImage: String?,      // "certified", "fresh", "rotten", "N/A"
    val tomatoRating: String?,     // "8.3/10"
    val tomatoReviews: String?,    // "351"
    val tomatoFresh: String?,      // "320"
    val tomatoRotten: String?,     // "31"
    val tomatoConsensus: String?,  // Critics consensus text
    val tomatoUserMeter: String?,  // Audience score (Popcornmeter) "91" or "N/A"
    val tomatoUserRating: String?, // "4.3/5"
    val tomatoUserReviews: String?,// "250,000+"
    val tomatoURL: String?         // RT URL
)

data class OmdbRating(
    val Source: String,   // "Internet Movie Database", "Rotten Tomatoes", "Metacritic"
    val Value: String     // "8.4/10", "91%", "74/100"
)

data class OmdbSearchResponse(
    val Search: List<OmdbSearchResult>?,
    val totalResults: String?,
    val Response: String?,
    val Error: String?
)

data class OmdbSearchResult(
    val Title: String?,
    val Year: String?,
    val imdbID: String?,
    val Type: String?,
    val Poster: String?
)
