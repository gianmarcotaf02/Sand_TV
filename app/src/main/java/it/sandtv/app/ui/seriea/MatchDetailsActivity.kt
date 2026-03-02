package it.sandtv.app.ui.seriea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.model.Incident
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.R
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.items as tvGridItems
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.StandardCardLayout
import androidx.tv.material3.Text
import it.sandtv.app.data.database.entity.Channel

@AndroidEntryPoint
class MatchDetailsActivity : ComponentActivity() {

    private val viewModel: MatchDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val eventId = intent.getLongExtra("EVENT_ID", -1)
        val homeTeam = intent.getStringExtra("HOME_TEAM") ?: ""
        val awayTeam = intent.getStringExtra("AWAY_TEAM") ?: ""
        val homeScore = intent.getStringExtra("HOME_SCORE") ?: "-"
        val awayScore = intent.getStringExtra("AWAY_SCORE") ?: "-"

        val homeTeamId = intent.getLongExtra("HOME_TEAM_ID", -1)
        val awayTeamId = intent.getLongExtra("AWAY_TEAM_ID", -1)

        if (eventId != -1L) {
            viewModel.loadDetails(eventId)
        }

        setContent {
            MatchDetailsScreen(viewModel, homeTeam, awayTeam, homeScore, awayScore, homeTeamId, awayTeamId)
        }
    }
}



