package it.sandtv.app.ui.details

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.sandtv.app.R
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.CustomGroup
import it.sandtv.app.data.database.entity.Episode
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.util.TitleCleaner

/**
 * Episode progress info for display
 */
data class EpisodeProgress(
    val episodeId: Long,
    val progress: Float,           // 0f to 1f
    val remainingMinutes: Int,     // Minutes remaining
    val isCompleted: Boolean       // True if watched > 95%
)

/**
 * Details state holder
 */
data class DetailsState(
    val title: String = "",
    val year: String = "",
    val overview: String = "",
    val genres: String = "",
    val duration: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val contentType: ContentType = ContentType.MOVIE,
    val isFavorite: Boolean = false,
    val trailerKey: String? = null,
    val isLoading: Boolean = true,
    // Ratings
    val tmdbRating: Float? = null,
    val imdbRating: String? = null,
    val rottenTomatoesScore: Int? = null,
    val metacriticScore: Int? = null,
    val audienceScore: Int? = null,  // Popcornmeter
    // Series specific
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    // Watch progress (for resume button)
    val resumeMinutes: Int? = null,  // Remaining minutes if watching in progress
    val resumeProgress: Float? = null, // 0f to 1f progress
    val resumeEpisodeSeason: Int? = null,  // Season number for series resume
    val resumeEpisodeNumber: Int? = null,  // Episode number for series resume
    // Episode progress map (episodeId -> progress)
    val episodeProgress: Map<Long, EpisodeProgress> = emptyMap(),
    // Next episode info (for "Watch next" button)
    val nextEpisodeInfo: String? = null,  // "S1 E2 - Episode Title"
    val nextEpisodeId: Long? = null,  // Episode ID to play when clicking "Watch next"
    // Custom lists
    val customLists: List<CustomGroup> = emptyList(),
    val selectedListIds: Set<Long> = emptySet(),  // Lists containing this content
    // Download state (for movies)
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,  // 0-100%
    // Episode download states (episodeId -> EpisodeDownloadState)
    val episodeDownloadStates: Map<Long, EpisodeDownloadState> = emptyMap(),
    // Auto-scroll target: index of the episode to scroll to in the episodes list
    val scrollToEpisodeIndex: Int? = null
)

// State for individual episode downloads
data class EpisodeDownloadState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0  // 0-100%
)

/**
 * Details Screen - Shows movie/series/channel details
 * Premium design with backdrop, ratings badges, and episodes list
 */
