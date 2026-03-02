package it.sandtv.app.ui.tv

import android.util.Log
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items as tvGridItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import it.sandtv.app.R
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.home.CarouselRow
import it.sandtv.app.ui.home.HeroItem
import it.sandtv.app.ui.home.HomeScreenState
import it.sandtv.app.ui.home.PosterCard
import it.sandtv.app.ui.theme.SandTVColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full TV Home Screen using Jetpack Compose for TV
 * Replaces MainFragment (Leanback BrowseSupportFragment)
 * 
 * Features:
 * - Hero banner with auto-rotate
 * - TvLazyColumn for vertical scrolling of carousel rows
 * - Proper D-pad navigation with focus management
 * - Integration with MainTopBar focus (UP key focuses TopBar)
 * - FocusRestorer for each row to remember last focused item
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    state: HomeScreenState,
    onItemClick: (CarouselItem) -> Unit,
    onSeeAllClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") // kept for API consistency with HomeScreen
    onPlayClick: (CarouselItem) -> Unit = {},
    onTopBarFocusRequest: () -> Unit = {},  // Called when UP from first row
    topBarFocusRequester: FocusRequester? = null,  // FocusRequester for TopBar navigation
    onCreateListClick: () -> Unit = {},  // Called when user clicks create list button
    onHeroClick: (HeroItem) -> Unit = {},  // Called when hero is clicked
    onHeroPlayClick: (HeroItem) -> Unit = {},  // Called when play button on hero is clicked
    onNextHero: () -> Unit = {},  // Navigation
    onPrevHero: () -> Unit = {},
    // Category filter callbacks
    onToggleCategoryFilter: (String) -> Unit = {},
    onSelectAllCategories: () -> Unit = {},
    onClearCategoryFilters: () -> Unit = {},
    onToggleHeroFavorite: (HeroItem) -> Unit = {},
    onAddHeroToPlaylist: (HeroItem) -> Unit = {},
    onTrailerClick: (HeroItem) -> Unit = {},
    onMarkAsWatchedClick: (HeroItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Key scroll state on first hero ID to reset when switching tabs
    val heroKey = state.heroItems.firstOrNull()?.id ?: 0
    key(heroKey) {
        TvHomeScreenContent(
            state = state,
            onItemClick = onItemClick,
            onSeeAllClick = onSeeAllClick,
            onPlayClick = onPlayClick,
            onTopBarFocusRequest = onTopBarFocusRequest,
            topBarFocusRequester = topBarFocusRequester,
            onCreateListClick = onCreateListClick,
            onHeroClick = onHeroClick,
            onHeroPlayClick = onHeroPlayClick,
            onNextHero = onNextHero,
            onPrevHero = onPrevHero,
            onToggleCategoryFilter = onToggleCategoryFilter,
            onSelectAllCategories = onSelectAllCategories,
            onClearCategoryFilters = onClearCategoryFilters,
            onToggleHeroFavorite = onToggleHeroFavorite,
            onAddHeroToPlaylist = onAddHeroToPlaylist,
            onTrailerClick = onTrailerClick,
            onMarkAsWatchedClick = onMarkAsWatchedClick,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvHomeScreenContent(
    state: HomeScreenState,
    onItemClick: (CarouselItem) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onPlayClick: (CarouselItem) -> Unit = {},
    onTopBarFocusRequest: () -> Unit = {},
    topBarFocusRequester: FocusRequester? = null,
    onCreateListClick: () -> Unit = {},
    onHeroClick: (HeroItem) -> Unit = {},
    onHeroPlayClick: (HeroItem) -> Unit = {},
    onNextHero: () -> Unit = {},
    onPrevHero: () -> Unit = {},
    onToggleCategoryFilter: (String) -> Unit = {},
    onSelectAllCategories: () -> Unit = {},
    onClearCategoryFilters: () -> Unit = {},
    onToggleHeroFavorite: (HeroItem) -> Unit = {},
    onAddHeroToPlaylist: (HeroItem) -> Unit = {},
    onTrailerClick: (HeroItem) -> Unit = {},
    onMarkAsWatchedClick: (HeroItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val columnListState = rememberTvLazyListState()
    @Suppress("UNUSED_VARIABLE") // kept for future focus management
    val focusManager = LocalFocusManager.current
    
    // No change neededsters for each carousel row
    val rowFocusRequesters = remember(state.carouselRows.size) {
        List(state.carouselRows.size) { FocusRequester() }
    }
    
    // First row focus requester for TOP -> content navigation
    @Suppress("UNUSED_VARIABLE") // kept for future TOP -> content navigation
    val firstRowFocusRequester = remember { FocusRequester() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.isLoading) {
            // Loading state
            SkeletonLoader()
        } else if (state.isGridMode && state.carouselRows.isNotEmpty()) {
            // Grid mode - show all items in a grid
            val items = state.carouselRows.first().items
            val categoryTitle = state.selectedCategory ?: state.carouselRows.first().title
            
            // Track grid state for proper UP navigation
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val backButtonFocusRequester = remember { FocusRequester() }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 50.dp)
            ) {
                // Category title header with optional filter button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Back Button
                        val backInteractionSource = remember { MutableInteractionSource() }
                        val isBackFocused by backInteractionSource.collectIsFocusedAsState()
                        val backScale by animateFloatAsState(targetValue = if (isBackFocused) 1.1f else 1f, label = "backScale")
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(backScale)
                                .background(
                                    color = if (isBackFocused) SandTVColors.Accent else SandTVColors.BackgroundTertiary,
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (isBackFocused) 2.dp else 0.dp,
                                    color = if (isBackFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = backInteractionSource,
                                    indication = null, 
                                    onClick = { onSeeAllClick("") }
                                )
                                .focusRequester(backButtonFocusRequester)
                                .focusable(interactionSource = backInteractionSource)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = if (isBackFocused) Color.White else SandTVColors.TextPrimary
                            )
                        }
                        
                        // Title
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = SandTVColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Filter button (only show when viewing all content)
                    if (state.availableCategories.isNotEmpty()) {
                        CategoryFilterButton(
                            selectedCount = state.selectedCategoryFilters.size,
                            totalCount = state.availableCategories.size,
                            availableCategories = state.availableCategories,
                            selectedCategories = state.selectedCategoryFilters,
                            onToggleCategory = onToggleCategoryFilter,
                            onSelectAll = onSelectAllCategories,
                            onClearAll = onClearCategoryFilters
                        )
                    }
                }
                
                // Grid of posters using TV Compose grid for proper D-pad navigation
                TvLazyVerticalGrid(
                    columns = TvGridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 80.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),  // More space for titles
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { keyEvent ->
                            // Only intercept UP when at very top of grid (first item visible at offset 0)
                            if (keyEvent.type == KeyEventType.KeyDown && 
                                keyEvent.key == Key.DirectionUp) {
                                // For TV grid, just focus back button when UP is pressed
                                backButtonFocusRequester.requestFocus()
                                true
                            } else if (keyEvent.type == KeyEventType.KeyDown && 
                                keyEvent.key == Key.Back) {
                                // Explicitly handle back key for grid mode
                                onSeeAllClick("") // Using empty string as signal to close
                                true
                            } else {
                                false
                            }
                        }
                ) {
                    tvGridItems(items, key = { "${it.contentType}_${it.id}" }) { item ->
                        var isFocused by remember { mutableStateOf(false) }
                        
                        // Grid item with fixed heights to guarantee title visibility
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .height(260.dp)  // Fixed total height: 200dp poster + 60dp for title area
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                }
                                .focusable()
                                .scale(if (isFocused) 1.05f else 1f)
                                .clickable { onItemClick(item) }
                        ) {
                            // Poster image with fixed height
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (isFocused) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = SandTVColors.Accent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .background(SandTVColors.CardBackground)
                            ) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            // Title text with guaranteed space
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFocused) SandTVColors.Accent else Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)  // Fixed height for 2 lines of text
                            )
                        }
                    }
                }
            }
        } else if (state.isFavoritesTab && state.carouselRows.isEmpty()) {
            android.util.Log.d("TvHomeScreenDebug", "BRANCH: Favorites empty")
            // Empty state for Favorites/Preferiti tab
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = SandTVColors.TextTertiary,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "I tuoi Preferiti",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "I contenuti che aggiungi ai preferiti appariranno qui",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
            }
        } else if (state.isListsTab && state.carouselRows.isEmpty()) {
            android.util.Log.d("TvHomeScreenDebug", "BRANCH: Lists empty")
            // Empty state for Lists tab - show create first list prompt
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SandTVColors.TextTertiary,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Title
                Text(
                    text = "Crea la tua prima lista",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Subtitle
                Text(
                    text = "Organizza i tuoi contenuti in liste personalizzate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Create button with focus handling
                val createButtonInteractionSource = remember { MutableInteractionSource() }
                val isCreateFocused by createButtonInteractionSource.collectIsFocusedAsState()
                
                val buttonScale by animateFloatAsState(
                    targetValue = if (isCreateFocused) 1.05f else 1f,
                    label = "createButtonScale"
                )
                
                Box(
                    modifier = Modifier
                        .scale(buttonScale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCreateFocused) SandTVColors.AccentLight else SandTVColors.Accent)
                        .focusable(interactionSource = createButtonInteractionSource)
                        .clickable(
                            interactionSource = createButtonInteractionSource,
                            indication = null,
                            onClick = onCreateListClick
                        )
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = SandTVColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Nuova lista",
                            style = MaterialTheme.typography.labelLarge,
                            color = SandTVColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else if (state.isHistoryTab && state.carouselRows.isEmpty()) {
            android.util.Log.d("TvHomeScreenDebug", "BRANCH: History empty")
            // Empty state for History tab
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.cronologia),
                    contentDescription = null,
                    tint = SandTVColors.TextTertiary,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Nessuna cronologia",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "I contenuti visti di recente appariranno qui",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
            }
        } else if (state.carouselRows.isEmpty() && state.heroItems.isEmpty()) {
            android.util.Log.d("TvHomeScreenDebug", "BRANCH: Generic empty state (waiting 10s)")
            // Generic Empty State (for Movies/Series when no content found)
            // Show only after a delay to avoid flash during normal loading
            var showEmptyState by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                delay(10000) // Wait 10 seconds before showing empty state
                showEmptyState = true
            }
            
            if (showEmptyState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brush = SandTVColors.BackgroundGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SandTVColors.TextTertiary,
                            modifier = Modifier.size(80.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Nessun contenuto trovato",
                            style = MaterialTheme.typography.headlineSmall,
                            color = SandTVColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Potrebbe esserci un errore di caricamento o il database è vuoto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandTVColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Show loading indicator while waiting
                SkeletonLoader()
            }
        } else {
            android.util.Log.d("TvHomeScreenDebug", "BRANCH: Normal carousel mode")
            // Carousel mode - hero is first item in TvLazyColumn
            // Simple layout: hero -> carousel rows, normal focus chain
            val coroutineScope = rememberCoroutineScope()
            val heroPlayButtonFocusRequester = remember { FocusRequester() }
            
            val hasHero = state.heroItems.isNotEmpty() && !state.isListsTab && !state.isHistoryTab
            
            // Track if hero buttons have focus to prevent scroll drift
            var isHeroFocused by remember { mutableStateOf(false) }
            var wasHeroFocused by remember { mutableStateOf(false) }
            // Track if focus left via DOWN (to carousel) vs UP (to TopBar)
            var focusWentDown by remember { mutableStateOf(false) }
            
            // When focus leaves Hero via DOWN, scroll carousel into view
            // Don't scroll if focus went UP to TopBar
            LaunchedEffect(isHeroFocused) {
                if (wasHeroFocused && !isHeroFocused && state.carouselRows.isNotEmpty() && focusWentDown) {
                    // Focus left Hero going DOWN - scroll to show first carousel
                    columnListState.animateScrollToItem(1)
                }
                wasHeroFocused = isHeroFocused
                // Reset direction tracking
                if (!isHeroFocused) focusWentDown = false
            }
            
            TvLazyColumn(
                state = columnListState,
                contentPadding = PaddingValues(
                    top = 80.dp,  // Account for TopBar
                    bottom = 56.dp
                ),
                // Pivot at 20% from top - high enough to hide Hero and show carousel title
                pivotOffsets = PivotOffsets(parentFraction = 0.20f),
                // Disable user scroll while Hero is focused to keep it stable
                // TvLazyColumn will still auto-scroll when focus changes (DOWN to carousel)
                userScrollEnabled = !isHeroFocused,
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Banner as first item
                if (hasHero) {
                    val firstHeroId = state.heroItems.firstOrNull()?.id ?: 0
                    item(key = "hero_banner_$firstHeroId") {
                        val currentHero = state.heroItems.getOrNull(state.currentHeroIndex)
                        currentHero?.let { hero ->
                            // Track Hero focus state and handle UP/DOWN navigation
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        isHeroFocused = focusState.hasFocus
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionUp -> {
                                                    // UP: Navigate to TopBar, don't scroll
                                                    onTopBarFocusRequest()
                                                    true // Consume event
                                                }
                                                Key.DirectionDown -> {
                                                    // DOWN: Mark that focus is going down, allow scroll
                                                    focusWentDown = true
                                                    false // Don't consume, let focus move down
                                                }
                                                else -> false
                                            }
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                HeroBanner(
                                    heroItem = hero,
                                    currentIndex = state.currentHeroIndex,
                                    totalCount = state.heroItems.size,
                                    isContinueWatching = state.isContinueWatchingHero,
                                    onPlayClick = { onHeroPlayClick(hero) },
                                    onInfoClick = { clickedHero -> onHeroClick(clickedHero) },
                                    onPrevClick = onPrevHero,
                                    onNextClick = onNextHero,
                                    onAutoNext = onNextHero,
                                    onFavoriteClick = { onToggleHeroFavorite(hero) },
                                    onAddToPlaylistClick = { onAddHeroToPlaylist(hero) },
                                    onTrailerClick = { onTrailerClick(hero) },
                                    onMarkAsWatchedClick = { onMarkAsWatchedClick(hero) },
                                    playButtonFocusRequester = heroPlayButtonFocusRequester,
                                    onFocusChanged = { /* Handled by internal logic if needed */ },
                                    topBarFocusRequester = topBarFocusRequester  // UP navigates to TopBar
                                )
                            }
                        }
                    }
                }
                
                // Carousel rows
                itemsIndexed(
                    items = state.carouselRows,
                    key = { _, row -> row.title }
                ) { index, row ->
                    // Section headers are rendered differently
                    if (row.isSectionHeader) {
                        SectionHeader(title = row.title)
                    } else {
                        val isFirstRow = index == 0 || 
                            (index > 0 && state.carouselRows.getOrNull(index - 1)?.isSectionHeader == true)
                        
                            // Add spacing above rows to avoid them being stuck at the very top
                        // contentPadding handles general top, but specific item padding helps pivot alignment
                        Column {
                            Spacer(modifier = Modifier.height(30.dp))
                            
                            // First carousel with Hero: intercept UP to return to Hero
                            if (isFirstRow && hasHero) {
                                Box(
                                    modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && 
                                            keyEvent.key == Key.DirectionUp) {
                                            // Navigate back to Hero
                                            coroutineScope.launch {
                                                columnListState.animateScrollToItem(0)
                                                delay(100)
                                                try {
                                                    heroPlayButtonFocusRequester.requestFocus()
                                                } catch (e: Exception) { /* ignore */ }
                                            }
                                            true // Consume event
                                        } else {
                                            false
                                        }
                                    }
                                ) {
                                    TvCarouselRow(
                                        row = row,
                                        onItemClick = onItemClick,
                                        onSeeAllClick = { onSeeAllClick(row.title) },
                                        focusRequester = rowFocusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                                    )
                                }
                            } else {
                                // Other carousels: normal navigation
                                TvCarouselRow(
                                    row = row,
                                    onItemClick = onItemClick,
                                    onSeeAllClick = { onSeeAllClick(row.title) },
                                    focusRequester = rowFocusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                                )
                            }
                        }
                }
            }
            }
            
            // NOTE: Focus is NOT requested here - user starts with TopBar focused
            // so they can navigate between tabs (Film, Serie TV, Live) first
        }
    }
}

