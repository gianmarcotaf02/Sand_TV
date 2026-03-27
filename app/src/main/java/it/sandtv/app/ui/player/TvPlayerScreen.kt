package it.sandtv.app.ui.player

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import it.sandtv.app.ui.theme.SandTVColors
import kotlinx.coroutines.delay

/**
 * Modern TV Player Screen with clean, fluid design
 * Netflix/Prime Video inspired UI
 */
@Composable
fun TvPlayerScreen(
    player: ExoPlayer,
    title: String,
    subtitle: String? = null,
    isLoading: Boolean,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekConfirm: () -> Unit,
    onSeekCancel: () -> Unit,
    onSubtitles: () -> Unit,
    onBack: () -> Unit,
    onRestart: () -> Unit = {},
    nextEpisode: NextEpisodeInfo? = null,
    onPlayNext: () -> Unit = {},
    onCancelNext: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onSpeedChange: () -> Unit = {},
    audioTracks: List<AudioTrackInfo> = emptyList(),
    currentAudioTrack: Int = 0,
    onAudioTrackChange: (AudioTrackInfo) -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    autoPlayEnabled: Boolean = true,
    hasNextEpisode: Boolean = false,
    hasPreviousEpisode: Boolean = false,
    onPlayPrevious: () -> Unit = {},
    cumulativeSeekSeconds: Int = 0,
    seekIndicatorVisible: Boolean = false,
    showStillWatching: Boolean = false,
    onStillWatchingContinue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val centerFocusRequester = remember { FocusRequester() }
    val bottomFirstFocusRequester = remember { FocusRequester() }
    
    // Show controls when seeking
    LaunchedEffect(seekIndicatorVisible) {
        if (seekIndicatorVisible) {
            onControlsVisibilityChanged(true)
        }
    }
    
    // Auto-hide controls after 3 seconds (only at start or when paused)
    // Don't hide while user is actively seeking
    LaunchedEffect(controlsVisible, isPlaying, seekIndicatorVisible, cumulativeSeekSeconds) {
        if (controlsVisible) {
            delay(3000)
            if (isPlaying && !seekIndicatorVisible && cumulativeSeekSeconds == 0) {
                onControlsVisibilityChanged(false)
            }
        }
    }
    
    // Show controls when paused
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            onControlsVisibilityChanged(true)
        }
    }
    
    // Focus on play/pause button when controls become visible
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && !isLoading) {
            delay(100) // Small delay to ensure composition is complete
            try {
                centerFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester might not be attached yet
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Removed global OK/ENTER handler - each button handles its own click
            // This prevents play/pause when focus is on other buttons like Subtitles
            .focusable()
    ) {
        // Video Player
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        
        // Loading indicator (minimal design)
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ModernLoadingIndicator()
        }
        
        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            ModernPlayerControls(
                title = title,
                subtitle = subtitle,
                currentPosition = currentPosition,
                duration = duration,
                isPlaying = isPlaying,
                isLoading = isLoading,
                onPlayPause = onPlayPause,
                onBack = onBack,
                onRestart = onRestart,
                onSubtitles = onSubtitles,
                cumulativeSeekSeconds = cumulativeSeekSeconds, // Pass cumulative seconds
                onSeek = onSeek,
                onSeekConfirm = onSeekConfirm,
                onSeekCancel = onSeekCancel,
                playbackSpeed = playbackSpeed,
                onSpeedChange = onSpeedChange,
                audioTracks = audioTracks,
                currentAudioTrack = currentAudioTrack,
                onAudioTrackChange = onAudioTrackChange,
                hasNextEpisode = hasNextEpisode,
                hasPreviousEpisode = hasPreviousEpisode,
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                centerFocusRequester = centerFocusRequester,
                bottomFirstFocusRequester = bottomFirstFocusRequester
            )
        }
        
        // Seek indicator (always visible when seeking)
        AnimatedVisibility(
            visible = seekIndicatorVisible && cumulativeSeekSeconds != 0,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            ModernSeekIndicator(seconds = cumulativeSeekSeconds)
        }
        
        // Next episode overlay
        nextEpisode?.let { next ->
            ModernNextEpisodeOverlay(
                title = next.title,
                subtitle = next.subtitle,
                countdown = next.countdown,
                autoPlay = next.autoPlay,
                onPlayNext = onPlayNext,
                onCancel = onCancelNext,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
            )
        }
        
        // Fixed clock overlay (always visible, semi-transparent)
        PlayerClockOverlay(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        
        // Still Watching Overlay
        if (showStillWatching) {
            StillWatchingOverlay(
                onContinue = onStillWatchingContinue,
                onExit = onBack
            )
        }
    }
}

