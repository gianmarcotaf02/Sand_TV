package it.sandtv.app.ui.profile

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.sandtv.app.R
import it.sandtv.app.data.database.entity.Profile
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme

/**
 * Profile Selection Screen - Netflix/Prime Video style
 * Shows profiles in a horizontal carousel with glow effects
 */
@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    onProfileSelected: (Profile) -> Unit,
    onProfileLongClick: (Profile) -> Unit,
    onAddProfile: () -> Unit,
    preSelectedProfileId: Long? = null,
    modifier: Modifier = Modifier
) {
    var focusedProfile by remember { mutableStateOf<Profile?>(null) }
    
    // Focus requester for pre-selected profile
    val focusRequesters = remember(profiles) { 
        profiles.associate { it.id to FocusRequester() }
    }
    
    // Auto-focus pre-selected profile
    LaunchedEffect(profiles, preSelectedProfileId) {
        if (profiles.isNotEmpty()) {
            val targetId = preSelectedProfileId ?: profiles.first().id
            focusRequesters[targetId]?.requestFocus()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Background (static subtle glow)
        ProfileSelectionBackground()
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            AppLogo()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = stringResource(R.string.who_is_watching),
                style = MaterialTheme.typography.headlineLarge,
                color = SandTVColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Seleziona il tuo profilo",
                style = MaterialTheme.typography.bodyLarge,
                color = SandTVColors.TextTertiary
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Profiles Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onProfileSelected(profile) },
                        onLongClick = { onProfileLongClick(profile) },
                        onFocus = { focusedProfile = profile },
                        focusRequester = focusRequesters[profile.id]
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Add Profile Button
            AddProfileButton(onClick = onAddProfile)
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * Background with subtle glow matching Loading/Splash screen
 */
@Composable
private fun ProfileSelectionBackground() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background glow effect - matching SplashScreen
        Box(
            modifier = Modifier
                .size(500.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SandTVColors.BackgroundSecondary)
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Notify parent of focus
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocus()
        }
    }
    
    // Animate scale on focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        label = "profileCardScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "borderColor"
    )
    
    // Glow inside the card (subtle)
    val cardGlowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.15f else 0f,
        label = "cardGlow"
    )

    val columnModifier = modifier
        .scale(scale)
        .let { mod -> 
            if (focusRequester != null) mod.focusRequester(focusRequester) else mod 
        }
        .focusable(interactionSource = interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
        .padding(12.dp)
    
    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(3.dp, borderColor, RoundedCornerShape(16.dp))
                .background(SandTVColors.CardBackground)
        ) {
            // Inner glow behind avatar
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SandTVColors.Accent.copy(alpha = cardGlowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            
            Image(
                painter = painterResource(getAvatarResource(profile.avatarIndex)),
                contentDescription = profile.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 140.dp)
                .padding(bottom = 8.dp)
        )
    }
}

/**
 * Circular add profile button
 */
