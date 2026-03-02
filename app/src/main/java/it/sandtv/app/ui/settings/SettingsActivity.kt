package it.sandtv.app.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.R
import it.sandtv.app.data.database.dao.PlaylistDao
import it.sandtv.app.data.database.dao.ProfileDao
import it.sandtv.app.data.database.entity.Playlist
import it.sandtv.app.data.database.entity.Profile
import it.sandtv.app.data.preferences.UserPreferences
import it.sandtv.app.ui.loading.LoadingActivity
import it.sandtv.app.ui.profile.getAvatarResource
import it.sandtv.app.ui.setup.SetupActivity
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import it.sandtv.app.ui.theme.AccentColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val isDestructive: Boolean = false
)

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var profileDao: ProfileDao
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var playlistRepository: it.sandtv.app.data.repository.PlaylistRepository
    @Inject lateinit var epgRepository: it.sandtv.app.data.repository.EpgRepository
    @Inject lateinit var appUpdateManager: it.sandtv.app.update.AppUpdateManager
    @Inject lateinit var openSubtitlesRepository: it.sandtv.app.data.repository.OpenSubtitlesRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SandTVTheme {
                SettingsScreenContent()
            }
        }
    }
    
    @Composable
    private fun SettingsScreenContent() {
        var selectedMenuId by remember { mutableStateOf<String?>(null) }
        
        // Focus requester for content area - to focus on first interactive element when section changes
        val contentFocusRequester = remember { FocusRequester() }
        
        // Auto-focus on content area when switching sections
        LaunchedEffect(selectedMenuId) {
            if (selectedMenuId != null) {
                kotlinx.coroutines.delay(200) // Wait for content to compose
                try {
                    contentFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            }
        }
        
        val menuItems = listOf(
            SettingsMenuItem("profile", "Profilo", "Modifica nome e avatar", Icons.Default.Person),
            SettingsMenuItem("playlist", "Playlist", "Aggiornamento e sincronizzazione", Icons.AutoMirrored.Filled.List),
            SettingsMenuItem("preferences", "Preferenze", "Impostazioni generali", Icons.Default.Settings),
            SettingsMenuItem("player", "Player", "Impostazioni riproduzione", Icons.Default.PlayArrow),
            SettingsMenuItem("subtitles", "Sottotitoli", "OpenSubtitles e lingua", Icons.Default.Subtitles),
            SettingsMenuItem("epg", "Guida TV (EPG)", "Aggiornamento guida programmi", Icons.Default.Tv),
            SettingsMenuItem("appearance", "Aspetto", "Tema e visualizzazione", Icons.Default.Palette),
            SettingsMenuItem("storage", "Archiviazione", "Cache e dati", Icons.Default.Storage),
            SettingsMenuItem("updates", "Aggiornamenti", "Controlla nuove versioni", Icons.Default.SystemUpdate),
            SettingsMenuItem("about", "Informazioni", "Info sull'app", Icons.Default.Info),
            SettingsMenuItem("logout", "Disconnetti", "Esci dall'account", Icons.AutoMirrored.Filled.ExitToApp, isDestructive = true)
        )
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(SandTVColors.BackgroundPrimary)
        ) {
            // Sidebar
            SettingsSidebar(
                menuItems = menuItems,
                selectedItem = selectedMenuId,
                onItemClick = { item ->
                    when (item.id) {
                        "logout" -> logout()
                        else -> selectedMenuId = item.id
                    }
                },
                modifier = Modifier
                    .width(350.dp)
                    .fillMaxHeight()
            )
            
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(40.dp)
            ) {
                when (selectedMenuId) {
                    "profile" -> ProfileSettings(profileDao, userPreferences, contentFocusRequester)
                    "playlist" -> PlaylistSettings(playlistDao, playlistRepository, userPreferences, contentFocusRequester)
                    "preferences" -> PreferencesSettings(userPreferences, contentFocusRequester)
                    "player" -> PlayerSettings(userPreferences, contentFocusRequester)
                    "subtitles" -> SubtitlesSettings(openSubtitlesRepository, userPreferences, contentFocusRequester)
                    "epg" -> EpgSettings(userPreferences, epgRepository, playlistDao, contentFocusRequester)
                    "appearance" -> AppearanceSettings(userPreferences)
                    "storage" -> StorageSettings()
                    "updates" -> UpdateSettings(appUpdateManager, contentFocusRequester)
                    "about" -> AboutSettings()
                    else -> {
                        Column {
                            Text(
                                text = "Impostazioni",
                                style = MaterialTheme.typography.headlineLarge,
                                color = SandTVColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Seleziona una categoria dal menu a sinistra",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SandTVColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun logout() {
        lifecycleScope.launch {
            userPreferences.setCurrentProfileId(-1)
            val intent = Intent(this@SettingsActivity, LoadingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}

@Composable
private fun SettingsSidebar(
    menuItems: List<SettingsMenuItem>,
    selectedItem: String?,
    onItemClick: (SettingsMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val regularItems = menuItems.filter { !it.isDestructive }
    val destructiveItems = menuItems.filter { it.isDestructive }
    
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SandTVColors.BackgroundSecondary,
                        SandTVColors.BackgroundDark
                    )
                )
            )
    ) {
        // Header with logo and title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // App icon with glow effect
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    SandTVColors.Accent,
                                    SandTVColors.AccentDark
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "Impostazioni",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configura la tua esperienza",
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.TextTertiary
                    )
                }
            }
        }
        
        // Subtle divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SandTVColors.TextTertiary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Regular menu items
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(regularItems) { item ->
                SettingsMenuItemRow(
                    item = item,
                    isSelected = selectedItem == item.id,
                    onClick = { onItemClick(item) }
                )
            }
        }
        
        // Separator before destructive actions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(1.dp)
                .background(SandTVColors.TextTertiary.copy(alpha = 0.2f))
        )
        
        // Destructive items (logout, etc.)
        destructiveItems.forEach { item ->
            SettingsMenuItemRow(
                item = item,
                isSelected = selectedItem == item.id,
                onClick = { onItemClick(item) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsMenuItemRow(
    item: SettingsMenuItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "menuScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent.copy(alpha = 0.15f)
            isFocused -> SandTVColors.BackgroundTertiary.copy(alpha = 0.8f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "menuBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> SandTVColors.Accent
            isSelected -> SandTVColors.Accent.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "menuBorder"
    )
    
    val iconBackgroundColor by animateColorAsState(
        targetValue = when {
            item.isDestructive -> Color.Red.copy(alpha = 0.15f)
            isSelected || isFocused -> SandTVColors.Accent.copy(alpha = 0.2f)
            else -> SandTVColors.BackgroundTertiary.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "iconBg"
    )
    
    val contentColor = when {
        item.isDestructive -> Color(0xFFEF4444)
        isSelected || isFocused -> SandTVColors.TextPrimary
        else -> SandTVColors.TextSecondary
    }
    
    val iconColor = when {
        item.isDestructive -> Color(0xFFEF4444)
        isSelected || isFocused -> SandTVColors.Accent
        else -> SandTVColors.TextSecondary
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Modern icon container with subtle gradient
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium
            )
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SandTVColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
        
        // Animated chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isFocused || isSelected) SandTVColors.Accent else SandTVColors.TextTertiary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ============ Settings Sections ============

@Composable
private fun ProfileSettings(profileDao: ProfileDao, userPreferences: UserPreferences, contentFocusRequester: FocusRequester? = null) {
    val coroutineScope = rememberCoroutineScope()
    var currentProfile by remember { mutableStateOf<Profile?>(null) }
    var editedName by remember { mutableStateOf("") }
    var selectedAvatarIndex by remember { mutableStateOf(0) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    
    // Load current profile
    LaunchedEffect(Unit) {
        val profileId = userPreferences.getCurrentProfileId()
        if (profileId != null && profileId > 0) {
            currentProfile = profileDao.getProfileById(profileId)
            currentProfile?.let {
                editedName = it.name
                selectedAvatarIndex = it.avatarIndex
            }
        }
    }
    
    SettingsSection(title = "Profilo") {
        currentProfile?.let { profile ->
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar with edit button
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SandTVColors.CardBackground)
                        .clickable { showAvatarPicker = true }
                ) {
                    Image(
                        painter = painterResource(getAvatarResource(selectedAvatarIndex)),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize()
                    )
                    // Edit overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Cambia avatar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Text(
                    text = "Tocca per cambiare avatar",
                    style = MaterialTheme.typography.bodySmall,
                    color = SandTVColors.TextTertiary
                )
                
                // Avatar picker
                if (showAvatarPicker) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SandTVColors.BackgroundSecondary)
                            .padding(16.dp)
                    ) {
                        (0..1).forEach { index ->
                            val isSelected = selectedAvatarIndex == index
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) SandTVColors.Accent else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedAvatarIndex = index
                                        showAvatarPicker = false
                                    }
                            ) {
                                Image(
                                    painter = painterResource(getAvatarResource(index)),
                                    contentDescription = "Avatar $index",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Name input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nome profilo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandTVColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SandTVColors.Accent,
                            unfocusedBorderColor = SandTVColors.TextTertiary,
                            cursorColor = SandTVColors.Accent,
                            focusedTextColor = SandTVColors.TextPrimary,
                            unfocusedTextColor = SandTVColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Save button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val updatedProfile = profile.copy(
                                name = editedName.trim(),
                                avatarIndex = selectedAvatarIndex
                            )
                            profileDao.update(updatedProfile)
                            currentProfile = updatedProfile
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SandTVColors.Accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salva modifiche")
                }
            }
        } ?: run {
            SettingsInfo(text = "Nessun profilo selezionato")
        }
    }
}

@Composable
private fun PlaylistSettings(
    playlistDao: PlaylistDao,
    playlistRepository: it.sandtv.app.data.repository.PlaylistRepository,
    userPreferences: UserPreferences,
    contentFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshingPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    
    // Auto-update settings
    var updateMode by remember { mutableStateOf("manual") }
    var updateInterval by remember { mutableStateOf("24h") }
    
    // Load current settings
    LaunchedEffect(Unit) {
        updateMode = userPreferences.getPlaylistUpdateMode()
        updateInterval = userPreferences.getPlaylistUpdateInterval()
    }
    
    SettingsSection(title = "Playlist") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Auto-update settings
            SettingsDropdown(
                label = "Modalità aggiornamento",
                value = updateMode,
                options = listOf(
                    "manual" to "Manuale",
                    "auto" to "Automatico"
                ),
                onValueChange = {
                    updateMode = it
                    coroutineScope.launch { userPreferences.setPlaylistUpdateMode(it) }
                }
            )
            
            // Update Interval (only if auto)
            if (updateMode == "auto") {
                SettingsDropdown(
                    label = "Frequenza aggiornamento",
                    value = updateInterval,
                    options = listOf(
                        "startup" to "All'avvio",
                        "6h" to "Ogni 6 ore",
                        "12h" to "Ogni 12 ore",
                        "24h" to "Ogni 24 ore",
                        "3d" to "Ogni 3 giorni",
                        "weekly" to "Settimanale"
                    ),
                    onValueChange = {
                        updateInterval = it
                        coroutineScope.launch { userPreferences.setPlaylistUpdateInterval(it) }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Add new playlist button
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aggiungi playlist")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (playlists.isEmpty()) {
                SettingsInfo(text = "Nessuna playlist configurata")
            } else {
                playlists.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onEdit = {
                            selectedPlaylist = playlist
                            showEditDialog = true
                        },
                        onDelete = {
                            coroutineScope.launch { playlistDao.delete(playlist) }
                        },
                        onRefresh = {
                            if (!isRefreshing) {
                                isRefreshing = true
                                refreshingPlaylistId = playlist.id
                                refreshError = null
                                coroutineScope.launch {
                                    try {
                                        playlistRepository.refreshPlaylist(playlist.id)
                                        showRestartDialog = true
                                    } catch (e: Exception) {
                                        android.util.Log.e("PlaylistSettings", "Refresh failed", e)
                                        refreshError = e.message ?: "Errore sconosciuto"
                                    } finally {
                                        isRefreshing = false
                                        refreshingPlaylistId = null
                                    }
                                }
                            }
                        }
                    )
                    
                    // Show modern loading indicator when refreshing this playlist
                    if (isRefreshing && refreshingPlaylistId == playlist.id) {
                        val infiniteTransition = rememberInfiniteTransition(label = "refreshProgress")
                        val shimmerOffset by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "shimmer"
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Text with animated emoji
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Professional loading indicator
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = SandTVColors.Accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Aggiornamento in corso...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandTVColors.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Modern progress bar with shimmer effect
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SandTVColors.BackgroundTertiary)
                            ) {
                                // Animated shimmer bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.35f)
                                        .offset(x = (shimmerOffset * 280).dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    SandTVColors.Accent.copy(alpha = 0.3f),
                                                    SandTVColors.Accent,
                                                    SandTVColors.Accent.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
            
            // Refresh all playlists button (only if playlists exist)
            if (playlists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (!isRefreshing) {
                            isRefreshing = true
                            refreshingPlaylistId = -1L // -1 means all
                            refreshError = null
                            coroutineScope.launch {
                                try {
                                    playlists.forEach { playlist ->
                                        refreshingPlaylistId = playlist.id
                                        playlistRepository.refreshPlaylist(playlist.id)
                                    }
                                    showRestartDialog = true
                                } catch (e: Exception) {
                                    android.util.Log.e("PlaylistSettings", "Refresh all failed", e)
                                    refreshError = e.message ?: "Errore sconosciuto"
                                } finally {
                                    isRefreshing = false
                                    refreshingPlaylistId = null
                                }
                            }
                        }
                    },
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRefreshing) "Aggiornamento in corso..." else "Aggiorna tutte le playlist")
                }
            }
            
            // Error message
            refreshError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Errore: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }
    
    // Restart Dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            containerColor = SandTVColors.BackgroundElevated,
            title = {
                Text("Aggiornamento completato", color = SandTVColors.TextPrimary)
            },
            text = {
                Text(
                    "La playlist è stata aggiornata. Riavvia l'app per visualizzare i nuovi contenuti.",
                    color = SandTVColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartDialog = false
                        // Restart the app
                        val packageManager = context.packageManager
                        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        (context as? android.app.Activity)?.finish()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent)
                ) {
                    Text("Riavvia app")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SandTVColors.TextSecondary)
                ) {
                    Text("Dopo")
                }
            }
        )
    }
    
    // Edit Playlist Dialog
    if (showEditDialog && selectedPlaylist != null) {
        PlaylistEditDialog(
            playlist = selectedPlaylist!!,
            onDismiss = { showEditDialog = false; selectedPlaylist = null },
            onSave = { updated ->
                coroutineScope.launch {
                    playlistDao.update(updated)
                    showEditDialog = false
                    selectedPlaylist = null
                }
            }
        )
    }
    
    // Add Playlist Dialog
    if (showAddDialog) {
        PlaylistEditDialog(
            playlist = null,
            onDismiss = { showAddDialog = false },
            onSave = { newPlaylist ->
                coroutineScope.launch {
                    playlistDao.insert(newPlaylist)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) SandTVColors.BackgroundTertiary else SandTVColors.SurfaceDark)
            .border(2.dp, if (isFocused) SandTVColors.Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onEdit)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = SandTVColors.TextPrimary)
            Text(
                text = "${playlist.type.uppercase()} • ${playlist.channelCount} canali • ${playlist.movieCount} film • ${playlist.seriesCount} serie",
                style = MaterialTheme.typography.bodySmall,
                color = SandTVColors.TextTertiary
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Aggiorna", tint = SandTVColors.Accent)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = SandTVColors.TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color.Red)
            }
        }
    }
}

