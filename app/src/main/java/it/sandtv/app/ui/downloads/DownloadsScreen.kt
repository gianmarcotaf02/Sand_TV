package it.sandtv.app.ui.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.sandtv.app.data.database.entity.ContentType
import it.sandtv.app.data.database.entity.DownloadedContent
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Downloads screen - displays all downloaded content
 */
@Composable
fun DownloadsScreen(
    completedDownloads: List<DownloadedContent>,
    inProgressDownloads: List<DownloadedContent>,
    totalSize: Long,
    onPlayClick: (DownloadedContent) -> Unit,
    onDeleteClick: (DownloadedContent) -> Unit,
    onCancelClick: (DownloadedContent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            DownloadsTopBar(
                totalSize = totalSize,
                onBackClick = onBackClick
            )
            
            // Content
            if (completedDownloads.isEmpty() && inProgressDownloads.isEmpty()) {
                // Empty state
                EmptyDownloadsState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // In-progress downloads
                    if (inProgressDownloads.isNotEmpty()) {
                        item {
                            Text(
                                text = "Download in corso",
                                style = MaterialTheme.typography.titleMedium,
                                color = SandTVColors.TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(inProgressDownloads, key = { it.id }) { download ->
                            DownloadInProgressCard(
                                download = download,
                                onCancelClick = { onCancelClick(download) }
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    // Completed downloads
                    if (completedDownloads.isNotEmpty()) {
                        item {
                            Text(
                                text = "Download completati",
                                style = MaterialTheme.typography.titleMedium,
                                color = SandTVColors.TextPrimary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(completedDownloads, key = { it.id }) { download ->
                            DownloadedContentCard(
                                download = download,
                                onPlayClick = { onPlayClick(download) },
                                onDeleteClick = { onDeleteClick(download) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsTopBar(
    totalSize: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "backButtonBorder"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(if (isFocused) SandTVColors.BackgroundTertiary else Color.Transparent)
                    .focusable(interactionSource = interactionSource)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                            onBackClick()
                            true
                        } else false
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Indietro",
                    tint = if (isFocused) SandTVColors.Accent else SandTVColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Title with icon
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = SandTVColors.Accent,
                modifier = Modifier.size(28.dp)
            )
            
            Text(
                text = "Download",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SandTVColors.TextPrimary
            )
        }
        
        // Total size
        Text(
            text = formatBytes(totalSize),
            style = MaterialTheme.typography.bodyLarge,
            color = SandTVColors.TextSecondary
        )
    }
}

@Composable
private fun EmptyDownloadsState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = SandTVColors.TextTertiary,
                modifier = Modifier.size(80.dp)
            )
            
            Text(
                text = "Nessun download",
                style = MaterialTheme.typography.titleLarge,
                color = SandTVColors.TextSecondary
            )
            
            Text(
                text = "I contenuti scaricati appariranno qui",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextTertiary
            )
        }
    }
}

@Composable
private fun DownloadedContentCard(
    download: DownloadedContent,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Focus requesters for explicit navigation
    val playAreaFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val deleteButtonFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // Parent container - Layout only, handles base background
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SandTVColors.BackgroundSecondary),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Play Interaction Area (Poster + Info)
        val playInteractionSource = remember { MutableInteractionSource() }
        val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
        
        val playBorderColor by animateColorAsState(
            targetValue = if (isPlayFocused) SandTVColors.Accent else Color.Transparent,
            label = "playBorder"
        )
        
        val playBackgroundColor by animateColorAsState(
            targetValue = if (isPlayFocused) SandTVColors.BackgroundTertiary else Color.Transparent,
            label = "playBg"
        )
        
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(2.dp, playBorderColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(playBackgroundColor)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .focusRequester(playAreaFocusRequester)
                .focusProperties {
                    right = deleteButtonFocusRequester
                }
                .focusable(interactionSource = playInteractionSource)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Enter, Key.DirectionCenter -> {
                                onPlayClick()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable(
                    interactionSource = playInteractionSource,
                    indication = null,
                    onClick = onPlayClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(85.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            ) {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = download.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Play overlay when focused
                if (isPlayFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Riproduci",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
            
            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Type badge
                val typeText = when (download.contentType) {
                    ContentType.MOVIE -> "Film"
                    ContentType.EPISODE -> "Episodio"
                    else -> ""
                }
                
                Text(
                    text = typeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = SandTVColors.Accent,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Title
                Text(
                    text = download.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SandTVColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Subtitle / Episode info
                download.subtitle?.let { sub ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.TextSecondary,
                        maxLines = 1
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Size
                Text(
                    text = formatBytes(download.downloadSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = SandTVColors.TextTertiary
                )
            }
        }
        
        // 2. Delete button (Separate focus target)
        DeleteButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .focusRequester(deleteButtonFocusRequester)
                .focusProperties {
                    left = playAreaFocusRequester
                }
        )
    }
}

@Composable
private fun DownloadInProgressCard(
    download: DownloadedContent,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SandTVColors.BackgroundSecondary),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster
        Box(
            modifier = Modifier
                .width(70.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        ) {
            AsyncImage(
                model = download.posterUrl,
                contentDescription = download.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Info + Progress
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = download.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SandTVColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { download.downloadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SandTVColors.Accent,
                trackColor = SandTVColors.BackgroundTertiary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${download.downloadProgress}%",
                style = MaterialTheme.typography.bodySmall,
                color = SandTVColors.TextSecondary
            )
        }
        
        // Cancel button
        CancelButton(
            onClick = onCancelClick,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.Red.copy(alpha = 0.2f) else Color.Transparent,
        label = "deleteBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.Red else Color.Transparent,
        label = "deleteBorder"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                    onClick()
                    true
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Elimina",
            tint = if (isFocused) Color.Red else SandTVColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.Red.copy(alpha = 0.2f) else Color.Transparent,
        label = "cancelBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.Red else Color.Transparent,
        label = "cancelBorder"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                    onClick()
                    true
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Annulla",
            tint = if (isFocused) Color.Red else SandTVColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Format bytes to human readable string
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
