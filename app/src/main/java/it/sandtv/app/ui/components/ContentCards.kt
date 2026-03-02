package it.sandtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.data.database.entity.Movie
import it.sandtv.app.data.database.entity.Series
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Reusable content cards for Movies, Series, Channels
 * with premium TV focus animations
 */

// ============ Movie Card ============

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 225.dp
) {
    // Clean the title if tmdbTitle not available
    val displayTitle = movie.tmdbTitle ?: cleanDisplayTitle(movie.name)
    
    ContentPosterCard(
        title = displayTitle,
        posterUrl = movie.posterUrl ?: movie.logoUrl,
        rating = movie.tmdbVoteAverage,
        year = movie.year,
        onClick = onClick,
        modifier = modifier,
        width = width,
        height = height
    )
}

/**
 * Quick title cleaning for display when TMDB title is not available
 * Removes common IPTV artifacts like leading dashes, brackets, hashtags
 */
private fun cleanDisplayTitle(name: String): String {
    var result = name
    // Remove leading special characters
    result = result.replace(Regex("""^[\-\#\*\|\[\]:•\s]+"""), "")
    // Remove square bracket content like [2020], [HD], etc.
    result = result.replace(Regex("""\[[^\]]*\]"""), " ")
    // Remove trailing special characters
    result = result.replace(Regex("""[\-\#\*\|\[\]:•\s]+$"""), "")
    // Remove hashtags
    result = result.replace(Regex("""#\w+"""), "")
    // Clean up whitespace
    return result.trim().replace(Regex("""\s+"""), " ")
}

// ============ Series Card ============

@Composable
fun SeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 225.dp
) {
    // Clean the title if tmdbName not available
    val displayTitle = series.tmdbName ?: cleanDisplayTitle(series.name)
    
    ContentPosterCard(
        title = displayTitle,
        posterUrl = series.posterUrl ?: series.logoUrl,
        rating = series.tmdbVoteAverage,
        year = series.year,
        onClick = onClick,
        modifier = modifier,
        width = width,
        height = height
    )
}

// ============ Channel Card ============

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
    height: Dp = 132.dp
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
        modifier = modifier
            .scale(scale)
            .width(width)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
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
                    .padding(16.dp)
            )
            
            // Live indicator
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
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============ Continue Watching Card ============

@Composable
fun ContinueWatchingCard(
    title: String,
    thumbnailUrl: String?,
    progress: Float,
    remainingTime: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "continueScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "continueBorder"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .width(280.dp)
            .height(157.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(SandTVColors.CardBackground)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Thumbnail
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SandTVColors.BackgroundDark.copy(alpha = 0.95f)
                        )
                    )
                )
        )
        
        // Title and remaining time
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            
            remainingTime?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = SandTVColors.TextTertiary
                )
            }
        }
        
        // Progress bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter),
            color = SandTVColors.Accent,
            trackColor = SandTVColors.BackgroundTertiary
        )
        
        // Play icon on focus
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(SandTVColors.Accent.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ============ Settings Card ============

@Composable
fun SettingsCard(
    title: String,
    @Suppress("UNUSED_PARAMETER") // iconResId kept for future resource-based icon loading
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "settingsScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.CardBackground,
        label = "settingsBg"
    )
    
    Column(
        modifier = modifier
            .scale(scale)
            .width(120.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            // Icon would be loaded here
            // For now, using placeholder
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = title,
                tint = SandTVColors.TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============ Base Poster Card (internal) ============

@Composable
private fun ContentPosterCard(
    title: String,
    posterUrl: String?,
    rating: Float?,
    year: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
    height: Dp = 225.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "posterScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "posterBorder"
    )
    
    Column(
        modifier = modifier
            .scale(scale)
            .width(width)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Poster image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .background(SandTVColors.CardBackground)
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Rating badge
            rating?.takeIf { it > 0 }?.let { r ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SandTVColors.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1f", r),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Year badge (on focus)
            if (isFocused && year != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SandTVColors.BackgroundSecondary.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = SandTVColors.TextPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}
