package it.sandtv.app.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items as tvGridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.data.database.dao.ChannelDao
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.ui.details.DetailsActivity
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search result item
 */
data class SearchResultItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val type: ContentType,
    val streamUrl: String?,
    val isCategory: Boolean = false,  // True for category results
    val categoryType: String? = null,  // "movies", "series", or "live"
    val isFavorite: Boolean = false   // True if item is in favorites
)

/**
 * Search Activity - Voice and text search
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class SearchActivity : ComponentActivity() {
    
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var channelDao: ChannelDao
    @Inject lateinit var favoriteDao: it.sandtv.app.data.database.dao.FavoriteDao
    @Inject lateinit var userPreferences: it.sandtv.app.data.preferences.UserPreferences
    
    private var searchJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force keyboard to show on Android TV
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        
        setContent {
            SandTVTheme {
                SearchScreenContent()
            }
        }
    }
    
    @Composable
    private fun SearchScreenContent() {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        
        // Focus on search input at start and show keyboard
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val view = androidx.compose.ui.platform.LocalView.current
        
        // Force keyboard open on Android TV
        LaunchedEffect(Unit) {
            // Wait for full composition before requesting focus
            delay(300)
            try {
                focusRequester.requestFocus()
                // Wait for focus to settle, then show keyboard
                delay(200)
                keyboardController?.show()
                
                // Additionally force open keyboard using InputMethodManager for TV devices
                delay(200)
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                // Try multiple methods to show keyboard on TV
                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                delay(100)
                // Fallback: toggle soft input which works better on some TV devices
                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            } catch (e: Exception) {
                // Ignore focus/keyboard errors on TV devices
            }
        }
        
        // Debounced search
        LaunchedEffect(query) {
            if (query.length >= 2) {
                delay(300) // Debounce
                isLoading = true
                results = performSearch(query)
                isLoading = false
            } else {
                results = emptyList()
            }
        }
        
        SearchScreen(
            query = query,
            onQueryChange = { query = it },
            results = results,
            isLoading = isLoading,
            focusRequester = focusRequester,
            onBackClick = { finish() },
            onItemClick = { item ->
                // Channels play directly, others go to details/category
                if (item.type == ContentType.CHANNEL && !item.isCategory) {
                    playContent(item)
                } else {
                    openDetails(item)
                }
            },
            onItemLongClick = { item ->
                // Long press toggles favorite
                coroutineScope.launch {
                    toggleFavorite(item)
                    // Refresh results to update favorite status
                    if (query.length >= 2) {
                        results = performSearch(query)
                    }
                    val message = if (!item.isFavorite) "Aggiunto ai preferiti" else "Rimosso dai preferiti"
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private suspend fun performSearch(query: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        val profileId = userPreferences.getCurrentProfileId() ?: 1L
        
        // Search movies
        val movies = movieDao.searchMovies(query)
        results.addAll(movies.map { movie ->
            SearchResultItem(
                id = movie.id,
                title = movie.name,
                subtitle = movie.year?.toString(),
                posterUrl = movie.posterUrl,
                type = ContentType.MOVIE,
                streamUrl = movie.streamUrl,
                isFavorite = favoriteDao.isFavorite(profileId, ContentType.MOVIE, movie.id)
            )
        })
        
        // Search series
        val series = seriesDao.searchSeries(query)
        results.addAll(series.map { s ->
            SearchResultItem(
                id = s.id,
                title = s.name,
                subtitle = s.year?.toString(),
                posterUrl = s.posterUrl,
                type = ContentType.SERIES,
                streamUrl = null,
                isFavorite = favoriteDao.isFavorite(profileId, ContentType.SERIES, s.id)
            )
        })
        
        // Search channels
        val channels = channelDao.searchChannels(query)
        results.addAll(channels.map { channel ->
            SearchResultItem(
                id = channel.id,
                title = channel.name,
                subtitle = channel.categoryName,
                posterUrl = channel.logoUrl,
                type = ContentType.CHANNEL,
                streamUrl = channel.streamUrl,
                isFavorite = favoriteDao.isFavorite(profileId, ContentType.CHANNEL, channel.id)
            )
        })
        
        // Search categories (movies)
        val movieCategories = movieDao.getCategoriesList().filter { cat ->
            cat.contains(query, ignoreCase = true)
        }.take(5)
        results.addAll(0, movieCategories.map { category ->
            SearchResultItem(
                id = 0L,
                title = category,
                subtitle = "Categoria Film",
                posterUrl = null,
                type = ContentType.MOVIE,
                streamUrl = null,
                isCategory = true,
                categoryType = "movies"
            )
        })
        
        // Search categories (series)
        val seriesCategories = seriesDao.getCategoriesList().filter { cat ->
            cat.contains(query, ignoreCase = true)
        }.take(5)
        results.addAll(movieCategories.size, seriesCategories.map { category ->
            SearchResultItem(
                id = 0L,
                title = category,
                subtitle = "Categoria Serie TV",
                posterUrl = null,
                type = ContentType.SERIES,
                streamUrl = null,
                isCategory = true,
                categoryType = "series"
            )
        })
        
        // Search categories (live)
        val liveCategories = channelDao.getCategoriesList().filter { cat ->
            cat.contains(query, ignoreCase = true)
        }.take(5)
        results.addAll(movieCategories.size + seriesCategories.size, liveCategories.map { category ->
            SearchResultItem(
                id = 0L,
                title = category,
                subtitle = "Categoria Live",
                posterUrl = null,
                type = ContentType.CHANNEL,
                streamUrl = null,
                isCategory = true,
                categoryType = "live"
            )
        })
        
        return results
    }
    
    private fun openDetails(item: SearchResultItem) {
        if (item.isCategory) {
            // Navigate to CategoryActivity (same as sidebar navigation)
            val contentType = when (item.categoryType) {
                "movies" -> "CATEGORY_MOVIE"
                "series" -> "CATEGORY_SERIES"
                "live" -> "CATEGORY_LIVE"
                else -> "CATEGORY_MOVIE"
            }
            val intent = Intent(this, it.sandtv.app.ui.category.CategoryActivity::class.java).apply {
                putExtra("categoryName", item.title)
                putExtra("contentType", contentType)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, DetailsActivity::class.java).apply {
                putExtra("content_id", item.id)
                putExtra("content_type", item.type.name)
            }
            startActivity(intent)
        }
    }
    
    private fun playContent(item: SearchResultItem) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("content_id", item.id)
            putExtra("content_type", item.type.name)
            putExtra("stream_url", item.streamUrl)
            putExtra("title", item.title)
        }
        startActivity(intent)
    }
    
    private suspend fun toggleFavorite(item: SearchResultItem) {
        // Categories cannot be added to favorites (only individual content)
        if (item.isCategory) return
        
        val profileId = userPreferences.getCurrentProfileId() ?: 1L
        val favorite = it.sandtv.app.data.database.entity.Favorite(
            profileId = profileId,
            contentType = item.type,
            contentId = item.id,
            title = item.title,
            posterUrl = item.posterUrl,
            addedAt = System.currentTimeMillis()
        )
        favoriteDao.toggleFavorite(favorite)
    }
}

/**
 * Search Screen Composable
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResultItem>,
    isLoading: Boolean,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onItemClick: (SearchResultItem) -> Unit,
    onItemLongClick: (SearchResultItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Top spacing
        Spacer(modifier = Modifier.height(24.dp))

        // Search header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SandTVColors.TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Search input
            SearchInput(
                query = query,
                onQueryChange = onQueryChange,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = SandTVColors.Accent
                    )
                }
                query.length >= 2 && results.isEmpty() -> {
                    Text(
                        text = "Nessun risultato per \"$query\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SandTVColors.TextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                results.isNotEmpty() -> {
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                    tvGridItems(results, key = { item -> 
                        if (item.isCategory) "cat_${item.categoryType}_${item.title}" 
                        else "${item.type}_${item.id}" 
                    }) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    }
                    }
                }
                else -> {
                    // Empty state - waiting for input
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SandTVColors.TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cerca film, serie TV e canali",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SandTVColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search input field
 */