@Composable
fun DetailsScreen(
    state: DetailsState,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onTrailerClick: () -> Unit = {},
    // Custom lists callbacks
    onAddToList: (Long) -> Unit = {},
    onRemoveFromList: (Long) -> Unit = {},
    onCreateList: (String) -> Unit = {},
    onRenameList: (Long, String) -> Unit = { _, _ -> },
    onMarkAsWatchedClick: () -> Unit = {},
    // Download callbacks
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    onDownloadEpisode: (Episode) -> Unit = {},
    onDownloadSeason: (Int) -> Unit = {},  // Season number
    modifier: Modifier = Modifier
) {
    // FocusRequester for automatic focus on Play button
    val playButtonFocusRequester = remember { FocusRequester() }
    
    // Dialog state for mark as watched confirmation
    var showMarkAsWatchedDialog by remember { mutableStateOf(false) }
    
    // Lazy list state for auto-scroll to current/next episode
    val listState = remember { androidx.tv.foundation.lazy.list.TvLazyListState() }
    
    // Auto-scroll to target episode when entering detail view
    LaunchedEffect(state.scrollToEpisodeIndex, state.selectedSeason) {
        val targetIndex = state.scrollToEpisodeIndex
        if (targetIndex != null && targetIndex >= 0) {
            // +2 offset: item 0 = top content block, item 1 = season header
            listState.animateScrollToItem(targetIndex + 2)
        }
    }
    
    // Request focus on Play button when content loads
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if focus request fails
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
        // Full content fade-in: everything appears at once with a smooth dissolve
        // While loading, the screen stays dark (BackgroundDark). When ready → fade in.
        AnimatedVisibility(
            visible = !state.isLoading,
            enter = fadeIn(tween(600))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop image - FULLSCREEN, shifted RIGHT
        if (!state.backdropUrl.isNullOrEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(state.backdropUrl)
                    .size(1280, 720)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,  // Shift image to the right
                modifier = Modifier.fillMaxSize()
            )
            
            // Horizontal gradient: DARK left (50% solid black) → transparent right
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                SandTVColors.BackgroundDark,  // 100% opaque at left edge
                                SandTVColors.BackgroundDark,  // Keep solid
                                SandTVColors.BackgroundDark,  // Keep solid
                                SandTVColors.BackgroundDark,  // Keep solid until ~50%
                                SandTVColors.BackgroundDark.copy(alpha = 0.95f),
                                SandTVColors.BackgroundDark.copy(alpha = 0.8f),
                                SandTVColors.BackgroundDark.copy(alpha = 0.5f),
                                SandTVColors.BackgroundDark.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1600f  // Extended much further for 50% solid dark
                        )
                    )
            )
            
            // Vertical gradient at top for header area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SandTVColors.BackgroundDark.copy(alpha = 0.9f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Vertical gradient at BOTTOM for cast/director readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                SandTVColors.BackgroundDark.copy(alpha = 0.8f),
                                SandTVColors.BackgroundDark
                            )
                        )
                    )
            )
        }
        
        // Content - LEFT ALIGNED with better width for readability
        // Using TvLazyColumn for focus-driven scrolling (like Live TV sidebar)
        // The whole page scrolls when navigating with D-pad
        androidx.tv.foundation.lazy.list.TvLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.75f)  // Use left 75% of screen for content (increased to fit all buttons)
                .padding(start = 32.dp, end = 16.dp)
        ) {
            // Top content block: top bar, poster, info, buttons, overview, cast
            item {
            // Top bar - always visible
            DetailsTopBar(
                onBackClick = onBackClick,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Main content - displayed immediately (no AnimatedVisibility)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Poster
                if (!state.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        )
                            .data(state.posterUrl)
                            .size(150, 225)
                            .crossfade(true)
                            .build(),
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(150.dp)
                            .height(225.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                
                // Info column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 400)
                        )
                ) {
                    // Title
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Year and genre
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state.year.isNotEmpty()) {
                            Text(
                                text = state.year,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SandTVColors.TextSecondary
                            )
                        }
                        
                        if (state.genres.isNotEmpty()) {
                            Text(
                                text = state.genres,
                                style = MaterialTheme.typography.bodyLarge,
                                color = SandTVColors.TextSecondary
                            )
                        }
                    }
                    
                    // Duration on separate line
                    state.duration?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⏱ $it",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SandTVColors.TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Ratings section with header
                    Text(
                        text = "Valutazioni",
                        style = MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    RatingsBadges(
                        tmdbRating = state.tmdbRating,
                        imdbRating = state.imdbRating,
                        rottenTomatoesScore = state.rottenTomatoesScore,
                        metacriticScore = state.metacriticScore,
                        audienceScore = state.audienceScore
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play button - compact text, remaining time shown separately
                        PlayButton(
                            text = when {
                                // If there's a next episode to watch (previous completed)
                                state.nextEpisodeInfo != null -> {
                                    state.nextEpisodeInfo
                                }
                                // If there's watch progress, show resume with S/E info only
                                state.resumeMinutes != null -> {
                                    if (state.resumeEpisodeSeason != null && state.resumeEpisodeNumber != null) {
                                        "Riprendi S${state.resumeEpisodeSeason}E${state.resumeEpisodeNumber}"
                                    } else {
                                        "Riprendi"
                                    }
                                }
                                state.contentType == ContentType.CHANNEL -> stringResource(R.string.watch_live)
                                state.contentType == ContentType.SERIES -> {
                                    // Show first episode of selected season
                                    val firstEpisode = state.episodes.minByOrNull { it.episodeNumber }
                                    if (firstEpisode != null) {
                                        "Riproduci - S${firstEpisode.seasonNumber}E${firstEpisode.episodeNumber}"
                                    } else {
                                        stringResource(R.string.play)
                                    }
                                }
                                else -> stringResource(R.string.play)
                            },
                            // Use white button for resume/next episode states
                            isResume = state.resumeMinutes != null || state.nextEpisodeInfo != null,
                            resumeProgress = state.resumeProgress,
                            onClick = onPlayClick,
                            focusRequester = playButtonFocusRequester
                        )
                        
                        // Trailer button
                        if (state.trailerKey != null) {
                            TrailerButton(
                                onClick = onTrailerClick
                            )
                        }
                        
                        // Favorite button
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            onClick = onFavoriteClick
                        )
                        
                        // Add to list button
                        AddToListButton(
                            customLists = state.customLists,
                            selectedListIds = state.selectedListIds,
                            onAddToList = onAddToList,
                            onRemoveFromList = onRemoveFromList,
                            onCreateList = onCreateList,
                            onRenameList = onRenameList
                        )
                        
                        // Mark as watched button (only for content with progress)
                        if (state.resumeMinutes != null) {
                            MarkAsWatchedButton(
                                onClick = { showMarkAsWatchedDialog = true }
                            )
                        }
                        
                        // Download button (only for movies, not series)
                        if (state.contentType == ContentType.MOVIE) {
                            DownloadButton(
                                isDownloaded = state.isDownloaded,
                                isDownloading = state.isDownloading,
                                downloadProgress = state.downloadProgress,
                                onDownloadClick = onDownloadClick,
                                onDeleteClick = onDeleteDownloadClick
                            )
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
                                        onMarkAsWatchedClick()
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
                    
                    // Remaining time text below buttons (for resume state)
                    if (state.resumeMinutes != null) {
                        val remainingText = when {
                            state.resumeMinutes <= 0 -> "Pochi minuti rimasti"
                            state.resumeMinutes >= 60 -> {
                                val hours = state.resumeMinutes / 60
                                val mins = state.resumeMinutes % 60
                                if (mins > 0) "${hours}h ${mins}min rimasti" else "${hours}h rimaste"
                            }
                            else -> "${state.resumeMinutes} min rimasti"
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = SandTVColors.TextTertiary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 1. Overview (Trama)
                    if (state.overview.isNotEmpty()) {
                        var isExpanded by remember { mutableStateOf(false) }
                        var hasOverflow by remember { mutableStateOf(false) }
                        val maxLines = if (isExpanded) Int.MAX_VALUE else 5
                        
                        Column {
                            Text(
                                text = state.overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandTVColors.TextSecondary,
                                maxLines = maxLines,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp,
                                onTextLayout = { textLayoutResult ->
                                    if (!isExpanded) {
                                        hasOverflow = textLayoutResult.hasVisualOverflow
                                    }
                                }
                            )
                            
                            // Show "Leggi di più" only if text actually overflows
                            if (!isExpanded && hasOverflow) {
                                Text(
                                    text = "Leggi di più...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.Accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { isExpanded = true }
                                )
                            } else if (isExpanded && hasOverflow) {
                                Text(
                                    text = "Leggi meno",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.Accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { isExpanded = false }
                                )
                            }
                        }
                    }
                    
                    // 2. Cast
                    state.cast?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_cast_purple),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                colorFilter = ColorFilter.tint(SandTVColors.Accent)
                            )
                            Text(
                                text = "Cast: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandTVColors.TextTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // 3. Director (Regia)
                    state.director?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_regia_purple),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                colorFilter = ColorFilter.tint(SandTVColors.Accent)
                            )
                            Text(
                                text = "Regia: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandTVColors.TextTertiary
                            )
                        }
                    }
                }
            }
            
            }  // end of top content item
            
            // Episodes section (for series) - inlined into TvLazyColumn
            if (state.contentType == ContentType.SERIES && state.seasons.isNotEmpty()) {
                // Season selector as its own item
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    EpisodesSectionHeader(
                        seasons = state.seasons,
                        selectedSeason = state.selectedSeason,
                        onSeasonSelected = onSeasonSelected,
                        onDownloadSeason = onDownloadSeason
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                
                // Each episode as an individual lazy item for proper D-pad scrolling
                items(state.episodes.size, key = { state.episodes[it].id }) { index ->
                    val episode = state.episodes[index]
                    val progress = state.episodeProgress[episode.id]
                    val downloadState = state.episodeDownloadStates[episode.id]
                    EpisodeCard(
                        episode = episode,
                        progress = progress,
                        downloadState = downloadState,
                        seriesName = state.title,
                        onClick = { onEpisodeClick(episode) },
                        onDownloadClick = { onDownloadEpisode(episode) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(56.dp))
            }
    }
    }  // end inner Box (AnimatedVisibility content)
    }  // end AnimatedVisibility
}

