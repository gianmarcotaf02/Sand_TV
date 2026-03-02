package it.sandtv.app.data.parser

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Xtream Codes API responses
 * Uses Android's built-in JSON parsing to avoid KAPT conflicts
 */
@Singleton
class XtreamParser @Inject constructor() {
    
    companion object {
        private const val TAG = "XtreamParser"
    }
    
    /**
     * Parse live categories JSON
     */
    fun parseLiveCategories(json: String): List<XtreamCategory> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                XtreamCategory(
                    id = obj.optString("category_id", ""),
                    name = obj.optString("category_name", ""),
                    parentId = obj.optInt("parent_id", 0).takeIf { it != 0 }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing live categories", e)
            emptyList()
        }
    }
    
    /**
     * Parse live streams JSON
     */
    fun parseLiveStreams(json: String): List<XtreamStream> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                XtreamStream(
                    id = obj.optInt("stream_id", 0),
                    name = obj.optString("name", ""),
                    logo = obj.optString("stream_icon", "").takeIf { it.isNotEmpty() },
                    epgId = obj.optString("epg_channel_id", "").takeIf { it.isNotEmpty() },
                    categoryId = obj.optString("category_id", "").takeIf { it.isNotEmpty() },
                    hasArchive = obj.optInt("tv_archive", 0)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing live streams", e)
            emptyList()
        }
    }
    
    /**
     * Parse VOD categories JSON
     */
    fun parseVodCategories(json: String): List<XtreamCategory> = parseLiveCategories(json)
    
    /**
     * Parse VOD streams (movies) JSON
     */
    fun parseVodStreams(json: String): List<XtreamVod> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
                // Try multiple fields for poster URL (different providers use different fields)
                // Prioritize 'cover' like series since it works for them
                val posterUrl = obj.optString("cover", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("cover_big", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("stream_icon", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("movie_image", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("icon", "").takeIf { it.isNotEmpty() }
                
                // Parse added timestamp (can be string or long)
                val addedTimestamp = obj.optLong("added", 0).takeIf { it != 0L }
                    ?: obj.optString("added", "").toLongOrNull()
                    
                XtreamVod(
                    id = obj.optInt("stream_id", 0),
                    name = obj.optString("name", ""),
                    poster = posterUrl,
                    categoryId = obj.optString("category_id", "").takeIf { it.isNotEmpty() },
                    extension = obj.optString("container_extension", "").takeIf { it.isNotEmpty() },
                    rating = obj.optString("rating", "").takeIf { it.isNotEmpty() },
                    year = obj.optString("year", "").takeIf { it.isNotEmpty() },
                    added = addedTimestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VOD streams", e)
            emptyList()
        }
    }
    
    /**
     * Parse series categories JSON
     */
    fun parseSeriesCategories(json: String): List<XtreamCategory> = parseLiveCategories(json)
    
    /**
     * Parse series list JSON
     */
    fun parseSeries(json: String): List<XtreamSeries> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                // Try multiple fields for poster URL
                val posterUrl = obj.optString("cover", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("stream_icon", "").takeIf { it.isNotEmpty() }
                    ?: obj.optString("cover_big", "").takeIf { it.isNotEmpty() }
                
                // Parse added timestamp (can be string or long)
                val addedTimestamp = obj.optLong("added", 0).takeIf { it != 0L }
                    ?: obj.optString("added", "").toLongOrNull()
                    
                XtreamSeries(
                    id = obj.optInt("series_id", 0),
                    name = obj.optString("name", ""),
                    poster = posterUrl,
                    categoryId = obj.optString("category_id", "").takeIf { it.isNotEmpty() },
                    rating = obj.optString("rating", "").takeIf { it.isNotEmpty() },
                    year = obj.optString("year", "").takeIf { it.isNotEmpty() },
                    added = addedTimestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing series", e)
            emptyList()
        }
    }
    
    /**
     * Parse series info (episodes) JSON
     */
    fun parseSeriesInfo(json: String): XtreamSeriesInfo? {
        return try {
            val obj = JSONObject(json)
            val infoObj = obj.optJSONObject("info")
            val episodesObj = obj.optJSONObject("episodes")
            
            val info = infoObj?.let {
                XtreamSeriesDetails(
                    name = it.optString("name", "").takeIf { s -> s.isNotEmpty() },
                    poster = it.optString("cover", "").takeIf { s -> s.isNotEmpty() },
                    plot = it.optString("plot", "").takeIf { s -> s.isNotEmpty() },
                    cast = it.optString("cast", "").takeIf { s -> s.isNotEmpty() },
                    director = it.optString("director", "").takeIf { s -> s.isNotEmpty() },
                    genre = it.optString("genre", "").takeIf { s -> s.isNotEmpty() },
                    releaseDate = it.optString("releaseDate", "").takeIf { s -> s.isNotEmpty() },
                    rating = it.optString("rating", "").takeIf { s -> s.isNotEmpty() }
                )
            }
            
            val episodes = mutableMapOf<String, List<XtreamEpisode>>()
            episodesObj?.keys()?.forEach { season ->
                val seasonArray = episodesObj.getJSONArray(season)
                episodes[season] = (0 until seasonArray.length()).map { i ->
                    val ep = seasonArray.getJSONObject(i)
                    XtreamEpisode(
                        id = ep.optString("id", ""),
                        episodeNum = ep.optInt("episode_num", 0),
                        title = ep.optString("title", "").takeIf { s -> s.isNotEmpty() },
                        extension = ep.optString("container_extension", "").takeIf { s -> s.isNotEmpty() },
                        info = null
                    )
                }
            }
            
            XtreamSeriesInfo(info, episodes.takeIf { it.isNotEmpty() })
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing series info", e)
            null
        }
    }
    
    /**
     * Parse VOD info (movie details) JSON
     * This comes from the /get_vod_info endpoint
     */
    fun parseVodInfo(json: String): XtreamVodInfo? {
        return try {
            val obj = JSONObject(json)
            val infoObj = obj.optJSONObject("info") ?: obj.optJSONObject("movie_data") ?: obj
            
            XtreamVodInfo(
                tmdbId = infoObj.optInt("tmdb_id", 0).takeIf { it != 0 },
                name = infoObj.optString("name", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("title", "").takeIf { it.isNotEmpty() },
                plot = infoObj.optString("plot", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("description", "").takeIf { it.isNotEmpty() },
                cast = infoObj.optString("cast", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("actors", "").takeIf { it.isNotEmpty() },
                director = infoObj.optString("director", "").takeIf { it.isNotEmpty() },
                genre = infoObj.optString("genre", "").takeIf { it.isNotEmpty() },
                releaseDate = infoObj.optString("releasedate", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("release_date", "").takeIf { it.isNotEmpty() },
                runtime = infoObj.optString("duration", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("runtime", "").takeIf { it.isNotEmpty() },
                rating = infoObj.optString("rating", "").takeIf { it.isNotEmpty() },
                backdrop = infoObj.optString("backdrop_path", "").takeIf { it.isNotEmpty() } ?: infoObj.optString("cover_big", "").takeIf { it.isNotEmpty() },
                youtubeTrailer = infoObj.optString("youtube_trailer", "").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VOD info", e)
            null
        }
    }
}

// Simple data classes without annotations
data class XtreamCategory(
    val id: String,
    val name: String,
    val parentId: Int? = null
)

data class XtreamStream(
    val id: Int,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val categoryId: String?,
    val hasArchive: Int = 0
)

data class XtreamVod(
    val id: Int,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val extension: String?,
    val rating: String?,
    val year: String?,
    val added: Long? = null  // Unix timestamp when content was added by provider
)

data class XtreamSeries(
    val id: Int,
    val name: String,
    val poster: String?,
    val categoryId: String?,
    val rating: String?,
    val year: String?,
    val added: Long? = null  // Unix timestamp when content was added by provider
)

data class XtreamSeriesInfo(
    val info: XtreamSeriesDetails?,
    val episodes: Map<String, List<XtreamEpisode>>?
)

data class XtreamSeriesDetails(
    val name: String?,
    val poster: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?
)

data class XtreamEpisode(
    val id: String,
    val episodeNum: Int,
    val title: String?,
    val extension: String?,
    val info: XtreamEpisodeInfo?
)

data class XtreamEpisodeInfo(
    val image: String?,
    val plot: String?,
    val durationSecs: Int?,
    val releaseDate: String?
)

data class XtreamVodInfo(
    val tmdbId: Int?,
    val name: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val runtime: String?,
    val rating: String?,
    val backdrop: String?,
    val youtubeTrailer: String?
)

