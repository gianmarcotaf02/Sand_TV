package it.sandtv.app.ui.dialog

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.sandtv.app.data.database.entity.StreamProvider
import it.sandtv.app.data.database.entity.StreamQuality
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Compose Dialog for selecting stream provider when multiple sources are available
 */
@Composable
fun StreamSelectionDialog(
    movieTitle: String,
    providers: List<StreamProvider>,
    onProviderSelected: (StreamProvider) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundSecondary)
                .padding(24.dp)
        ) {
            // Title
            Text(
                text = movieTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Seleziona sorgente",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Providers list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(providers) { provider ->
                    ProviderItem(
                        provider = provider,
                        onClick = { onProviderSelected(provider) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Cancel button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Annulla",
                    color = SandTVColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ProviderItem(
    provider: StreamProvider,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent.copy(alpha = 0.2f) else SandTVColors.BackgroundTertiary,
        label = "providerBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "providerBorder"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Provider info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.originalName,
                style = MaterialTheme.typography.bodyLarge,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
            
            // Subtitle
            val subtitleParts = buildList {
                provider.category?.let { add(it) }
                provider.language?.let { add(it) }
                if (provider.isExtended) add("Extended")
            }
            
            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = SandTVColors.TextTertiary
                )
            }
        }
        
        // Quality badge
        val qualityText = buildString {
            append(when (provider.quality) {
                StreamQuality.AUTO -> "Auto"
                StreamQuality.UHD_4K, StreamQuality.UHD -> "4K"
                StreamQuality.FHD, StreamQuality.HD -> "HD"
                StreamQuality.SD -> "SD"
                StreamQuality.UNKNOWN -> ""
            })
            if (provider.isHdr && isNotEmpty()) append(" HDR")
        }
        
        if (qualityText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SandTVColors.Accent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = qualityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Helper class for backward compatibility - wraps Compose dialog in function call
 */
object StreamSelectionDialogHelper {
    private var showDialog: MutableState<Boolean>? = null
    private var dialogData: Triple<String, List<StreamProvider>, (StreamProvider) -> Unit>? = null
    
    /**
     * Get current dialog state for composition
     */
    @Composable
    fun DialogHost() {
        val show = showDialog?.value ?: return
        val data = dialogData ?: return
        
        if (show) {
            StreamSelectionDialog(
                movieTitle = data.first,
                providers = data.second,
                onProviderSelected = { provider ->
                    showDialog?.value = false
                    data.third(provider)
                },
                onDismiss = { showDialog?.value = false }
            )
        }
    }
}
