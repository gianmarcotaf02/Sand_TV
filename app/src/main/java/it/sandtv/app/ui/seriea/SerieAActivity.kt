package it.sandtv.app.ui.seriea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import coil.compose.AsyncImage

@AndroidEntryPoint
class SerieAActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandTVTheme {
                SerieAScreen()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SerieAScreen(
    viewModel: SerieAViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Classifica", "Calendario", "Live")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.serie_a_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.7f),
            contentScale = ContentScale.Crop
        )

        // Radial Gradient Overlay for better readability/premium look
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        radius = 1200f
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                 Image(
                    painter = painterResource(id = R.drawable.serie_a),
                    contentDescription = "Serie A Logo",
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Serie A Enilive",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tabs Row
            Row(modifier = Modifier.padding(bottom = 24.dp)) {
                tabs.forEachIndexed { index, title ->
                    SerieATabButton(
                        text = title,
                        isSelected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                when (selectedTab) {
                    0 -> StandingsTab(viewModel)
                    1 -> CalendarTab(viewModel)
                    2 -> LiveTab(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SerieATabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val backgroundColor = when {
        isSelected -> SandTVColors.Accent
        isFocused -> Color.White.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    
    val textColor = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.7f)
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "scale")

    Button(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.colors(
            containerColor = backgroundColor,
            contentColor = textColor,
            focusedContainerColor = backgroundColor,
            focusedContentColor = textColor
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor // Explicitly set color to override defaults
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StandingsTab(viewModel: SerieAViewModel) {
    val standings by viewModel.standings.collectAsState()
    
    if (standings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SandTVColors.Accent)
        }
    } else {
        LazyColumn(
             verticalArrangement = Arrangement.spacedBy(4.dp) // Tighter spacing
        ) {
            item {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("", modifier = Modifier.width(40.dp)) // Position placeholder
                    Text("SQUADRA", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("G", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("V", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("N", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("P", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("DIFF", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("GOL", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("PTI", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            items(standings) { row ->
                var isRowFocused by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRowFocused) Color.White.copy(alpha = 0.2f) 
                            else Color.Transparent
                        )
                        .onFocusChanged { isRowFocused = it.isFocused }
                        .clickable {
                            // Navigate to TeamDetailsActivity
                            val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                                putExtra("TEAM_ID", row.team.id)
                                putExtra("TEAM_NAME", row.team.name)
                            }
                            context.startActivity(intent)
                        }
                        .focusable()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Position Badge logic
                    val badgeColor = when (row.position) {
                        in 1..4 -> Color(0xFF2ECC71) // CL (Green)
                        5 -> Color(0xFFE67E22) // EL (Orange usually, user said Blue but typical is Orange/Blue) - User image shows Blue: #3498db
                        6 -> Color(0xFF3498DB) // ECL (Blue/Light Blue) - User image shows Light Blue: #00cec9 or similar. 
                        // Let's match user Image roughly: 
                        // 1-4 Green
                        // 5 Blue
                        // 6 Light Blue
                        // 18-20 Red
                        // Let's refine based on user uploaded image interpretation if possible, but standard is:
                        // 1-4 Green
                        // 5 Orange/Blue
                        // 6 Blue
                        // 18-20 Red
                        // I will stick to User textual description if detailed, or standard.
                        // User request: "1-4 Green, 5 Blue, 6 Light Blue, 18-20 Red".
                        in 1..4 -> Color(0xFF2ECC71) 
                        5 -> Color(0xFF007AFF) // Blue
                        6 -> Color(0xFF00C7BE) // Light Blue
                        in 18..20 -> Color(0xFFFF3B30) // Red
                        else -> Color.Transparent
                    }
                    
                    // Position badge (colored circle)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${row.position}", 
                            color = if (badgeColor == Color.Transparent) Color.White else Color.Black, 
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Team logo (with subtle white background for dark logos)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = if (TeamLogoUtils.getTeamLogo(row.team.name) != it.sandtv.app.R.drawable.serie_a) {
                                TeamLogoUtils.getTeamLogo(row.team.name)
                            } else {
                                TeamLogoUtils.getTeamLogoUrl(row.team.id)
                            },
                            contentDescription = row.team.name,
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit,
                            placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(row.team.name)),
                            error = painterResource(id = TeamLogoUtils.getTeamLogo(row.team.name))
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Team Name
                    Text(
                        text = row.team.name, 
                        color = Color.White, 
                        style = MaterialTheme.typography.titleSmall, 
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    // Stats
                    val goalDiff = row.scoresFor - row.scoresAgainst
                    val diffColor = if (goalDiff > 0) Color(0xFF2ECC71) else if (goalDiff < 0) Color(0xFFFF3B30) else Color.Gray

                    Text("${row.matches}", color = Color.White, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Text("${row.wins}", color = Color.White, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Text("${row.draws}", color = Color.White, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Text("${row.losses}", color = Color.White, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    
                    Text(
                        text = (if (goalDiff > 0) "+" else "") + "$goalDiff", 
                        color = Color.White, 
                        modifier = Modifier.width(40.dp), 
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text("${row.scoresFor}:${row.scoresAgainst}", color = Color.White, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    
                    Text("${row.points}", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CalendarTab(viewModel: SerieAViewModel) {
    val events by viewModel.events.collectAsState()
    val selectedRound by viewModel.selectedRound.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        
        // Round Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (selectedRound > 1) viewModel.selectRound(selectedRound - 1) },
                enabled = selectedRound > 1,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.colors(containerColor = Color(0xFF333333), contentColor = Color.White)
            ) {
                Text("<", color = Color.White)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Central Dropdown Trigger
            Box {
                 Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Transparent, 
                        contentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Giornata $selectedRound",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Dropdown Menu
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF222222)).heightIn(max = 300.dp) // Limit height for scroll
                ) {
                    (1..38).forEach { round ->
                         androidx.compose.material3.DropdownMenuItem(
                            text = { 
                                Text(
                                    "Giornata $round", 
                                    color = if (round == selectedRound) SandTVColors.Accent else Color.White
                                ) 
                            },
                            onClick = {
                                viewModel.selectRound(round)
                                expanded = false
                            },
                            modifier = Modifier.background(if (round == selectedRound) Color(0xFF333333) else Color.Transparent)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = { if (selectedRound < 38) viewModel.selectRound(selectedRound + 1) },
                enabled = selectedRound < 38,
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.colors(containerColor = Color(0xFF333333), contentColor = Color.White)
            ) {
                Text(">", color = Color.White)
            }
        }
    


        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SandTVColors.Accent)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(events) { event ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                MatchCard(
                    event = event,
                    onTeamClick = { teamId, teamName ->
                         val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                            putExtra("TEAM_ID", teamId)
                            putExtra("TEAM_NAME", teamName)
                        }
                        context.startActivity(intent)
                    }
                ) { eventId ->
                        val intent = android.content.Intent(context, MatchDetailsActivity::class.java).apply {
                            putExtra("EVENT_ID", eventId)
                            putExtra("HOME_TEAM", event.homeTeam.name)
                            putExtra("AWAY_TEAM", event.awayTeam.name)
                            putExtra("HOME_SCORE", event.homeScore.display?.toString() ?: "-")
                            putExtra("AWAY_SCORE", event.awayScore.display?.toString() ?: "-")
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
fun LiveTab(viewModel: SerieAViewModel) {
    // Reusing CalendarTab logic for now, or filter by status
    val events by viewModel.events.collectAsState()
    val liveEvents = events.filter { it.status.type == "inprogress" }
    
    if (liveEvents.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessuna partita live al momento", color = Color.Gray, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(liveEvents) { event ->
                val context = androidx.compose.ui.platform.LocalContext.current
                MatchCard(
                    event = event, 
                    isLive = true,
                    onTeamClick = { teamId, teamName ->
                         val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                            putExtra("TEAM_ID", teamId)
                            putExtra("TEAM_NAME", teamName)
                        }
                        context.startActivity(intent)
                    }
                ) { eventId ->
                        val intent = android.content.Intent(context, MatchDetailsActivity::class.java).apply {
                            putExtra("EVENT_ID", eventId)
                            putExtra("HOME_TEAM", event.homeTeam.name)
                            putExtra("AWAY_TEAM", event.awayTeam.name)
                            putExtra("HOME_SCORE", event.homeScore.display?.toString() ?: "-")
                            putExtra("AWAY_SCORE", event.awayScore.display?.toString() ?: "-")
                        }
                        context.startActivity(intent)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MatchCard(
    event: it.sandtv.app.data.model.Event,
    isLive: Boolean = false,
    onTeamClick: ((Long, String) -> Unit)? = null,  // Moved before onClick
    onClick: (Long) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF2A2A2A) else Color(0xFF1E1E1E))
            .border(2.dp, if (isFocused) SandTVColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick(event.id) }
            .focusable()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Home Team (clickable area)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null  // No ripple effect inside card
                ) {
                    onTeamClick?.invoke(event.homeTeam.id, event.homeTeam.name) ?: run {
                        // Default navigation if no callback provided
                        val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                            putExtra("TEAM_ID", event.homeTeam.id)
                            putExtra("TEAM_NAME", event.homeTeam.name)
                        }
                        context.startActivity(intent)
                    }
                },
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                event.homeTeam.name, 
                color = Color.White, 
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = if (TeamLogoUtils.getTeamLogo(event.homeTeam.name) != it.sandtv.app.R.drawable.serie_a) {
                    TeamLogoUtils.getTeamLogo(event.homeTeam.name)
                } else {
                    TeamLogoUtils.getTeamLogoUrl(event.homeTeam.id)
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(event.homeTeam.name)),
                error = painterResource(id = TeamLogoUtils.getTeamLogo(event.homeTeam.name))
            )
        }
        
        // Score / Time (non-clickable)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(120.dp)
        ) {
            if (event.status.type == "notstarted") {
                // Time
                 val date = java.util.Date(event.startTimestamp * 1000)
                 val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                 Text(
                    text = format.format(date),
                    color = SandTVColors.Accent,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                 val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                 Text(
                    text = dateFormat.format(date),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val isMatchLive = event.status.type == "inprogress"
                Text(
                    text = "${event.homeScore.display ?: 0} - ${event.awayScore.display ?: 0}",
                    color = if (isMatchLive) Color.Red else Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (isMatchLive) {
                    // Show exact minute if available, otherwise description
                    val currentMinute = event.status.statusTime?.current
                    val extraTime = event.status.statusTime?.extra
                    val liveText = when {
                        currentMinute != null && extraTime != null && extraTime > 0 -> "$currentMinute'+$extraTime'"
                        currentMinute != null -> "$currentMinute'"
                        else -> event.status.description.let { if (it.length < 10) it else "LIVE" }.uppercase()
                    }
                    Text(
                        text = liveText,
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else if (event.status.type == "finished") {
                     val date = java.util.Date(event.startTimestamp * 1000)
                     val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                     
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Text(
                            text = "FT",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = dateFormat.format(date),
                            color = Color.Gray.copy(alpha=0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                     }
                } else {
                    // Other statuses (canceled, postponed, etc)
                    Text(
                        text = event.status.description.uppercase(),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        // Away Team (clickable area)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onTeamClick?.invoke(event.awayTeam.id, event.awayTeam.name) ?: run {
                        // Default navigation if no callback provided
                        val intent = android.content.Intent(context, TeamDetailsActivity::class.java).apply {
                            putExtra("TEAM_ID", event.awayTeam.id)
                            putExtra("TEAM_NAME", event.awayTeam.name)
                        }
                        context.startActivity(intent)
                    }
                },
            horizontalArrangement = Arrangement.Start
        ) {
             AsyncImage(
                model = if (TeamLogoUtils.getTeamLogo(event.awayTeam.name) != it.sandtv.app.R.drawable.serie_a) {
                    TeamLogoUtils.getTeamLogo(event.awayTeam.name)
                } else {
                    TeamLogoUtils.getTeamLogoUrl(event.awayTeam.id)
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                placeholder = painterResource(id = TeamLogoUtils.getTeamLogo(event.awayTeam.name)),
                error = painterResource(id = TeamLogoUtils.getTeamLogo(event.awayTeam.name))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                event.awayTeam.name, 
                color = Color.White, 
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
    }
}


