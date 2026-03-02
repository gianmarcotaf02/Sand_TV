package it.sandtv.app.ui.live

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.grid.items as tvGridItems
import androidx.tv.foundation.lazy.list.items as tvListItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.ChannelDao
import it.sandtv.app.data.database.dao.PlaylistDao
import it.sandtv.app.data.database.dao.RecentlyWatchedDao
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.database.entity.RecentlyWatchedChannel
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.data.repository.EpgRepository
import it.sandtv.app.ui.epg.EpgProgram
import it.sandtv.app.ui.multiscreen.MultiscreenActivity
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.search.SearchActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import it.sandtv.app.data.database.entity.FavoriteCategory
import it.sandtv.app.data.database.dao.FavoriteCategoryDao
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Live TV Activity with Jetpack Compose
 * Converted from original XML-based LiveActivity
 * Features:
 * - Grid mode: channel cards
 * - Timeline mode: EPG with horizontal program blocks and current time line (red)
 * - EPG parsing from Xtream or URL
 */
@AndroidEntryPoint
class LiveActivity : ComponentActivity() {
    
    companion object {
        private const val RECENT_CATEGORY = "Visti di recente"
        private const val RECENT_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var epgRepository: EpgRepository
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var recentlyWatchedDao: RecentlyWatchedDao
    @Inject lateinit var favoriteCategoryDao: FavoriteCategoryDao
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SandTVTheme {
                LiveScreenContent()
            }
        }
    }
    
    @Composable
    private fun LiveScreenContent() {
        var categories by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedCategory by remember { mutableStateOf<String?>(null) }
        var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
        var channelPrograms by remember { mutableStateOf<Map<Long, List<EpgProgram>>>(emptyMap()) }
        var isGridMode by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(true) }
        var isEpgLoading by remember { mutableStateOf(false) }
        var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
        var favoriteCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
        
        // Load favorites
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val profileId = userPreferences.getCurrentProfileId() ?: 1L
                val favs = favoriteCategoryDao.getFavoriteCategoriesByType(profileId, "channels")
                favoriteCategories = favs.map { it.categoryName }.toSet()
            }
        }
        
        val onToggleFavorite: (String) -> Unit = { category ->
            lifecycleScope.launch(Dispatchers.IO) {
                val profileId = userPreferences.getCurrentProfileId() ?: 1L
                val item = FavoriteCategory(profileId = profileId, categoryType = "channels", categoryName = category)
                favoriteCategoryDao.toggleFavoriteCategory(item)
                val favs = favoriteCategoryDao.getFavoriteCategoriesByType(profileId, "channels")
                favoriteCategories = favs.map { it.categoryName }.toSet()
            }
        }
        
        // Load categories and preferences
        LaunchedEffect(Unit) {
            // Load preference for layout mode
            val mode = userPreferences.getLiveLayoutMode()
            isGridMode = (mode == "grid")
            
            // Cleanup old entries
            val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
            recentlyWatchedDao.cleanupOlderThan(cutoff)
            
            // Get categories and prepend "Visti di recente" (always shown)
            val cats = channelDao.getCategoriesList().toMutableList()
            cats.add(0, RECENT_CATEGORY)  // Always show, even if empty
            categories = cats
            
            // Auto-select first category AND load its channels immediately
            cats.firstOrNull()?.let { firstCat ->
                selectedCategory = firstCat
                channels = if (firstCat == RECENT_CATEGORY) {
                    val recentIds = recentlyWatchedDao.getRecentChannelIds(cutoff)
                    channelDao.getChannelsByIds(recentIds)
                } else {
                    channelDao.getChannelsByCategoryList(firstCat)
                }
            }
            isLoading = false
        }
        
        // Load EPG data if needed (respecting cache)
        LaunchedEffect(Unit) {
            // Check if we already have valid data in RAM or Disk
            if (epgRepository.isCacheValid()) {
                 isEpgLoading = false
                 return@LaunchedEffect
            }
            
            try {
                isEpgLoading = true
                val playlists = playlistDao.getAllPlaylists().first()
                
                for (playlist in playlists) {
                    if (playlist.type == "xtream" &&
                        !playlist.username.isNullOrEmpty() &&
                        !playlist.password.isNullOrEmpty()) {
                        
                        withContext(Dispatchers.IO) {
                            epgRepository.loadEpgFromXtream(
                                baseUrl = playlist.url,
                                username = playlist.username,
                                password = playlist.password
                            )
                        }
                    } else if (!playlist.epgUrl.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            epgRepository.loadEpgFromUrl(playlist.epgUrl)
                        }
                    }
                }
                isEpgLoading = false
            } catch (e: Exception) {
                isEpgLoading = false
            }
        }
        
        // Load channels and EPG when category changes
        LaunchedEffect(selectedCategory, isEpgLoading) {
            if (isEpgLoading) return@LaunchedEffect
            
            selectedCategory?.let { cat ->
                isLoading = true
                
                // Load channels based on category type
                channels = if (cat == RECENT_CATEGORY) {
                    val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
                    val recentIds = recentlyWatchedDao.getRecentChannelIds(cutoff)
                    channelDao.getChannelsByIds(recentIds)
                } else {
                    channelDao.getChannelsByCategoryList(cat)
                }
                
                // Load EPG for channels (EPG should already be loaded)
                val programs = mutableMapOf<Long, List<EpgProgram>>()
                
                channels.forEach { channel ->
                    // Try multiple channel ID formats (like original code)
                    val channelIds = listOfNotNull(
                        channel.xtreamEpgChannelId,
                        channel.name,
                        channel.xtreamStreamId?.toString()
                    )
                    
                    var epgPrograms = emptyList<EpgProgram>()
                    for (channelId in channelIds) {
                        epgPrograms = epgRepository.getProgramsForChannel(channelId)
                        if (epgPrograms.isNotEmpty()) break
                    }
                    programs[channel.id] = epgPrograms
                }
                
                channelPrograms = programs
                isLoading = false
            }
        }
        
        // Update current time every minute (for red line position)
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                currentTime = System.currentTimeMillis()
            }
        }
        
        LiveScreen(
            categories = categories,
            selectedCategory = selectedCategory,
            channels = channels,
            channelPrograms = channelPrograms,
            isGridMode = isGridMode,
            isLoading = isLoading,
            isEpgLoading = isEpgLoading,
            currentTime = currentTime,
            onCategorySelect = { selectedCategory = it },
            onChannelClick = { playChannel(it) },
            onToggleMode = { isGridMode = !isGridMode },
            onSearchClick = {
                startActivity(Intent(this@LiveActivity, SearchActivity::class.java))
            },
            onBackClick = { finish() },
            onMultiscreenClick = {
                startActivity(Intent(this@LiveActivity, MultiscreenActivity::class.java))
            },
            favoriteCategories = favoriteCategories,
            onToggleFavorite = onToggleFavorite
        )
    }
    
    private fun playChannel(channel: Channel) {
        // Track recently watched
        lifecycleScope.launch {
            recentlyWatchedDao.insertOrUpdate(
                RecentlyWatchedChannel(channel.id, System.currentTimeMillis())
            )
        }
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("content_id", channel.id)
            putExtra("content_type", "CHANNEL")
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
        }
        startActivity(intent)
    }
}