@Composable
private fun PlaylistEditDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onSave: (Playlist) -> Unit
) {
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var url by remember { mutableStateOf(playlist?.url ?: "") }
    var username by remember { mutableStateOf(playlist?.username ?: "") }
    var password by remember { mutableStateOf(playlist?.password ?: "") }
    var epgUrl by remember { mutableStateOf(playlist?.epgUrl ?: "") }
    var type by remember { mutableStateOf(playlist?.type ?: "xtream") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SandTVColors.BackgroundElevated,
        title = {
            Text(
                if (playlist == null) "Aggiungi Playlist" else "Modifica Playlist",
                color = SandTVColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL Server") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = epgUrl, onValueChange = { epgUrl = it },
                    label = { Text("URL EPG (opzionale)") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        focusedTextColor = SandTVColors.TextPrimary,
                        unfocusedTextColor = SandTVColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        val newPlaylist = Playlist(
                            id = playlist?.id ?: 0,
                            name = name.trim(),
                            type = type,
                            url = url.trim(),
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                            epgUrl = epgUrl.ifBlank { null },
                            channelCount = playlist?.channelCount ?: 0,
                            movieCount = playlist?.movieCount ?: 0,
                            seriesCount = playlist?.seriesCount ?: 0
                        )
                        onSave(newPlaylist)
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = SandTVColors.Accent)
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SandTVColors.TextSecondary)
            ) { Text("Annulla") }
        }
    )
}

