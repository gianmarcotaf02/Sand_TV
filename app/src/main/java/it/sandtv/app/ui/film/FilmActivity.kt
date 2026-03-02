package it.sandtv.app.ui.film

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items as tvGridItems
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items as tvListItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.CategoryWithCount
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.WatchProgressDao
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.ContinueWatchingItem
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.ui.components.ContinueWatchingCarousel
import it.sandtv.app.ui.details.DetailsActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Film Activity with category sidebar and movie grid
 * Now using Jetpack Compose for UI
 */
@AndroidEntryPoint
class FilmActivity : ComponentActivity() {
    
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var watchProgressDao: WatchProgressDao
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Read filter_category - null means "View All"
        val initialCategory = intent.getStringExtra("filter_category")
        
        setContent {
            SandTVTheme {
                FilmScreenContent(initialCategory)
            }
        }
    }
    
    @Composable
    private fun FilmScreenContent(initialCategory: String?) {
        var categories by remember { mutableStateOf<List<CategoryWithCount>>(emptyList()) }
        var selectedCategory by remember { mutableStateOf<String?>(null) }
        var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var showingAllMovies by remember { mutableStateOf(initialCategory == null) }
        
        // Continue watching state
        var continueWatchingItems by remember { mutableStateOf<List<ContinueWatchingItem>>(emptyList()) }
        
        // Load categories and continue watching on first composition
        LaunchedEffect(Unit) {
            // Load continue watching movies
            val progressList = watchProgressDao.getContinueWatchingMovies(1L)
            continueWatchingItems = progressList.mapNotNull { progress ->
                val movie = movieDao.getMovieById(progress.contentId) ?: return@mapNotNull null
                val remaining = ((progress.duration - progress.position) / 60000).toInt()
                ContinueWatchingItem(
                    watchProgressId = progress.id,
                    contentType = ContentType.MOVIE,
                    contentId = progress.contentId,
                    title = movie.tmdbTitle ?: movie.name,
                    posterUrl = movie.posterUrl,
                    backdropUrl = movie.backdropUrl,
                    position = progress.position,
                    duration = progress.duration,
                    progressPercent = progress.progressPercent,
                    remainingMinutes = remaining.coerceAtLeast(1),
                    lastWatchedAt = progress.lastWatchedAt
                )
            }
            
            val cats = movieDao.getCategoriesWithCount()
            categories = cats
            
            if (initialCategory == null) {
                // "Vedi tutti" - show all movies without category filter
                showingAllMovies = true
                movies = movieDao.getAllMovies().first()
                selectedCategory = null
            } else {
                // Specific category selected
                showingAllMovies = false
                selectedCategory = initialCategory
                movies = movieDao.getMoviesByCategoryList(initialCategory)
            }
            isLoading = false
        }
        
        // Load movies when category changes
        LaunchedEffect(selectedCategory) {
            if (selectedCategory != null) {
                isLoading = true
                showingAllMovies = false
                movies = movieDao.getMoviesByCategoryList(selectedCategory!!)
                isLoading = false
            }
        }
        
        FilmScreen(
            categories = categories,
            selectedCategory = selectedCategory,
            movies = movies,
            isLoading = isLoading,
            showingAllMovies = showingAllMovies,
            totalMoviesCount = movies.size,
            continueWatchingItems = continueWatchingItems,
            onCategorySelect = { cat ->
                showingAllMovies = false
                selectedCategory = cat
            },
            onViewAllClick = {
                lifecycleScope.launch {
                    isLoading = true
                    showingAllMovies = true
                    selectedCategory = null
                    movies = movieDao.getAllMovies().first()
                    isLoading = false
                }
            },
            onMovieClick = { openMovieDetails(it) },
            onContinueWatchingClick = { item -> 
                val intent = Intent(this@FilmActivity, DetailsActivity::class.java).apply {
                    putExtra("content_id", item.contentId)
                    putExtra("content_type", "MOVIE")
                    putExtra("title", item.title)
                    putExtra("poster_url", item.posterUrl)
                    putExtra("backdrop_url", item.backdropUrl)
                }
                startActivity(intent)
            },
            onBackClick = { finish() }
        )
    }
    
    private fun openMovieDetails(movie: Movie) {
        val intent = Intent(this, DetailsActivity::class.java).apply {
            putExtra("content_id", movie.id)
            putExtra("content_type", "MOVIE")
            putExtra("title", movie.tmdbTitle ?: movie.name)
            putExtra("poster_url", movie.posterUrl ?: movie.logoUrl)
            putExtra("backdrop_url", movie.backdropUrl)
        }
        startActivity(intent)
    }
}