@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchDetailsScreen(
    viewModel: MatchDetailsViewModel,
    homeTeam: String,
    awayTeam: String,
    homeScore: String,
    awayScore: String,
    homeTeamId: Long = -1,
    awayTeamId: Long = -1
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val incidentsResponse by viewModel.incidents.collectAsState()
    val incidents = incidentsResponse?.incidents?.reversed() // Newest first

    // Trigger Live Search on Entry
    LaunchedEffect(homeTeam, awayTeam) {
        viewModel.searchLiveChannels(homeTeam, awayTeam)
    }

    val event by viewModel.event.collectAsState()

    val tabs = listOf("Eventi", "Formazioni", "Statistiche", "Diretta")
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Match Header with Scorers
            MatchHeader(homeTeam, awayTeam, homeScore, awayScore, incidents, homeTeamId, awayTeamId, event)

            // Header Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp), // Reduced top padding as Header is above
                horizontalArrangement = Arrangement.Center
            ) {
                tabs.forEachIndexed { index, title ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = selectedTab == index
                    
                     Button(
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .onFocusChanged { isFocused = it.isFocused },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) SandTVColors.Accent else if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = title, 
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 32.dp, vertical = 0.dp) // Reset vertical padding
            ) {
                when (selectedTab) {
                    0 -> MatchIncidentsTab(incidents)
                    1 -> MatchLineupsTab(viewModel, incidents)
                    2 -> MatchStatisticsTab(viewModel, event)
                    3 -> MatchLiveTab(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchHeader(
    homeTeam: String,
    awayTeam: String, 
    homeScore: String, 
    awayScore: String,
    incidents: List<Incident>?,
    homeTeamId: Long = -1,
    awayTeamId: Long = -1,
    event: it.sandtv.app.data.model.Event? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val homeGoals = incidents?.filter { it.incidentType == "goal" && it.isHome == true }?.reversed() ?: emptyList()
    val awayGoals = incidents?.filter { it.incidentType == "goal" && it.isHome == false }?.reversed() ?: emptyList()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp)
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF222222))
            .padding(vertical = 24.dp, horizontal = 16.dp)
    ) {
        // Main score row - fixed height
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- HOME TEAM ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (homeTeamId != -1L) {
                            val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                                putExtra("TEAM_ID", homeTeamId)
                                putExtra("TEAM_NAME", homeTeam)
                            }
                            context.startActivity(intent)
                        }
                    }
            ) {
                Text(
                    text = homeTeam, 
                    color = Color.White, 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(
                    model = if (TeamLogoUtils.getTeamLogo(homeTeam) != it.sandtv.app.R.drawable.serie_a) {
                        TeamLogoUtils.getTeamLogo(homeTeam)
                    } else {
                        TeamLogoUtils.getTeamLogoUrl(homeTeamId)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(homeTeam)),
                    error = painterResource(id = TeamLogoUtils.getTeamLogo(homeTeam))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = homeScore, 
                    color = SandTVColors.Accent, 
                    style = MaterialTheme.typography.displayMedium, 
                    fontWeight = FontWeight.Bold
                )
            }

            // --- SEPARATOR & STATUS ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp) // Lower the dash a bit
            ) {
                Text(
                    text = " - ", 
                    color = SandTVColors.Accent, 
                    style = MaterialTheme.typography.displayMedium, 
                    fontWeight = FontWeight.Bold
                )
                
                // STATUS / MINUTE
                if (event != null && event.status.type == "inprogress") {
                    val currentMinute = event.status.statusTime?.current
                    val extraTime = event.status.statusTime?.extra
                    val liveText = when {
                        currentMinute != null && extraTime != null && extraTime > 0 -> "$currentMinute'+$extraTime'"
                        currentMinute != null -> "$currentMinute'"
                        else -> event.status.description.uppercase()
                    }
                    Text(
                        text = liveText,
                        color = Color.Red,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else if (event?.status?.type == "finished") {
                    Text(
                        text = "FT",
                        color = Color.Gray,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- AWAY TEAM ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (awayTeamId != -1L) {
                            val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                                putExtra("TEAM_ID", awayTeamId)
                                putExtra("TEAM_NAME", awayTeam)
                            }
                            context.startActivity(intent)
                        }
                    }
            ) {
                Text(
                    text = awayScore, 
                    color = SandTVColors.Accent, 
                    style = MaterialTheme.typography.displayMedium, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                AsyncImage(
                    model = if (TeamLogoUtils.getTeamLogo(awayTeam) != it.sandtv.app.R.drawable.serie_a) {
                        TeamLogoUtils.getTeamLogo(awayTeam)
                    } else {
                        TeamLogoUtils.getTeamLogoUrl(awayTeamId)
                    },
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(awayTeam)),
                    error = painterResource(id = TeamLogoUtils.getTeamLogo(awayTeam))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = awayTeam, 
                    color = Color.White, 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Scorers row - expands downward
        if (homeGoals.isNotEmpty() || awayGoals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Home scorers
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    homeGoals.forEach { goal ->
                        Text(
                            text = "${goal.player?.formattedName ?: "Gol"} ${goal.time}' ⚽",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(80.dp)) // Space for center column
                
                // Away scorers
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    awayGoals.forEach { goal ->
                        Text(
                            text = "⚽ ${goal.time}' ${goal.player?.formattedName ?: "Gol"}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchIncidentsTab(incidents: List<Incident>?) {
    // val incidentsResponse by viewModel.incidents.collectAsState()  <-- REMOVE (passed as arg)
    // val incidents = incidentsResponse?.incidents?.reversed() <-- REMOVE (passed as arg)

    if (incidents == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
            androidx.compose.material3.CircularProgressIndicator(color = SandTVColors.Accent) 
        }
    } else if (incidents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
            Text("Nessun evento disponibile", color = Color.Gray) 
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(incidents) { incident ->
                if (incident.incidentType == "period") {
                    PeriodHeader(incident)
                } else {
                    IncidentItem(incident)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PeriodHeader(incident: Incident) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = incident.text ?: "Fine Tempo",
            color = Color.Gray,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun IncidentItem(incident: Incident) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Transparent Row (Just for layout and focus handling)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(HORIZONTAL_PADDING_INCIDENT), // Define constant or hardcode
        verticalAlignment = Alignment.CenterVertically
    ) {
        // HOME EVENT (45% width)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (incident.isHome == true) {
                // Focus styling wrapper? Or apply to bubble?
                // User asked for "box text piu trasparente" only on event.
                // Let's wrap content in a surface/box
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) Color(0xFF444444) else Color(0x66000000)) // Transparent Black
                        .padding(8.dp)
                ) {
                    EventContent(incident, Alignment.End)
                }
            }
        }

        // TIME (10% width) - Remove background here too? Or keep it? User said "solo all'evento interessato"
        // Let's keep time simple text, no box, or separate box.
        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
             // Maybe highlight time if row is focused?
             Text(
                text = "${incident.time}'",
                color = if (isFocused) Color.White else SandTVColors.Accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // AWAY EVENT (45% width)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (incident.isHome == false) {
                 Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) Color(0xFF444444) else Color(0x66000000))
                        .padding(8.dp)
                ) {
                    EventContent(incident, Alignment.Start)
                }
            }
        }
    }
}