@Composable
private fun PreferencesSettings(userPreferences: UserPreferences, contentFocusRequester: FocusRequester? = null) {
    val coroutineScope = rememberCoroutineScope()
    var liveLayoutMode by remember { mutableStateOf("grid") }
    
    // Load current settings
    LaunchedEffect(Unit) {
        liveLayoutMode = userPreferences.getLiveLayoutMode()
    }
    
    SettingsSection(title = "Preferenze") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Live TV Layout
            SettingsDropdown(
                label = "Visualizzazione Canali Live",
                value = liveLayoutMode,
                options = listOf(
                    "grid" to "Griglia (schede)",
                    "timeline" to "Timeline (EPG)"
                ),
                onValueChange = {
                    liveLayoutMode = it
                    coroutineScope.launch { userPreferences.setLiveLayoutMode(it) }
                }
            )
        }
    }
}

@Composable
private fun PlayerSettings(userPreferences: UserPreferences, contentFocusRequester: FocusRequester? = null) {
    val coroutineScope = rememberCoroutineScope()
    
    val audioLanguage by userPreferences.getDefaultAudioLanguageFlow().collectAsState(initial = "ita")
    val subtitleLanguage by userPreferences.getDefaultSubtitleLanguageFlow().collectAsState(initial = "ita")
    val subtitlesEnabled by userPreferences.getSubtitlesEnabledFlow().collectAsState(initial = false)
    
    SettingsSection(title = "Player") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsDropdown(
                label = "Lingua audio predefinita",
                value = audioLanguage,
                options = listOf("ita" to "Italiano", "eng" to "English", "fra" to "Français", "deu" to "Deutsch", "spa" to "Español"),
                onValueChange = { coroutineScope.launch { userPreferences.setDefaultAudioLanguage(it) } }
            )
            
            SettingsDropdown(
                label = "Lingua sottotitoli predefinita",
                value = subtitleLanguage,
                options = listOf("ita" to "Italiano", "eng" to "English", "fra" to "Français", "deu" to "Deutsch", "spa" to "Español"),
                onValueChange = { coroutineScope.launch { userPreferences.setDefaultSubtitleLanguage(it) } }
            )
            
            SettingsSwitch(
                label = "Abilita sottotitoli automatici",
                checked = subtitlesEnabled,
                onCheckedChange = { coroutineScope.launch { userPreferences.setSubtitlesEnabled(it) } }
            )
            
            // Seek settings
            val seekForwardSeconds by userPreferences.getSeekForwardSecondsFlow().collectAsState(initial = 10)
            val seekBackwardSeconds by userPreferences.getSeekBackwardSecondsFlow().collectAsState(initial = 10)
            
            SettingsDropdown(
                label = "Avanzamento rapido (Freccia Destra)",
                value = seekForwardSeconds.toString(),
                options = listOf(
                    "5" to "5 secondi", 
                    "10" to "10 secondi", 
                    "15" to "15 secondi", 
                    "30" to "30 secondi", 
                    "60" to "60 secondi"
                ),
                onValueChange = { coroutineScope.launch { userPreferences.setSeekForwardSeconds(it.toInt()) } }
            )
            
            SettingsDropdown(
                label = "Riavvolgimento rapido (Freccia Sinistra)",
                value = seekBackwardSeconds.toString(),
                options = listOf(
                    "5" to "5 secondi", 
                    "10" to "10 secondi", 
                    "15" to "15 secondi", 
                    "30" to "30 secondi", 
                    "60" to "60 secondi"
                ),
                onValueChange = { coroutineScope.launch { userPreferences.setSeekBackwardSeconds(it.toInt()) } }
            )
            
            // YouTube Player Selection
            val youtubePackage by userPreferences.getYoutubePlayerPackageFlow().collectAsState(initial = null)
            
            SettingsDropdown(
                label = "App per Trailer YouTube",
                value = youtubePackage ?: "",
                options = listOf(
                    "" to "Chiedi sempre",
                    "org.smarttube.beta" to "SmartTube Beta",
                    "com.google.android.youtube.tv" to "YouTube TV",
                    "com.google.android.youtube" to "YouTube (Standard)"
                ),
                onValueChange = { 
                    coroutineScope.launch { 
                        userPreferences.setYoutubePlayerPackage(if (it.isEmpty()) null else it) 
                    } 
                }
            )
        }
    }
}

