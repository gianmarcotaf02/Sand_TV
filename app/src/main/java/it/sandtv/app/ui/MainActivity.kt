package it.sandtv.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.core.app.ActivityOptionsCompat
import it.sandtv.app.ui.theme.AppAnimations
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import android.net.Uri
import it.sandtv.app.data.preferences.UserPreferences
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import it.sandtv.app.ui.downloads.DownloadsActivity
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.ui.details.DetailsActivity
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.home.HeroItem
import it.sandtv.app.ui.home.HomeContentType
import it.sandtv.app.ui.home.HomeViewModel
import it.sandtv.app.ui.player.PlayerActivity
import it.sandtv.app.ui.search.SearchActivity
import it.sandtv.app.ui.settings.SettingsActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.ui.theme.AccentColor
import it.sandtv.app.ui.tv.TvHomeScreen
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent



/**
 * Main Tab enum for navigation
 */
enum class MainTab { MOVIES, SERIES, LIVE, SERIE_A, FAVORITES, LISTS, HISTORY }

/**
 * Main Activity for Android TV
 * Pure Jetpack Compose implementation (no Leanback Fragments)
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    private var currentTab = MainTab.MOVIES
    private var lastBackPressTime = 0L
    private var backKeyDownTime = 0L
    
    // Companion object for focus control communication
    companion object {
        var isTopBarFocused = false
        var topBarView: View? = null
        private const val BACK_PRESS_INTERVAL = 2000L // 2 seconds
        private const val LONG_PRESS_THRESHOLD = 500L // 500ms for long press
        
        // Callback to focus search button (set by Composable)
        var onLongPressBackToSearch: (() -> Unit)? = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restore state
        if (savedInstanceState != null) {
            try {
                currentTab = MainTab.valueOf(savedInstanceState.getString("current_tab", "MOVIES"))
            } catch (e: Exception) {
                currentTab = MainTab.MOVIES
            }
        }
        
        setContent {
            SandTVTheme {
                MainActivityScreen(
                    initialTab = currentTab,
                    onTabChanged = { currentTab = it },
                    activity = this
                )
            }
        }
    }
    
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var trailerManager: it.sandtv.app.util.TrailerManager
    
    fun playTrailer(trailerKey: String) {
        lifecycleScope.launch {
            trailerManager.openTrailer(this@MainActivity, trailerKey)
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Double-back to exit on all tabs
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
            // Second press within interval - exit
            super.onBackPressed()
        } else {
            // First press - show toast
            lastBackPressTime = currentTime
            android.widget.Toast.makeText(
                this,
                "Premi di nuovo per uscire",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        backKeyDownTime = System.currentTimeMillis()
                    }
                }
                android.view.KeyEvent.ACTION_UP -> {
                    val pressDuration = System.currentTimeMillis() - backKeyDownTime
                    if (pressDuration >= LONG_PRESS_THRESHOLD) {
                        // Long press detected - focus search button
                        onLongPressBackToSearch?.invoke()
                        return true // Consume the event
                    }
                    backKeyDownTime = 0L
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("current_tab", currentTab.name)
    }
    
    // Focus the TopBar - called when UP from first row via TvHomeScreen
    fun focusTopBar() {
        isTopBarFocused = true
        topBarView?.let { view ->
            val focusables = ArrayList<View>()
            view.addFocusables(focusables, View.FOCUS_FORWARD)
            focusables.firstOrNull()?.requestFocus()
            android.util.Log.d("MainActivity", "TopBar focused, ${focusables.size} focusables found")
        }
    }
}


@Composable
private fun MainActivityScreen(
    initialTab: MainTab,
    onTabChanged: (MainTab) -> Unit,
    @Suppress("UNUSED_PARAMETER") // activity kept for future use
    activity: MainActivity
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    
    android.util.Log.d("SandTVDebug", "MainActivityScreen: Starting...")
    
    // ViewModel for home content
    val homeViewModel: HomeViewModel = hiltViewModel()
    android.util.Log.d("SandTVDebug", "MainActivityScreen: ViewModel created")
    
    
    val homeState by homeViewModel.uiState.collectAsState()
    android.util.Log.d("SandTVDebug", "MainActivityScreen: State collected, isLoading=${homeState.isLoading}")
    

    
    // State
    var selectedTab by remember { mutableStateOf(initialTab) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    
    // Categories from ViewModel
    val movieCategories by homeViewModel.movieCategories.collectAsState()
    val seriesCategories by homeViewModel.seriesCategories.collectAsState()
    
    // Get current categories based on tab
    val currentCategories = when (selectedTab) {
        MainTab.MOVIES -> movieCategories
        MainTab.SERIES -> seriesCategories
        MainTab.FAVORITES -> emptyList()  // No sidebar for favorites
        MainTab.LISTS -> emptyList()      // No sidebar for lists
        MainTab.LIVE -> emptyList()
        MainTab.SERIE_A -> emptyList()
        MainTab.HISTORY -> emptyList()    // No sidebar for history
    }
    
    // Handle back press to exit grid mode (See All view) - restore previous scroll position
    androidx.activity.compose.BackHandler(enabled = homeState.isGridMode) {
        homeViewModel.exitGridMode()
    }
    
    // Focus requester for content area (carousels)
    val contentFocusRequester = remember { FocusRequester() }
    
    // Coroutine scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // Focus requester for top bar (Film tab)
    val topBarFocusRequester = remember { FocusRequester() }
    
    // Focus requester for search button (for long press back)
    val searchButtonFocusRequester = remember { FocusRequester() }
    
    // Store reference to root view for focus control
    LaunchedEffect(rootView) {
        MainActivity.topBarView = rootView
    }
    
    // Register callback for long press back to focus search
    LaunchedEffect(Unit) {
        MainActivity.onLongPressBackToSearch = {
            try {
                searchButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
    
    // Refresh content on resume (e.g., after returning from player)
    // Refresh content on resume (e.g., after returning from player)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Force refresh to show updated "Continue Watching" immediately
                homeViewModel.forceRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Helper for animated activity navigation
    fun startActivityWithTransition(intent: Intent) {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            context,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        context.startActivity(intent, options.toBundle())
    }
    
    // Handle tab selection
    fun selectTab(tab: MainTab) {
        // For LIVE tab, just navigate to LiveActivity without changing selectedTab
        // This way when user returns, the previous tab is still selected
        if (tab == MainTab.LIVE) {
            startActivityWithTransition(Intent(context, it.sandtv.app.ui.live.LiveActivity::class.java))
            return
        }
        
        if (tab == MainTab.SERIE_A) {
            startActivityWithTransition(Intent(context, it.sandtv.app.ui.seriea.SerieAActivity::class.java))
            return
        }
        
        if (tab == selectedTab) return
        selectedTab = tab
        selectedCategory = null  // Reset category selection
        onTabChanged(tab)
        
        // Load content for the selected tab
        val contentType = when (tab) {
            MainTab.MOVIES -> HomeContentType.MOVIES
            MainTab.SERIES -> HomeContentType.SERIES
            MainTab.FAVORITES -> HomeContentType.FAVORITES
            MainTab.LISTS -> HomeContentType.LISTS
            MainTab.HISTORY -> HomeContentType.HISTORY
            MainTab.LIVE, MainTab.SERIE_A -> HomeContentType.MOVIES  // Fallback (shouldn't reach here)
        }
        homeViewModel.loadContent(contentType)
    }

    
    // Handle item click
    fun handleItemClick(item: CarouselItem) {
        when (item.contentType) {
            "CHANNEL" -> {
                startActivityWithTransition(Intent(context, PlayerActivity::class.java).apply {
                    putExtra("content_type", ContentType.CHANNEL.name)
                    putExtra("content_id", item.id)
                    putExtra("title", item.title)
                })
            }
            "MOVIE" -> {
                startActivityWithTransition(Intent(context, DetailsActivity::class.java).apply {
                    putExtra("content_type", ContentType.MOVIE.name)
                    putExtra("content_id", item.id)
                    putExtra("title", item.title)
                    putExtra("poster_url", item.posterUrl)
                    putExtra("backdrop_url", item.backdropUrl)
                })
            }
            "SERIES" -> {
                startActivityWithTransition(Intent(context, DetailsActivity::class.java).apply {
                    putExtra("content_type", ContentType.SERIES.name)
                    putExtra("content_id", item.id)
                    putExtra("title", item.title)
                    putExtra("poster_url", item.posterUrl)
                    putExtra("backdrop_url", item.backdropUrl)
                })
            }
            // Category cards -> CategoryActivity
            "CATEGORY_MOVIE", "CATEGORY_SERIES", "CATEGORY_LIVE" -> {
                startActivityWithTransition(Intent(context, it.sandtv.app.ui.category.CategoryActivity::class.java).apply {
                    putExtra("categoryName", item.title)
                    putExtra("contentType", item.contentType)
                })
            }
        }
    }
    
    fun handleSeeAllClick(rowTitle: String) {
        if (rowTitle.isEmpty()) {
            homeViewModel.exitGridMode()
            return
        }
        
        // Navigate to CategoryActivity (same as sidebar navigation)
        val contentType = when (selectedTab) {
            MainTab.MOVIES -> "CATEGORY_MOVIE"
            MainTab.SERIES -> "CATEGORY_SERIES"
            else -> {
                // For mixed tabs, detect based on row title
                if (rowTitle.contains("Film", ignoreCase = true) || 
                    rowTitle == context.getString(it.sandtv.app.R.string.popular_movies)) {
                    "CATEGORY_MOVIE"
                } else {
                    "CATEGORY_SERIES"
                }
            }
        }
        
        startActivityWithTransition(Intent(context, it.sandtv.app.ui.category.CategoryActivity::class.java).apply {
            putExtra("categoryName", rowTitle)
            putExtra("contentType", contentType)
        })
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Main content with animated tab transition
        androidx.compose.animation.AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // Slide transition based on tab order
                val direction = if (targetState.ordinal > initialState.ordinal) {
                    (androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn()).togetherWith(
                        androidx.compose.animation.slideOutHorizontally { -it } + androidx.compose.animation.fadeOut()
                    )
                } else {
                    (androidx.compose.animation.slideInHorizontally { -it } + androidx.compose.animation.fadeIn()).togetherWith(
                        androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut()
                    )
                }
                direction.using(
                    androidx.compose.animation.SizeTransform(clip = false)
                )
            },
            label = "tabTransition"
        ) { targetTab ->
            // Show shimmer while loading, content when ready
            val isContentLoading = homeState.isLoading && homeState.carouselRows.isEmpty()
            
            if (isContentLoading) {
                // Shimmer loading skeleton (Prime Video style)
                TabLoadingSkeleton()
            } else {
                TvHomeScreen(
                    state = homeState,
                    onItemClick = { handleItemClick(it) },
                    onSeeAllClick = { handleSeeAllClick(it) },
                    onPlayClick = { handleItemClick(it) },
                    onTopBarFocusRequest = {
                        // Focus on Film tab when UP is pressed from first carousel row
                        try {
                            topBarFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            // Ignore focus errors
                        }
                    },
                    topBarFocusRequester = topBarFocusRequester,  // Pass to Hero for focusProperties
                    onCreateListClick = {
                        // Show dialog to create new list
                        showCreateListDialog = true
                    },
                    onHeroClick = { heroItem ->
                        // Navigate to details page with preloaded data for instant rendering
                        val intent = Intent(context, DetailsActivity::class.java).apply {
                            putExtra("content_id", heroItem.id)
                            putExtra("content_type", heroItem.contentType)
                            putExtra("title", heroItem.title)
                            putExtra("poster_url", heroItem.posterUrl)
                            putExtra("backdrop_url", heroItem.backdropUrl)
                        }
                        startActivityWithTransition(intent)
                    },
                    onHeroPlayClick = { heroItem ->
                        // Navigate directly to player - PlayerActivity now handles series stream URL lookup
                        val intent = Intent(context, it.sandtv.app.ui.player.PlayerActivity::class.java).apply {
                            putExtra("content_id", heroItem.id)
                            putExtra("content_type", heroItem.contentType)
                            putExtra("title", heroItem.title)
                        }
                        startActivityWithTransition(intent)
                    },
                    onTrailerClick = { heroItem ->
                        heroItem.trailerKey?.let { activity.playTrailer(it) }
                    },
                    onNextHero = { homeViewModel.nextHero() },
                    onPrevHero = { homeViewModel.prevHero() },
                    onToggleHeroFavorite = { homeViewModel.toggleHeroFavorite(it) },
                    onAddHeroToPlaylist = { 
                        homeViewModel.addHeroToWatchLater(it)
                        android.widget.Toast.makeText(context, "Aggiunto a Da guardare", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    // Category filter callbacks
                    onToggleCategoryFilter = { category -> homeViewModel.toggleCategoryFilter(category) },
                    onSelectAllCategories = { homeViewModel.selectAllCategories() },
                    onClearCategoryFilters = { homeViewModel.clearCategoryFilters() },
                    onMarkAsWatchedClick = { heroItem ->
                        homeViewModel.markAsWatched(heroItem)
                        android.widget.Toast.makeText(context, "Rimosso da Continua a guardare", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(contentFocusRequester)
                )
            }
        }
        
        // Top bar overlay
        MainTopBar(
            selectedTab = selectedTab,
            onTabSelected = { selectTab(it) },
            onMenuClick = { isDrawerOpen = true },
            onProfileClick = { 
                startActivityWithTransition(Intent(context, it.sandtv.app.ui.profile.ProfileSelectionActivity::class.java))
            },
            onSearchClick = {
                startActivityWithTransition(Intent(context, SearchActivity::class.java))
            },
            onSettingsClick = {
                startActivityWithTransition(Intent(context, SettingsActivity::class.java))
            },
            onRandomClick = {
                // Get random content and navigate to details
                coroutineScope.launch {
                    val randomItem = homeViewModel.getRandomContent()
                    if (randomItem != null) {
                        val intent = Intent(context, DetailsActivity::class.java).apply {
                            putExtra("content_id", randomItem.first)
                            putExtra("content_type", randomItem.second)
                        }
                        startActivityWithTransition(intent)
                    }
                }
            },
            onDownloadsClick = {
                startActivityWithTransition(Intent(context, DownloadsActivity::class.java))
            },
            onContentFocusRequest = {
                // Focus on first carousel when DOWN is pressed from tabs
                try {
                    contentFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            },
            filmTabFocusRequester = topBarFocusRequester,
            searchButtonFocusRequester = searchButtonFocusRequester,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Sidebar drawer overlay
        if (isDrawerOpen) {
            val favoriteCategories by (
                if (selectedTab == MainTab.MOVIES) homeViewModel.favoriteMovieCategories
                else homeViewModel.favoriteSeriesCategories
            ).collectAsState()
            
            Row(modifier = Modifier.fillMaxSize()) {
            CategorySidebar(
                    categories = currentCategories,
                    selectedCategory = selectedCategory,
                    favoriteCategories = favoriteCategories,
                    onCategorySelected = { category ->
                        selectedCategory = category
                        isDrawerOpen = false
                        // Load content for selected category
                        val isMovies = selectedTab == MainTab.MOVIES
                        homeViewModel.loadCategoryContent(category, isMovies)
                    },
                    onToggleFavorite = { category ->
                        val isMovies = selectedTab == MainTab.MOVIES
                        homeViewModel.toggleFavoriteCategory(category, isMovies)
                    },
                    onViewAllCategories = {
                        isDrawerOpen = false
                        val contentType = if (selectedTab == MainTab.MOVIES) "movies" else "series"
                        startActivityWithTransition(Intent(context, it.sandtv.app.ui.category.AllCategoriesActivity::class.java).apply {
                            putExtra("contentType", contentType)
                        })
                    },
                    onClose = { isDrawerOpen = false }
                )
                
                // Click outside to close
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SandTVColors.BackgroundDark.copy(alpha = 0.5f))
                        .noRippleClickable { isDrawerOpen = false }
                )
            }
        }
        
        // Create list dialog
        if (showCreateListDialog) {
            CreateListDialog(
                onDismiss = { showCreateListDialog = false },
                onCreate = { listName ->
                    homeViewModel.createList(listName)
                    showCreateListDialog = false
                    // Reload lists tab to show the new list
                    homeViewModel.loadContent(HomeContentType.LISTS)
                }
            )
        }
    }
}

/**
 * Extension to handle clicks without ripple effect
 */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

