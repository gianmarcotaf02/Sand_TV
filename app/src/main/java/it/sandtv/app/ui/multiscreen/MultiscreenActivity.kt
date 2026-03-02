package it.sandtv.app.ui.multiscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.foundation.lazy.grid.items
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.ChannelDao
import it.sandtv.app.data.database.entity.Channel
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * MultiscreenActivity - Display up to 3 live channels simultaneously
 * 
 * Layouts:
 * - 1 channel: Fullscreen
 * - 2 channels: Horizontal split (top/bottom)
 * - 3 channels: Inverted triangle (2 top, 1 bottom centered)
 */
@AndroidEntryPoint
class MultiscreenActivity : ComponentActivity() {
    
    companion object {
        const val MAX_SCREENS = 3
    }
    
    @Inject lateinit var channelDao: ChannelDao
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during multiscreen playback
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        setContent {
            SandTVTheme {
                MultiscreenContent(
                    onBackClick = { finish() },
                    onChannelSelect = { openChannelSelector() }
                )
            }
        }
    }
    
    private fun openChannelSelector() {
        // Will be handled by a dialog in the composable
    }
}

/**
 * Data class representing a screen slot in multiscreen view
 */
data class ScreenSlot(
    val index: Int,
    val channel: Channel? = null,
    val player: ExoPlayer? = null
)

