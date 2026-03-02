package it.sandtv.app.ui.seriea

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.data.model.Event
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage

@AndroidEntryPoint
class TeamDetailsActivity : ComponentActivity() {

    private val viewModel: TeamDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val teamId = intent.getLongExtra("TEAM_ID", -1)
        val teamName = intent.getStringExtra("TEAM_NAME") ?: ""

        if (teamId != -1L) {
            viewModel.loadTeamData(teamId)
        }

        setContent {
            SandTVTheme {
                TeamDetailsScreen(viewModel, teamId, teamName)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TeamDetailsScreen(viewModel: TeamDetailsViewModel, teamId: Long, teamName: String) {
    val teamDetails by viewModel.teamDetails.collectAsState()
    val nextEvents by viewModel.nextEvents.collectAsState()
    val lastEvents by viewModel.lastEvents.collectAsState()
    val loading by viewModel.loading.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Prossime Partite", "Ultime Partite")
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loading && teamDetails == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = SandTVColors.Accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp)
            ) {
                // Header
                item {
                    TeamDetailsHeader(
                        teamId = teamId,
                        teamName = teamName,
                        teamDetails = teamDetails
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
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
                }

                // Content
                when (selectedTab) {
                    0 -> {
                        // Next Events
                        if (nextEvents.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Nessuna partita programmata",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            items(nextEvents) { event ->
                                MatchCard(
                                    event = event,
                                    isLive = false,
                                    onClick = { eventId ->
                                        // Navigate to MatchDetailsActivity
                                        val intent = Intent(context, MatchDetailsActivity::class.java).apply {
                                            putExtra("EVENT_ID", eventId)
                                            putExtra("HOME_TEAM", event.homeTeam.name)
                                            putExtra("AWAY_TEAM", event.awayTeam.name)
                                            putExtra("HOME_SCORE", event.homeScore.display?.toString() ?: "-")
                                            putExtra("AWAY_SCORE", event.awayScore.display?.toString() ?: "-")
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    1 -> {
                        // Last Events
                        if (lastEvents.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Nessuna partita recente",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            // Sort by date descending (newest first)
                            val sortedEvents = lastEvents.sortedByDescending { it.startTimestamp }
                            
                            items(sortedEvents) { event ->
                                MatchCard(
                                    event = event,
                                    isLive = event.status.type == "inprogress",
                                    onClick = { eventId ->
                                        // Navigate to MatchDetailsActivity
                                        val intent = Intent(context, MatchDetailsActivity::class.java).apply {
                                            putExtra("EVENT_ID", eventId)
                                            putExtra("HOME_TEAM", event.homeTeam.name)
                                            putExtra("AWAY_TEAM", event.awayTeam.name)
                                            putExtra("HOME_SCORE", event.homeScore.display?.toString() ?: "-")
                                            putExtra("AWAY_SCORE", event.awayScore.display?.toString() ?: "-")
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TeamDetailsHeader(
    teamId: Long,
    teamName: String,
    teamDetails: it.sandtv.app.data.model.TeamDetailsResponse?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Team Logo
        AsyncImage(
            model = if (TeamLogoUtils.getTeamLogo(teamName) != it.sandtv.app.R.drawable.serie_a) {
                TeamLogoUtils.getTeamLogo(teamName)
            } else {
                TeamLogoUtils.getTeamLogoUrl(teamId)
            },
            contentDescription = teamName,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .padding(8.dp),
            placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(teamName)),
            error = painterResource(id = TeamLogoUtils.getTeamLogo(teamName))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Team Name
        Text(
            text = teamDetails?.team?.name ?: teamName,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Team Info (if available)
        teamDetails?.team?.let { team ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // City
                team.venue?.city?.name?.let { city ->
                    Text(
                        text = city,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Stadium
                team.venue?.stadium?.name?.let { stadium ->
                    if (team.venue.city != null) {
                        Text(" • ", color = Color.Gray)
                    }
                    Text(
                        text = stadium,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Foundation Year (if available)
            team.foundationDateTimestamp?.let { timestamp ->
                val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(timestamp * 1000))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fondata nel $year",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