/**
 * Section Header - displays a styled section title for organizing content
 * Used in Favorites tab to separate categories, movies, series, and channels
 */
@Composable
private fun SectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp, start = 48.dp, end = 48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Section icon based on title
            val icon = when {
                title.contains("Categorie", ignoreCase = true) -> Icons.Default.Folder
                title.contains("Film", ignoreCase = true) -> Icons.Default.Movie
                title.contains("Serie", ignoreCase = true) -> Icons.Default.Tv
                title.contains("Canali", ignoreCase = true) -> Icons.Default.LiveTv
                else -> Icons.Default.Star
            }
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SandTVColors.Accent,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtle divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = 0.5f),
                            SandTVColors.Accent.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Request focus on the first carousel row
 * Called by MainActivity when DOWN from TopBar
 */
@Composable
fun rememberFirstRowFocusRequester(): FocusRequester {
    return remember { FocusRequester() }
}

/**
 * Hero Banner - shows continue watching or random content
 * ~40% height, auto-rotates every 7 seconds with slide animation
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HeroBanner(
    heroItem: HeroItem,
    currentIndex: Int,
    totalCount: Int,
    isContinueWatching: Boolean,
    onPlayClick: () -> Unit,
    onInfoClick: (HeroItem) -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onAutoNext: () -> Unit,
    onTrailerClick: (HeroItem) -> Unit,
    onFavoriteClick: (HeroItem) -> Unit = {},
    onAddToPlaylistClick: (HeroItem) -> Unit = {},
    onMarkAsWatchedClick: (HeroItem) -> Unit = {},
    playButtonFocusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},  // Called when any hero button gains/loses focus
    topBarFocusRequester: FocusRequester? = null,  // FocusRequester for TopBar - UP navigation redirect
    modifier: Modifier = Modifier
) {
    // Auto-rotate every 7 seconds (only when not focused)
    var isPaused by remember { mutableStateOf(false) }
    // Track slide direction
    var slideDirection by remember { mutableIntStateOf(1) }
    // Dialog state for mark as watched confirmation
    var showMarkAsWatchedDialog by remember { mutableStateOf(false) }
    
    // Propagate focus state to parent to control scroll behavior
    LaunchedEffect(isPaused) {
        onFocusChanged(isPaused)
    }
    
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused && totalCount > 1) {
            delay(7000)
            slideDirection = 1
            onAutoNext()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)  // Standard height
    ) {
        // Animated content with slide transition - ENTIRE HERO BLOCK slides
        AnimatedContent(
            targetState = heroItem,
            contentKey = { it.id },
            transitionSpec = {
                val enterOffset = if (slideDirection > 0) 300 else -300
                val exitOffset = if (slideDirection > 0) -300 else 300
                
                slideInHorizontally(
                    initialOffsetX = { enterOffset },
                    animationSpec = tween(500)
                ) + fadeIn(tween(300)) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { exitOffset },
                    animationSpec = tween(500)
                ) + fadeOut(tween(300))
            },
            label = "heroSlide"
        ) { hero ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Backdrop image - reduced size to show more on screen
                AsyncImage(
                    model = hero.backdropUrl ?: hero.posterUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,  // Maintain aspect ratio
                    alignment = Alignment.Center,  // Center the image
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.60f)  // 60% width instead of full
                        .align(Alignment.CenterEnd)  // Align to right side
                )

                
                
                
                // Top fade - pure black for backdrop fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black,
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Left fade - strong black for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black,
                                    0.30f to Color.Black.copy(alpha = 0.98f),
                                    0.45f to Color.Black.copy(alpha = 0.85f),
                                    0.60f to Color.Black.copy(alpha = 0.5f),
                                    0.80f to Color.Transparent
                                )
                            )
                        )
                )
                
                // Right fade - subtle black
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(100.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
                
                // Bottom fade - for smooth transition
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black)
                            )
                        )
                )
                
                // Content - inside animation block for full slide effect
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 80.dp, end = 24.dp, top = 40.dp, bottom = 20.dp)
                        .fillMaxHeight()
                        .fillMaxWidth(), // Force full width for buttons
                    verticalArrangement = Arrangement.Bottom  // Put content at bottom
                ) {
                        // Title
                        Text(
                            text = hero.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = SandTVColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Metadata row: Year, Duration/Seasons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            hero.year?.let {
                                Text(
                                    text = it.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextSecondary
                                )
                            }
                            hero.duration?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextSecondary
                                )
                            }
                            // Season count for series
                            hero.seasonCount?.takeIf { it > 0 }?.let { seasons ->
                                Text(
                                    text = if (seasons == 1) "1 Stagione" else "$seasons Stagioni",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextSecondary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Ratings badges with logos (matching DetailsScreen style)
                        // Ratings - Modern minimal style
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // IMDB - Always show, N/A if null
                            val imdbRating = hero.imdbRating
                            if (imdbRating != null) {
                                HeroRatingItem(
                                    iconResId = R.drawable.imdb_logo,
                                    value = imdbRating,
                                    label = "IMDb"
                                )
                            } else {
                                HeroRatingItem(
                                    iconResId = R.drawable.imdb_na,
                                    value = "N/A",
                                    label = "IMDb"
                                )
                            }
                            
                            // Tomatometer (Critics Score) - Always show, N/A if null
                            val rtScore = hero.rottenTomatoesScore
                            if (rtScore != null) {
                                val isFresh = rtScore >= 60
                                HeroRatingItem(
                                    iconResId = if (isFresh) R.drawable.rotten_tomatoes_logo else R.drawable.rotten_tomatoes_rotten,
                                    value = "$rtScore%",
                                    label = "Tomatometer®"
                                )
                            } else {
                                HeroRatingItem(
                                    iconResId = R.drawable.rotten_tomatoes_no_score,
                                    value = "N/A",
                                    label = "Tomatometer®"
                                )
                            }
                            
                            // Popcornmeter (Audience Score) - Always show, N/A if null
                            val audScore = hero.audienceScore
                            if (audScore != null) {
                                val isFresh = audScore >= 60
                                HeroRatingItem(
                                    iconResId = if (isFresh) R.drawable.popcornmeter_fresh else R.drawable.popcornmeter_rotten,
                                    value = "$audScore%",
                                    label = "Popcornmeter®"
                                )
                            } else {
                                HeroRatingItem(
                                    iconResId = R.drawable.popcornmeter_na,
                                    value = "N/A",
                                    label = "Popcornmeter®"
                                )
                            }
                            
                            // Metacritic - Always show, N/A if null
                            val metaScore = hero.metacriticScore
                            if (metaScore != null) {
                                HeroRatingItem(
                                    iconResId = R.drawable.metacritic_logo,
                                    value = "$metaScore",
                                    label = "Metascore"
                                )
                            } else {
                                HeroRatingItem(
                                    iconResId = R.drawable.metacritic_na,
                                    value = "N/A",
                                    label = "Metascore"
                                )
                            }
                            
                            // TMDB (always show, with N/A if not available)
                            val tmdbRating = hero.tmdbRating?.takeIf { it > 0 }
                            if (tmdbRating != null) {
                                HeroRatingItem(
                                    iconResId = R.drawable.tmdb_logo,
                                    value = String.format("%.1f", tmdbRating),
                                    label = "TMDb"
                                )
                            } else {
                                HeroRatingItem(
                                    iconResId = R.drawable.tmdb_na,
                                    value = "N/A",
                                    label = "TMDb"
                                )
                            }
                        }
                        
                        // Genres
                        hero.genres?.let { genres ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = genres,
                                style = MaterialTheme.typography.bodySmall,
                                color = SandTVColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Overview with "Leggi di più" - Limited width for readability
                        hero.overview?.let { overview ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(0.6f)  // Limit overview width relative to new parent width
                            ) {
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 20.sp
                                )
                                if (overview.length > 100) {
                                    Text(
                                        text = "Leggi di più...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SandTVColors.Accent,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            // Make not focusable via D-pad - users should use Info button
                                            .focusProperties { canFocus = false }
                                            .clickable { onInfoClick(hero) }
                                    )
                                }
                            }
                        }
                        
                        // Cast - Limited width for readability
                        hero.cast?.let { cast ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Cast: $cast",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 18.sp
                                ),
                                color = SandTVColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(0.65f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Action buttons - exactly like DetailsScreen
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play button using Material3 Button with progress bar
                            val playInteractionSource = remember { MutableInteractionSource() }
                            val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
                            
                            LaunchedEffect(isPlayFocused) { isPaused = isPlayFocused }
                            
                            val playScale by animateFloatAsState(
                                targetValue = if (isPlayFocused) 1.1f else 1f,  // Increased from 1.05f for better visibility
                                label = "playScale"
                            )
                            
                            // Different style for resume vs play
                            val hasProgress = hero.resumeMinutes != null
                            val playBg by animateColorAsState(
                                targetValue = when {
                                    hasProgress && isPlayFocused -> Color.White
                                    hasProgress -> Color.White.copy(alpha = 0.95f)
                                    isPlayFocused -> SandTVColors.AccentLight
                                    else -> SandTVColors.Accent
                                },
                                label = "playBg"
                            )
                            val playContent = if (hasProgress) Color.Black else SandTVColors.TextPrimary
                            
                            val playBorderColor by animateColorAsState(
                                targetValue = if (isPlayFocused) SandTVColors.Accent else Color.Transparent,
                                label = "playBorder"
                            )

                            // Play button with focusProperties to redirect UP to TopBar
                            Button(
                                onClick = onPlayClick,
                                modifier = Modifier
                                    .scale(playScale)
                                    .then(if (playButtonFocusRequester != null) Modifier.focusRequester(playButtonFocusRequester) else Modifier)
                                    .focusProperties {
                                        // Redirect UP navigation to TopBar
                                        if (topBarFocusRequester != null) {
                                            up = topBarFocusRequester
                                        }
                                    }
                                    .height(52.dp)
                                    .border(3.dp, playBorderColor, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp)) // Clip content (including progress bar) to match button shape
                                    .then(
                                        if (hasProgress && hero.progressPercent != null) {
                                            val progress = hero.progressPercent.coerceIn(0.05f, 1f)
                                            Modifier.drawWithContent {
                                                drawContent()
                                                val trackHeight = 4.dp.toPx() // Thinner
                                                val trackY = size.height - trackHeight
                                                
                                                // Draw track (black background)
                                                drawRect(
                                                    color = Color.Black.copy(alpha = 0.4f),
                                                    topLeft = Offset(0f, trackY),
                                                    size = Size(size.width, trackHeight)
                                                )
                                                
                                                // Draw progress
                                                drawRect(
                                                    color = SandTVColors.Accent, // Use accent color instead of hardcoded purple
                                                    topLeft = Offset(0f, trackY),
                                                    size = Size(size.width * progress, trackHeight)
                                                )
                                            }
                                        } else Modifier
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = playBg,
                                    contentColor = playContent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                interactionSource = playInteractionSource
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Text with episode info like DetailsScreen
                                val buttonText = if (hasProgress && hero.resumeMinutes != null) {
                                    val episodeInfo = if (hero.resumeEpisodeSeason != null && hero.resumeEpisodeNumber != null) {
                                        "S${hero.resumeEpisodeSeason} E${hero.resumeEpisodeNumber} - "
                                    } else ""
                                    "${episodeInfo}Riprendi"
                                } else if (hero.resumeEpisodeSeason != null && hero.resumeEpisodeNumber != null) {
                                    "Riproduci S${hero.resumeEpisodeSeason} E${hero.resumeEpisodeNumber}"
                                } else {
                                    "Riproduci"
                                }
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp)) // Gap after Play button
                            
                            // Text: "xx min rimasti di yy min" - Same level as buttons
                            if (hasProgress && hero.resumeMinutes != null && hero.totalDurationMinutes != null) {
                                Text(
                                    text = "${hero.resumeMinutes} min rimasti di ${hero.totalDurationMinutes} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SandTVColors.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp)) // Gap before icon buttons
                            }
                            
                            // Trailer button (only if key exists)
                            if (hero.trailerKey != null) {
                                HeroTrailerButton(
                                    onClick = { onTrailerClick(hero) },
                                    onFocusChange = { if (it) isPaused = true }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            
                            // Favorite button
                            HeroIconButton(
                                icon = if (hero.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (hero.isFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                                onClick = { onFavoriteClick(hero) },
                                onFocusChange = { if (it) isPaused = true },
                                isActive = hero.isFavorite  // Red when favorite
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // List button
                            HeroIconButton(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Aggiungi alla lista",
                                onClick = { onAddToPlaylistClick(hero) },
                                onFocusChange = { if (it) isPaused = true }
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // Info button
                            HeroIconButton(
                                icon = Icons.Default.Info,
                                contentDescription = "Info",
                                onClick = { onInfoClick(hero) },
                                onFocusChange = { if (it) isPaused = true }
                            )
                            
                            // Mark as watched button (only for continue watching)
                            if (isContinueWatching) {
                                Spacer(modifier = Modifier.width(4.dp))
                                HeroIconButton(
                                    icon = painterResource(id = R.drawable.ic_eye),
                                    contentDescription = "Segna come già visto",
                                    onClick = { showMarkAsWatchedDialog = true },
                                    onFocusChange = { if (it) isPaused = true }
                                )
                            }
                        }
                    // Removed inner Column closing brace
                }  // Close outer Column
            }
        }
        
        // Mark as watched confirmation dialog
        if (showMarkAsWatchedDialog) {
            AlertDialog(
                onDismissRequest = { showMarkAsWatchedDialog = false },
                title = { Text("Conferma") },
                text = { Text("Sei sicuro di eliminare il contenuto dai \"Continua a guardare\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showMarkAsWatchedDialog = false
                            onMarkAsWatchedClick(heroItem)
                        }
                    ) {
                        Text("Sì", color = SandTVColors.Accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMarkAsWatchedDialog = false }) {
                        Text("Annulla", color = SandTVColors.TextSecondary)
                    }
                },
                containerColor = SandTVColors.BackgroundSecondary,
                titleContentColor = SandTVColors.TextPrimary,
                textContentColor = SandTVColors.TextSecondary
            )
        }
        
        // Navigation arrows (OUTSIDE animation - not focusable, just clickable)
        if (totalCount > 1) {
            // Left arrow
            HeroNavArrow(
                isLeft = true,
                onClick = {
                    slideDirection = -1
                    onPrevClick()
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            )
            
            // Right arrow
            HeroNavArrow(
                isLeft = false,
                onClick = {
                    slideDirection = 1
                    onNextClick()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
        
        // Pagination indicator (OUTSIDE animation - stay stable)
        if (isContinueWatching && totalCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SandTVColors.BackgroundSecondary.copy(alpha = 0.8f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${currentIndex + 1}/$totalCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}



/**
 * Hero action button (Play, Info) with optional progress bar
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HeroButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    progress: Float? = null,  // 0.0 to 1.0, null = no progress bar
    focusRequester: FocusRequester? = null,
    onLeftPress: (() -> Unit)? = null,   // Called when LEFT D-pad pressed while focused
    onRightPress: (() -> Unit)? = null   // Called when RIGHT D-pad pressed while focused
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "heroButtonScale"
    )
    
    val backgroundColor = when {
        isPrimary && isFocused -> Color.White
        isPrimary -> Color.White.copy(alpha = 0.9f)
        isFocused -> SandTVColors.BackgroundTertiary
        else -> SandTVColors.BackgroundSecondary.copy(alpha = 0.8f)
    }
    
    val contentColor = if (isPrimary) Color.Black else SandTVColors.TextPrimary
    
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Button content
        Row(
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent { keyEvent ->
                    // Handle LEFT/RIGHT to trigger prev/next hero (when available)
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                onLeftPress?.let { it(); true } ?: false
                            }
                            Key.DirectionRight -> {
                                onRightPress?.let { it(); true } ?: false
                            }
                            else -> false
                        }
                    } else false
                }
                .background(backgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Progress bar at bottom of button (if provided)
        progress?.let { prog ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(prog.coerceIn(0f, 1f))
                        .background(SandTVColors.Accent)
                )
            }
        }
    }
}

/**
 * Hero circular icon button (for secondary actions like Info, Favorites, List)
 * When isActive = true, uses same styling as FavoriteButton in DetailsScreen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HeroIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onLeftPress: (() -> Unit)? = null,
    onRightPress: (() -> Unit)? = null,
    isActive: Boolean = false  // When true, uses FavoriteButton styling (pink/red)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }
    
    // Bounce animation when becoming active (same as FavoriteButton)
    var bounceScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            bounceScale = 1.4f
            kotlinx.coroutines.delay(50)
            bounceScale = 1f
        }
    }
    
    val animatedBounce by animateFloatAsState(
        targetValue = bounceScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconBounce"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "heroIconButtonScale"
    )
    
    // Pink color from FavoriteButton: 0xFFE91E63
    val activeColor = Color(0xFFE91E63)
    
    // Background - matches FavoriteButton exactly
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor.copy(alpha = 0.15f)  // Pink tint when active
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.BackgroundSecondary.copy(alpha = 0.5f)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "heroBtnBg"
    )
    
    // Border - matches FavoriteButton exactly  
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> activeColor  // Pink border when active
            isFocused -> SandTVColors.Accent  // Accent when focused
            else -> SandTVColors.TextSecondary.copy(alpha = 0.7f)  // Visible default
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "heroBtnBorder"
    )
    
    // Icon tint - matches FavoriteButton exactly
    val iconTint by animateColorAsState(
        targetValue = if (isActive) activeColor else SandTVColors.TextPrimary,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "heroBtnTint"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onLeftPress?.let { it(); true } ?: false
                        }
                        Key.DirectionRight -> {
                            onRightPress?.let { it(); true } ?: false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .scale(animatedBounce)
        )
    }
}

/**
 * Hero circular icon button variant that accepts a Painter (for custom drawables)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HeroIconButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onLeftPress: (() -> Unit)? = null,
    onRightPress: (() -> Unit)? = null,
    isActive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "heroIconButtonScale"
    )
    
    // Background
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFFE91E63).copy(alpha = 0.15f)
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.BackgroundSecondary.copy(alpha = 0.5f)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "heroBtnBg"
    )
    
    // Border
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> Color(0xFFE91E63)
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary.copy(alpha = 0.7f)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "heroBtnBorder"
    )
    
    // Icon tint
    val iconTint by animateColorAsState(
        targetValue = if (isActive) Color(0xFFE91E63) else SandTVColors.TextPrimary,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "heroBtnTint"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onLeftPress?.let { it(); true } ?: false
                        }
                        Key.DirectionRight -> {
                            onRightPress?.let { it(); true } ?: false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Hero Trailer button with YouTube logo
 */
