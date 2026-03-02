package it.sandtv.app.ui.home

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
import it.sandtv.app.data.tmdb.TMDBService
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Compose Dialog for selecting content source when multiple are available
 */
@Composable
fun SourceSelectionDialog(
    sources: List<TMDBService.ContentSource>,
    onSourceSelected: (TMDBService.ContentSource) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SandTVColors.BackgroundSecondary)
                .padding(24.dp)
        ) {
            // Title
            Text(
                text = "Seleziona sorgente",
                style = MaterialTheme.typography.headlineSmall,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sources list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(sources) { source ->
                    SourceItem(
                        source = source,
                        onClick = { onSourceSelected(source) }
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
private fun SourceItem(
    source: TMDBService.ContentSource,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent.copy(alpha = 0.2f) else SandTVColors.BackgroundTertiary,
        label = "sourceBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "sourceBorder"
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = source.category,
            style = MaterialTheme.typography.bodyLarge,
            color = SandTVColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
        
        source.quality?.let { quality ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SandTVColors.Accent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = quality,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