@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.BackgroundTertiary,
        label = "searchBorder"
    )
    
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(2.dp, borderColor, RoundedCornerShape(28.dp))
            .background(SandTVColors.BackgroundSecondary)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = SandTVColors.TextTertiary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = SandTVColors.TextPrimary,
                    fontSize = 18.sp
                ),
                cursorBrush = SolidColor(SandTVColors.Accent),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Cerca...",
                            style = TextStyle(
                                color = SandTVColors.TextTertiary,
                                fontSize = 18.sp
                            )
                        )
                    }
                    innerTextField()
                }
            )
            
            // Clear button
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = SandTVColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Search result card
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SearchResultCard(
    item: SearchResultItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Long press detection
    var pressStartTime by remember { mutableStateOf(0L) }
    val longPressThreshold = 500L // 500ms for long press
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "resultScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "resultBorder"
    )
    
    Column(
        modifier = Modifier
            .scale(scale)
            .width(140.dp)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { keyEvent ->
                when {
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (pressStartTime == 0L) {
                                pressStartTime = System.currentTimeMillis()
                            }
                            true // Consume to prevent default click behavior
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            val pressDuration = System.currentTimeMillis() - pressStartTime
                            pressStartTime = 0L
                            if (pressDuration >= longPressThreshold) {
                                // Long press detected - toggle favorite
                                onLongClick()
                            } else {
                                // Short press - normal click
                                onClick()
                            }
                            true // Always consume KeyUp
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        // Poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .background(SandTVColors.CardBackground),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCategory) {
                // Category folder icon with gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    SandTVColors.Accent.copy(alpha = 0.3f),
                                    SandTVColors.Accent.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = SandTVColors.Accent,
                        modifier = Modifier.size(64.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Type badge (top left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            item.isCategory -> Color(0xFF9C27B0) // Purple for categories
                            item.type == ContentType.MOVIE -> SandTVColors.Accent
                            item.type == ContentType.SERIES -> Color(0xFF4CAF50)
                            item.type == ContentType.CHANNEL -> Color.Red
                            else -> SandTVColors.BackgroundSecondary
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when {
                        item.isCategory -> "Categoria"
                        item.type == ContentType.MOVIE -> "Film"
                        item.type == ContentType.SERIES -> "Serie"
                        item.type == ContentType.CHANNEL -> "Live"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Favorite heart icon (top right) - only for non-category items
            if (item.isFavorite && !item.isCategory) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Preferito",
                        tint = Color(0xFFE91E63), // Red/Pink
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title with heart if favorite
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        
        // Subtitle
        item.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SandTVColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
