package it.sandtv.app.ui.category

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.ChannelDao
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.repository.EpgRepository
import it.sandtv.app.ui.details.DetailsActivity
import it.sandtv.app.ui.epg.EPGScreen
import it.sandtv.app.ui.epg.EpgProgram
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.ui.tv.TvContentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Activity for displaying category details (Movies, Series, or Live channels)
 * Shows all content in a grid view, with EPG timeline option for Live categories
 */
@AndroidEntryPoint
class CategoryActivity : ComponentActivity() {
    
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var epgRepository: EpgRepository
    
    private var categoryName: String = ""
    private var contentType: String = "" // "CATEGORY_MOVIE", "CATEGORY_SERIES", "CATEGORY_LIVE"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        categoryName = intent.getStringExtra("categoryName") ?: ""
        contentType = intent.getStringExtra("contentType") ?: ""
        
        setContent {
            SandTVTheme {
                CategoryScreen(
                    categoryName = categoryName,
                    contentType = contentType,
                    onItemClick = { item ->
                        // Navigate to details or play
                        when (item.contentType) {
                            "MOVIE" -> openDetails(item)
                            "SERIES" -> openDetails(item)
                            "CHANNEL" -> playChannel(item.id)
                        }
                    },
                    onChannelClick = { channel -> playChannel(channel.id) },
                    onBack = { finish() },
                    loadItems = { loadCategoryItems() },
                    loadChannels = { loadCategoryChannels() },
                    loadEpgPrograms = { channels -> loadEpgForChannels(channels) }
                )
            }
        }
    }
    
    private suspend fun loadCategoryItems(): List<CarouselItem> {
        return withContext(Dispatchers.IO) {
            // Check for special popular carousel names first
            val isPopularMovies = categoryName.contains("popolari", ignoreCase = true) && !categoryName.contains("serie", ignoreCase = true)
            val isPopularSeries = categoryName.contains("popolari", ignoreCase = true) && categoryName.contains("serie", ignoreCase = true)
            
            when {
                // Popular movies special case
                isPopularMovies || contentType == "POPULAR_MOVIES" -> {
                    // Use strictly the trending category to avoid random content
                    movieDao.getByTrendingCategory("Film Popolari").map { movie ->
                        CarouselItem(
                            id = movie.id,
                            title = movie.name,
                            posterUrl = movie.posterUrl,
                            backdropUrl = movie.backdropUrl,
                            contentType = "MOVIE",
                            year = movie.year,
                            rating = movie.rating
                        )
                    }
                }
                // Popular series special case
                isPopularSeries || contentType == "POPULAR_SERIES" -> {
                    // Use strictly the trending category to avoid random content
                    seriesDao.getByTrendingCategory("Serie Popolari").map { series ->
                        CarouselItem(
                            id = series.id,
                            title = series.name,
                            posterUrl = series.posterUrl,
                            backdropUrl = series.backdropUrl,
                            contentType = "SERIES",
                            year = series.year,
                            rating = series.rating
                        )
                    }
                }
                // Regular movie categories
                contentType == "CATEGORY_MOVIE" -> {
                    movieDao.getMoviesByCategoryList(categoryName).map { movie ->
                        CarouselItem(
                            id = movie.id,
                            title = movie.name,
                            posterUrl = movie.posterUrl,
                            backdropUrl = movie.backdropUrl,
                            contentType = "MOVIE",
                            year = movie.year,
                            rating = movie.rating
                        )
                    }
                }
                // Regular series categories
                contentType == "CATEGORY_SERIES" -> {
                    seriesDao.getSeriesByCategoryList(categoryName).map { series ->
                        CarouselItem(
                            id = series.id,
                            title = series.name,
                            posterUrl = series.posterUrl,
                            backdropUrl = series.backdropUrl,
                            contentType = "SERIES",
                            year = series.year,
                            rating = series.rating
                        )
                    }
                }
                // Live channels
                contentType == "CATEGORY_LIVE" -> {
                    channelDao.getChannelsByCategoryList(categoryName).map { channel ->
                        CarouselItem(
                            id = channel.id,
                            title = channel.name,
                            posterUrl = channel.logoUrl,
                            backdropUrl = null,
                            contentType = "CHANNEL"
                        )
                    }
                }
                else -> emptyList()
            }
        }
    }
    
    private suspend fun loadCategoryChannels(): List<Channel> {
        return withContext(Dispatchers.IO) {
            if (contentType == "CATEGORY_LIVE") {
                channelDao.getChannelsByCategoryList(categoryName)
            } else {
                emptyList()
            }
        }
    }
    
    private suspend fun loadEpgForChannels(channels: List<Channel>): Map<Long, List<EpgProgram>> {
        return withContext(Dispatchers.IO) {
            val programs = mutableMapOf<Long, List<EpgProgram>>()
            channels.forEach { channel ->
                val epgId = channel.xtreamEpgChannelId ?: channel.name
                programs[channel.id] = epgRepository.getProgramsForChannel(epgId)
            }
            programs
        }
    }
    
    private fun openDetails(item: CarouselItem) {
        android.util.Log.d("CategoryActivity", "Opening details: id=${item.id}, type=${item.contentType}")
        try {
            val intent = Intent(this, DetailsActivity::class.java).apply {
                putExtra("content_id", item.id)
                putExtra("content_type", item.contentType)
                putExtra("title", item.title)
                putExtra("poster_url", item.posterUrl)
                putExtra("backdrop_url", item.backdropUrl)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("CategoryActivity", "Error opening details", e)
        }
    }
    
    private fun playChannel(channelId: Long) {
        lifecycleScope.launch {
            val channel = withContext(Dispatchers.IO) { channelDao.getChannelById(channelId) }
            channel?.let { ch ->
                val intent = Intent(this@CategoryActivity, PlayerActivity::class.java).apply {
                    putExtra("stream_url", ch.streamUrl)  // Fixed: must match PlayerActivity's expected key
                    putExtra("content_id", ch.id)
                    putExtra("content_type", "CHANNEL")
                    putExtra("title", ch.name)
                }
                startActivity(intent)
            }
        }
    }
}

