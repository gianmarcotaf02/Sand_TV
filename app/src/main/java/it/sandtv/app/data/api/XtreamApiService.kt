package it.sandtv.app.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Xtream Codes API client
 * Supports standard Xtream API endpoints
 */
interface XtreamApiService {
    
    /**
     * Player API - Authentication and account info
     */
    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuthResponse
    
    /**
     * Get live stream categories
     */
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XtreamCategory>
    
    /**
     * Get live streams
     */
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<XtreamLiveStream>
    
    /**
     * Get VOD (movie) categories
     */
    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<XtreamCategory>
    
    /**
     * Get VOD streams
     */
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String? = null
    ): List<XtreamVodStream>
    
    /**
     * Get VOD info (movie details)
     */
    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_info",
        @Query("vod_id") vodId: Int
    ): XtreamVodInfo
    
    /**
     * Get series categories
     */
    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<XtreamCategory>
    
    /**
     * Get series
     */
    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String? = null
    ): List<XtreamSeries>
    
    /**
     * Get series info (with episodes)
     */
    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): XtreamSeriesInfo
    
    /**
     * Get short EPG for a stream
     */
    @GET("player_api.php")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int = 10
    ): XtreamEpgResponse
}

// Response models

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo?,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo?
)

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    val username: String?,
    val password: String?,
    val message: String?,
    val auth: Int?,
    val status: String?,
    @Json(name = "exp_date") val expDate: String?,
    @Json(name = "is_trial") val isTrial: String?,
    @Json(name = "active_cons") val activeCons: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "max_connections") val maxConnections: String?,
    @Json(name = "allowed_output_formats") val allowedOutputFormats: List<String>?
)

@JsonClass(generateAdapter = true)
data class XtreamServerInfo(
    val url: String?,
    val port: String?,
    @Json(name = "https_port") val httpsPort: String?,
    @Json(name = "server_protocol") val serverProtocol: String?,
    @Json(name = "rtmp_port") val rtmpPort: String?,
    val timezone: String?,
    @Json(name = "timestamp_now") val timestampNow: Long?,
    @Json(name = "time_now") val timeNow: String?
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int?
)

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    @Json(name = "num") val num: Int?,
    val name: String?,
    @Json(name = "stream_type") val streamType: String?,
    @Json(name = "stream_id") val streamId: Int?,
    @Json(name = "stream_icon") val streamIcon: String?,
    @Json(name = "epg_channel_id") val epgChannelId: String?,
    val added: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "custom_sid") val customSid: String?,
    @Json(name = "tv_archive") val tvArchive: Int?,
    @Json(name = "direct_source") val directSource: String?,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int?
)

@JsonClass(generateAdapter = true)
data class XtreamVodStream(
    @Json(name = "num") val num: Int?,
    val name: String?,
    @Json(name = "stream_type") val streamType: String?,
    @Json(name = "stream_id") val streamId: Int?,
    @Json(name = "stream_icon") val streamIcon: String?,
    val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float?,
    val added: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "container_extension") val containerExtension: String?,
    @Json(name = "custom_sid") val customSid: String?,
    @Json(name = "direct_source") val directSource: String?
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    val info: XtreamVodDetails?,
    @Json(name = "movie_data") val movieData: XtreamMovieData?
)

@JsonClass(generateAdapter = true)
data class XtreamVodDetails(
    @Json(name = "movie_image") val movieImage: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @Json(name = "release_date") val releaseDate: String?,
    val duration: String?,
    @Json(name = "duration_secs") val durationSecs: Int?
    // Note: video and audio fields removed - they can be arrays or objects causing parse errors
)

@JsonClass(generateAdapter = true)
data class XtreamMovieData(
    @Json(name = "stream_id") val streamId: Int?,
    val name: String?,
    val added: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "container_extension") val containerExtension: String?
)

@JsonClass(generateAdapter = true)
data class XtreamVideoInfo(
    val codec: String?,
    val width: Int?,
    val height: Int?
)

@JsonClass(generateAdapter = true)
data class XtreamAudioInfo(
    val codec: String?,
    val channels: Int?,
    @Json(name = "sample_rate") val sampleRate: Int?
)

@JsonClass(generateAdapter = true)
data class XtreamSeries(
    @Json(name = "num") val num: Int?,
    val name: String?,
    @Json(name = "series_id") val seriesId: Int?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "last_modified") val lastModified: String?,
    val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float?,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    val youtube: String?,
    @Json(name = "episode_run_time") val episodeRunTime: String?,
    @Json(name = "category_id") val categoryId: String?
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfo(
    val seasons: List<XtreamSeason>?,
    val info: XtreamSeriesDetails?,
    val episodes: Map<String, List<XtreamEpisode>>?
)

@JsonClass(generateAdapter = true)
data class XtreamSeason(
    @Json(name = "season_number") val seasonNumber: Int?,
    val name: String?,
    @Json(name = "episode_count") val episodeCount: Int?,
    val cover: String?,
    @Json(name = "air_date") val airDate: String?
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesDetails(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "last_modified") val lastModified: String?,
    val rating: String?,
    @Json(name = "rating_5based") val rating5Based: Float?,
    @Json(name = "backdrop_path") val backdropPath: List<String>?,
    val youtube: String?,
    @Json(name = "episode_run_time") val episodeRunTime: String?,
    @Json(name = "category_id") val categoryId: String?
)

@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    val id: String?,
    @Json(name = "episode_num") val episodeNum: Int?,
    val title: String?,
    @Json(name = "container_extension") val containerExtension: String?,
    val info: XtreamEpisodeInfo?,
    @Json(name = "custom_sid") val customSid: String?,
    val added: String?,
    val season: Int?,
    @Json(name = "direct_source") val directSource: String?
)

@JsonClass(generateAdapter = true)
data class XtreamEpisodeInfo(
    @Json(name = "movie_image") val movieImage: String?,
    val plot: String?,
    @Json(name = "release_date") val releaseDate: String?,
    val rating: String?,  // Changed to String? because Xtream API can return empty string ""
    @Json(name = "duration_secs") val durationSecs: Int?,
    val duration: String?,
    val video: XtreamVideoInfo?,
    val audio: XtreamAudioInfo?
)

@JsonClass(generateAdapter = true)
data class XtreamEpgResponse(
    @Json(name = "epg_listings") val epgListings: List<XtreamEpgListing>?
)

@JsonClass(generateAdapter = true)
data class XtreamEpgListing(
    val id: String?,
    @Json(name = "epg_id") val epgId: String?,
    val title: String?,
    val lang: String?,
    val start: String?,
    val end: String?,
    val description: String?,
    @Json(name = "channel_id") val channelId: String?,
    @Json(name = "start_timestamp") val startTimestamp: Long?,
    @Json(name = "stop_timestamp") val stopTimestamp: Long?,
    @Json(name = "now_playing") val nowPlaying: Int?,
    @Json(name = "has_archive") val hasArchive: Int?
)
