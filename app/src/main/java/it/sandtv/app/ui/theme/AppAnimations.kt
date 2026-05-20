package it.sandtv.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * Centralized animation constants for consistent UX across the app
 * Premium Netflix-level animation system
 */
object AppAnimations {
    
    // ============== Spring Animations ==============
    
    /** Bouncy spring for playful elements (tabs, indicators) */
    val SpringBouncy = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = 300f
    )
    
    /** Smooth spring for subtle animations (focus, hover) */
    val SpringSmooth = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )
    
    /** Quick spring for responsive feedback */
    val SpringQuick = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    /** Gentle spring for carousel items */
    val SpringGentle = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    )
    
    /** Card focus spring - fast and snappy with subtle overshoot */
    val SpringCardFocus = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 500f
    )
    
    /** Button press spring - instant response */
    val SpringButtonPress = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 600f
    )
    
    /** Glow spring - smooth fade for glow effects */
    val SpringGlow = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 350f
    )
    
    // ============== Duration Constants ==============
    
    /** Fast fade duration (ms) */
    const val FadeMs = 250
    
    /** Standard slide duration (ms) */
    const val SlideMs = 300
    
    /** Stagger delay between items (ms) */
    const val StaggerMs = 50
    
    /** Activity transition duration (ms) */
    const val ActivityTransitionMs = 350
    
    /** Hero crossfade duration (ms) */
    const val HeroCrossfadeMs = 500
    
    /** Card info reveal duration (ms) */
    const val CardInfoRevealMs = 200
    
    // ============== Scale Values ==============
    
    /** Card focus scale */
    const val CardFocusScale = 1.08f
    
    /** Button focus scale */
    const val ButtonFocusScale = 1.05f
    
    /** Icon button focus scale */
    const val IconButtonFocusScale = 1.15f
    
    /** Button press scale */
    const val ButtonPressScale = 0.98f
    
    /** Grid item focus scale */
    const val GridItemFocusScale = 1.05f
    
    // ============== Glow Values ==============
    
    /** Glow shadow elevation when focused */
    const val FocusGlowElevation = 16f
    
    /** Glow shadow elevation when unfocused */
    const val UnfocusedGlowElevation = 0f
    
    /** Glow alpha when focused */
    const val FocusGlowAlpha = 0.4f
    
    // ============== Pre-built Enter/Exit Specs ==============
    
    /** Fade in animation */
    val fadeInSpec = fadeIn(tween(FadeMs))
    
    /** Fade out animation */
    val fadeOutSpec = fadeOut(tween(FadeMs))
    
    /** Slide in from bottom with fade */
    fun slideInFromBottom(delayMs: Int = 0) = fadeIn(tween(SlideMs, delayMillis = delayMs)) + 
        slideInVertically(tween(SlideMs, delayMillis = delayMs)) { it / 4 }
    
    /** Slide out to bottom with fade */
    val slideOutToBottom = fadeOut(tween(SlideMs)) + 
        slideOutVertically(tween(SlideMs)) { it / 4 }
}
