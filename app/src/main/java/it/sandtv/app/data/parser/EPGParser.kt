package it.sandtv.app.data.parser

import it.sandtv.app.data.database.entity.EPGProgram
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for XMLTV/EPG XML files
 * Supports standard XMLTV format used by most IPTV providers
 */
@Singleton
class EPGParser @Inject constructor() {
    
    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    )
    
    data class EPGParseResult(
        val programs: List<EPGProgram>,
        val channelMapping: Map<String, String> // epgChannelId -> channelName
    )
    
    /**
     * Parse XMLTV EPG from input stream
     */
    fun parse(inputStream: InputStream, channelIdToDbId: Map<String, Long>): EPGParseResult {
        val programs = mutableListOf<EPGProgram>()
        val channelMapping = mutableMapOf<String, String>()
        
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")
        
        var eventType = parser.eventType
        var currentChannelId: String? = null
        var currentProgram: TempProgram? = null
        var inProgramme = false
        var inChannel = false
        var currentText = ""
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            inChannel = true
                            currentChannelId = parser.getAttributeValue(null, "id")
                        }
                        "display-name" -> {
                            // Will read text in TEXT event
                        }
                        "programme" -> {
                            inProgramme = true
                            val channelRef = parser.getAttributeValue(null, "channel")
                            val start = parser.getAttributeValue(null, "start")
                            val stop = parser.getAttributeValue(null, "stop")
                            
                            currentProgram = TempProgram(
                                epgChannelId = channelRef ?: "",
                                startTime = parseDate(start),
                                endTime = parseDate(stop)
                            )
                        }
                        "title" -> {
                            // Will read in TEXT
                        }
                        "desc" -> {
                            // Will read in TEXT
                        }
                        "category" -> {
                            // Will read in TEXT
                        }
                        "icon" -> {
                            currentProgram?.iconUrl = parser.getAttributeValue(null, "src")
                        }
                        "episode-num" -> {
                            // Will read in TEXT
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    currentText = parser.text?.trim() ?: ""
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            inChannel = false
                            currentChannelId = null
                        }
                        "display-name" -> {
                            if (inChannel && currentChannelId != null) {
                                channelMapping[currentChannelId] = currentText
                            }
                        }
                        "programme" -> {
                            inProgramme = false
                            currentProgram?.let { prog ->
                                // Map epgChannelId to our database channelId
                                val dbChannelId = channelIdToDbId[prog.epgChannelId]
                                if (dbChannelId != null && prog.startTime != null && prog.endTime != null) {
                                    programs.add(
                                        EPGProgram(
                                            channelId = dbChannelId,
                                            epgChannelId = prog.epgChannelId,
                                            title = prog.title ?: "Programma sconosciuto",
                                            description = prog.description,
                                            startTime = prog.startTime,
                                            endTime = prog.endTime,
                                            category = prog.category,
                                            iconUrl = prog.iconUrl,
                                            episode = prog.episodeInfo
                                        )
                                    )
                                }
                            }
                            currentProgram = null
                        }
                        "title" -> {
                            if (inProgramme) {
                                currentProgram?.title = currentText
                            }
                        }
                        "desc" -> {
                            if (inProgramme) {
                                currentProgram?.description = currentText
                            }
                        }
                        "category" -> {
                            if (inProgramme) {
                                currentProgram?.category = currentText
                            }
                        }
                        "episode-num" -> {
                            if (inProgramme) {
                                currentProgram?.episodeInfo = currentText
                                parseEpisodeNumber(currentText)?.let { (season, episode) ->
                                    currentProgram?.seasonNumber = season
                                    currentProgram?.episodeNumber = episode
                                    currentProgram?.episodeInfo = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        
        return EPGParseResult(programs, channelMapping)
    }
    
    private fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        
        for (format in dateFormats) {
            try {
                return format.parse(dateStr)?.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return null
    }
    
    private fun parseEpisodeNumber(epNum: String): Pair<Int, Int>? {
        // Format: "S01E05" or "1.5" or "1/5"
        val patterns = listOf(
            """[sS](\d+)[eE](\d+)""".toRegex(),
            """(\d+)\.(\d+)""".toRegex(),
            """(\d+)/(\d+)""".toRegex()
        )
        
        for (pattern in patterns) {
            pattern.find(epNum)?.let { match ->
                val season = match.groupValues[1].toIntOrNull() ?: return@let
                val episode = match.groupValues[2].toIntOrNull() ?: return@let
                return Pair(season, episode)
            }
        }
        return null
    }
    
    private data class TempProgram(
        val epgChannelId: String,
        val startTime: Long?,
        val endTime: Long?,
        var title: String? = null,
        var description: String? = null,
        var category: String? = null,
        var iconUrl: String? = null,
        var seasonNumber: Int? = null,
        var episodeNumber: Int? = null,
        var episodeInfo: String? = null
    )
}