/**
 * Main Top Bar with tabs and action buttons
 */
@Composable
private fun MainTopBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRandomClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onContentFocusRequest: () -> Unit = {},
    filmTabFocusRequester: FocusRequester = remember { FocusRequester() },
    searchButtonFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Menu + Tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Menu button with TV-friendly focus indicator
            TopBarIconButton(
                icon = Icons.Default.Menu,
                contentDescription = "Menu",
                onClick = onMenuClick,
                onDownPress = onContentFocusRequest
            )
            
            // Tabs container with sliding indicator
            val tabWidths = remember { mutableStateMapOf<Int, Float>() }
            val selectedTabIndex = MainTab.entries.indexOf(selectedTab)
            
            // Calculate indicator offset with spring animation
            val indicatorOffset by animateDpAsState(
                targetValue = (0 until selectedTabIndex).sumOf { 
                    (tabWidths[it] ?: 80f).toInt() + 16 // tab width + spacing
                }.dp,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 300f
                ),
                label = "indicatorOffset"
            )
            
            val indicatorWidth by animateDpAsState(
                targetValue = (tabWidths[selectedTabIndex] ?: 80f).dp,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 300f
                ),
                label = "indicatorWidth"
            )
            
            Box {
                // Sliding indicator behind tabs (hide for FAVORITES/LISTS/HISTORY - they have their own background)
                if (selectedTab != MainTab.FAVORITES && selectedTab != MainTab.LISTS && selectedTab != MainTab.HISTORY) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(indicatorWidth)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SandTVColors.Accent)
                    )
                }
                
                // Tab buttons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        // Add separator before SERIE_A (after LIVE)
                        if (tab == MainTab.SERIE_A) {
                            Text(
                                text = "|",
                                color = SandTVColors.TextTertiary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        
                        if (tab == MainTab.HISTORY) {
                            // History icon button
                            HistoryTabButton(
                                isSelected = tab == selectedTab,
                                onClick = { onTabSelected(tab) },
                                onDownPress = onContentFocusRequest,
                                onWidthMeasured = { width -> tabWidths[index] = width }
                            )
                        } else if (tab == MainTab.FAVORITES) {
                            // Heart icon button for favorites
                            FavoritesTabButton(
                                isSelected = tab == selectedTab,
                                onClick = { onTabSelected(tab) },
                                onDownPress = onContentFocusRequest,
                                onWidthMeasured = { width -> tabWidths[index] = width }
                            )
                        } else if (tab == MainTab.LISTS) {
                            // List icon button for custom lists
                            ListsTabButton(
                                isSelected = tab == selectedTab,
                                onClick = { onTabSelected(tab) },
                                onDownPress = onContentFocusRequest,
                                onWidthMeasured = { width -> tabWidths[index] = width }
                            )
                        } else if (tab == MainTab.SERIE_A) {
                             // Serie A Logo Button
                             ImageTabButton(
                                 painter = androidx.compose.ui.res.painterResource(it.sandtv.app.R.drawable.serie_a),
                                 contentDescription = "Serie A",
                                 isSelected = tab == selectedTab,
                                 onClick = { onTabSelected(tab) },
                                 onDownPress = onContentFocusRequest,
                                 onWidthMeasured = { width -> tabWidths[index] = width }
                             )
                        } else {
                            TabButton(
                                text = when (tab) {
                                    MainTab.MOVIES -> "Film"
                                    MainTab.SERIES -> "Serie TV"
                                    MainTab.LIVE -> "Live"
                                    MainTab.LIVE -> "Live"
                                    else -> ""
                                },
                                isSelected = tab == selectedTab,
                                onClick = { onTabSelected(tab) },
                                focusRequester = if (tab == MainTab.MOVIES) filmTabFocusRequester else null,
                                onDownPress = onContentFocusRequest,
                                onWidthMeasured = { width -> tabWidths[index] = width }
                            )
                        }
                    }
                }
            }
            
            // Request focus on Film tab at startup
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300) // Wait for composition
                try {
                    filmTabFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            }
        }
        
        // Right side: Clock + Actions with TV-friendly focus indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Digital Clock
            DigitalClock()
            
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Random content button (dice/shuffle)
            TopBarIconButton(
                painter = androidx.compose.ui.res.painterResource(it.sandtv.app.R.drawable.dadi),
                contentDescription = "Contenuto casuale",
                onClick = onRandomClick,
                onDownPress = onContentFocusRequest
            )
            TopBarIconButton(
                icon = Icons.Default.Download,
                contentDescription = "Download",
                onClick = onDownloadsClick,
                onDownPress = onContentFocusRequest
            )
            TopBarIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Cerca",
                onClick = onSearchClick,
                onDownPress = onContentFocusRequest,
                focusRequester = searchButtonFocusRequester
            )
            TopBarIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Impostazioni",
                onClick = onSettingsClick,
                onDownPress = onContentFocusRequest
            )
            TopBarIconButton(
                icon = Icons.Default.Person,
                contentDescription = "Profilo",
                onClick = onProfileClick,
                onDownPress = onContentFocusRequest
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onDownPress: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    
    // No background here - indicator is behind
    val focusBackground by animateColorAsState(
        targetValue = if (isFocused && !isSelected) SandTVColors.BackgroundTertiary else Color.Transparent,
        label = "tabFocusBg"
    )
    
    // Border color when focused (purple border for visibility)
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "tabBorderColor"
    )
    
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(focusBackground)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coordinates ->
                with(density) {
                    onWidthMeasured(coordinates.size.width.toDp().value)
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Top bar icon button with TV-friendly focus indicator
 * Shows scale animation and accent border when focused
 */
@Composable
private fun TopBarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "iconButtonScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else Color.Transparent,
        label = "iconButtonBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "iconButtonBorder"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextPrimary,
        label = "iconButtonColor"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Top bar icon button with TV-friendly focus indicator (Painter overload)
 */
@Composable
private fun TopBarIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "iconButtonScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else Color.Transparent,
        label = "iconButtonBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "iconButtonBorder"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.Unspecified, // Use original PNG colors
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Digital clock that shows the current time
 * Updates every second
 */
@Composable
fun DigitalClock(
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    var currentTime by remember { mutableStateOf(getCurrentTimeString()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTimeString()
            kotlinx.coroutines.delay(1000L) // Update every second
        }
    }
    
    Text(
        text = currentTime,
        color = textColor,
        style = textStyle,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

private fun getCurrentTimeString(): String {
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}

@Composable
private fun ImageTabButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    
    // Calculate width for indicator
    LaunchedEffect(density) {
        onWidthMeasured(50f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "imgTabScale"
    )

    // Border color when focused
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "imgTabBorder"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .height(36.dp) 
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(24.dp)
        )
    }
}

/**
 * Favorites tab button - Heart icon only (Prime Video style)
 */
@Composable
private fun FavoritesTabButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "heartTabScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "heartTabBg"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary
        },
        label = "heartTabIcon"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coordinates ->
                with(density) {
                    onWidthMeasured(coordinates.size.width.toDp().value)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Preferiti",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Lists tab button - List icon only
 */
@Composable
private fun ListsTabButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listsTabScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "listsTabBg"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary
        },
        label = "listsTabIcon"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coordinates ->
                with(density) {
                    onWidthMeasured(coordinates.size.width.toDp().value)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
            contentDescription = "Liste",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * History tab button - History icon only
 */
@Composable
private fun HistoryTabButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    onDownPress: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "historyTabScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "historyTabBg"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary
        },
        label = "historyTabIcon"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown) {
                    onDownPress()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onGloballyPositioned { coordinates ->
                with(density) {
                    onWidthMeasured(coordinates.size.width.toDp().value)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(it.sandtv.app.R.drawable.cronologia),
            contentDescription = "Cronologia",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Category Sidebar for filtering content
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CategorySidebar(
    categories: List<String>,
    selectedCategory: String?,
    favoriteCategories: Set<String> = emptySet(),
    onCategorySelected: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onViewAllCategories: () -> Unit = {},
    onClose: () -> Unit
) {
    // Focus requester for view all button and first category
    val viewAllFocusRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }
    
    // Track list state to prevent UP from escaping sidebar
    val listState = androidx.tv.foundation.lazy.list.rememberTvLazyListState()
    
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Color.Black)  // OLED black for premium look
    ) {
        // Header with close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Categorie",
                style = MaterialTheme.typography.titleLarge,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            // TV-friendly close button
            TopBarIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Chiudi",
                onClick = onClose
            )
        }

        // Category list using TvLazyColumn for proper D-pad navigation
        androidx.tv.foundation.lazy.list.TvLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back -> {
                                onClose()
                                true  // Consume BACK to close sidebar
                            }
                            Key.DirectionLeft -> {
                                onClose()
                                true  // LEFT closes sidebar
                            }
                            else -> false
                        }
                    } else false
                },
            contentPadding = PaddingValues(8.dp)
        ) {
            // View All Categories button at top
            item {
                ViewAllCategoriesButton(
                    onClick = onViewAllCategories,
                    focusRequester = viewAllFocusRequester
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            itemsIndexed(categories) { index, category ->
                // Strip count suffix for favorite comparison (e.g., "4k UHD (377)" -> "4k UHD")
                val cleanCategory = category.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
                CategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    isFavorite = favoriteCategories.contains(cleanCategory),
                    onClick = { onCategorySelected(category) },
                    onLongPress = { onToggleFavorite(category) },
                    focusRequester = if (index == 0) firstCategoryFocusRequester else null
                )
            }
        }
    }
    
    // Request focus on first category when sidebar opens
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Wait for composition
        try {
            firstCategoryFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }
}