/**
 * Overlay "Stai ancora guardando?"
 */
@Composable
private fun StillWatchingOverlay(
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {}, // Block clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Stai ancora guardando?",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Hai guardato 3 episodi di fila.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Primary Button: Continue
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SandTVColors.Accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text("Continua a guardare")
                }
                
                // Secondary Button: Exit
                OutlinedButton(
                    onClick = onExit,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Text("Esci")
                }
            }
        }
    }
}

/**
 * Netflix-style player controls
 * - Title at top left with back button
 * - Play/pause at bottom left next to progress bar
 * - Additional controls at bottom right below progress bar
 */
@Composable
private fun ModernPlayerControls(
    title: String,
    subtitle: String?,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onSubtitles: () -> Unit,
    cumulativeSeekSeconds: Int,
    onSeek: (Int) -> Unit,
    onSeekConfirm: () -> Unit,
    onSeekCancel: () -> Unit,
    playbackSpeed: Float,
    onSpeedChange: () -> Unit,
    audioTracks: List<AudioTrackInfo>,
    currentAudioTrack: Int,
    onAudioTrackChange: (AudioTrackInfo) -> Unit,
    hasNextEpisode: Boolean,
    hasPreviousEpisode: Boolean,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    centerFocusRequester: FocusRequester,
    bottomFirstFocusRequester: FocusRequester
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top area with floating title (no opaque bar - modern floating style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 32.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Back button
            ModernIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                onClick = onBack,
                size = 44.dp
            )
            
            // Title and subtitle - floating text with shadow
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                            blurRadius = 6f
                        )
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                                blurRadius = 4f
                            )
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Bottom controls - Netflix style layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(horizontal = 40.dp, vertical = 24.dp)
        ) {
            // Main row: Restart + Play/Pause button + Time + Progress bar + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Restart Button (Bottom Left) - Expands on focus
                RestartButton(onClick = onRestart)

                // Play/Pause button (LEFT - Netflix style)
                if (!isLoading) {
                    NetflixPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPause,
                        focusRequester = centerFocusRequester
                    )
                }
                
                // Current time (updates during seek to show preview position)
                val displayPosition = if (cumulativeSeekSeconds != 0) {
                    (currentPosition + cumulativeSeekSeconds * 1000L).coerceIn(0, duration)
                } else {
                    currentPosition
                }
                Text(
                    text = formatTime(displayPosition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(60.dp)
                )
                
                // Progress bar (takes remaining space)
                ModernProgressBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    cumulativeSeekSeconds = cumulativeSeekSeconds,
                    onSeek = onSeek,
                    onSeekConfirm = onSeekConfirm,
                    onSeekCancel = onSeekCancel,
                    modifier = Modifier.weight(1f)
                )
                
                // Duration / Remaining time
                val remaining = duration - displayPosition
                Text(
                    text = "${formatTime(duration)} / -${formatTime(remaining.coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Secondary row: Additional controls at RIGHT (below progress bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed button
                    ModernPillButton(
                        text = "${playbackSpeed}x",
                        onClick = onSpeedChange,
                        focusRequester = bottomFirstFocusRequester
                    )
                    
                    // Audio tracks (if multiple)
                    if (audioTracks.size > 1) {
                        ModernAudioButton(
                            audioTracks = audioTracks,
                            currentTrack = currentAudioTrack,
                            onTrackChange = onAudioTrackChange
                        )
                    }
                    
                    // Subtitles
                    ModernIconButton(
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Sottotitoli",
                        onClick = onSubtitles,
                        size = 36.dp
                    )
                    
                    // Previous episode button (for series)
                    if (hasPreviousEpisode) {
                        ModernPreviousButton(onClick = onPlayPrevious)
                    }
                    
                    // Next episode button (for series)
                    if (hasNextEpisode) {
                        ModernNextButton(onClick = onPlayNext)
                    }
                }
            }
        }
    }
}