@Composable
private fun SubtitlesSettings(
    openSubtitlesRepo: it.sandtv.app.data.repository.OpenSubtitlesRepository,
    userPreferences: UserPreferences,
    contentFocusRequester: FocusRequester? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    // State for login
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginSuccess by remember { mutableStateOf(false) }
    
    // Auth state
    var isAuthenticated by remember { mutableStateOf(openSubtitlesRepo.isAuthenticated()) }
    var remainingDownloads by remember { mutableStateOf(openSubtitlesRepo.getRemainingDownloads()) }
    var currentUsername by remember { mutableStateOf(openSubtitlesRepo.getUsername()) }
    
    // Preferred language
    var preferredLanguage by remember { mutableStateOf("it") }
    
    // Load preferences
    LaunchedEffect(Unit) {
        preferredLanguage = userPreferences.getSubtitleLanguage()
    }
    
    SettingsSection(title = "Sottotitoli") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            
            // OpenSubtitles section
            Text(
                text = "OpenSubtitles",
                style = MaterialTheme.typography.titleMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            if (isAuthenticated) {
                // Logged in state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SandTVColors.Accent.copy(alpha = 0.15f))
                        .border(1.dp, SandTVColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Connesso come ${currentUsername ?: "utente"}",
                                color = SandTVColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$remainingDownloads download rimasti oggi",
                            style = MaterialTheme.typography.bodySmall,
                            color = SandTVColors.TextSecondary
                        )
                    }
                    
                    // Logout button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                openSubtitlesRepo.logout()
                                isAuthenticated = false
                                currentUsername = null
                                remainingDownloads = 0
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text("Esci")
                    }
                }
            } else {
                // Login form
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Accedi con il tuo account OpenSubtitles per scaricare i sottotitoli automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.TextSecondary
                    )
                    
                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; loginError = null },
                        label = { Text("Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SandTVColors.Accent,
                            unfocusedBorderColor = SandTVColors.TextTertiary,
                            cursorColor = SandTVColors.Accent,
                            focusedTextColor = SandTVColors.TextPrimary,
                            unfocusedTextColor = SandTVColors.TextPrimary,
                            focusedLabelColor = SandTVColors.Accent,
                            unfocusedLabelColor = SandTVColors.TextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (contentFocusRequester != null) Modifier.focusRequester(contentFocusRequester) else Modifier)
                    )
                    
                    // Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; loginError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SandTVColors.Accent,
                            unfocusedBorderColor = SandTVColors.TextTertiary,
                            cursorColor = SandTVColors.Accent,
                            focusedTextColor = SandTVColors.TextPrimary,
                            unfocusedTextColor = SandTVColors.TextPrimary,
                            focusedLabelColor = SandTVColors.Accent,
                            unfocusedLabelColor = SandTVColors.TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Error message
                    loginError?.let { error ->
                        Text(
                            text = error,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Success message
                    if (loginSuccess) {
                        Text(
                            text = "✓ Login effettuato con successo!",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Login button
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                loginError = "Inserisci username e password"
                                return@Button
                            }
                            
                            isLoggingIn = true
                            loginError = null
                            coroutineScope.launch {
                                val result = openSubtitlesRepo.login(username, password)
                                isLoggingIn = false
                                
                                when (result) {
                                    is it.sandtv.app.data.repository.OpenSubtitlesRepository.AuthResult.Success -> {
                                        loginSuccess = true
                                        isAuthenticated = true
                                        currentUsername = openSubtitlesRepo.getUsername()
                                        remainingDownloads = openSubtitlesRepo.getRemainingDownloads()
                                        password = "" // Clear password
                                    }
                                    is it.sandtv.app.data.repository.OpenSubtitlesRepository.AuthResult.Error -> {
                                        loginError = result.message
                                    }
                                }
                            }
                        },
                        enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Accedi")
                        }
                    }
                    
                    // Register link
                    Text(
                        text = "Non hai un account? Registrati su opensubtitles.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = SandTVColors.Accent
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Preferred language
            SettingsDropdown(
                label = "Lingua sottotitoli predefinita",
                value = preferredLanguage,
                options = listOf(
                    "it" to "Italiano",
                    "en" to "English",
                    "es" to "Español",
                    "fr" to "Français",
                    "de" to "Deutsch",
                    "pt" to "Português"
                ),
                onValueChange = {
                    preferredLanguage = it
                    coroutineScope.launch { userPreferences.setSubtitleLanguage(it) }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Info text
            Text(
                text = "I sottotitoli vengono cercati automaticamente quando premi l'icona sottotitoli nel player.",
                style = MaterialTheme.typography.bodySmall,
                color = SandTVColors.TextTertiary
            )
        }
    }
}

