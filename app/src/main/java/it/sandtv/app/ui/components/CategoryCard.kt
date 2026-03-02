package it.sandtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Category Card for Favorites tab
 * Shows category name, content count, and type-specific icon/color
 * Used for CATEGORY_MOVIE, CATEGORY_SERIES, CATEGORY_LIVE content types
 */
@Composable
fun CategoryCard(
    item: CarouselItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(stiffness = 300f),
        label = "categoryCardScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "categoryCardBorder"
    )
    
    // Determine icon and accent based on content type
    val (icon, accentColor, label) = when (item.contentType) {
        "CATEGORY_MOVIE" -> Triple(Icons.Default.Movie, Color(0xFFE91E63), "film")
        "CATEGORY_SERIES" -> Triple(Icons.Default.Tv, Color(0xFF9C27B0), "serie")
        "CATEGORY_LIVE" -> Triple(Icons.Default.LiveTv, Color(0xFF4CAF50), "canali")
        else -> Triple(Icons.Default.Movie, SandTVColors.Accent, "contenuti")
    }
    
    Box(
        modifier = modifier
            .scale(scale)
            .width(180.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.3f),
                        SandTVColors.CardBackground
                    )
                )
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) accentColor else SandTVColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            
            // Category name and count
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                
                item.contentCount?.let { count ->
                    Text(
                        text = "$count $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // Focus glow effect
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
