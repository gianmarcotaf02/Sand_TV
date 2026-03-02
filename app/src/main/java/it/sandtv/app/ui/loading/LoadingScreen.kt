package it.sandtv.app.ui.loading

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sandtv.app.R
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.delay

// Fun phrases that rotate during loading
private val loadingPhrases = listOf(
    "Prepariamo il popcorn... 🍿",
    "Scopriamo cosa c'è in onda...",
    "Sintonizzazione in corso...",
    "Accendiamo le luci del cinema...",
    "Controlliamo la programmazione...",
    "Un momento, quasi pronti...",
    "Carichiamo i contenuti migliori...",
    "Sistema di intrattenimento attivo...",
    "Prepariamo il tuo cinema personale...",
    "Connettiamo ai canali..."
)

/**
 * Loading Screen - Modern design with fluid animations
 * Premium streaming app style with rotating phrases
 */
@Composable
fun LoadingScreen(
    statusText: String,
    detailText: String,
    progress: Int,
    showProgressBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Rotating phrase state
    var currentPhraseIndex by remember { mutableIntStateOf(0) }
    
    // Cycle through phrases every 3 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentPhraseIndex = (currentPhraseIndex + 1) % loadingPhrases.size
        }
    }
    
    // Phrase fade animation
    val phraseAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "phraseAlpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
    ) {
        // Animated background with moving gradient
        AnimatedGradientBackground()
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo with glow
            AnimatedLogo()
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Bouncing dots loader
            BouncingDotsLoader()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Rotating fun phrase
            Text(
                text = loadingPhrases[currentPhraseIndex],
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = SandTVColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(phraseAlpha)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status/detail text (actual progress info)
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandTVColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            
            if (detailText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SandTVColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
            
            // Progress bar (optional)
            if (showProgressBar && progress > 0) {
                Spacer(modifier = Modifier.height(40.dp))
                
                GlassProgressBar(progress = progress)
            }
        }
    }
}

/**
 * Animated gradient background with subtle movement
 */
@Composable
private fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bgGradient")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffsetX"
    )
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffsetY"
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main glow orb - moves subtly
        Box(
            modifier = Modifier
                .size(700.dp)
                .offset(x = offsetX.dp, y = offsetY.dp)
                .align(Alignment.Center)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = 0.15f),
                            SandTVColors.Accent.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Secondary accent orb
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-offsetX * 0.5f).dp, y = (-offsetY * 0.7f).dp)
                .align(Alignment.TopEnd)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

/**
 * Animated logo with pulse and glow effect
 */
@Composable
private fun AnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logoAnim")
    
    // Subtle scale pulse
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    
    // Glow intensity
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(contentAlignment = Alignment.Center) {
        // Glow behind logo
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scale * 1.2f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SandTVColors.Accent.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Logo container
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = SandTVColors.Accent.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(24.dp))
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
}

/**
 * Bouncing dots loader - modern streaming app style
 */
@Composable
private fun BouncingDotsLoader() {
    val dotCount = 3
    val dotSize = 12.dp
    val dotSpacing = 8.dp
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            BouncingDot(
                delay = index * 150,
                size = dotSize
            )
        }
    }
}

@Composable
private fun BouncingDot(delay: Int, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot$delay")
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 0 using EaseInOut
                -12f at 200 using EaseInOut
                0f at 400 using EaseInOut
                0f at 600
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delay)
        ),
        label = "dotOffset"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                1f at 0 using EaseInOut
                1.2f at 200 using EaseInOut
                1f at 400 using EaseInOut
                1f at 600
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delay)
        ),
        label = "dotScale"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                translationY = offsetY
                scaleX = scale
                scaleY = scale
            }
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SandTVColors.Accent,
                        SandTVColors.Accent.copy(alpha = 0.7f)
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Glass-morphism style progress bar with glow
 */
@Composable
private fun GlassProgressBar(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic),
        label = "progressAnimation"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = modifier
                .width(350.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SandTVColors.BackgroundTertiary.copy(alpha = 0.5f))
        ) {
            // Glow effect behind progress
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                SandTVColors.Accent.copy(alpha = 0.3f),
                                SandTVColors.Accent.copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            // Main progress bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                SandTVColors.Accent,
                                SandTVColors.AccentLight
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Percentage
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = SandTVColors.Accent
        )
    }
}

// ============ Loading State Data Class ============

/**
 * Loading state holder
 */
data class LoadingState(
    val status: String = "",
    val detail: String = "",
    val progress: Int = 0,
    val showProgress: Boolean = false,
    val isComplete: Boolean = false,
    val hasError: Boolean = false
)

// ============ Preview ============

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION
)
@Composable
private fun LoadingScreenPreview() {
    SandTVTheme {
        LoadingScreen(
            statusText = "Sincronizzazione playlist...",
            detailText = "IPTV Italia",
            progress = 65,
            showProgressBar = true
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 1280,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION
)
@Composable
private fun LoadingScreenSimplePreview() {
    SandTVTheme {
        LoadingScreen(
            statusText = "",
            detailText = "",
            progress = 0,
            showProgressBar = false
        )
    }
}
