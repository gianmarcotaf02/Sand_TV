package it.sandtv.app.data.repository

import it.sandtv.app.data.api.SofascoreApi
import it.sandtv.app.data.model.Event
import it.sandtv.app.data.model.StandingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object SerieARepository {

    private const val BASE_URL = "https://api.sofascore.com/api/v1/"
    private const val SERIE_A_ID = 23 // Unique Tournament ID for Serie A
    private const val CURRENT_SEASON_ID = 76457 // 25/26 Season ID

    // IMPORTANT: Sofascore often blocks plain requests. We might need headers.
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://www.sofascore.com/")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    private val api: SofascoreApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SofascoreApi::class.java)
    }

    // --- Caching Variables ---
    private var cachedStandings: List<StandingRow>? = null
    private var lastStandingsFetchTime: Long = 0
    private const val STANDINGS_CACHE_DURATION = 60 * 60 * 1000L // 1 Hour

    private var cachedNextMatches: List<Event>? = null
    private var lastNextMatchesFetchTime: Long = 0
    private const val MATCHES_CACHE_DURATION = 4 * 60 * 60 * 1000L // 4 Hours

    // --- Public API ---

    suspend fun getStandings(forceRefresh: Boolean = false): Result<List<StandingRow>> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // Return cached if valid and not forced
        if (!forceRefresh && cachedStandings != null && (currentTime - lastStandingsFetchTime < STANDINGS_CACHE_DURATION)) {
            return@withContext Result.success(cachedStandings!!)
        }

        try {
            val response = api.getStandings(SERIE_A_ID, CURRENT_SEASON_ID)
            
            // Usually returns "total", "home", "away". We want "total".
            val totalStandings = response.standings.find { it.type == "total" }?.rows ?: emptyList()
            
            // Update cache
            if (totalStandings.isNotEmpty()) {
                cachedStandings = totalStandings
                lastStandingsFetchTime = currentTime
            }
            
            Result.success(totalStandings)
        } catch (e: Exception) {
            e.printStackTrace()
            // Return old cache if API fails
            if (cachedStandings != null) Result.success(cachedStandings!!)
            else Result.failure(e)
        }
    }

    suspend fun getNextMatches(forceRefresh: Boolean = false): Result<List<Event>> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        if (!forceRefresh && cachedNextMatches != null && (currentTime - lastNextMatchesFetchTime < MATCHES_CACHE_DURATION)) {
            return@withContext Result.success(cachedNextMatches!!)
        }

        try {
            val response = api.getNextEvents(SERIE_A_ID, CURRENT_SEASON_ID, 0)
            
            if (response.events.isNotEmpty()) {
                cachedNextMatches = response.events
                lastNextMatchesFetchTime = currentTime
            }
            Result.success(response.events)
        } catch (e: Exception) {
            e.printStackTrace()
            // Return old cache if API fails
            if (cachedNextMatches != null) Result.success(cachedNextMatches!!)
            else Result.failure(e)
        }
    }
    
    suspend fun getEventsByRound(round: Int): Result<List<Event>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEventsByRound(SERIE_A_ID, CURRENT_SEASON_ID, round)
            Result.success(response.events)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getLastMatches(): Result<List<Event>> = withContext(Dispatchers.IO) {
        // Last matches (Results) might update more often (live games finishing). 
        // We can cache them for shorter time (e.g. 10 mins) or just fetch.
        // For now, let's keep direct fetch for results to ensure freshness.
        try {
            val response = api.getLastEvents(SERIE_A_ID, CURRENT_SEASON_ID, 0)
            Result.success(response.events)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    // --- Match Details ---
    
    // Simple caching for details could be added (e.g. Map<Long, IncidentsResponse>) 
    // but for now relying on user navigation pattern (open once).
    
    suspend fun getIncidents(eventId: Long): Result<it.sandtv.app.data.model.IncidentsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getIncidents(eventId)
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getLineups(eventId: Long): Result<it.sandtv.app.data.model.LineupsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLineups(eventId)
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getEvent(eventId: Long): Result<Event> = withContext(Dispatchers.IO) {
        try {
            val response = api.getEvent(eventId)
            Result.success(response.event)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getStatistics(eventId: Long): Result<it.sandtv.app.data.model.StatisticsResponse> = withContext(Dispatchers.IO) {
        try {
             val response = api.getStatistics(eventId)
             Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    // --- Team Details ---
    
    // Cache for team details (infrequent changes)
    private val teamDetailsCache = mutableMapOf<Long, it.sandtv.app.data.model.TeamDetailsResponse>()
    private val teamDetailsFetchTimes = mutableMapOf<Long, Long>()
    private const val TEAM_DETAILS_CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 Hours
    
    suspend fun getTeamDetails(teamId: Long, forceRefresh: Boolean = false): Result<it.sandtv.app.data.model.TeamDetailsResponse> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // Check cache
        if (!forceRefresh && teamDetailsCache.containsKey(teamId)) {
            val lastFetch = teamDetailsFetchTimes[teamId] ?: 0
            if (currentTime - lastFetch < TEAM_DETAILS_CACHE_DURATION) {
                return@withContext Result.success(teamDetailsCache[teamId]!!)
            }
        }
        
        try {
            val response = api.getTeamDetails(teamId)
            
            // Update cache
            teamDetailsCache[teamId] = response
            teamDetailsFetchTimes[teamId] = currentTime
            
            Result.success(response)
        } catch (e: Exception) {
            e.printStackTrace()
            // Return cached value on error if available
            if (teamDetailsCache.containsKey(teamId)) {
                Result.success(teamDetailsCache[teamId]!!)
            } else {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getTeamNextEvents(teamId: Long): Result<List<it.sandtv.app.data.model.Event>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTeamNextEvents(teamId, 0)
            Result.success(response.events)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    suspend fun getTeamLastEvents(teamId: Long): Result<List<it.sandtv.app.data.model.Event>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTeamLastEvents(teamId, 0)
            Result.success(response.events)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