@Composable
private fun CategoryItem(
    category: String,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Long press detection state
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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
    
    // Parse category name and count from format "Category + (123)" or "Tutti i Film (123)"
    val (name, count) = remember(category) {
        val countMatch = Regex("\\((\\d+)\\)$").find(category)
        if (countMatch != null) {
            val countStr = countMatch.groupValues[1]
            val nameStr = category.removeSuffix(" (${countStr})").removeSuffix("(${countStr})")
                .removeSuffix(" + ").trim()
            Pair(nameStr, countStr)
        } else {
            Pair(category.trim(), null)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter
                
                if (isEnter) {
                     if (event.type == KeyEventType.KeyDown) {
                         if (!isLongPressing) {
                             isLongPressing = true
                             longPressTriggered = false
                             longPressJob?.cancel()
                             longPressJob = coroutineScope.launch {
                                 kotlinx.coroutines.delay(800L) // 0.8 second hold
                                 longPressTriggered = true
                                 onLongPress()
                             }
                         }
                         true // Consume key down
                     } else if (event.type == KeyEventType.KeyUp) {
                         isLongPressing = false
                         longPressJob?.cancel()
                         
                         if (!longPressTriggered) {
                             // Short press - navigate
                             onClick()
                         }
                         true // Consume key up
                     } else {
                         false
                     }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Handled by onKeyEvent for TV D-pad, this handles touch mainly */ 
                    onClick() 
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Star icon for favorites
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Preferito",
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
        }
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        count?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = SandTVColors.TextTertiary
            )
        }
    }
}