/**
 * Top bar with back button
 */
@Composable
private fun DetailsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "backScale"
    )
    
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(48.dp)
                .clip(CircleShape)
                .background(SandTVColors.BackgroundTertiary.copy(alpha = 0.8f))
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onBackClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (isFocused) SandTVColors.Accent else SandTVColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Ratings row - Modern minimal style with icon + value + label
 * Inspired by premium streaming services
 */
@Composable
private fun RatingsBadges(
    tmdbRating: Float?,
    imdbRating: String?,
    rottenTomatoesScore: Int?,
    metacriticScore: Int?,
    audienceScore: Int? = null,
    imdbVotes: String? = null,
    tmdbVotes: String? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        // IMDB - Always show, N/A if null
        if (imdbRating != null) {
            ModernRatingItem(
                iconResId = R.drawable.imdb_logo,
                value = imdbRating,
                label = imdbVotes ?: "IMDb"
            )
        } else {
            ModernRatingItem(
                iconResId = R.drawable.imdb_na,
                value = "N/A",
                label = "IMDb"
            )
        }
        
        // Tomatometer (Critics Score) - Always show, N/A if null
        if (rottenTomatoesScore != null) {
            val isFresh = rottenTomatoesScore >= 60
            ModernRatingItem(
                iconResId = if (isFresh) R.drawable.rotten_tomatoes_logo else R.drawable.rotten_tomatoes_rotten,
                value = "$rottenTomatoesScore%",
                label = "Tomatometer®"
            )
        } else {
            ModernRatingItem(
                iconResId = R.drawable.rotten_tomatoes_no_score,
                value = "N/A",
                label = "Tomatometer®"
            )
        }
        
        // Popcornmeter (Audience Score) - Always show, N/A if null
        if (audienceScore != null) {
            val isFresh = audienceScore >= 60
            ModernRatingItem(
                iconResId = if (isFresh) R.drawable.popcornmeter_fresh else R.drawable.popcornmeter_rotten,
                value = "$audienceScore%",
                label = "Popcornmeter®"
            )
        } else {
            ModernRatingItem(
                iconResId = R.drawable.popcornmeter_na,
                value = "N/A",
                label = "Popcornmeter®"
            )
        }
        
        // Metacritic
        if (metacriticScore != null) {
            ModernRatingItem(
                iconResId = R.drawable.metacritic_logo,
                value = "$metacriticScore",
                label = "Metascore"
            )
        } else {
            ModernRatingItem(
                iconResId = R.drawable.metacritic_na,
                value = "N/A",
                label = "Metascore"
            )
        }
        
        // TMDB - Always show, N/A if null
        if (tmdbRating != null && tmdbRating > 0) {
            ModernRatingItem(
                iconResId = R.drawable.tmdb_logo,
                value = String.format("%.1f", tmdbRating),
                label = tmdbVotes ?: "TMDb"
            )
        } else {
            ModernRatingItem(
                iconResId = R.drawable.tmdb_na,
                value = "N/A",
                label = "TMDb"
            )
        }
    }
}

