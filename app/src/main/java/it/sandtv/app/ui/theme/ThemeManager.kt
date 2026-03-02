package it.sandtv.app.ui.theme

import android.content.Context
import androidx.core.content.ContextCompat
import it.sandtv.app.R
import it.sandtv.app.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Theme Manager for handling app themes and accent colors
 * Note: For Android TV with Compose, we always use dark mode
 */
@Singleton
class ThemeManager @Inject constructor(
    private val userPreferences: UserPreferences
) {
    
    companion object {
        // Accent color options
        val ACCENT_COLORS = listOf(
            AccentColor("purple", "#8B5CF6", R.color.accent),
            AccentColor("blue", "#3B82F6", R.color.accent_blue),
            AccentColor("green", "#10B981", R.color.accent_green),
            AccentColor("red", "#EF4444", R.color.accent_red),
            AccentColor("orange", "#F59E0B", R.color.accent_orange),
            AccentColor("pink", "#EC4899", R.color.accent_pink)
        )
    }
    
    data class AccentColor(
        val id: String,
        val hex: String,
        val colorRes: Int
    )
    
    /**
     * Initialize theme based on preferences
     * For TV: always dark mode, no dynamic switching needed
     */
    fun init(scope: CoroutineScope) {
        // For Compose TV app, theme is handled by SandTVTheme composable
        // No AppCompat delegate needed
    }
    
    /**
     * Apply theme mode - no-op for pure Compose TV app
     * Theme is always dark mode, controlled by SandTVTheme
     */
    fun applyThemeMode(mode: String) {
        // No-op: Compose handles theming via SandTVTheme
        // TV apps typically always use dark mode
    }
    
    /**
     * Set theme mode
     */
    suspend fun setThemeMode(mode: String) {
        userPreferences.setThemeMode(mode)
        // Theme is applied through Compose, no legacy delegate needed
    }
    
    /**
     * Set accent color
     */
    suspend fun setAccentColor(colorId: String) {
        userPreferences.setAccentColor(colorId)
    }
    
    /**
     * Get current accent color resource
     */
    suspend fun getAccentColorRes(): Int {
        val colorId = userPreferences.getAccentColorFlow().toString()
        return ACCENT_COLORS.find { it.id == colorId }?.colorRes ?: R.color.accent
    }
    
    /**
     * Get accent color for views
     */
    fun getAccentColor(context: Context, colorId: String?): Int {
        val accent = ACCENT_COLORS.find { it.id == colorId } ?: ACCENT_COLORS[0]
        return ContextCompat.getColor(context, accent.colorRes)
    }
}