@Composable
fun MultiscreenContent(
    onBackClick: () -> Unit,
    onChannelSelect: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State for screens (max 3)
    var screens by remember { mutableStateOf<List<ScreenSlot>>(emptyList()) }
    var focusedScreenIndex by remember { mutableIntStateOf(-1) }
    var isMuted by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var pickerTargetIndex by remember { mutableIntStateOf(-1) }
    
    // Channels for picker
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var channelCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Get ChannelDao from Hilt
    val channelDao = remember {
        (context as? MultiscreenActivity)?.channelDao
    }
    
    // Load channels
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            channelDao?.let { dao ->
                allChannels = dao.getAllChannelsList()
                channelCategories = dao.getCategoriesList()
            }
        }
    }
    
    // Function to create optimized player for multiscreen
    fun createPlayer(): ExoPlayer {
        // Reduced buffer for multi-stream to save memory
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,    // Min buffer (10s)
                30_000,    // Max buffer (30s) - reduced for multi-stream
                2_500,     // Buffer for playback
                5_000      // Buffer after seek
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
    
    // Function to add a channel
    fun addChannel(channel: Channel, targetIndex: Int = -1) {
        if (screens.size >= MultiscreenActivity.MAX_SCREENS && targetIndex < 0) return
        
        val player = createPlayer()
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.volume = if (isMuted) 0f else 1f
        player.play()
        
        val newSlot = ScreenSlot(
            index = if (targetIndex >= 0) targetIndex else screens.size,
            channel = channel,
            player = player
        )
        
        if (targetIndex >= 0 && targetIndex < screens.size) {
            // Replace existing
            screens[targetIndex].player?.release()
            screens = screens.toMutableList().also { it[targetIndex] = newSlot }
        } else {
            // Add new
            screens = screens + newSlot
        }
        
        if (focusedScreenIndex < 0) {
            focusedScreenIndex = 0
        }
    }
    
    // Function to remove a channel
    fun removeChannel(index: Int) {
        if (index < 0 || index >= screens.size) return
        
        screens[index].player?.release()
        screens = screens.filterIndexed { i, _ -> i != index }
            .mapIndexed { i, slot -> slot.copy(index = i) }
        
        if (focusedScreenIndex >= screens.size) {
            focusedScreenIndex = (screens.size - 1).coerceAtLeast(-1)
        }
    }
    
    // Toggle mute for all players
    fun toggleMute() {
        isMuted = !isMuted
        screens.forEach { slot ->
            slot.player?.volume = if (isMuted) 0f else 1f
        }
    }
    
    // Cleanup players on dispose
    DisposableEffect(Unit) {
        onDispose {
            screens.forEach { it.player?.release() }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            // Navigate up in layout
                            if (screens.size == 2 && focusedScreenIndex == 1) {
                                focusedScreenIndex = 0
                                true
                            } else if (screens.size == 3 && focusedScreenIndex == 2) {
                                focusedScreenIndex = 0
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            // Navigate down in layout
                            if (screens.size == 2 && focusedScreenIndex == 0) {
                                focusedScreenIndex = 1
                                true
                            } else if (screens.size == 3 && focusedScreenIndex in 0..1) {
                                focusedScreenIndex = 2
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (screens.size == 3 && focusedScreenIndex == 1) {
                                focusedScreenIndex = 0
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (screens.size == 3 && focusedScreenIndex == 0) {
                                focusedScreenIndex = 1
                                true
                            } else false
                        }
                        Key.Back -> {
                            if (showChannelPicker) {
                                showChannelPicker = false
                                true
                            } else {
                                onBackClick()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Main content based on screen count
        when (screens.size) {
            0 -> {
                // Empty state - prompt to add channel
                EmptyMultiscreenState(
                    onAddChannel = {
                        pickerTargetIndex = -1
                        showChannelPicker = true
                    }
                )
            }
            1 -> {
                // Fullscreen single channel
                SingleScreenLayout(
                    slot = screens[0],
                    isFocused = focusedScreenIndex == 0,
                    onFocus = { focusedScreenIndex = 0 },
                    onRemove = { removeChannel(0) },
                    onReplace = {
                        pickerTargetIndex = 0
                        showChannelPicker = true
                    }
                )
            }
            2 -> {
                // Horizontal split (top/bottom)
                TwoScreenLayout(
                    topSlot = screens[0],
                    bottomSlot = screens[1],
                    focusedIndex = focusedScreenIndex,
                    onFocus = { focusedScreenIndex = it },
                    onRemove = { removeChannel(it) },
                    onReplace = { index ->
                        pickerTargetIndex = index
                        showChannelPicker = true
                    }
                )
            }
            3 -> {
                // Inverted triangle (2 top, 1 bottom)
                ThreeScreenLayout(
                    topLeftSlot = screens[0],
                    topRightSlot = screens[1],
                    bottomSlot = screens[2],
                    focusedIndex = focusedScreenIndex,
                    onFocus = { focusedScreenIndex = it },
                    onRemove = { removeChannel(it) },
                    onReplace = { index ->
                        pickerTargetIndex = index
                        showChannelPicker = true
                    }
                )
            }
        }
        
        // Top bar with back button, title, and controls
        MultiscreenTopBar(
            screenCount = screens.size,
            isMuted = isMuted,
            onBackClick = onBackClick,
            onMuteToggle = { toggleMute() },
            onAddChannel = {
                if (screens.size < MultiscreenActivity.MAX_SCREENS) {
                    pickerTargetIndex = -1
                    showChannelPicker = true
                }
            },
            canAddMore = screens.size < MultiscreenActivity.MAX_SCREENS
        )
        
        // Channel picker overlay
        if (showChannelPicker) {
            ChannelPickerOverlay(
                channels = allChannels,
                categories = channelCategories,
                onChannelSelected = { channel ->
                    addChannel(channel, pickerTargetIndex)
                    showChannelPicker = false
                },
                onDismiss = { showChannelPicker = false }
            )
        }
    }
}

@Composable
fun MultiscreenTopBar(
    screenCount: Int,
    isMuted: Boolean,
    onBackClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onAddChannel: () -> Unit,
    canAddMore: Boolean
) {
    val backInteraction = remember { MutableInteractionSource() }
    val muteInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }
    
    val isBackFocused by backInteraction.collectIsFocusedAsState()
    val isMuteFocused by muteInteraction.collectIsFocusedAsState()
    val isAddFocused by addInteraction.collectIsFocusedAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isBackFocused) SandTVColors.Accent.copy(alpha = 0.3f)
                    else Color.Black.copy(alpha = 0.6f)
                )
                .border(
                    2.dp,
                    if (isBackFocused) SandTVColors.Accent else Color.Transparent,
                    CircleShape
                )
                .focusable(interactionSource = backInteraction)
                .clickable(
                    interactionSource = backInteraction,
                    indication = null,
                    onClick = onBackClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                tint = SandTVColors.TextPrimary
            )
        }
        
        // Title
        Text(
            text = "Multiscreen",
            style = MaterialTheme.typography.headlineMedium,
            color = SandTVColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute button
            if (screenCount > 0) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMuteFocused) SandTVColors.Accent.copy(alpha = 0.3f)
                            else Color.Black.copy(alpha = 0.6f)
                        )
                        .border(
                            2.dp,
                            if (isMuteFocused) SandTVColors.Accent else Color.Transparent,
                            CircleShape
                        )
                        .focusable(interactionSource = muteInteraction)
                        .clickable(
                            interactionSource = muteInteraction,
                            indication = null,
                            onClick = onMuteToggle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Riattiva audio" else "Muta tutti",
                        tint = if (isMuted) SandTVColors.Error else SandTVColors.TextPrimary
                    )
                }
            }
            
            // Add channel button
            if (canAddMore) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isAddFocused) SandTVColors.Accent.copy(alpha = 0.3f)
                            else Color.Black.copy(alpha = 0.6f)
                        )
                        .border(
                            2.dp,
                            if (isAddFocused) SandTVColors.Accent else Color.Transparent,
                            CircleShape
                        )
                        .focusable(interactionSource = addInteraction)
                        .clickable(
                            interactionSource = addInteraction,
                            indication = null,
                            onClick = onAddChannel
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aggiungi canale",
                        tint = SandTVColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyMultiscreenState(onAddChannel: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Multiscreen",
                style = MaterialTheme.typography.displaySmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Visualizza fino a 3 canali contemporaneamente",
                style = MaterialTheme.typography.bodyLarge,
                color = SandTVColors.TextSecondary
            )
            
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isFocused) SandTVColors.Accent
                        else SandTVColors.BackgroundTertiary
                    )
                    .border(
                        2.dp,
                        if (isFocused) SandTVColors.AccentLight else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onAddChannel
                    )
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = SandTVColors.TextPrimary
                    )
                    Text(
                        text = "Aggiungi canale",
                        style = MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SingleScreenLayout(
    slot: ScreenSlot,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onRemove: () -> Unit,
    onReplace: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MultiscreenPlayerCell(
            slot = slot,
            isFocused = isFocused,
            onFocus = onFocus,
            onRemove = onRemove,
            onReplace = onReplace,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun TwoScreenLayout(
    topSlot: ScreenSlot, // Kept name for compatibility, effectively Left
    bottomSlot: ScreenSlot, // Kept name for compatibility, effectively Right
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onReplace: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp, bottom = 16.dp, start = 16.dp, end = 16.dp), // Added padding for aesthetics
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Screen (was top)
        MultiscreenPlayerCell(
            slot = topSlot,
            isFocused = focusedIndex == 0,
            onFocus = { onFocus(0) },
            onRemove = { onRemove(0) },
            onReplace = { onReplace(0) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        
        // Right Screen (was bottom)
        MultiscreenPlayerCell(
            slot = bottomSlot,
            isFocused = focusedIndex == 1,
            onFocus = { onFocus(1) },
            onRemove = { onRemove(1) },
            onReplace = { onReplace(1) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun ThreeScreenLayout(
    topLeftSlot: ScreenSlot,
    topRightSlot: ScreenSlot,
    bottomSlot: ScreenSlot,
    focusedIndex: Int,
    onFocus: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onReplace: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp) // Space for top bar
    ) {
        // Top row - 2 screens
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MultiscreenPlayerCell(
                slot = topLeftSlot,
                isFocused = focusedIndex == 0,
                onFocus = { onFocus(0) },
                onRemove = { onRemove(0) },
                onReplace = { onReplace(0) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
            
            MultiscreenPlayerCell(
                slot = topRightSlot,
                isFocused = focusedIndex == 1,
                onFocus = { onFocus(1) },
                onRemove = { onRemove(1) },
                onReplace = { onReplace(1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
        }
        
        // Bottom row - 1 centered screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            MultiscreenPlayerCell(
                slot = bottomSlot,
                isFocused = focusedIndex == 2,
                onFocus = { onFocus(2) },
                onRemove = { onRemove(2) },
                onReplace = { onReplace(2) },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun MultiscreenPlayerCell(
    slot: ScreenSlot,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onRemove: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "cellBorder"
    )
    
    var showOptions by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .background(SandTVColors.BackgroundSecondary)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onFocus
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && isFocused) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            showOptions = !showOptions
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Player view
        slot.player?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Channel info overlay (bottom)
        slot.channel?.let { channel ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Channel logo
                    if (channel.logoUrl != null) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Options overlay when focused and pressed
        if (showOptions && isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Replace channel
                    OptionButton(
                        icon = Icons.Default.Add,
                        label = "Cambia",
                        onClick = {
                            showOptions = false
                            onReplace()
                        }
                    )
                    
                    // Remove channel
                    OptionButton(
                        icon = Icons.Default.Close,
                        label = "Rimuovi",
                        onClick = {
                            showOptions = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) SandTVColors.Accent.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .border(
                2.dp,
                if (isFocused) SandTVColors.Accent else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = SandTVColors.TextPrimary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SandTVColors.TextPrimary
        )
    }
}

@Composable
fun ChannelPickerOverlay(
    channels: List<Channel>,
    categories: List<String>,
    onChannelSelected: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    
    // Focus management for search
    val gridFocusRequester = remember { FocusRequester() }
    
    // Search Results Logic
    // 1. Matched Categories
    val matchedCategories = remember(searchQuery, categories) {
        if (searchQuery.isNotEmpty()) {
            categories.filter { it.contains(searchQuery, ignoreCase = true) }
        } else emptyList()
    }
    
    // 2. Matched Channels (direct name match)
    val matchedChannels = remember(searchQuery, channels) {
        if (searchQuery.isNotEmpty()) {
            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else emptyList()
    }
    
    // Filter logic to display channels when a Category is selected OR standard browser
    val displayedChannels = remember(selectedCategory, channels) {
        if (selectedCategory != null) {
             if (selectedCategory == "Tutti") channels 
             else channels.filter { it.category == selectedCategory }
        } else {
            emptyList()
        }
    }
    
    // Handle back navigation inside the dialog
    fun onBack() {
        if (selectedCategory != null) {
            selectedCategory = null
            // If we came from search, we might want to keep search? 
            // The user flow "open category -> back" usually returns to previous state.
            // If we cleared search on category select, we return to empty state.
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else {
            onDismiss()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundSecondary)
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Show back arrow if we are deep in navigation
                    if (selectedCategory != null || searchQuery.isNotEmpty()) {
                        val backInteraction = remember { MutableInteractionSource() }
                        val isBackFocused by backInteraction.collectIsFocusedAsState()
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isBackFocused) SandTVColors.Accent.copy(alpha = 0.3f) else Color.Transparent)
                                .focusable(interactionSource = backInteraction)
                                .clickable(
                                    interactionSource = backInteraction,
                                    indication = null,
                                    onClick = { onBack() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = SandTVColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    
                    Text(
                        text = when {
                            selectedCategory != null -> selectedCategory!!
                            searchQuery.isNotEmpty() -> "Cerca: \"$searchQuery\""
                            else -> "Seleziona Categoria"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                val closeInteraction = remember { MutableInteractionSource() }
                val isCloseFocused by closeInteraction.collectIsFocusedAsState()
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCloseFocused) SandTVColors.Accent
                            else SandTVColors.BackgroundTertiary
                        )
                        .focusable(interactionSource = closeInteraction)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi",
                        tint = SandTVColors.TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar (Only show if NOT inside a category, or maybe allow search inside category?)
            // Design choice: Search Bar searches globally. If inside category, it might be confusing.
            // Let's hide search bar if category is selected to give focused view, 
            // OR keep it but make it clear it starts a new search.
            if (selectedCategory == null) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    onSearch = { gridFocusRequester.requestFocus() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // CONTENT GRID
            androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid(
                columns = androidx.tv.foundation.lazy.grid.TvGridCells.Fixed(4), // Base 4 columns
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(gridFocusRequester)
            ) {
                // CASE 1: SEARCHING (And no category selected)
                if (searchQuery.isNotEmpty() && selectedCategory == null) {
                    
                    // A) Matched Categories
                    if (matchedCategories.isNotEmpty()) {
                        item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(4) }) {
                            Text(
                                text = "Gruppi (${matchedCategories.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = SandTVColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(matchedCategories.size) { index ->
                            val category = matchedCategories[index]
                            CategoryGridItem(
                                name = category,
                                count = null, // Could calc count but expensive
                                icon = Icons.Default.Folder, // Use Folder Icon
                                onClick = { 
                                    // Open this category
                                    selectedCategory = category
                                    searchQuery = "" // Clear search to show full category
                                }
                            )
                        }
                    }
                    
                    // B) Matched Channels
                    if (matchedChannels.isNotEmpty()) {
                        item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(4) }) {
                            Text(
                                text = "Canali (${matchedChannels.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = SandTVColors.TextSecondary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        items(matchedChannels.size) { index ->
                            val channel = matchedChannels[index]
                            ChannelPickerItem(
                                channel = channel,
                                onClick = { onChannelSelected(channel) }
                            )
                        }
                    }
                    
                    if (matchedCategories.isEmpty() && matchedChannels.isEmpty()) {
                        item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(4) }) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nessun risultato trovato",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = SandTVColors.TextSecondary
                                )
                            }
                        }
                    }
                    
                } 
                // CASE 2: BROWSING CATEGORIES (Search is empty, Category is null)
                else if (selectedCategory == null) {
                    item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(4) }) {
                        Text(
                            text = "Categorie",
                            style = MaterialTheme.typography.titleMedium,
                            color = SandTVColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    
                    // "All Channels"
                    item {
                        CategoryGridItem(
                            name = "Tutti i canali",
                            count = channels.size,
                            icon = Icons.Default.Folder,
                            onClick = { selectedCategory = "Tutti" }
                        )
                    }
                    
                    items(categories.size) { index ->
                        val category = categories[index]
                        CategoryGridItem(
                            name = category,
                            count = null, 
                            icon = Icons.Default.Folder,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
                // CASE 3: INSIDE A CATEGORY (Show channels)
                else {
                    if (displayedChannels.isEmpty()) {
                        item(span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(4) }) {
                             Text("Nessun canale in questa categoria")
                        }
                    } else {
                        items(displayedChannels.size) { index ->
                            val channel = displayedChannels[index]
                            ChannelPickerItem(
                                channel = channel,
                                onClick = { onChannelSelected(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = SandTVColors.TextPrimary),
        cursorBrush = Brush.verticalGradient(listOf(SandTVColors.Accent, SandTVColors.Accent)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandTVColors.BackgroundTertiary)
                    .border(
                        2.dp,
                        if (isFocused) SandTVColors.Accent else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Cerca canale...",
                            color = SandTVColors.TextTertiary
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancella",
                        tint = SandTVColors.TextSecondary,
                        modifier = Modifier.clickable(onClick = onClear)
                    )
                }
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().focusable(interactionSource = interactionSource)
    )
}

@Composable
private fun CategoryGridItem(
    name: String,
    count: Int? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.BackgroundTertiary,
        label = "catBg"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundDark else SandTVColors.TextPrimary,
        label = "catText"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .size(32.dp)
                    .padding(bottom = 8.dp)
            )
        }
        
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        if (count != null) {
            Text(
                text = "$count canali",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ChannelPickerItem(
    channel: Channel,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "channelBorder"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(
                if (isFocused) SandTVColors.BackgroundTertiary
                else SandTVColors.CardBackground
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SandTVColors.BackgroundSecondary),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = channel.name.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = SandTVColors.TextSecondary
                )
            }
        }
        
        // Name
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            color = SandTVColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