/**
 * Modern rating item - Icon + Value inline, Label below
 */
@Composable
private fun ModernRatingItem(
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SandTVColors.TextTertiary
        )
    }
}

/**
 * Primary play button with optional progress bar for resume state
 */
@Composable
private fun PlayButton(
    text: String,
    isResume: Boolean = false,
    resumeProgress: Float? = null,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,  // Increased from 1.05f for better visibility
        label = "playScale"
    )
    
    // Border color - accent when focused for clear indication
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "playBorder"
    )
    
    // Use white background for resume/next episode states, purple for normal play
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isResume && isFocused -> Color.White.copy(alpha = 0.9f)
            isResume -> Color.White
            isFocused -> SandTVColors.AccentLight
            else -> SandTVColors.Accent
        },
        label = "playBg"
    )
    
    val contentColor = if (isResume) Color.Black else SandTVColors.TextPrimary
    
    Box(
        modifier = Modifier
            .scale(scale)
            .widthIn(max = 220.dp)  // Limit width so other buttons can fit
            .height(52.dp)
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Button content
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Progress bar at bottom of button (for resume state)
        // Show progress bar if we have any resume state (isResume), even if resumeProgress is null
        if (isResume) {
            // Use actual progress if available, otherwise show a minimal bar
            val effectiveProgress = (resumeProgress ?: 0.1f).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(6.dp)  // Slightly thicker for visibility
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))  // Darker track
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(effectiveProgress)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = if (effectiveProgress > 0.95f) 12.dp else 0.dp))
                        .background(SandTVColors.Accent)
                )
            }
        }
    }
}

