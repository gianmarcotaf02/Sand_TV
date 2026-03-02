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
    
    // ============== Duration Constants ==============
    
    /** Fast fade duration (ms) */
    const val FadeMs = 250
    
    /** Standard slide duration (ms) */
    const val SlideMs = 300
    
    /** Stagger delay between items (ms) */
    const val StaggerMs = 50
    
    /** Activity transition duration (ms) */
    const val ActivityTransitionMs = 350
    
    // ============== Scale Values ==============
    
    /** Card focus scale */
    const val CardFocusScale = 1.05f
    
    /** Button press scale */
    const val ButtonPressScale = 0.95f
    
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