/**
 * Button for "View All Categories" in sidebar
 */
@Composable
private fun ViewAllCategoriesButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else Color.Transparent,
        label = "viewAllBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "viewAllBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 4-squares icon
        it.sandtv.app.ui.category.FourSquaresIcon(
            modifier = Modifier.size(20.dp),
            color = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary
        )
        
        Text(
            text = "Vedi tutte le categorie",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog for creating a new list
 */
@Composable
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    // Semi-transparent backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .noRippleClickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Dialog card
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundSecondary)
                .noRippleClickable { } // Prevent closing when clicking inside
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nuova lista",
                style = MaterialTheme.typography.titleLarge,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Text input
            androidx.compose.material3.OutlinedTextField(
                value = listName,
                onValueChange = { listName = it },
                label = { Text("Nome della lista") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SandTVColors.TextPrimary,
                    unfocusedTextColor = SandTVColors.TextSecondary,
                    focusedBorderColor = SandTVColors.Accent,
                    unfocusedBorderColor = SandTVColors.TextTertiary,
                    focusedLabelColor = SandTVColors.Accent,
                    unfocusedLabelColor = SandTVColors.TextTertiary
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cancel button
                val cancelInteraction = remember { MutableInteractionSource() }
                val cancelFocused by cancelInteraction.collectIsFocusedAsState()
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (cancelFocused) SandTVColors.BackgroundTertiary else Color.Transparent)
                        .border(1.dp, SandTVColors.TextTertiary, RoundedCornerShape(8.dp))
                        .focusable(interactionSource = cancelInteraction)
                        .clickable(
                            interactionSource = cancelInteraction,
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Annulla",
                        color = SandTVColors.TextSecondary
                    )
                }
                
                // Create button
                val createInteraction = remember { MutableInteractionSource() }
                val createFocused by createInteraction.collectIsFocusedAsState()
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (createFocused) SandTVColors.AccentLight else SandTVColors.Accent)
                        .focusable(interactionSource = createInteraction)
                        .clickable(
                            interactionSource = createInteraction,
                            indication = null,
                            onClick = { 
                                if (listName.isNotBlank()) {
                                    onCreate(listName.trim())
                                }
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Crea",
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
    
    // Request focus on text input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Loading skeleton with shimmer effect (Prime Video style)
 * Shows animated placeholder cards while content loads
 */
@Composable
private fun TabLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    // Shimmer animation - sweeping highlight
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            SandTVColors.BackgroundSecondary,
            SandTVColors.BackgroundTertiary.copy(alpha = 0.8f),
            SandTVColors.BackgroundSecondary
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerOffset * 1000f, 0f),
        end = androidx.compose.ui.geometry.Offset((shimmerOffset + 1f) * 1000f, 0f)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
            .padding(top = 80.dp) // Space for top bar
    ) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 48.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Carousel row placeholders
        repeat(3) { rowIndex ->
            Column(
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                // Row title placeholder
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Card placeholders row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .height(225.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