/**
 * Favorite toggle button with fluid heart animation
 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Track toggle for bounce animation
    var bounceScale by remember { mutableFloatStateOf(1f) }
    
    // Bounce animation when favorite changes
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            // Start from bigger scale and bounce down
            bounceScale = 1.4f
            kotlinx.coroutines.delay(50)
            bounceScale = 1f
        }
    }
    
    // Animated bounce with spring
    val animatedBounce by animateFloatAsState(
        targetValue = bounceScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "heartBounce"
    )
    
    // Focus scale
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "focusScale"
    )
    
    // Heart color - red when favorite
    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFE91E63) else SandTVColors.TextSecondary,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 400f
        ),
        label = "heartColor"
    )
    
    // Border color - animated based on state
    val borderColor by animateColorAsState(
        targetValue = when {
            isFavorite -> Color(0xFFE91E63)  // Pink border when favorite
            isFocused -> SandTVColors.Accent  // Accent border when focused
            else -> SandTVColors.TextSecondary.copy(alpha = 0.7f)  // Visible default border
        },
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "favBorder"
    )
    
    // Background when favorite
    val backgroundColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFE91E63).copy(alpha = 0.15f) else SandTVColors.BackgroundSecondary.copy(alpha = 0.5f),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "favBg"
    )
    
    Box(
        modifier = Modifier
            .scale(focusScale)
            .size(52.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape) // Consistent 1dp border
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Favorite",
            tint = heartColor,
            modifier = Modifier
                .size(26.dp)
                .scale(animatedBounce)
        )
    }
}

/**
 * Mark as watched button with eye icon
 */