/**
 * Expanding Restart Button
 * Shows text "Dall'inizio" when focused
 */
@Composable
private fun RestartButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.2f),
        label = "bg"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "scale"
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(), // Animate width change
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "Ricomincia",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        
        if (isFocused) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Dall'inizio",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Netflix-style play/pause button (smaller, inline with progress bar)
 */
@Composable
private fun NetflixPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White,
        animationSpec = tween(150),
        label = "bg"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pausa" else "Play",
            tint = Color.Black,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Modern progress bar with sleek thumb
 */
@Composable
private fun ModernProgressBar(
    currentPosition: Long,
    duration: Long,
    cumulativeSeekSeconds: Int,
    onSeek: (Int) -> Unit,
    onSeekConfirm: () -> Unit,
    onSeekCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate preview position if seeking
    val effectivePosition = if (cumulativeSeekSeconds != 0) {
        (currentPosition + cumulativeSeekSeconds * 1000L).coerceIn(0, duration)
    } else {
        currentPosition
    }

    val progress = if (duration > 0) effectivePosition.toFloat() / duration.toFloat() else 0f
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Seek Mode State
    var isSeekModeActive by remember { mutableStateOf(false) }
    
    // Reset seek mode if focus is lost
    LaunchedEffect(isFocused) {
        if (!isFocused && isSeekModeActive) {
            isSeekModeActive = false
            onSeekCancel()
        }
    }
    
    val barHeight by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 4.dp, // Thicker when focused
        animationSpec = tween(150),
        label = "barHeight"
    )
    
    val thumbScale by animateFloatAsState(
        targetValue = if (isSeekModeActive) 1.5f else if (isFocused) 1.2f else 0.7f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumbScale"
    )
    
    val thumbColor by animateColorAsState(
        targetValue = if (isSeekModeActive || isFocused) SandTVColors.Accent else Color.White,
        label = "thumbColor"
    )
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp) // Incresed hit area
            .onKeyEvent { keyEvent ->
                if (!isFocused) return@onKeyEvent false
                
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        // Key.DirectionCenter and Key.Enter are handled by clickable modifier
                        Key.DirectionLeft -> {
                            if (isSeekModeActive) {
                                onSeek(-10) // Seek backward 10s
                                return@onKeyEvent true
                            }
                        }
                        Key.DirectionRight -> {
                            if (isSeekModeActive) {
                                onSeek(10) // Seek forward 10s
                                return@onKeyEvent true
                            }
                        }
                        Key.Back -> {
                             if (isSeekModeActive) {
                                 isSeekModeActive = false
                                 onSeekCancel()
                                 return@onKeyEvent true
                             }
                        }
                    }
                }
                false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null, 
                onClick = { 
                    // Click handling duplicated here just in case, but onKeyEvent usually handles D-pad center
                    isSeekModeActive = !isSeekModeActive
                    if (!isSeekModeActive) {
                         onSeekConfirm()
                    }
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.25f))
        )
        
        // Progress fill with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = 0.8f),
                            SandTVColors.Accent
                        )
                    )
                )
        )
        
    // Thumb indicator (Pallino) - centered exactly at progress point
    val thumbOffset = (maxWidth * progress.coerceIn(0f, 1f) - 10.dp).coerceAtLeast(0.dp)
    Box(
        modifier = Modifier
            .offset(x = thumbOffset)
            .size(20.dp)
            .align(Alignment.CenterStart)
            .scale(thumbScale)
            .background(thumbColor, CircleShape)
            .then(
                if (isFocused || isSeekModeActive) {
                    Modifier.border(2.dp, if (isSeekModeActive) Color.White else SandTVColors.Accent, CircleShape)
                } else Modifier
            )
            .then(
                if (isSeekModeActive) {
                    Modifier.shadow(8.dp, CircleShape, spotColor = SandTVColors.Accent)
                } else Modifier
            )
    )
}
}