/**
 * Category details screen with grid view and optional EPG toggle for Live
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryScreen(
    categoryName: String,
    contentType: String,
    onItemClick: (CarouselItem) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onBack: () -> Unit,
    loadItems: suspend () -> List<CarouselItem>,
    loadChannels: suspend () -> List<Channel>,
    loadEpgPrograms: suspend (List<Channel>) -> Map<Long, List<EpgProgram>>
) {
    var items by remember { mutableStateOf<List<CarouselItem>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var channelPrograms by remember { mutableStateOf<Map<Long, List<EpgProgram>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf("grid") } // "grid" or "epg" (for Live only)
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val isLiveCategory = contentType == "CATEGORY_LIVE"
    
    // Load items and channels
    LaunchedEffect(categoryName, contentType) {
        isLoading = true
        items = loadItems()
        
        // For Live categories, also load channels and EPG
        if (isLiveCategory) {
            channels = loadChannels()
            channelPrograms = loadEpgPrograms(channels)
        }
        
        isLoading = false
    }
    
    // Update time every minute for EPG
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            currentTime = System.currentTimeMillis()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        // Compact Header - minimal height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Compact back button
                androidx.tv.material3.Button(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedContentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Title
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // View toggle (only for Live categories)
            if (isLiveCategory) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewToggleButton(
                        icon = Icons.Default.GridView,
                        label = "Griglia",
                        isSelected = viewMode == "grid",
                        onClick = { viewMode = "grid" }
                    )
                    ViewToggleButton(
                        icon = Icons.Default.ViewTimeline,
                        label = "EPG",
                        isSelected = viewMode == "epg",
                        onClick = { viewMode = "epg" }
                    )
                }
            }
        }
        
        // Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SandTVColors.Accent)
            }
        } else if (viewMode == "grid" || !isLiveCategory) {
            // Grid view - use smaller cells for Live channels
            val gridMinSize = if (isLiveCategory) 100.dp else 150.dp
            TvLazyVerticalGrid(
                columns = TvGridCells.Adaptive(minSize = gridMinSize),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),  // Reduced top padding
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { "${it.contentType}_${it.id}" }) { item ->
                    TvContentCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        customHeight = 220.dp // Increased height as requested
                    )
                }
            }
        } else {
            // EPG Timeline view - using real EPGScreen
            EPGScreen(
                channels = channels,
                channelPrograms = channelPrograms,
                currentTime = currentTime,
                isLoading = false,
                onChannelClick = { channel -> onChannelClick(channel) },
                onProgramClick = { channel, _ -> onChannelClick(channel) }
            )
        }
    }
}

@Composable
private fun ViewToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor = when {
        isSelected -> SandTVColors.Accent
        isFocused -> SandTVColors.BackgroundTertiary
        else -> Color.Transparent
    }
    
    val contentColor = if (isSelected || isFocused) Color.White else SandTVColors.TextSecondary
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

private fun getLabelForContentType(contentType: String): String {
    return when (contentType) {
        "CATEGORY_MOVIE" -> "film"
        "CATEGORY_SERIES" -> "serie"
        "CATEGORY_LIVE" -> "canali"
        else -> "contenuti"
    }
}
