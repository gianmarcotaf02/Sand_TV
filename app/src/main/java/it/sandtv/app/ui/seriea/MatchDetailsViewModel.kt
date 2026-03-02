package it.sandtv.app.ui.seriea

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.sandtv.app.data.model.IncidentsResponse
import it.sandtv.app.data.model.LineupsResponse
import it.sandtv.app.data.model.StatisticsResponse
import it.sandtv.app.data.repository.SerieARepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchDetailsViewModel @Inject constructor(
    private val channelDao: it.sandtv.app.data.database.dao.ChannelDao,
    private val teamChannelMapDao: it.sandtv.app.data.database.dao.TeamChannelMapDao
) : ViewModel() {

    private val _incidents = MutableStateFlow<IncidentsResponse?>(null)
    val incidents: StateFlow<IncidentsResponse?> = _incidents.asStateFlow()

    private val _lineups = MutableStateFlow<LineupsResponse?>(null)
    val lineups: StateFlow<LineupsResponse?> = _lineups.asStateFlow()

    private val _statistics = MutableStateFlow<StatisticsResponse?>(null)
    val statistics: StateFlow<StatisticsResponse?> = _statistics.asStateFlow()
    
    // Live Channels
    private val _liveChannels = MutableStateFlow<List<it.sandtv.app.data.database.entity.Channel>>(emptyList())
    val liveChannels: StateFlow<List<it.sandtv.app.data.database.entity.Channel>> = _liveChannels.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _event = MutableStateFlow<it.sandtv.app.data.model.Event?>(null)
    val event: StateFlow<it.sandtv.app.data.model.Event?> = _event.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun loadDetails(eventId: Long) {
        viewModelScope.launch {
            _loading.value = true
            
            // Fetch everything in parallel
            val eventJob = launch {
                val result = SerieARepository.getEvent(eventId)
                if (result.isSuccess) {
                    val event = result.getOrNull()
                    _event.value = event
                    // Start polling if live
                    if (event?.status?.type == "inprogress") {
                        startPolling(eventId)
                    }
                }
            }

            val incidentsJob = launch {
                val result = SerieARepository.getIncidents(eventId)
                if (result.isSuccess) _incidents.value = result.getOrNull()
            }
            
            val lineupsJob = launch {
                 val result = SerieARepository.getLineups(eventId)
                if (result.isSuccess) _lineups.value = result.getOrNull()
            }
            
            val statsJob = launch {
                 val result = SerieARepository.getStatistics(eventId)
                if (result.isSuccess) _statistics.value = result.getOrNull()
            }
            
            eventJob.join()
            incidentsJob.join()
            lineupsJob.join()
            statsJob.join()

            _loading.value = false
        }
    }
    
    private fun startPolling(eventId: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000) // 1 minute
                val result = SerieARepository.getEvent(eventId)
                if (result.isSuccess) {
                    val event = result.getOrNull()
                    _event.value = event
                    if (event?.status?.type != "inprogress") {
                        break // Stop polling if finished
                    }
                    // Also refresh incidents
                    val incResult = SerieARepository.getIncidents(eventId)
                    if (incResult.isSuccess) _incidents.value = incResult.getOrNull()
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
    
    // Comprehensive alias map: API team name keywords → IPTV search keywords
    private val teamAliases: Map<String, List<String>> = mapOf(
        // Each entry: lowercase keyword from API name → list of search terms for IPTV channels
        "inter" to listOf("inter"),
        "milan" to listOf("milan"),
        "juventus" to listOf("juventus", "juve"),
        "napoli" to listOf("napoli"),
        "roma" to listOf("roma"),
        "lazio" to listOf("lazio"),
        "atalanta" to listOf("atalanta"),
        "fiorentina" to listOf("fiorentina"),
        "bologna" to listOf("bologna"),
        "torino" to listOf("torino"),
        "udinese" to listOf("udinese"),
        "empoli" to listOf("empoli"),
        "cagliari" to listOf("cagliari"),
        "genoa" to listOf("genoa"),
        "parma" to listOf("parma"),
        "como" to listOf("como"),
        "verona" to listOf("hellas", "verona"),
        "hellas" to listOf("hellas", "verona"),
        "venezia" to listOf("venezia"),
        "lecce" to listOf("lecce"),
        "monza" to listOf("monza"),
        "sassuolo" to listOf("sassuolo"),
        "salernitana" to listOf("salernitana"),
        "frosinone" to listOf("frosinone"),
        "sampdoria" to listOf("sampdoria", "samp"),
        "cremonese" to listOf("cremonese"),
        "spezia" to listOf("spezia"),
    )

    companion object {
        private const val TAG = "MatchDetailsVM"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun searchLiveChannels(homeTeam: String, awayTeam: String) {
        viewModelScope.launch {
            val allResults = mutableListOf<it.sandtv.app.data.database.entity.Channel>()

            suspend fun getChannelsForTeam(teamName: String): List<it.sandtv.app.data.database.entity.Channel> {
                // Check cache (with expiry)
                val cachedMap = teamChannelMapDao.getMapForTeam(teamName)
                val now = System.currentTimeMillis()

                if (cachedMap != null && cachedMap.channelIds.isNotEmpty() 
                    && (now - cachedMap.cachedAt) < CACHE_DURATION_MS) {
                    val ids = cachedMap.channelIds.split(",").mapNotNull { it.toLongOrNull() }
                    val channels = channelDao.getChannelsByIds(ids)
                    android.util.Log.d(TAG, "Cache HIT for '$teamName': ${channels.size} channels")
                    return channels
                }

                // Cache Miss or Expired: Perform Search
                android.util.Log.d(TAG, "Cache MISS/EXPIRED for '$teamName', searching...")
                
                val keywords = mutableSetOf<String>()
                val lower = teamName.lowercase().trim()

                // 1. Check alias map
                for ((key, aliases) in teamAliases) {
                    if (lower.contains(key)) {
                        keywords.addAll(aliases)
                    }
                }

                // 2. If no alias match, use smart cleanup
                if (keywords.isEmpty()) {
                    keywords.add(teamName.trim())
                    
                    var clean = lower
                    val prefixes = listOf("ac ", "as ", "ss ", "ssc ", "fc ", "us ", "ssd ", "sc ")
                    val suffixes = listOf(" fc", " calcio", " spa", " srl", " ac", " as", " bc", " ssc")
                    
                    prefixes.forEach { clean = clean.removePrefix(it) }
                    suffixes.forEach { clean = clean.removeSuffix(it) }
                    
                    clean = clean.trim()
                    if (clean.length > 2 && clean != lower) {
                        keywords.add(clean)
                    }
                }

                android.util.Log.d(TAG, "Searching for '$teamName' with keywords: $keywords")

                // 3. Search channels by name AND category
                val searchResults = mutableListOf<it.sandtv.app.data.database.entity.Channel>()
                keywords.forEach { query ->
                    if (query.isNotEmpty()) {
                        searchResults.addAll(channelDao.searchChannels(query))
                    }
                }
                val distinctChannels = searchResults.distinctBy { it.id }

                android.util.Log.d(TAG, "Found ${distinctChannels.size} channels for '$teamName'")

                // Cache results (even if empty, to avoid repeated searches — will expire in 24h)
                val idsString = distinctChannels.joinToString(",") { it.id.toString() }
                teamChannelMapDao.insert(
                    it.sandtv.app.data.database.entity.TeamChannelMap(
                        teamName = teamName,
                        channelIds = idsString,
                        cachedAt = now
                    )
                )

                return distinctChannels
            }

            // Fetch for Home and Away
            if (homeTeam.isNotEmpty()) allResults.addAll(getChannelsForTeam(homeTeam))
            if (awayTeam.isNotEmpty()) allResults.addAll(getChannelsForTeam(awayTeam))

            // Update State (deduplicated)
            _liveChannels.value = allResults.distinctBy { it.id }
            android.util.Log.d(TAG, "Total live channels: ${_liveChannels.value.size} for $homeTeam vs $awayTeam")
        }
    }
}