@Composable
private fun EpgSettings(
    userPreferences: UserPreferences,
    epgRepository: it.sandtv.app.data.repository.EpgRepository,
    playlistDao: PlaylistDao,
    contentFocusRequester: FocusRequester? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    var updateMode by remember { mutableStateOf("manual") }
    var updateInterval by remember { mutableStateOf("12h") }
    var timezone by remember { mutableStateOf("auto") }
    
    // Loading and status states
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    
    // Load current settings
    LaunchedEffect(Unit) {
        updateMode = userPreferences.getEpgUpdateMode()
        updateInterval = userPreferences.getEpgUpdateInterval()
    }
    
    SettingsSection(title = "Guida TV (EPG)") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Update Mode
            SettingsDropdown(
                label = "Modalità aggiornamento",
                value = updateMode,
                options = listOf(
                    "manual" to "Manuale",
                    "auto" to "Automatico"
                ),
                onValueChange = {
                    updateMode = it
                    coroutineScope.launch { userPreferences.setEpgUpdateMode(it) }
                }
            )
            
            // Update Interval (only if auto)
            if (updateMode == "auto") {
                SettingsDropdown(
                    label = "Frequenza aggiornamento",
                    value = updateInterval,
                    options = listOf(
                        "startup" to "All'avvio",
                        "3h" to "Ogni 3 ore",
                        "6h" to "Ogni 6 ore",
                        "12h" to "Ogni 12 ore",
                        "24h" to "Ogni 24 ore",
                        "3d" to "Ogni 3 giorni",
                        "weekly" to "Settimanale"
                    ),
                    onValueChange = {
                        updateInterval = it
                        coroutineScope.launch { userPreferences.setEpgUpdateInterval(it) }
                    }
                )
            }
            
            // Timezone
            SettingsDropdown(
                label = "Fuso orario EPG",
                value = timezone,
                options = listOf(
                    "auto" to "Automatico (sistema)",
                    "UTC" to "UTC",
                    "Europe/Rome" to "Roma (CET/CEST)",
                    "Europe/London" to "Londra (GMT/BST)",
                    "Europe/Paris" to "Parigi (CET/CEST)",
                    "Europe/Berlin" to "Berlino (CET/CEST)",
                    "America/New_York" to "New York (EST/EDT)",
                    "America/Los_Angeles" to "Los Angeles (PST/PDT)"
                ),
                onValueChange = { timezone = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status message
            refreshStatus?.let { status ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SandTVColors.BackgroundTertiary)
                        .padding(12.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = SandTVColors.Accent
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandTVColors.TextSecondary
                    )
                }
            }
            
            // Error message
            refreshError?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF331111))
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF8A80)
                    )
                }
            }
            
            // Manual refresh button
            Button(
                onClick = {
                    if (!isRefreshing) {
                        coroutineScope.launch {
                            isRefreshing = true
                            refreshStatus = "Aggiornamento EPG in corso..."
                            refreshError = null
                            
                            try {
                                // Get all playlists
                                val playlists = playlistDao.getAllPlaylists().first()
                                var successCount = 0
                                
                                for (playlist in playlists) {
                                    try {
                                        if (playlist.type == "xtream" && 
                                            !playlist.username.isNullOrEmpty() && 
                                            !playlist.password.isNullOrEmpty()) {
                                            
                                            refreshStatus = "Caricamento EPG: ${playlist.name}..."
                                            
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                epgRepository.loadEpgFromXtream(
                                                    baseUrl = playlist.url,
                                                    username = playlist.username,
                                                    password = playlist.password
                                                )
                                            }
                                            successCount++
                                        } else if (!playlist.epgUrl.isNullOrEmpty()) {
                                            refreshStatus = "Caricamento EPG da URL..."
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                epgRepository.loadEpgFromUrl(playlist.epgUrl)
                                            }
                                            successCount++
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("EpgSettings", "Error loading EPG for ${playlist.name}", e)
                                    }
                                }
                                
                                // Update last refresh time
                                userPreferences.setEpgLastUpdate(System.currentTimeMillis())
                                
                                refreshStatus = "EPG aggiornato con successo! ($successCount playlist)"
                                
                                // Clear success message after 5 seconds
                                kotlinx.coroutines.delay(5000)
                                refreshStatus = null
                                
                            } catch (e: Exception) {
                                android.util.Log.e("EpgSettings", "Error refreshing EPG", e)
                                refreshStatus = null
                                refreshError = "Errore: ${e.message ?: "Errore sconosciuto"}"
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }
                },
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRefreshing) SandTVColors.BackgroundTertiary else SandTVColors.Accent,
                    disabledContainerColor = SandTVColors.BackgroundTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SandTVColors.TextSecondary
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRefreshing) "Aggiornamento in corso..." else "Aggiorna EPG adesso")
            }
        }
    }
}

