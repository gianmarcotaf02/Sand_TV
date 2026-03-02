package it.sandtv.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import it.sandtv.app.R
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.theme.SandTVColors

/**
 * TV-optimized content card using androidx.tv.material3
 * Replaces MovieCardPresenter, SeriesCardPresenter, ChannelCardPresenter, ContinueWatchingPresenter
 * 
 * Features:
 * - Built-in focus animation and border
 * - Proper D-pad navigation
 * - Scale animation on focus
 * - Progress overlay for Continue Watching
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvContentCard(
    item: CarouselItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    customWidth: androidx.compose.ui.unit.Dp? = null,
    customHeight: androidx.compose.ui.unit.Dp? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "cardScale"
    )
    
    // Use smaller dimensions for channels (square-ish), larger for movies/series (poster)
    val isChannel = item.contentType == "CHANNEL"
    val cardWidth = customWidth ?: if (isChannel) 90.dp else 130.dp
    val cardHeight = customHeight ?: if (isChannel) 70.dp else 195.dp
    
    Column(
        modifier = modifier
            .width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .onFocusChanged { isFocused = it.isFocused },
            scale = CardDefaults.scale(
                scale = 1f,
                focusedScale = 1.08f,
                pressedScale = 0.98f
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 3.dp,
                        color = SandTVColors.Accent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            ),
            shape = CardDefaults.shape(
                shape = RoundedCornerShape(8.dp),
                focusedShape = RoundedCornerShape(8.dp),
                pressedShape = RoundedCornerShape(8.dp)
            ),
            colors = CardDefaults.colors(
                containerColor = SandTVColors.CardBackground
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster/Logo image - use Fit for channels, Crop for movies/series
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(item.posterUrl)
                        .size(390, 585)  // 3x of 130x195 for sharp rendering
                        .crossfade(200)
                        .build(),
                    contentDescription = item.title,
                    contentScale = if (isChannel) ContentScale.Fit else ContentScale.Crop,
                    placeholder = coil.compose.rememberAsyncImagePainter(R.drawable.placeholder_poster),
                    error = coil.compose.rememberAsyncImagePainter(R.drawable.placeholder_poster),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isChannel) Modifier.padding(8.dp) else Modifier)
                )
                
                // Rating badge (if available and NOT continue watching)
                if (item.progressPercent == null) {
                    item.rating?.takeIf { it > 0 }?.let { rating ->
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
                
                // Episode label for series (top left)
                item.episodeLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Progress bar overlay (for Continue Watching)
                item.progressPercent?.let { progress ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(8.dp)
                    ) {
                        // Remaining time text
                        item.remainingMinutes?.let { mins ->
                            val timeText = if (mins >= 60) {
                                "${mins / 60}h ${mins % 60}m rimasti"
                            } else {
                                "$mins min rimasti"
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SandTVColors.Accent)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title below card
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Wide card variant for channels and horizontal content
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvWideCard(
    item: CarouselItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(157.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = CardDefaults.scale(
            scale = 1f,
            focusedScale = 1.05f,
            pressedScale = 0.98f
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 3.dp,
                    color = SandTVColors.Accent
                ),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        shape = CardDefaults.shape(
            shape = RoundedCornerShape(8.dp)
        ),
        colors = CardDefaults.colors(
            containerColor = SandTVColors.CardBackground
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop/thumbnail
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                SandTVColors.BackgroundDark.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            
            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
            
            // Progress bar (for continue watching)
            item.progressPercent?.let { progress ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(SandTVColors.BackgroundTertiary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(SandTVColors.Accent)
                    )
                }
            }
        }
    }
}
