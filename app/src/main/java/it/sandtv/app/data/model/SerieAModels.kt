package it.sandtv.app.data.model

import com.google.gson.annotations.SerializedName

// --- Wrappers ---

data class StandingsResponse(
    @SerializedName("standings") val standings: List<StandingSection>
)

data class EventsResponse(
    @SerializedName("events") val events: List<Event>
)

data class SingleEventResponse(
    @SerializedName("event") val event: Event
)

// --- Core Models ---

data class StandingSection(
    @SerializedName("tournament") val tournament: Tournament,
    @SerializedName("type") val type: String, // "total", "home", "away"
    @SerializedName("rows") val rows: List<StandingRow>
)

data class StandingRow(
    @SerializedName("team") val team: Team,
    @SerializedName("position") val position: Int,
    @SerializedName("matches") val matches: Int,
    @SerializedName("wins") val wins: Int,
    @SerializedName("draws") val draws: Int,
    @SerializedName("losses") val losses: Int,
    @SerializedName("scoresFor") val scoresFor: Int,
    @SerializedName("scoresAgainst") val scoresAgainst: Int,
    @SerializedName("points") val points: Int
)

data class Event(
    @SerializedName("id") val id: Long,
    @SerializedName("tournament") val tournament: Tournament,
    @SerializedName("homeTeam") val homeTeam: Team,
    @SerializedName("awayTeam") val awayTeam: Team,
    @SerializedName("status") val status: EventStatus,
    @SerializedName("homeScore") val homeScore: Score,
    @SerializedName("awayScore") val awayScore: Score,
    @SerializedName("startTimestamp") val startTimestamp: Long,
    @SerializedName("roundInfo") val roundInfo: RoundInfo?
)

data class RoundInfo(
    @SerializedName("round") val round: Int,
    @SerializedName("name") val name: String?
)

data class EventStatus(
    @SerializedName("code") val code: Int,
    @SerializedName("description") val description: String, // "Ended", "Not started", "1st half"
    @SerializedName("type") val type: String, // "finished", "inprogress", "notstarted"
    @SerializedName("statusTime") val statusTime: StatusTime? = null // Live match current minute
)

data class StatusTime(
    @SerializedName("current") val current: Int?, // Current minute (e.g., 34)
    @SerializedName("timestamp") val timestamp: Long?, // When the status was updated
    @SerializedName("extra") val extra: Int? = null, // Extra time minutes
    @SerializedName("currentPeriodStartTimestamp") val currentPeriodStartTimestamp: Long? = null
)

data class Team(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("shortName") val shortName: String,
    // Sofascore doesn't always send logo URL directly in team object, we construct it:
    // https://api.sofascore.app/api/v1/team/{id}/image
)

data class Tournament(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String
)

data class Score(
    @SerializedName("current") val current: Int?,
    @SerializedName("display") val display: Int?,
    @SerializedName("period1") val period1: Int?,
    @SerializedName("period2") val period2: Int?
)

// --- Details Models ---

data class IncidentsResponse(
    @SerializedName("incidents") val incidents: List<Incident>
)

data class Incident(
    @SerializedName("text") val text: String?,
    @SerializedName("homeScore") val homeScore: Int?,
    @SerializedName("awayScore") val awayScore: Int?,
    @SerializedName("isHome") val isHome: Boolean?,
    @SerializedName("time") val time: Int?,
    @SerializedName("length") val length: Int?,
    @SerializedName("player") val player: Player?,
    @SerializedName("playerIn") val playerIn: Player?,
    @SerializedName("playerOut") val playerOut: Player?,
    @SerializedName("assist1") val assist1: Player?,
    @SerializedName("incidentClass") val incidentClass: String?, // "goal", "card", "substitution", "regular", "yellow", "red"
    @SerializedName("incidentType") val incidentType: String? // "card", "period", "goal", "substitution"
)

data class LineupsResponse(
    @SerializedName("home") val home: LineupTeam,
    @SerializedName("away") val away: LineupTeam
)

data class LineupTeam(
    @SerializedName("players") val players: List<LineupPlayer>,
    @SerializedName("formation") val formation: String?
)

data class LineupPlayer(
    @SerializedName("player") val player: Player,
    @SerializedName("shirtNumber") val shirtNumber: Int?,
    @SerializedName("jerseyNumber") val jerseyNumber: String?, // Sometimes it's string or named jerseyNumber
    @SerializedName("position") val position: String?,
    @SerializedName("substitute") val substitute: Boolean = false
)

data class Player(
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("shortName") val shortName: String?
) {
    /**
     * Returns the formatted name as "Initial. Surname" (e.g., "Mario Rossi" -> "M. Rossi")
     */
    val formattedName: String
        get() {
            val parts = name.trim().split(" ", limit = 2)
            return if (parts.size >= 2) {
                val firstName = parts[0]
                val lastName = parts[1]
                "${firstName.firstOrNull()?.uppercase() ?: ""}. $lastName"
            } else {
                name // If name doesn't have a space, return as is
            }
        }
}

data class StatisticsResponse(
    @SerializedName("statistics") val statistics: List<StatisticsPeriod>
)

data class StatisticsPeriod(
    @SerializedName("period") val period: String, // "ALL", "1ST", "2ND"
    @SerializedName("groups") val groups: List<StatisticsGroup>
)

data class StatisticsGroup(
    @SerializedName("groupName") val groupName: String,
    @SerializedName("statisticsItems") val statisticsItems: List<StatisticItem>
)

data class StatisticItem(
    @SerializedName("name") val name: String,
    @SerializedName("home") val home: String,
    @SerializedName("away") val away: String,
    @SerializedName("compareCode") val compareCode: Int // 1=home better, 2=away better, 3=equal
)

// --- Team Details Models ---

data class TeamDetailsResponse(
    @SerializedName("team") val team: TeamDetails
)

data class TeamDetails(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("shortName") val shortName: String,
    @SerializedName("sport") val sport: Sport?,
    @SerializedName("tournament") val tournament: Tournament?,
    @SerializedName("primaryUniqueTournament") val primaryUniqueTournament: Tournament?,
    @SerializedName("country") val country: Country?,
    @SerializedName("teamColors") val teamColors: TeamColors?,
    @SerializedName("foundationDateTimestamp") val foundationDateTimestamp: Long?,
    @SerializedName("venue") val venue: Venue?
)

data class Sport(
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("id") val id: Int
)

data class Country(
    @SerializedName("name") val name: String,
    @SerializedName("alpha2") val alpha2: String
)

data class TeamColors(
    @SerializedName("primary") val primary: String?,
    @SerializedName("secondary") val secondary: String?,
    @SerializedName("text") val text: String?
)

data class Venue(
    @SerializedName("stadium") val stadium: Stadium?,
    @SerializedName("city") val city: City?
)

data class Stadium(
    @SerializedName("name") val name: String,
    @SerializedName("capacity") val capacity: Int?
)

data class City(
    @SerializedName("name") val name: String
)