val HORIZONTAL_PADDING_INCIDENT = 16.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EventContent(incident: Incident, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        when (incident.incidentType) {
            "goal" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alignment == Alignment.End) { // Home
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(incident.player?.formattedName ?: "Gol", color = Color.White, fontWeight = FontWeight.Bold)
                                if (incident.assist1 != null) {
                                    Text(incident.assist1.formattedName, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(painter = painterResource(id = it.sandtv.app.R.drawable.gol), contentDescription = "Gol", modifier = Modifier.size(20.dp))
                        }
                    } else { // Away
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painter = painterResource(id = it.sandtv.app.R.drawable.gol), contentDescription = "Gol", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(incident.player?.formattedName ?: "Gol", color = Color.White, fontWeight = FontWeight.Bold)
                                if (incident.assist1 != null) {
                                    Text(incident.assist1.formattedName, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                if (incident.incidentClass == "penalty") {
                     Text("(Rigore)", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
            "card" -> {
                val iconRes = if (incident.incidentClass?.contains("Red", ignoreCase = true) == true || incident.incidentClass == "red") it.sandtv.app.R.drawable.rosso else it.sandtv.app.R.drawable.giallo
                Row(verticalAlignment = Alignment.CenterVertically) {
                     if (alignment == Alignment.End) { // Home
                        Text(incident.player?.formattedName ?: "Cartellino", color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(painter = painterResource(id = iconRes), contentDescription = "Cartellino", modifier = Modifier.size(18.dp))
                    } else { // Away
                        Image(painter = painterResource(id = iconRes), contentDescription = "Cartellino", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(incident.player?.formattedName ?: "Cartellino", color = Color.White)
                    }
                }
            }
            "substitution" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                     if (alignment == Alignment.End) {
                         Column(horizontalAlignment = Alignment.End) {
                            Text("${incident.playerIn?.formattedName ?: "?"}", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                            Text("${incident.playerOut?.formattedName ?: "?"}", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                         }
                         Spacer(modifier = Modifier.width(8.dp))
                         Image(painter = painterResource(id = it.sandtv.app.R.drawable.sostituzione), contentDescription = "Cambio", modifier = Modifier.size(20.dp))
                     } else {
                         Image(painter = painterResource(id = it.sandtv.app.R.drawable.sostituzione), contentDescription = "Cambio", modifier = Modifier.size(20.dp))
                         Spacer(modifier = Modifier.width(8.dp))
                         Column(horizontalAlignment = Alignment.Start) {
                            Text("${incident.playerIn?.formattedName ?: "?"}", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                            Text("${incident.playerOut?.formattedName ?: "?"}", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                         }
                     }
                }
            }
            else -> {
                Text(incident.player?.formattedName ?: incident.text ?: "", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchLineupsTab(viewModel: MatchDetailsViewModel, incidents: List<Incident>?) {
    val lineupsResponse by viewModel.lineups.collectAsState()
    
    if (lineupsResponse == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
            androidx.compose.material3.CircularProgressIndicator(color = SandTVColors.Accent)
        }
    } else {
        val homeLineup = lineupsResponse!!.home
        val awayLineup = lineupsResponse!!.away
        
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // HOME SQUAD
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { 
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .background(if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Text(
                            "CASA (${homeLineup.formation ?: ""})", 
                            color = if (isFocused) Color.White else SandTVColors.Accent, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Starters
                items(homeLineup.players.filter { !it.substitute }) { player ->
                    LineupPlayerItem(player, incidents)
                }
                
                item { 
                    Spacer(modifier = Modifier.height(16.dp))
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .background(if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Text("PANCHINA", color = if (isFocused) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Subs
                items(homeLineup.players.filter { it.substitute }) { player ->
                    LineupPlayerItem(player, incidents)
                }
            }
            
            Spacer(modifier = Modifier.width(32.dp))
            
            // AWAY SQUAD
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { 
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .background(if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Text(
                            "TRASFERTA (${awayLineup.formation ?: ""})", 
                            color = if (isFocused) Color.White else SandTVColors.Accent, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Starters
                items(awayLineup.players.filter { !it.substitute }) { player ->
                    LineupPlayerItem(player, incidents)
                }

                item { 
                    Spacer(modifier = Modifier.height(16.dp))
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .background(if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Text("PANCHINA", color = if (isFocused) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Subs
                items(awayLineup.players.filter { it.substitute }) { player ->
                    LineupPlayerItem(player, incidents)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LineupPlayerItem(lineupPlayer: it.sandtv.app.data.model.LineupPlayer, incidents: List<Incident>?) {
    val player = lineupPlayer.player
    val number = lineupPlayer.shirtNumber?.toString() ?: lineupPlayer.jerseyNumber ?: "-"
    var isFocused by remember { mutableStateOf(false) }
    
    // Find Events
    val goals = incidents?.count { it.incidentType == "goal" && it.player?.name == player.name } ?: 0
    val yellow = incidents?.any { it.incidentType == "card" && (it.incidentClass == "yellow" || it.incidentClass == "Yellow") && it.player?.name == player.name } == true
    val red = incidents?.any { it.incidentType == "card" && (it.incidentClass == "red" || it.incidentClass == "Red") && it.player?.name == player.name } == true
    val subOut = incidents?.any { it.incidentType == "substitution" && it.playerOut?.name == player.name } == true
    val subIn = incidents?.any { it.incidentType == "substitution" && it.playerIn?.name == player.name } == true
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF444444) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number
        Text(
            number, 
            color = SandTVColors.Accent, 
            fontWeight = FontWeight.Bold, 
            modifier = Modifier.width(30.dp)
        )
        
        // Name
        Text(
            player.formattedName, 
            color = Color.White, 
            modifier = Modifier.weight(1f)
        )
        
        // Event Icons
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subIn) {
                Image(
                    painter = painterResource(id = R.drawable.sostituzione), 
                    contentDescription = "In", 
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Green),
                    modifier = Modifier.size(16.dp).padding(horizontal = 2.dp)
                )
            }
            if (subOut) {
                Image(
                    painter = painterResource(id = R.drawable.sostituzione), 
                    contentDescription = "Out", 
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Red),
                    modifier = Modifier.size(16.dp).padding(horizontal = 2.dp)
                )
            }
            if (yellow) Image(painter = painterResource(id = R.drawable.giallo), contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
            if (red) Image(painter = painterResource(id = R.drawable.rosso), contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
            if (goals > 0) {
                repeat(goals) {
                    Image(painter = painterResource(id = R.drawable.gol), contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 2.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchStatisticsTab(viewModel: MatchDetailsViewModel, event: it.sandtv.app.data.model.Event?) {
    val statsResponse by viewModel.statistics.collectAsState()
    
    // Check if match hasn't started yet
    if (event?.status?.type == "notstarted") {
        // Show zeroed stats for future/pre-match
         val zeroStats = listOf(
             "Ball possession", "Total shots", "Shots on target", "Shots off target", 
             "Blocked shots", "Corner kicks", "Fouls", 
             "Yellow cards", "Red cards", "Goalkeeper saves", "Big chances"
         ).map { name ->
             it.sandtv.app.data.model.StatisticItem(
                 name = name, 
                 home = "0", 
                 away = "0", 
                 compareCode = 0
             )
         }
         
         LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                 // Explicit message that the match hasn't started, but showing stats as requested (all 0)
                 Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                     Text("Partita non iniziata", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                 }
            }
            items(zeroStats) { item ->
                StatItem(item)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else if (statsResponse == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
            androidx.compose.material3.CircularProgressIndicator(color = SandTVColors.Accent)
        }
    } else {
        val groups = statsResponse!!.statistics.find { it.period == "ALL" }?.groups ?: emptyList()
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(groups) { group ->
                // Group Name (optional, specific case usually empty or "Match overview")
               // Text(group.groupName, color = SandTVColors.Accent, fontWeight = FontWeight.Bold) 
                
                group.statisticsItems.forEach { item ->
                    StatItem(item)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StatItem(item: it.sandtv.app.data.model.StatisticItem) {
    val homeVal = parseStatValue(item.home)
    val awayVal = parseStatValue(item.away)
    val total = homeVal + awayVal
    val homePercent = if (total > 0) homeVal / total else 0f
    
    val translatedName = translateStatName(item.name)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Name Centered
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(translatedName, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        // Row: Value - Bar - Value
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Home Value
            Text(
                item.home, 
                color = Color.White, 
                fontWeight = if(item.compareCode == 1) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(50.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Bars
            StatBar(homePercent, item.compareCode)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Away Value
            Text(
                item.away, 
                color = Color.White, 
                fontWeight = if(item.compareCode == 2) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(50.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
        }
    }
}

@Composable
fun StatBar(homePercent: Float, compareCode: Int) {
    // Total width of the bar area
    val barHeight = 6.dp
    val totalWidth = 400.dp // Fixed width for the graphical part
    
    Row(
        modifier = Modifier
            .width(totalWidth)
            .height(barHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF333333)) // Background track
    ) {
        // Home Portion
        if (homePercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(homePercent)
                    .background(if (compareCode == 1) SandTVColors.Accent else Color.Gray)
            )
        }
        
        // Away Portion (Remaining weight)
        if (homePercent < 1f) {
             Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - homePercent)
                    .background(if (compareCode == 2) SandTVColors.Accent else Color(0xFF555555)) // Darker gray for loser or neutral
            )
        }
    }
}

fun parseStatValue(value: String): Float {
    return try {
        value.replace("%", "").toFloat()
    } catch (e: Exception) {
        0f
    }
}

fun translateStatName(name: String): String {
    return when (name) {
        "Ball possession" -> "Possesso Palla"
        "Expected goals" -> "Goal Attesi (xG)"
        "Big chances" -> "Grandi Occasioni"
        "Total shots" -> "Tiri Totali"
        "Goalkeeper saves" -> "Parate"
        "Corner kicks" -> "Calci d'Angolo"
        "Fouls" -> "Falli"
        "Passes" -> "Passaggi"
        "Tackles" -> "Contrasti"
        "Free kicks" -> "Calci di Punizione"
        "Yellow cards" -> "Cartellini Gialli"
        "Red cards" -> "Cartellini Rossi"
        "Shots on target" -> "Tiri in Porta"
        "Shots off target" -> "Tiri Fuori"
        "Blocked shots" -> "Tiri Respinti"
        "Hit woodwork" -> "Legni"
        "Shots inside box" -> "Tiri in Area"
        "Shots outside box" -> "Tiri da Fuori"
        "Crosses" -> "Cross"
        "Interceptions" -> "Intercetti"
        "Aerials won" -> "Duelli Aerei"
        "Dribbles" -> "Dribbling"
        "Dribbled past" -> "Dribbling subiti"
        "Clearances" -> "Spazzate"
        "Dispossessed" -> "Palla Persa"
        "Long balls" -> "Lanci Lunghi"
        "Accurate passes" -> "Passaggi Precisi"
        else -> name
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchLiveTab(viewModel: MatchDetailsViewModel) {
    val allChannels by viewModel.liveChannels.collectAsState()
    val channels = remember(allChannels) {
        allChannels.filter { it.category?.contains("DAZN", ignoreCase = true) == true }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    if (channels.isEmpty()) {
         Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
             Text("Nessun canale live trovato per queste squadre.", color = Color.Gray)
         }
    } else {
        val groupedChannels = channels.groupBy { it.category ?: "Generale" }
        
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groupedChannels.forEach { (category, catChannels) ->
                item(span = { TvGridItemSpan(maxLineSpan) }) {
                   Text(
                       text = category,
                       color = SandTVColors.Accent,
                       style = MaterialTheme.typography.titleMedium,
                       fontWeight = FontWeight.Bold,
                       modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                   ) 
                }
                
                tvGridItems(catChannels) { channel ->
                    LiveChannelCard(channel) {
                        // Play Channel
                        val intent = android.content.Intent(context, it.sandtv.app.ui.player.PlayerActivity::class.java).apply {
                            putExtra("stream_url", channel.streamUrl)
                            putExtra("title", channel.name)
                            putExtra("content_type", "CHANNEL")
                            putExtra("content_id", channel.id)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveChannelCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
             .width(180.dp)
             .aspectRatio(16f/9f)
             .onFocusChanged { isFocused = it.isFocused },
        border = CardDefaults.border(
             focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, SandTVColors.Accent)),
             pressedBorder = Border(border = androidx.compose.foundation.BorderStroke(2.dp, SandTVColors.Accent))
        ),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF222222),
            focusedContainerColor = Color(0xFF333333)
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(0.6f)
            )
            
            // Name Overlay (Always Visible)
           Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(if (isFocused) SandTVColors.Accent else Color.Black.copy(alpha=0.7f))
                    .padding(4.dp)
           ) {
               Text(
                   channel.name, 
                   color = Color.White, 
                   style = MaterialTheme.typography.labelSmall,
                   maxLines = 1,
                   modifier = Modifier.align(Alignment.Center)
               )
           }
        }
    }
}
