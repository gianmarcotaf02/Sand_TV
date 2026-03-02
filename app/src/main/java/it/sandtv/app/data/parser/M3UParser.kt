package it.sandtv.app.data.parser

import android.util.Log
import it.sandtv.app.data.database.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced M3U/M3U8 Playlist Parser with intelligent content detection
 * 
 * Parses IPTV playlists and:
 * - Detects content type (live/movie/series) from category and name
 * - Extracts clean titles for TMDB matching
 * - Groups same content from different sources
 * - Preserves original categories from playlist
 */
@Singleton
class M3UParser @Inject constructor(
    private val contentNameParser: ContentNameParser
) {
    companion object {
        private const val TAG = "M3UParser"
        private const val EXTINF_PREFIX = "#EXTINF:"
        private const val EXTM3U = "#EXTM3U"
    }
    
    data class ParseResult(
        val channels: List<ParsedChannel>,
        val movies: List<ParsedMovie>,
        val series: List<ParsedSeries>,
        val categories: Map<String, List<String>>  // Type -> List of categories
    )
    
    data class ParsedChannel(
        val name: String,
        val streamUrl: String,
        val logoUrl: String?,
        val category: String,
        val epgId: String?,
        val quality: StreamQuality
    )
    
    data class ParsedMovie(
        val originalName: String,    // Original name from playlist
        val cleanName: String,       // Clean name for TMDB search
        val streamUrl: String,
        val logoUrl: String?,
        val category: String,
        val year: Int?,
        val quality: StreamQuality,
        val language: String?,
        val isExtended: Boolean,
        val isHdr: Boolean,
        val playlistOrder: Int = 0   // Position in M3U file
    )
    
    data class ParsedSeries(
        val originalName: String,
        val cleanName: String,       // Clean name (without S01E01) for TMDB
        val streamUrl: String,
        val logoUrl: String?,
        val category: String,
        val season: Int?,
        val episode: Int?,
        val quality: StreamQuality,
        val language: String?,
        val playlistOrder: Int = 0   // Position in M3U file
    )
    
    /**
     * Parse M3U playlist from InputStream
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun parse(inputStream: InputStream, playlistId: Long): ParseResult = withContext(Dispatchers.IO) {
        val channels = mutableListOf<ParsedChannel>()
        val movies = mutableListOf<ParsedMovie>()
        val series = mutableListOf<ParsedSeries>()
        val categoryMap = mutableMapOf<String, MutableSet<String>>()
        
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var currentInfo: ExtInfInfo? = null
        var entryCounter = 0  // Track position in M3U file
        
        while (reader.readLine().also { line = it } != null) {
            val trimmedLine = line?.trim() ?: continue
            
            when {
                trimmedLine.startsWith(EXTINF_PREFIX) -> {
                    currentInfo = parseExtInf(trimmedLine)
                }
                trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") && currentInfo != null -> {
                    // This is a stream URL
                    val streamUrl = trimmedLine
                    processEntry(currentInfo, streamUrl, channels, movies, series, categoryMap, entryCounter)
                    entryCounter++  // Increment for next entry
                    currentInfo = null
                }
            }
        }
        
        Log.d(TAG, "Parsed: ${channels.size} channels, ${movies.size} movies, ${series.size} series")
        
        ParseResult(
            channels = channels,
            movies = movies,
            series = series,
            categories = categoryMap.mapValues { it.value.toList() }
        )
    }
    
    /**
     * Parse M3U from URL string content
     */
    suspend fun parseContent(content: String, playlistId: Long): ParseResult = withContext(Dispatchers.IO) {
        content.byteInputStream().use { parse(it, playlistId) }
    }
    
    private fun processEntry(
        info: ExtInfInfo,
        streamUrl: String,
        channels: MutableList<ParsedChannel>,
        movies: MutableList<ParsedMovie>,
        series: MutableList<ParsedSeries>,
        categoryMap: MutableMap<String, MutableSet<String>>,
        playlistOrder: Int = 0
    ) {
        // Parse the name intelligently
        val parsed = contentNameParser.parse(info.name, info.groupTitle)
        
        when (parsed.contentType) {
            ContentNameParser.ContentType.LIVE_TV -> {
                val category = contentNameParser.normalizeCategory(info.groupTitle)
                channels.add(ParsedChannel(
                    name = info.name,
                    streamUrl = streamUrl,
                    logoUrl = info.logo,
                    category = category,
                    epgId = info.tvgId,
                    quality = parsed.quality
                ))
                categoryMap.getOrPut("channels") { mutableSetOf() }.add(category)
            }
            
            ContentNameParser.ContentType.SERIES -> {
                val category = contentNameParser.normalizeSeriesCategory(info.groupTitle)
                series.add(ParsedSeries(
                    originalName = info.name,
                    cleanName = parsed.cleanTitle,
                    streamUrl = streamUrl,
                    logoUrl = info.logo,
                    category = category,
                    season = parsed.season,
                    episode = parsed.episode,
                    quality = parsed.quality,
                    language = parsed.language,
                    playlistOrder = playlistOrder
                ))
                categoryMap.getOrPut("series") { mutableSetOf() }.add(category)
            }
            
            ContentNameParser.ContentType.MOVIE, ContentNameParser.ContentType.UNKNOWN -> {
                // Default to movie for VOD content
                val category = contentNameParser.normalizeMovieCategory(info.groupTitle)
                movies.add(ParsedMovie(
                    originalName = info.name,
                    cleanName = parsed.cleanTitle,
                    streamUrl = streamUrl,
                    logoUrl = info.logo,
                    category = category,
                    year = info.year ?: parsed.year,
                    quality = parsed.quality,
                    language = parsed.language,
                    isExtended = parsed.isExtended,
                    isHdr = parsed.isHdr,
                    playlistOrder = playlistOrder
                ))
                categoryMap.getOrPut("movies") { mutableSetOf() }.add(category)
            }
        }
    }
    
    /**
     * Parse #EXTINF line and extract attributes
     */
    private fun parseExtInf(line: String): ExtInfInfo {
        // Example: #EXTINF:-1 tvg-id="rai1.it" tvg-logo="http://..." group-title="Italy",Rai 1 HD
        
        var name = ""
        var duration: Int
        var tvgId: String?
        var tvgName: String?
        var logo: String?
        var groupTitle: String?
        var yearAttributes: Int? = null
        
        // Extract name (after last comma)
        val commaIndex = line.lastIndexOf(',')
        if (commaIndex != -1) {
            name = line.substring(commaIndex + 1).trim()
        }
        
        val attributesPart = if (commaIndex != -1) {
            line.substring(EXTINF_PREFIX.length, commaIndex)
        } else {
            line.substring(EXTINF_PREFIX.length)
        }
        
        // Parse duration (first number)
        val durationMatch = Regex("""^(-?\d+)""").find(attributesPart.trim())
        duration = durationMatch?.value?.toIntOrNull() ?: -1
        
        // Parse attributes
        tvgId = extractAttribute(attributesPart, "tvg-id")
        tvgName = extractAttribute(attributesPart, "tvg-name")
        logo = extractAttribute(attributesPart, "tvg-logo")
        groupTitle = extractAttribute(attributesPart, "group-title")
        
        // Extract Year from attributes (year, generic release, copyright, etc)
        val yearStr = extractAttribute(attributesPart, "year") ?: 
                      extractAttribute(attributesPart, "production_year") ?:
                      extractAttribute(attributesPart, "release_date") ?:
                      extractAttribute(attributesPart, "tvg-shift") // Sometimes misused as year
        
        yearAttributes = yearStr?.toIntOrNull()
        if (yearAttributes == null && yearStr != null) {
            // Try to extract first 4 digits if string contains date (e.g. 2023-01-01)
            val yMatch = Regex("(\\d{4})").find(yearStr)
            yearAttributes = yMatch?.value?.toIntOrNull()
        }
        
        // Fallback: use tvg-name if name is empty
        if (name.isEmpty() && tvgName != null) {
            name = tvgName
        }
        
        return ExtInfInfo(
            name = name,
            duration = duration,
            tvgId = tvgId,
            tvgName = tvgName,
            logo = logo,
            groupTitle = groupTitle,
            year = yearAttributes
        )
    }
    
    private fun extractAttribute(text: String, attributeName: String): String? {
        // Match: attribute="value" or attribute='value'
        val patterns = listOf(
            Regex("""$attributeName="([^"]*?)""""),
            Regex("""$attributeName='([^']*?)"""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
    
    private data class ExtInfInfo(
        val name: String,
        val duration: Int,
        val tvgId: String?,
        val tvgName: String?,
        val logo: String?,
        val groupTitle: String?,
        val year: Int? = null
    )
}