/**
 * Modern play/pause button with clean design
 */
@Composable
private fun ModernPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scale"
    )
    
    // Transparent by default, accent only on focus
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        animationSpec = tween(150),
        label = "bg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.4f),
        animationSpec = tween(150),
        label = "border"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = CircleShape
            )
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pausa" else "Play",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * Modern icon button
 */
@Composable
private fun ModernIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "bg"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Modern pill button for speed etc.
 */
@Composable
private fun ModernPillButton(
    text: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.15f),
        label = "bg"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Modern audio track button with dropdown
 */
@Composable
private fun ModernAudioButton(
    audioTracks: List<AudioTrackInfo>,
    currentTrack: Int,
    onTrackChange: (AudioTrackInfo) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.15f),
        label = "bg"
    )
    
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .focusable(interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { showMenu = true }
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = audioTracks.getOrNull(currentTrack)?.label ?: "Audio",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            audioTracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = track.label,
                            fontWeight = if (track.index == currentTrack) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onTrackChange(track)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (track.index == currentTrack) {
                            Icon(Icons.Default.Check, null)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Modern previous episode button
 */
@Composable
private fun ModernPreviousButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.15f),
        label = "bg"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Precedente",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Modern next episode button
 */
@Composable
private fun ModernNextButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.15f),
        label = "bg"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Prossimo",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Modern seek indicator
 */
@Composable
private fun ModernSeekIndicator(seconds: Int) {
    val isForward = seconds > 0
    val absSeconds = kotlin.math.abs(seconds)
    val minutes = absSeconds / 60
    val secs = absSeconds % 60
    val timeText = if (minutes > 0) {
        "${minutes}:${secs.toString().padStart(2, '0')}"
    } else {
        "${absSeconds}s"
    }
    val text = if (isForward) "+$timeText" else "-$timeText"
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = SandTVColors.Accent,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Modern loading indicator
 */
@Composable
private fun ModernLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = SandTVColors.Accent,
            strokeWidth = 4.dp
        )
    }
}

/**
 * Modern next episode overlay
 */
@Composable
private fun ModernNextEpisodeOverlay(
    title: String,
    subtitle: String?,
    countdown: Int,
    autoPlay: Boolean,
    onPlayNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "scale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.White.copy(alpha = 0.2f),
        label = "border"
    )
    
    // Smooth progress animation - fills from 0 to 1 as countdown goes from 10 to 0
    // This makes the ring fill up clockwise from 12 o'clock
    val animatedProgress by animateFloatAsState(
        targetValue = 1f - (countdown / 10f),
        animationSpec = tween(
            durationMillis = 1000,  // 1 second smooth transition
            easing = LinearEasing    // Constant speed
        ),
        label = "countdownProgress"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .width(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.9f))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlayNext
            )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Countdown circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
            ) {
                if (autoPlay && countdown > 0) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },  // Smooth animated progress
                        modifier = Modifier.fillMaxSize(),
                        color = SandTVColors.Accent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "$countdown",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PROSSIMO EPISODIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = SandTVColors.Accent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Data class for next episode info
 */
data class NextEpisodeInfo(
    val title: String,
    val subtitle: String? = null,
    val countdown: Int,
    val autoPlay: Boolean = true
)

/**
 * Format time from milliseconds to HH:MM:SS or MM:SS
 */
private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60_000) % 60
    val hours = ms / 3600_000
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Fixed clock overlay for player - always visible, semi-transparent white
 */
@Composable
private fun PlayerClockOverlay(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(getPlayerCurrentTime()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getPlayerCurrentTime()
            delay(1000L)
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = currentTime,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun getPlayerCurrentTime(): String {
    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}
