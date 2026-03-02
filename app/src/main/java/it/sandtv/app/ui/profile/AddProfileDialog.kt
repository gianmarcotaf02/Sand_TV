package it.sandtv.app.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import it.sandtv.app.data.database.entity.Playlist
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Compose Dialog for adding a new profile with playlist selection
 */
@Composable
fun AddProfileDialog(
    playlists: List<Playlist>,
    onProfileCreated: (name: String, playlistId: Long?, avatarIndex: Int, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    val avatarColors = listOf(
        "#8B5CF6", // Purple
        "#3B82F6", // Blue
        "#10B981", // Green
        "#EF4444", // Red
        "#F59E0B", // Orange
        "#EC4899"  // Pink
    )
    
    var profileName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedPlaylistIndex by remember { mutableIntStateOf(0) }
    
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(450.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundSecondary)
                .padding(24.dp)
        ) {
            // Title
            Text(
                text = "Aggiungi profilo",
                style = MaterialTheme.typography.headlineSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Name input
            Text(
                text = "Nome",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SandTVColors.BackgroundTertiary)
                    .padding(horizontal = 16.dp)
            ) {
                BasicTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .wrapContentHeight(Alignment.CenterVertically),
                    textStyle = TextStyle(
                        color = SandTVColors.TextPrimary,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(SandTVColors.Accent),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (profileName.isEmpty()) {
                                Text(
                                    text = "Inserisci nome...",
                                    color = SandTVColors.TextTertiary,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Color selection
            Text(
                text = "Colore avatar",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(avatarColors) { index, color ->
                    ColorCircle(
                        color = color,
                        isSelected = index == selectedColorIndex,
                        onClick = { selectedColorIndex = index }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Playlist selection
            if (playlists.isNotEmpty()) {
                Text(
                    text = "Playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        PlaylistChip(
                            name = "Nessuna",
                            isSelected = selectedPlaylistIndex == 0,
                            onClick = { selectedPlaylistIndex = 0 }
                        )
                    }
                    itemsIndexed(playlists) { index, playlist ->
                        PlaylistChip(
                            name = playlist.name,
                            isSelected = selectedPlaylistIndex == index + 1,
                            onClick = { selectedPlaylistIndex = index + 1 }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Annulla",
                        color = SandTVColors.TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Button(
                    onClick = {
                        if (profileName.isNotBlank()) {
                            val playlistId = if (selectedPlaylistIndex == 0) null 
                                else playlists.getOrNull(selectedPlaylistIndex - 1)?.id
                            onProfileCreated(
                                profileName.trim(),
                                playlistId,
                                selectedColorIndex,
                                avatarColors[selectedColorIndex]
                            )
                        }
                    },
                    enabled = profileName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SandTVColors.Accent,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "Crea")
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale = if (isSelected) 1.2f else 1f
    val borderColor = if (isFocused) Color.White else Color.Transparent
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .border(3.dp, borderColor, CircleShape)
            .background(Color(android.graphics.Color.parseColor(color)))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun PlaylistChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.BackgroundTertiary
            else -> SandTVColors.BackgroundTertiary.copy(alpha = 0.5f)
        },
        label = "chipBg"
    )
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) Color.White else SandTVColors.TextPrimary
        )
    }
}