// ========== Constants ==========
private const val PIXELS_PER_MINUTE = 2
private const val TIMELINE_HOURS = 6

/**
 * Live Screen Composable
 */
@Composable
fun LiveScreen(
    categories: List<String>,
    selectedCategory: String?,
    channels: List<Channel>,
    channelPrograms: Map<Long, List<EpgProgram>>,
    isGridMode: Boolean,
    isLoading: Boolean,
    isEpgLoading: Boolean,
    currentTime: Long,
    onCategorySelect: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleMode: () -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    onMultiscreenClick: () -> Unit = {},
    favoriteCategories: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {}
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    // FocusRequesters for header buttons - allow navigation from content back to header
    val searchButtonFocusRequester = remember { FocusRequester() }
    val toggleButtonFocusRequester = remember { FocusRequester() }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Left sidebar - categories with back button
        LiveCategorySidebar(
            categories = categories,
            selectedCategory = selectedCategory,
            favoriteCategories = favoriteCategories,
            onCategorySelect = onCategorySelect,
            onToggleFavorite = onToggleFavorite,
            onBackClick = onBackClick,
            onMultiscreenClick = onMultiscreenClick,
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
        )
        
        // Right content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header with back button and toggle
            LiveHeader(
                selectedCategory = selectedCategory,
                channelCount = channels.size,
                isGridMode = isGridMode,
                onToggleMode = onToggleMode,
                onSearchClick = onSearchClick,
                onBackClick = onBackClick,
                searchButtonFocusRequester = searchButtonFocusRequester,
                toggleButtonFocusRequester = toggleButtonFocusRequester
            )
            
            // Content
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = SandTVColors.Accent
                        )
                    }
                    channels.isEmpty() -> {
                        Text(
                            text = "Nessun canale in questa categoria",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SandTVColors.TextSecondary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    isGridMode -> {
                        // Grid mode - channel cards using TvLazyVerticalGrid for proper D-pad navigation
                        androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid(
                            columns = androidx.tv.foundation.lazy.grid.TvGridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .focusProperties {
                                    // Allow navigating up to header buttons
                                    up = toggleButtonFocusRequester
                                }
                        ) {
                            tvGridItems(channels, key = { it.id }) { channel ->
                                LiveChannelCard(
                                    channel = channel,
                                    currentProgram = channelPrograms[channel.id]?.find { 
                                        it.start <= currentTime && it.end > currentTime 
                                    },
                                    onClick = { onChannelClick(channel) }
                                )
                            }
                        }
                    }
                    else -> {
                        // EPG Timeline mode with time header and red current time line
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Time header
                            EpgTimeHeader(
                                currentTime = currentTime,
                                timeFormat = timeFormat
                            )
                            
                            // Channel rows with programs using TvLazyColumn for proper D-pad navigation
                            Box(modifier = Modifier.weight(1f)) {
                                androidx.tv.foundation.lazy.list.TvLazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusProperties {
                                            // Allow navigating up to header buttons
                                            up = toggleButtonFocusRequester
                                        },
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    tvListItems(channels, key = { it.id }) { channel ->
                                        EpgChannelRow(
                                            channel = channel,
                                            programs = channelPrograms[channel.id] ?: emptyList(),
                                            currentTime = currentTime,
                                            timeFormat = timeFormat,
                                            onClick = { onChannelClick(channel) }
                                        )
                                    }
                                }
                                
                                // Current time line (RED)
                                CurrentTimeLine(currentTime = currentTime)
                                
                                // Loading overlay for EPG
                                if (isEpgLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = SandTVColors.Accent)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Caricamento EPG...",
                                                color = SandTVColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * EPG Time Header - shows hours (e.g., 18:00, 19:00, 20:00...)
 */
@Composable
private fun EpgTimeHeader(
    currentTime: Long,
    timeFormat: SimpleDateFormat
) {
    val calendar = remember(currentTime) {
        Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(SandTVColors.BackgroundSecondary)
            .padding(start = 180.dp) // Offset for channel column
            .horizontalScroll(rememberScrollState())
    ) {
        for (i in 0 until TIMELINE_HOURS) {
            val slotCalendar = calendar.clone() as Calendar
            slotCalendar.add(Calendar.HOUR_OF_DAY, i)
            
            Box(
                modifier = Modifier
                    .width((PIXELS_PER_MINUTE * 60).dp)
                    .fillMaxHeight()
                    .background(
                        if (i == 0) SandTVColors.Accent.copy(alpha = 0.2f) 
                        else Color.Transparent
                    )
                    .border(
                        width = 1.dp,
                        color = SandTVColors.BackgroundTertiary
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = timeFormat.format(slotCalendar.time),
                    color = SandTVColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Current time line (RED vertical line)
 */
@Composable
private fun CurrentTimeLine(currentTime: Long) {
    val calendar = remember(currentTime) { Calendar.getInstance() }
    val currentMinute = calendar.get(Calendar.MINUTE)
    
    // Calculate offset: channel column (180dp) + minutes into current hour
    val channelColumnWidth = 180
    val minuteOffset = currentMinute * PIXELS_PER_MINUTE
    val totalOffset = channelColumnWidth + minuteOffset
    
    Box(
        modifier = Modifier
            .offset(x = totalOffset.dp)
            .width(2.dp)
            .fillMaxHeight()
            .background(Color.Red)
    )
}

/**
 * EPG Channel Row - channel info + horizontal program blocks
 */
@Composable
private fun EpgChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    currentTime: Long,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else SandTVColors.BackgroundSecondary,
        label = "rowBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "rowBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel info column (fixed 180dp)
        Row(
            modifier = Modifier
                .width(180.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
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
            
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = SandTVColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Programs timeline (horizontal scroll)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (programs.isEmpty()) {
                // No program placeholder - spans full timeline width
                EpgProgramBlock(
                    title = "Nessun programma disponibile",
                    timeRange = "",
                    durationMinutes = TIMELINE_HOURS * 60,
                    isCurrent = false,
                    isEmpty = true
                )
            } else {
                programs.forEach { program ->
                    val isCurrent = program.start <= currentTime && program.end > currentTime
                    val durationMinutes = ((program.end - program.start) / 60_000).toInt()
                    
                    EpgProgramBlock(
                        title = program.title,
                        timeRange = "${timeFormat.format(Date(program.start))} - ${timeFormat.format(Date(program.end))}",
                        durationMinutes = durationMinutes,
                        isCurrent = isCurrent,
                        isEmpty = false
                    )
                }
            }
        }
    }
}

/**
 * EPG Program Block - width based on duration
 */
@Composable
private fun EpgProgramBlock(
    title: String,
    timeRange: String,
    durationMinutes: Int,
    isCurrent: Boolean,
    isEmpty: Boolean
) {
    val widthDp = (durationMinutes * PIXELS_PER_MINUTE).coerceAtLeast(60)
    
    val backgroundColor = when {
        isCurrent -> SandTVColors.Accent.copy(alpha = 0.4f)
        isEmpty -> SandTVColors.BackgroundTertiary.copy(alpha = 0.5f)
        else -> SandTVColors.CardBackground
    }
    
    val borderColor = if (isCurrent) SandTVColors.Accent else Color.Transparent
    
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            if (timeRange.isNotEmpty()) {
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = SandTVColors.TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Live header with category info and mode toggle
 */
@Composable
private fun LiveHeader(
    selectedCategory: String?,
    channelCount: Int,
    isGridMode: Boolean,
    onToggleMode: () -> Unit,
    onSearchClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") // onBackClick kept for API consistency
    onBackClick: () -> Unit,
    searchButtonFocusRequester: FocusRequester,
    toggleButtonFocusRequester: FocusRequester
) {
    val searchInteractionSource = remember { MutableInteractionSource() }
    val toggleInteractionSource = remember { MutableInteractionSource() }
    val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()
    val isToggleFocused by toggleInteractionSource.collectIsFocusedAsState()
    
    val searchBorderColor by animateColorAsState(
        targetValue = if (isSearchFocused) SandTVColors.Accent else Color.Transparent,
        label = "searchBorder"
    )
    val toggleBorderColor by animateColorAsState(
        targetValue = if (isToggleFocused) SandTVColors.Accent else Color.Transparent,
        label = "toggleBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category info
        Column {
            Text(
                text = selectedCategory ?: "Live TV",
                style = MaterialTheme.typography.headlineMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$channelCount canali",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
        }
        
        // Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .focusRequester(searchButtonFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, searchBorderColor, RoundedCornerShape(8.dp))
                    .background(if (isSearchFocused) SandTVColors.BackgroundTertiary else SandTVColors.BackgroundSecondary.copy(alpha = 0.5f))
                    .focusable(interactionSource = searchInteractionSource)
                    .clickable(
                        interactionSource = searchInteractionSource,
                        indication = null,
                        onClick = onSearchClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cerca",
                    tint = SandTVColors.TextPrimary
                )
            }
            
            // Toggle button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .focusRequester(toggleButtonFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, toggleBorderColor, RoundedCornerShape(8.dp))
                    .background(if (isToggleFocused) SandTVColors.BackgroundTertiary else SandTVColors.BackgroundSecondary.copy(alpha = 0.5f))
                    .focusable(interactionSource = toggleInteractionSource)
                    .clickable(
                        interactionSource = toggleInteractionSource,
                        indication = null,
                        onClick = onToggleMode
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridMode) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (isGridMode) "Mostra timeline" else "Mostra griglia",
                    tint = SandTVColors.TextPrimary
                )
            }
        }
    }
}

/**
 * Category sidebar with back button at top
 */
@Composable
private fun LiveCategorySidebar(
    categories: List<String>,
    selectedCategory: String?,
    favoriteCategories: Set<String> = emptySet(),
    onCategorySelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onBackClick: () -> Unit,
    onMultiscreenClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SandTVColors.BackgroundPrimary)
    ) {
        // Back button and title at top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandTVColors.BackgroundSecondary.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = SandTVColors.TextPrimary
                )
            }
            
            Text(
                text = "Live TV",
                style = MaterialTheme.typography.titleMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        HorizontalDivider(
            color = SandTVColors.BackgroundTertiary,
            thickness = 1.dp
        )
        
        // Multiscreen button
        MultiscreenButton(onClick = onMultiscreenClick)
        
        // Categories list
        val firstCategoryFocusRequester = remember { FocusRequester() }
        val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
        
        // Request focus on first category when loaded
        LaunchedEffect(categories) {
            if (categories.isNotEmpty()) {
                kotlinx.coroutines.delay(300)
                try {
                    firstCategoryFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            }
        }
        
        androidx.tv.foundation.lazy.list.TvLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(categories) { index, category ->
                LiveCategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    isFavorite = favoriteCategories.contains(category),
                    onClick = { onCategorySelect(category) },
                    onLongPress = { onToggleFavorite(category) },
                    focusRequester = if (index == 0) firstCategoryFocusRequester else null
                )
            }
        }
    }
}

@Composable
private fun LiveCategoryItem(
    category: String,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val coroutineScope = rememberCoroutineScope()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent.copy(alpha = 0.3f)
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "catBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "catBorder"
    )
    
    // Long press detection
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .onKeyEvent { event ->
                if (event.key == androidx.compose.ui.input.key.Key.Enter || event.key == androidx.compose.ui.input.key.Key.DirectionCenter) {
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && !isLongPressing) {
                        isLongPressing = true
                        longPressJob = coroutineScope.launch {
                            delay(1000L) // 1 second
                            onLongPress()
                        }
                    } else if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyUp) {
                        isLongPressing = false
                        longPressJob?.cancel()
                    }
                    false
                } else false
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Preferito",
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        
        Text(
            text = category,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Live Channel Card for grid mode
 */
@Composable
private fun LiveChannelCard(
    channel: Channel,
    currentProgram: EpgProgram?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "channelScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "channelBorder"
    )
    
    Column(
        modifier = Modifier
            .scale(scale)
            .width(140.dp)  // Reduced from 180.dp for more compact grid
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Logo container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)  // Reduced from 100.dp for more compact cards
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .background(SandTVColors.CardBackground),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
            
            // Live badge
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Channel name
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        
        // Current program
        currentProgram?.let { program ->
            Text(
                text = program.title,
                style = MaterialTheme.typography.labelSmall,
                color = SandTVColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Multiscreen button for Live TV sidebar
 */
@Composable
private fun MultiscreenButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> SandTVColors.Accent.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        label = "multiscreenBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "multiscreenBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = "Multiscreen",
            tint = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = "Multiscreen",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