@Composable
private fun MarkAsWatchedButton(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Focus scale
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "focusScale"
    )
    
    // Border color - animated based on state
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary.copy(alpha = 0.7f),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "eyeBorder"
    )
    
    // Background
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.BackgroundSecondary.copy(alpha = 0.5f),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f
        ),
        label = "eyeBg"
    )
    
    // Icon tint
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 400f
        ),
        label = "eyeColor"
    )
    
    Box(
        modifier = Modifier
            .scale(focusScale)
            .size(52.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_eye),
            contentDescription = "Segna come già visto",
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Download button with progress and delete states
 */
@Composable
private fun DownloadButton(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int = 0,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "focusScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isDownloaded && isFocused -> Color.Red
            isDownloaded -> Color.Green
            isDownloading -> SandTVColors.Accent
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary.copy(alpha = 0.7f)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "downloadBorder"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDownloaded -> Color.Green.copy(alpha = 0.15f)
            isDownloading -> SandTVColors.Accent.copy(alpha = 0.15f)
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.BackgroundSecondary.copy(alpha = 0.5f)
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "downloadBg"
    )
    
    val iconTint by animateColorAsState(
        targetValue = when {
            isDownloaded -> Color.Green
            isDownloading -> SandTVColors.Accent
            isFocused -> SandTVColors.TextPrimary
            else -> SandTVColors.TextSecondary
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "downloadColor"
    )
    
    Box(
        modifier = Modifier
            .scale(focusScale)
            .size(52.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = if (isDownloaded) onDeleteClick else onDownloadClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isDownloading -> {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(36.dp),
                        color = SandTVColors.Accent,
                        strokeWidth = 3.dp,
                        trackColor = SandTVColors.BackgroundTertiary
                    )
                    Text(
                        text = "$downloadProgress%",
                        style = MaterialTheme.typography.labelSmall,
                        color = SandTVColors.TextPrimary
                    )
                }
            }
            isDownloaded -> {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Scaricato - clicca per eliminare",
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Scarica",
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Episodes section HEADER - Season dropdown + download button (Prime Video style)
 * Episodes are rendered separately as individual lazy items in the parent TvLazyColumn
 */
@Composable
private fun EpisodesSectionHeader(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onDownloadSeason: (Int) -> Unit = {}
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Season download button focus state
    val seasonDownloadInteractionSource = remember { MutableInteractionSource() }
    val isSeasonDownloadFocused by seasonDownloadInteractionSource.collectIsFocusedAsState()
    
    // Section title + download button + Season dropdown
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Episodi + download season button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.episodes),
                style = MaterialTheme.typography.headlineSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            // Download season button
            val seasonDownloadScale by animateFloatAsState(
                targetValue = if (isSeasonDownloadFocused) 1.15f else 1f,
                label = "seasonDownloadScale"
            )
            val seasonDownloadBg by animateColorAsState(
                targetValue = if (isSeasonDownloadFocused) SandTVColors.Accent else SandTVColors.BackgroundTertiary,
                label = "seasonDownloadBg"
            )
            
            Box(
                modifier = Modifier
                    .scale(seasonDownloadScale)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(seasonDownloadBg)
                    .focusable(interactionSource = seasonDownloadInteractionSource)
                    .clickable(
                        interactionSource = seasonDownloadInteractionSource,
                        indication = null,
                        onClick = { onDownloadSeason(selectedSeason) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Scarica stagione $selectedSeason",
                    tint = if (isSeasonDownloadFocused) Color.White else SandTVColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Season dropdown
        Box {
            val borderColor by animateColorAsState(
                targetValue = if (isFocused || dropdownExpanded) SandTVColors.Accent else SandTVColors.BackgroundTertiary,
                label = "dropdownBorder"
            )
            
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(if (dropdownExpanded) SandTVColors.BackgroundTertiary else SandTVColors.BackgroundSecondary)
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { dropdownExpanded = !dropdownExpanded }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.season_number, selectedSeason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (dropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SandTVColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(SandTVColors.BackgroundSecondary)
            ) {
                seasons.forEach { season ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.season_number, season),
                                color = if (season == selectedSeason) SandTVColors.Accent else SandTVColors.TextPrimary,
                                fontWeight = if (season == selectedSeason) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSeasonSelected(season)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Season tab button
 */
@Composable
private fun SeasonTab(
    seasonNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "seasonScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.BackgroundTertiary
            else -> SandTVColors.BackgroundSecondary
        },
        label = "seasonBg"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.season_number, seasonNumber),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else SandTVColors.TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Episode card - Prime Video style (horizontal layout)
 * Thumbnail on left, episode info on right
 * With progress bar at bottom when in-progress
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    progress: EpisodeProgress?,
    downloadState: EpisodeDownloadState? = null,
    seriesName: String? = null,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Download state
    val isDownloaded = downloadState?.isDownloaded == true
    val isDownloading = downloadState?.isDownloading == true
    val downloadProgress = downloadState?.downloadProgress ?: 0
    
    // Download button focus state
    val downloadInteractionSource = remember { MutableInteractionSource() }
    val isDownloadFocused by downloadInteractionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "episodeScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "episodeBorder"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else SandTVColors.CardBackground,
        label = "episodeBg"
    )
    
    val hasProgress = progress != null && progress.progress > 0.01f && !progress.isCompleted
    // Episode is "watched" if isCompleted OR remaining time <= 7 minutes (for old data before threshold change)
    val isWatched = progress?.isCompleted == true || 
        (progress != null && progress.remainingMinutes <= 7 && progress.progress > 0.9f)
    
    // Outer Row: non-focusable container with card + download button as separate focus targets
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Episode card (focusable, clickable)
        Row(
            modifier = Modifier
                .weight(1f)
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail (landscape aspect ratio 16:9)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandTVColors.BackgroundSecondary)
            ) {
                AsyncImage(
                    model = episode.posterUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Episode number badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "E${episode.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Play/Resume button overlay on thumbnail (when focused or in-progress)
                if (isFocused || hasProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                
                // Progress bar at bottom of thumbnail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    if (hasProgress && progress != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.progress.coerceIn(0f, 1f))
                                .background(SandTVColors.Accent)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Episode info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Use TitleCleaner for universal title formatting
                val episodeLabel = remember(episode.name, episode.episodeNumber, seriesName) { 
                    TitleCleaner.getFormattedEpisodeTitle(
                        originalName = episode.name,
                        episodeNumber = episode.episodeNumber,
                        seriesName = seriesName
                    )
                }
                
                Text(
                    text = episodeLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Duration and progress info row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isWatched) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SandTVColors.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Guardato",
                                style = MaterialTheme.typography.labelMedium,
                                color = SandTVColors.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else if (hasProgress && progress != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = SandTVColors.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Riprendi",
                                style = MaterialTheme.typography.labelMedium,
                                color = SandTVColors.Accent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = SandTVColors.TextTertiary
                            )
                            Text(
                                text = "${progress.remainingMinutes} min rimasti",
                                style = MaterialTheme.typography.labelMedium,
                                color = SandTVColors.TextTertiary
                            )
                        }
                    } else {
                        episode.tmdbRuntime?.let { runtime ->
                            Text(
                                text = "${runtime} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = SandTVColors.TextTertiary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description if available
                episode.tmdbOverview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        
        // Download button (separately focusable, outside the card)
        val downloadScale by animateFloatAsState(
            targetValue = if (isDownloadFocused) 1.15f else 1f,
            label = "downloadBtnScale"
        )
        val downloadBg by animateColorAsState(
            targetValue = when {
                isDownloaded -> SandTVColors.Accent
                isDownloadFocused -> SandTVColors.Accent
                else -> SandTVColors.BackgroundTertiary
            },
            label = "downloadBtnBg"
        )
        
        Box(
            modifier = Modifier
                .scale(downloadScale)
                .size(44.dp)
                .clip(CircleShape)
                .background(downloadBg)
                .focusable(interactionSource = downloadInteractionSource)
                .clickable(
                    interactionSource = downloadInteractionSource,
                    indication = null,
                    onClick = onDownloadClick
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDownloading -> {
                    Box(contentAlignment = Alignment.Center) {
                        if (downloadProgress > 0) {
                            // Determinate progress (known total size)
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(32.dp),
                                color = SandTVColors.Accent,
                                strokeWidth = 3.dp,
                                trackColor = SandTVColors.BackgroundTertiary
                            )
                            Text(
                                text = "$downloadProgress%",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = SandTVColors.TextPrimary
                            )
                        } else {
                            // Indeterminate progress (HLS/DASH - unknown total size)
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SandTVColors.Accent,
                                strokeWidth = 3.dp,
                                trackColor = SandTVColors.BackgroundTertiary
                            )
                        }
                    }
                }
                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Scaricato",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Scarica episodio",
                        tint = if (isDownloadFocused) Color.White else SandTVColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ============ Preview ============

/**
 * Trailer button with YouTube logo
 */
@Composable
private fun TrailerButton(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "trailerBtnScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else SandTVColors.BackgroundSecondary.copy(alpha = 0.5f),
        label = "trailerBtnBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary.copy(alpha = 0.5f),
        label = "trailerBtnBorder"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(52.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
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
 * Add to List button with dropdown menu
 */
@Composable
private fun AddToListButton(
    customLists: List<CustomGroup>,
    selectedListIds: Set<Long>,
    onAddToList: (Long) -> Unit,
    onRemoveFromList: (Long) -> Unit,
    onCreateList: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") // onRenameList kept for API consistency
    onRenameList: (Long, String) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingListId by remember { mutableStateOf<Long?>(null) }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Check if content is in any list
    val isInList = selectedListIds.isNotEmpty()
    
    // Animated scale
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listBtnScale"
    )
    
    // Animated background - solid colors for visibility
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isInList -> Color.White
            isFocused -> SandTVColors.BackgroundTertiary
            else -> SandTVColors.BackgroundSecondary.copy(alpha = 0.5f)  // Semi-transparent like other buttons
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "listBtnBg"
    )
    
    // Animated border color
    val borderColor by animateColorAsState(
        targetValue = when {
            isInList -> Color.White
            isFocused -> SandTVColors.Accent  // Bright accent when focused
            else -> SandTVColors.TextSecondary.copy(alpha = 0.7f) // Visible default border
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "listBtnBorder"
    )
    
    // Animated icon color - dark when in list (for contrast on white bg)
    val iconColor by animateColorAsState(
        targetValue = when {
            isInList -> SandTVColors.BackgroundDark
            isFocused -> SandTVColors.Accent
            else -> SandTVColors.TextSecondary
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "listBtnIcon"
    )

    Box {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(52.dp)
                .clip(CircleShape) // Changed to CircleShape
                .background(backgroundColor)
                .border(1.dp, borderColor, CircleShape) // Consistent 1dp border
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { showDropdown = !showDropdown }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Crossfade between + and ✓ icons
            androidx.compose.animation.Crossfade(
                targetState = isInList,
                animationSpec = tween(durationMillis = 300),
                label = "listIconCrossfade"
            ) { inList ->
                Icon(
                    imageVector = if (inList) Icons.Default.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = if (inList) "In lista" else "Aggiungi a lista",
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.background(SandTVColors.BackgroundSecondary, RoundedCornerShape(12.dp)).width(280.dp)
        ) {
            Text(
                text = "Aggiungi a lista",
                style = MaterialTheme.typography.titleSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            HorizontalDivider(color = SandTVColors.BackgroundTertiary, thickness = 1.dp)
            
            if (customLists.isEmpty()) {
                Text(
                    text = "Nessuna lista creata",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextTertiary,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                customLists.forEach { list ->
                    val isSelected = selectedListIds.contains(list.id)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = list.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { renamingListId = list.id }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Modifica", tint = SandTVColors.TextTertiary, modifier = Modifier.size(16.dp))
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = SandTVColors.Accent, uncheckedColor = SandTVColors.TextTertiary)
                                )
                            }
                        },
                        onClick = { if (isSelected) onRemoveFromList(list.id) else onAddToList(list.id) }
                    )
                }
            }
            
            HorizontalDivider(color = SandTVColors.BackgroundTertiary, thickness = 1.dp)
            
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Add, null, tint = SandTVColors.Accent, modifier = Modifier.size(20.dp))
                        Text("Crea nuova lista", style = MaterialTheme.typography.bodyMedium, color = SandTVColors.Accent, fontWeight = FontWeight.Medium)
                    }
                },
                onClick = { showDropdown = false; showCreateDialog = true }
            )
        }
    }
    
    if (showCreateDialog) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SandTVColors.BackgroundSecondary,
            title = { Text("Crea nuova lista", color = SandTVColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("Nome lista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary,
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.BackgroundTertiary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { if (listName.isNotBlank()) { onCreateList(listName.trim()); showCreateDialog = false } }) {
                    Text("Crea", color = SandTVColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Annulla", color = SandTVColors.TextSecondary) }
            }
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION
)
@Composable
private fun DetailsScreenPreview() {
    SandTVTheme {
        DetailsScreen(
            state = DetailsState(
                title = "Oppenheimer",
                year = "2023",
                overview = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
                genres = "Drama, History, Biography",
                duration = "180 min",
                director = "Christopher Nolan",
                cast = "Cillian Murphy, Emily Blunt, Matt Damon",
                isFavorite = true,
                contentType = ContentType.MOVIE,
                isLoading = false,
                tmdbRating = 8.4f,
                imdbRating = "8.5",
                rottenTomatoesScore = 93,
                metacriticScore = 88
            ),
            onBackClick = {},
            onPlayClick = {},
            onFavoriteClick = {},
            onSeasonSelected = {},
            onEpisodeClick = {}
        )
    }
}