@Composable
private fun AddProfileButton(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "addButtonScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextTertiary,
        label = "addButtonBorder"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(64.dp)
            .clip(CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .background(SandTVColors.BackgroundSecondary)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.add_profile),
            tint = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Get avatar drawable resource based on index
 * 0 = Male avatar, 1 = Female avatar
 */
fun getAvatarResource(index: Int): Int {
    return when (index) {
        0 -> R.drawable.avatar_male
        1 -> R.drawable.avatar_female
        else -> R.drawable.avatar_male  // Default to male
    }
}

// ============ Dialogs ============

/**
 * Add Profile Dialog with avatar selection
 */
@Composable
fun AddProfileDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, avatarIndex: Int) -> Unit
) {
    if (!isVisible) return
    
    var profileName by remember { mutableStateOf("") }
    var selectedAvatarIndex by remember { mutableIntStateOf(0) }  // 0 = male, 1 = female
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SandTVColors.BackgroundElevated,
        titleContentColor = SandTVColors.TextPrimary,
        textContentColor = SandTVColors.TextSecondary,
        title = {
            Text(stringResource(R.string.add_profile))
        },
        text = {
            Column {
                // Avatar selection
                Text(
                    text = "Scegli avatar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Male avatar
                    AvatarOption(
                        avatarRes = R.drawable.avatar_male,
                        isSelected = selectedAvatarIndex == 0,
                        onClick = { selectedAvatarIndex = 0 }
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // Female avatar
                    AvatarOption(
                        avatarRes = R.drawable.avatar_female,
                        isSelected = selectedAvatarIndex == 1,
                        onClick = { selectedAvatarIndex = 1 }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Name input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.profile_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        cursorColor = SandTVColors.Accent,
                        focusedLabelColor = SandTVColors.Accent,
                        unfocusedLabelColor = SandTVColors.TextSecondary,
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
                    if (profileName.isNotBlank()) {
                        onConfirm(profileName.trim(), selectedAvatarIndex)
                        profileName = ""
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.Accent
                )
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.TextSecondary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Avatar selection option
 */
@Composable
private fun AvatarOption(
    avatarRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected || isFocused) 1.15f else 1f,
        label = "avatarScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent
            isFocused -> SandTVColors.TextSecondary
            else -> Color.Transparent
        },
        label = "avatarBorder"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .background(SandTVColors.CardBackground)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = if (avatarRes == R.drawable.avatar_male) "Maschio" else "Femmina",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Edit Profile Dialog with avatar selection
 */
@Composable
fun EditProfileDialog(
    isVisible: Boolean,
    profile: Profile?,
    onDismiss: () -> Unit,
    onConfirm: (Profile) -> Unit
) {
    if (!isVisible || profile == null) return
    
    var profileName by remember(profile) { mutableStateOf(profile.name) }
    var selectedAvatarIndex by remember(profile) { mutableIntStateOf(profile.avatarIndex) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SandTVColors.BackgroundElevated,
        titleContentColor = SandTVColors.TextPrimary,
        textContentColor = SandTVColors.TextSecondary,
        title = {
            Text(stringResource(R.string.edit_profile))
        },
        text = {
            Column {
                // Avatar selection
                Text(
                    text = "Scegli avatar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Male avatar
                    AvatarOption(
                        avatarRes = R.drawable.avatar_male,
                        isSelected = selectedAvatarIndex == 0,
                        onClick = { selectedAvatarIndex = 0 }
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // Female avatar
                    AvatarOption(
                        avatarRes = R.drawable.avatar_female,
                        isSelected = selectedAvatarIndex == 1,
                        onClick = { selectedAvatarIndex = 1 }
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Name input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text(stringResource(R.string.profile_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SandTVColors.Accent,
                        unfocusedBorderColor = SandTVColors.TextTertiary,
                        cursorColor = SandTVColors.Accent,
                        focusedLabelColor = SandTVColors.Accent,
                        unfocusedLabelColor = SandTVColors.TextSecondary,
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
                    if (profileName.isNotBlank()) {
                        onConfirm(profile.copy(
                            name = profileName.trim(),
                            avatarIndex = selectedAvatarIndex
                        ))
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.Accent
                )
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.TextSecondary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Delete Profile Confirmation Dialog
 */
@Composable
fun DeleteProfileDialog(
    isVisible: Boolean,
    profile: Profile?,
    onDismiss: () -> Unit,
    onConfirm: (Profile) -> Unit
) {
    if (!isVisible || profile == null) return
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SandTVColors.BackgroundElevated,
        titleContentColor = SandTVColors.TextPrimary,
        textContentColor = SandTVColors.TextSecondary,
        title = {
            Text(stringResource(R.string.delete_profile))
        },
        text = {
            Text(
                text = "Sei sicuro di voler eliminare il profilo \"${profile.name}\"?",
                color = SandTVColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(profile) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.Error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.TextSecondary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Profile Options Bottom Sheet / Dialog
 */
@Composable
fun ProfileOptionsDialog(
    isVisible: Boolean,
    profile: Profile?,
    onDismiss: () -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit
) {
    if (!isVisible || profile == null) return
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SandTVColors.BackgroundElevated,
        titleContentColor = SandTVColors.TextPrimary,
        title = {
            Text(profile.name)
        },
        text = {
            Column {
                // Edit option
                TextButton(
                    onClick = { onEdit(profile) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = SandTVColors.TextPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.edit),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Delete option (only if not default)
                if (!profile.isDefault) {
                    TextButton(
                        onClick = { onDelete(profile) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SandTVColors.Error
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = SandTVColors.TextSecondary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ============ Preview ============

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION
)
@Composable
private fun ProfileSelectionScreenPreview() {
    val sampleProfiles = listOf(
        Profile(id = 1, name = "Principale", avatarIndex = 0, isDefault = true),  // Male
        Profile(id = 2, name = "Gianmarco", avatarIndex = 0),  // Male
        Profile(id = 3, name = "Ospite", avatarIndex = 1)  // Female
    )
    
    SandTVTheme {
        ProfileSelectionScreen(
            profiles = sampleProfiles,
            onProfileSelected = {},
            onProfileLongClick = {},
            onAddProfile = {}
        )
    }
}