@Composable
private fun AppearanceSettings(userPreferences: UserPreferences) {
    val currentAccentId by userPreferences.getAccentColorFlow().collectAsState(initial = "violet")
    val scope = rememberCoroutineScope()
    
    SettingsSection(title = "Aspetto") {
        Text(
            text = "Colore Accento",
            style = MaterialTheme.typography.titleMedium,
            color = SandTVColors.TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            AccentColor.entries.forEach { accentOption ->
                // Check if selected (handle null as violet/default)
                val isSelected = (currentAccentId == accentOption.id) || 
                               (currentAccentId == null && accentOption.id == "violet")
                
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                
                // Color circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentOption.primary)
                        .border(
                            width = if (isFocused) 4.dp else if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) Color.White else if (isFocused) SandTVColors.Accent else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null // Handle visual state manually
                        ) {
                            // Update immediately for responsiveness
                            SandTVColors.updateAccent(accentOption)
                            
                            // Save preference
                            scope.launch {
                                userPreferences.setAccentColor(accentOption.id)
                            }
                        }
                        .focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center
                ) {
                   if (isSelected) {
                       Icon(
                           imageVector = Icons.Default.Check,
                           contentDescription = "Selezionato",
                           tint = Color.White,
                           modifier = Modifier.size(24.dp)
                       )
                   }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        SettingsInfo(text = "Scegli il colore principale per l'interfaccia. La modifica viene applicata immediatamente a tutta l'app.")
    }
}

@Composable
private fun StorageSettings() {
    SettingsSection(title = "Archiviazione") {
        SettingsInfo(text = "Le impostazioni di archiviazione saranno disponibili qui.")
    }
}

@Composable
private fun UpdateSettings(updateManager: it.sandtv.app.update.AppUpdateManager, contentFocusRequester: FocusRequester? = null) {
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<it.sandtv.app.update.UpdateInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var noUpdateMessage by remember { mutableStateOf<String?>(null) }
    
    val downloadState by updateManager.downloadState.collectAsState()
    
    val currentVersion = remember { updateManager.getInstalledVersionName() }
    val currentVersionCode = remember { updateManager.getInstalledVersionCode() }
    
    // Focus requesters for smart focus management
    val checkButtonFocusRequester = remember { FocusRequester() }
    val downloadButtonFocusRequester = remember { FocusRequester() }
    val installButtonFocusRequester = remember { FocusRequester() }
    val retryButtonFocusRequester = remember { FocusRequester() }
    
    // Smart focus: move focus to appropriate button when state changes
    LaunchedEffect(downloadState, updateInfo) {
        kotlinx.coroutines.delay(100) // Small delay to let UI update
        try {
            when {
                downloadState is it.sandtv.app.update.DownloadState.Downloaded -> {
                    installButtonFocusRequester.requestFocus()
                }
                downloadState is it.sandtv.app.update.DownloadState.Failed -> {
                    retryButtonFocusRequester.requestFocus()
                }
                downloadState is it.sandtv.app.update.DownloadState.Idle && updateInfo != null -> {
                    downloadButtonFocusRequester.requestFocus()
                }
            }
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }
    
    // Focus on download button when update is found
    LaunchedEffect(updateInfo) {
        if (updateInfo != null) {
            kotlinx.coroutines.delay(200)
            try {
                downloadButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    SettingsSection(title = "Aggiornamenti") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Current version info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SandTVColors.BackgroundSecondary)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Versione corrente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SandTVColors.TextSecondary
                    )
                    Text(
                        text = "$currentVersion (build $currentVersionCode)",
                        style = MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SandTVColors.Accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Check for updates button
            Button(
                onClick = {
                    isChecking = true
                    errorMessage = null
                    noUpdateMessage = null
                    updateInfo = null
                    coroutineScope.launch {
                        when (val result = updateManager.checkForUpdate()) {
                            is it.sandtv.app.update.UpdateCheckResult.UpdateAvailable -> {
                                updateInfo = result.info
                            }
                            is it.sandtv.app.update.UpdateCheckResult.NoUpdateAvailable -> {
                                noUpdateMessage = "Sei già alla versione più recente!"
                            }
                            is it.sandtv.app.update.UpdateCheckResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                        isChecking = false
                    }
                },
                enabled = !isChecking && downloadState !is it.sandtv.app.update.DownloadState.Downloading,
                colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(checkButtonFocusRequester)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Controllo in corso...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Controlla aggiornamenti")
                }
            }
            
            // No update available message
            noUpdateMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF10B981)
                    )
                }
            }
            
            // Error message
            errorMessage?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Red.copy(alpha = 0.15f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red
                    )
                }
            }
            
            // Update available card
            updateInfo?.let { info ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SandTVColors.Accent.copy(alpha = 0.15f))
                        .border(1.dp, SandTVColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = SandTVColors.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Nuova versione disponibile!",
                                style = MaterialTheme.typography.titleMedium,
                                color = SandTVColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Versione ${info.versionName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandTVColors.TextSecondary
                            )
                        }
                    }
                    
                    if (info.changelog.isNotEmpty()) {
                        Text(
                            text = "Novità:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SandTVColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = SandTVColors.TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Download/Install button based on state
                    when (val state = downloadState) {
                        is it.sandtv.app.update.DownloadState.Idle -> {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateManager.downloadUpdate()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(downloadButtonFocusRequester)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scarica aggiornamento")
                            }
                        }
                        is it.sandtv.app.update.DownloadState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Download in corso...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SandTVColors.TextPrimary
                                    )
                                    Text(
                                        text = "${state.progress}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SandTVColors.Accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = state.progress / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = SandTVColors.Accent,
                                    trackColor = SandTVColors.BackgroundTertiary
                                )
                            }
                        }
                        is it.sandtv.app.update.DownloadState.Downloaded -> {
                            Button(
                                onClick = { updateManager.installUpdate() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(installButtonFocusRequester)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installa aggiornamento")
                            }
                        }
                        is it.sandtv.app.update.DownloadState.Failed -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Download fallito: ${state.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red
                                )
                                Button(
                                    onClick = {
                                        updateManager.resetState()
                                        coroutineScope.launch {
                                            updateManager.downloadUpdate()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SandTVColors.Accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(retryButtonFocusRequester)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Riprova download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    SettingsSection(title = "Informazioni") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "SandTV",
                style = MaterialTheme.typography.headlineMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Versione ${it.sandtv.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = SandTVColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "App per Android TV per streaming IPTV con supporto EPG, film e serie TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextTertiary
            )
        }
    }
}

// ============ Helper Composables ============

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = SandTVColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun SettingsInfo(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = SandTVColors.TextSecondary
    )
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = options.find { it.first == value }?.second ?: value
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        animationSpec = tween(150),
        label = "dropdownBorder"
    )
    
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SandTVColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                    .background(SandTVColors.SurfaceDark)
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { expanded = true }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayValue,
                    color = SandTVColors.TextPrimary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, display) ->
                    DropdownMenuItem(
                        text = { Text(display) },
                        onClick = {
                            onValueChange(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        animationSpec = tween(150),
        label = "switchBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(SandTVColors.SurfaceDark)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SandTVColors.Accent,
                checkedTrackColor = SandTVColors.Accent.copy(alpha = 0.5f)
            )
        )
    }
}
