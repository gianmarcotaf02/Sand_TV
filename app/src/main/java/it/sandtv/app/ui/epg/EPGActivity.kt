package it.sandtv.app.ui.epg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items as tvListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.ChannelDao
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.repository.EpgRepository
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * EPG Program data class
 */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long,
    val category: String? = null
)

/**
 * EPG Activity - Electronic Program Guide
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class EPGActivity : ComponentActivity() {
    
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var epgRepository: EpgRepository
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.ITALIAN)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SandTVTheme {
                EPGScreenContent()
            }
        }
    }
    
    @Composable
    private fun EPGScreenContent() {
        var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
        var channelPrograms by remember { mutableStateOf<Map<Long, List<EpgProgram>>>(emptyMap()) }
        var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
        var isLoading by remember { mutableStateOf(true) }
        
        // Load channels and EPG
        LaunchedEffect(Unit) {
            channels = channelDao.getAllChannels().first()
            
            // Load programs for each channel
            val programs = mutableMapOf<Long, List<EpgProgram>>()
            channels.forEach { channel ->
                val epgId = channel.xtreamEpgChannelId ?: channel.name
                programs[channel.id] = epgRepository.getProgramsForChannel(epgId)
            }
            channelPrograms = programs
            isLoading = false
        }
        
        // Update time every minute
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                currentTime = System.currentTimeMillis()
            }
        }
        
        EPGScreen(
            channels = channels,
            channelPrograms = channelPrograms,
            currentTime = currentTime,
            isLoading = isLoading,
            onChannelClick = { channel -> playChannel(channel) },
            onProgramClick = { channel, _ -> playChannel(channel) }
        )
    }
    
    private fun playChannel(channel: Channel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("content_id", channel.id)
            putExtra("content_type", "CHANNEL")
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
        }
        startActivity(intent)
    }
}

/**
 * EPG Screen Composable
 */
@Composable
fun EPGScreen(
    channels: List<Channel>,
    channelPrograms: Map<Long, List<EpgProgram>>,
    currentTime: Long,
    isLoading: Boolean,
    onChannelClick: (Channel) -> Unit,
    onProgramClick: (Channel, EpgProgram) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ITALIAN) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Header with time
        EPGHeader(
            currentTime = currentTime,
            timeFormat = timeFormat,
            dateFormat = dateFormat
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SandTVColors.Accent)
            }
        } else {
            // Channel list with programs using TvLazyColumn for proper D-pad navigation
            TvLazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tvListItems(channels, key = { it.id }) { channel ->
                    val programs = channelPrograms[channel.id] ?: emptyList()
                    EPGChannelRow(
                        channel = channel,
                        programs = programs,
                        currentTime = currentTime,
                        timeFormat = timeFormat,
                        onChannelClick = { onChannelClick(channel) },
                        onProgramClick = { program -> onProgramClick(channel, program) }
                    )
                }
            }
        }
    }
}

/**
 * EPG Header with current time
 */
@Composable
private fun EPGHeader(
    currentTime: Long,
    timeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SandTVColors.BackgroundSecondary)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Guida TV",
            style = MaterialTheme.typography.headlineSmall,
            color = SandTVColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
            
            Text(
                text = timeFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.headlineMedium,
                color = SandTVColors.Accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * EPG Channel Row with programs timeline
 */
@Composable
private fun EPGChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    currentTime: Long,
    timeFormat: SimpleDateFormat,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else SandTVColors.BackgroundSecondary,
        label = "rowBg"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onChannelClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel info (fixed width)
        Row(
            modifier = Modifier
                .width(200.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel logo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandTVColors.CardBackground),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
            
            // Channel name
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Programs timeline (scrollable)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (programs.isEmpty()) {
                // No programs available
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(200.dp)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SandTVColors.CardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun programma",
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.TextTertiary
                    )
                }
            } else {
                programs.forEach { program ->
                    EPGProgramBlock(
                        program = program,
                        currentTime = currentTime,
                        timeFormat = timeFormat,
                        onClick = { onProgramClick(program) }
                    )
                }
            }
        }
    }
}

/**
 * EPG Program Block
 */
@Composable
private fun EPGProgramBlock(
    program: EpgProgram,
    currentTime: Long,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isCurrent = program.start <= currentTime && program.end > currentTime
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "programScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isCurrent -> SandTVColors.Accent.copy(alpha = 0.3f)
            isFocused -> SandTVColors.BackgroundTertiary
            else -> SandTVColors.CardBackground
        },
        label = "programBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> SandTVColors.Accent
            isCurrent -> SandTVColors.Accent.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        label = "programBorder"
    )
    
    // Width based on duration (min 120dp, max 400dp)
    val durationMinutes = ((program.end - program.start) / 60_000).toInt()
    val width = (durationMinutes * 3).coerceIn(120, 400).dp
    
    Box(
        modifier = Modifier
            .scale(scale)
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            
            Text(
                text = "${timeFormat.format(Date(program.start))} - ${timeFormat.format(Date(program.end))}",
                style = MaterialTheme.typography.labelSmall,
                color = SandTVColors.TextTertiary
            )
        }
        
        // Live indicator
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Red)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