/**
 * Film Screen Composable - sidebar + grid layout
 */
@Composable
fun FilmScreen(
    categories: List<CategoryWithCount>,
    selectedCategory: String?,
    movies: List<Movie>,
    isLoading: Boolean,
    showingAllMovies: Boolean,
    totalMoviesCount: Int,
    continueWatchingItems: List<ContinueWatchingItem> = emptyList(),
    onCategorySelect: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit = {},
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Left sidebar - categories
        CategorySidebar(
            categories = categories,
            selectedCategory = selectedCategory,
            showingAllMovies = showingAllMovies,
            totalMoviesCount = totalMoviesCount,
            onCategorySelect = onCategorySelect,
            onViewAllClick = onViewAllClick,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
        )
        
        // Right content - movie grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = SandTVColors.Accent
                )
            } else if (movies.isEmpty()) {
                Text(
                    text = "Nessun film in questa categoria",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SandTVColors.TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column {
                    // Back button and title row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = SandTVColors.TextPrimary
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text(
                                text = if (showingAllMovies) "🎬 Tutti i film" else (selectedCategory ?: ""),
                                style = MaterialTheme.typography.headlineMedium,
                                color = SandTVColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = "${movies.size} film",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandTVColors.TextSecondary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Continue Watching Carousel (if items exist)
                    if (continueWatchingItems.isNotEmpty()) {
                        ContinueWatchingCarousel(
                            title = "▶ Continua a guardare",
                            items = continueWatchingItems,
                            onItemClick = onContinueWatchingClick,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    
                    // Movie grid using TV Compose for proper D-pad navigation
                    TvLazyVerticalGrid(
                        columns = TvGridCells.Adaptive(minSize = 150.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        tvGridItems(movies, key = { it.id }) { movie ->
                            MovieGridCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Category sidebar
 */
@Composable
private fun CategorySidebar(
    categories: List<CategoryWithCount>,
    selectedCategory: String?,
    showingAllMovies: Boolean,
    totalMoviesCount: Int,
    onCategorySelect: (String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvLazyColumn(
        modifier = modifier
            .background(Color.Black)  // OLED black for premium look
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // "View All" item at the top
        item(key = "__view_all__") {
            ViewAllItem(
                label = "🎬 Tutti i film",
                count = totalMoviesCount,
                isSelected = showingAllMovies,
                onClick = onViewAllClick
            )
        }
        
        tvListItems(categories, key = { it.name }) { category ->
            CategoryItem(
                category = category,
                isSelected = !showingAllMovies && category.name == selectedCategory,
                onClick = { onCategorySelect(category.name) }
            )
        }
    }
}

/**
 * View All item in sidebar
 */
@Composable
private fun ViewAllItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent.copy(alpha = 0.3f)
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "viewAllBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "viewAllBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = SandTVColors.TextTertiary
        )
    }
}

/**
 * Category item in sidebar
 */
@Composable
private fun CategoryItem(
    category: CategoryWithCount,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected || isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = category.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = SandTVColors.TextTertiary
        )
    }
}

/**
 * Movie card for grid
 */
@Composable
private fun MovieGridCard(
    movie: Movie,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "movieScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "movieBorder"
    )
    
    Column(
        modifier = Modifier
            .scale(scale)
            .width(150.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .background(SandTVColors.CardBackground)
        ) {
            AsyncImage(
                model = movie.posterUrl ?: movie.logoUrl,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Rating badge
            movie.tmdbVoteAverage?.takeIf { it > 0 }?.let { rating ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SandTVColors.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = movie.tmdbTitle ?: movie.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        
        // Year
        movie.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = SandTVColors.TextTertiary
            )
        }
    }
}