@Composable
private fun HeroTrailerButton(
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "heroTrailerScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else SandTVColors.BackgroundSecondary.copy(alpha = 0.6f),
        label = "heroTrailerBg"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(48.dp)
            .clip(CircleShape)
            .border(1.dp, SandTVColors.TextSecondary.copy(alpha = 0.5f), CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_youtube_logo),
            contentDescription = "Trailer",
            modifier = Modifier.size(28.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Hero navigation arrow
 * Focusable with purple border when focused
 */
@Composable
private fun HeroNavArrow(
    isLeft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "arrowScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.BackgroundSecondary.copy(alpha = 0.6f),
        label = "arrowBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "arrowBorder"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .size(48.dp)
            .clip(CircleShape)
            .border(3.dp, borderColor, CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLeft) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
            contentDescription = if (isLeft) "Precedente" else "Successivo",
            tint = SandTVColors.TextPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Hero rating item - Modern minimal style (icon + value, label below)
 */
@Composable
private fun HeroRatingItem(
    iconResId: Int,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon + Value row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(30.dp),  // Increased from 28dp
                contentScale = ContentScale.Fit
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
        
        // Label below
        Spacer(modifier = Modifier.height(4.dp))  // Increased from 2dp
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SandTVColors.TextTertiary,
            fontSize = 10.sp
        )
    }
}

/**
 * Category filter button with dropdown for multi-select filtering
 */
@Composable
fun CategoryFilterButton(
    selectedCount: Int,
    totalCount: Int,
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "filterButtonScale"
    )
    
    Box {
        // Filter button
        Row(
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isFocused) SandTVColors.Accent 
                    else SandTVColors.BackgroundSecondary.copy(alpha = 0.8f)
                )
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { isExpanded = !isExpanded }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filtri",
                tint = if (isFocused) Color.White else SandTVColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (selectedCount == totalCount) "Filtri" else "Filtri ($selectedCount)",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) Color.White else SandTVColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Dropdown menu - OLED Black design
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { 
                isExpanded = false
                searchQuery = ""
            },
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 400.dp)
                .background(Color.Black)  // OLED black
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { 
                    Text(
                        "Cerca categoria...", 
                        color = SandTVColors.TextTertiary
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = SandTVColors.TextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancella",
                            tint = SandTVColors.TextSecondary,
                            modifier = Modifier.clickable { searchQuery = "" }
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SandTVColors.Accent,
                    unfocusedBorderColor = SandTVColors.TextTertiary,
                    cursorColor = SandTVColors.Accent,
                    focusedTextColor = SandTVColors.TextPrimary,
                    unfocusedTextColor = SandTVColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            
            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onSelectAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SandTVColors.Accent
                    )
                ) {
                    Text("Seleziona tutti")
                }
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SandTVColors.TextSecondary
                    )
                ) {
                    Text("Pulisci")
                }
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SandTVColors.TextTertiary.copy(alpha = 0.3f))
            )
            
            // Category list with checkboxes
            val filteredCategories = availableCategories.filter {
                it.contains(searchQuery, ignoreCase = true)
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(vertical = 4.dp)
            ) {
                filteredCategories.forEach { category ->
                    val isSelected = selectedCategories.contains(category)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCategory(category) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleCategory(category) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = SandTVColors.Accent,
                                uncheckedColor = SandTVColors.TextSecondary,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                if (filteredCategories.isEmpty()) {
                    Text(
                        text = "Nessuna categoria trovata",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandTVColors.TextTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
