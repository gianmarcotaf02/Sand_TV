package it.sandtv.app.ui.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import it.sandtv.app.ui.theme.SandTVColors

/**
 * Loading skeleton with shimmer effect for TV Home Screen
 * Replaces simple spinner with "Netflix-style" placeholder UI
 */
@Composable
fun SkeletonLoader(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    // Shimmer animation - sweeping highlight from left to right
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    // Gradient brush for the shimmer effect
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            SandTVColors.BackgroundSecondary,
            SandTVColors.BackgroundTertiary.copy(alpha = 0.8f),
            SandTVColors.BackgroundSecondary
        ),
        start = Offset(shimmerOffset * 1000f, 0f),
        end = Offset((shimmerOffset + 1f) * 1000f, 0f)
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
            // Padding to match TvHomeScreen content padding? 
            // TvHomeScreen has contentPadding(top = 80.dp), matching that here.
            .padding(top = 80.dp) 
    ) {
        // Hero Banner Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp) // Matching HeroBanner height in TvHomeScreen
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Carousel Row Placeholders
        repeat(3) { // Show 3 dummy rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Row Title
                Box(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .width(180.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Horizontal List of Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(6) { // 6 cards per row
                        Box(
                            modifier = Modifier
                                .width(130.dp) // Matching PosterCard width
                                .height(195.dp) // Matching PosterCard height
                                .clip(RoundedCornerShape(8.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
