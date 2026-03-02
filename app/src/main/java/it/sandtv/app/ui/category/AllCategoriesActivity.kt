package it.sandtv.app.ui.category

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import dagger.hilt.android.AndroidEntryPoint
import it.sandtv.app.data.database.dao.MovieDao
import it.sandtv.app.data.database.dao.SeriesDao
import it.sandtv.app.ui.theme.SandTVColors
import it.sandtv.app.ui.theme.SandTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/**
 * Data class for category with item count
 */
data class CategoryInfo(
    val name: String,
    val itemCount: Int
)

/**
 * Activity for displaying all categories in a grid
 */
@AndroidEntryPoint
class AllCategoriesActivity : ComponentActivity() {
    
    @Inject lateinit var movieDao: MovieDao
    @Inject lateinit var seriesDao: SeriesDao
    
    private var contentType: String = "" // "movies" or "series"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        contentType = intent.getStringExtra("contentType") ?: "movies"
        
        setContent {
            SandTVTheme {
                AllCategoriesScreen(
                    contentType = contentType,
                    onCategoryClick = { categoryName ->
                        val categoryType = if (contentType == "movies") "CATEGORY_MOVIE" else "CATEGORY_SERIES"
                        val intent = Intent(this, CategoryActivity::class.java).apply {
                            putExtra("categoryName", categoryName)
                            putExtra("contentType", categoryType)
                        }
                        startActivity(intent)
                    },
                    onBack = { finish() },
                    loadCategories = { loadCategories() }
                )
            }
        }
    }
    
    private suspend fun loadCategories(): List<CategoryInfo> {
        return withContext(Dispatchers.IO) {
            if (contentType == "movies") {
                movieDao.getCategoriesList().map { categoryName ->
                    CategoryInfo(
                        name = categoryName,
                        itemCount = movieDao.getMovieCountByCategory(categoryName)
                    )
                }
            } else {
                seriesDao.getCategoriesList().map { categoryName ->
                    CategoryInfo(
                        name = categoryName,
                        itemCount = seriesDao.getSeriesCountByCategory(categoryName)
                    )
                }
            }
        }
    }
}

/**
 * All Categories Screen
 */
@Composable
private fun AllCategoriesScreen(
    contentType: String,
    onCategoryClick: (String) -> Unit,
    onBack: () -> Unit,
    loadCategories: suspend () -> List<CategoryInfo>
) {
    var categories by remember { mutableStateOf<List<CategoryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        isLoading = true
        categories = loadCategories()
        isLoading = false
    }
    
    val title = if (contentType == "movies") "Categorie Film" else "Categorie Serie TV"
    val itemLabel = if (contentType == "movies") "film" else "serie"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SandTVColors.BackgroundDark)
            .padding(horizontal = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Back button
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                tint = SandTVColors.TextPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() }
            )
            
            // Icon (4 squares)
            FourSquaresIcon(
                modifier = Modifier.size(32.dp)
            )
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Count
            Text(
                text = "${categories.size} categorie",
                style = MaterialTheme.typography.bodyMedium,
                color = SandTVColors.TextSecondary
            )
        }
        
        // Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SandTVColors.Accent)
            }
        } else {
            TvLazyVerticalGrid(
                columns = TvGridCells.Adaptive(minSize = 200.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories, key = { it.name }) { category ->
                    CategoryCard(
                        category = category,
                        itemLabel = itemLabel,
                        isSeries = contentType == "series",
                        onClick = { onCategoryClick(category.name) }
                    )
                }
            }
        }
    }
}

/**
 * Category card with background image or gradient fallback
 */
@Composable
private fun CategoryCard(
    category: CategoryInfo,
    itemLabel: String,
    isSeries: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "categoryScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else Color.Transparent,
        label = "categoryBorder"
    )
    
    // Generate unique gradient colors for fallback
    val gradientColors = remember(category.name) {
        generateGradientColors(category.name)
    }

    // Try to get background image resource ID
    val backgroundImageRes = remember(category.name, isSeries) {
        getCategoryBackgroundImage(context, category.name, isSeries)
    }

    // Dark gradient overlay for text readability (increased opacity)
    val scrimColors = listOf(
        Color.Black.copy(alpha = 0f),
        Color.Black.copy(alpha = 0.85f)
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .height(150.dp)  // Increased from 120dp
            .clip(RoundedCornerShape(16.dp))
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        // Background: Image or gradient fallback
        if (backgroundImageRes != null) {
            // Background image - fills entire card with rounded corners
            AsyncImage(
                model = backgroundImageRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = gradientColors,
                            start = Offset.Zero,
                            end = Offset.Infinite
                        )
                    )
            )
        }
        
        // Dark gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = scrimColors
                    )
                )
        )

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${category.itemCount} $itemLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Four squares icon (grid icon)
 */
@Composable
fun FourSquaresIcon(
    modifier: Modifier = Modifier,
    color: Color = SandTVColors.Accent
) {
    Canvas(modifier = modifier) {
        val squareSize = size.minDimension / 2.5f
        val gap = size.minDimension / 10f
        val cornerRadius = CornerRadius(squareSize / 4, squareSize / 4)
        
        // Top-left
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        // Top-right
        drawRoundRect(
            color = color.copy(alpha = 0.7f),
            topLeft = Offset(squareSize + gap, 0f),
            size = Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        // Bottom-left
        drawRoundRect(
            color = color.copy(alpha = 0.7f),
            topLeft = Offset(0f, squareSize + gap),
            size = Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        // Bottom-right
        drawRoundRect(
            color = color.copy(alpha = 0.5f),
            topLeft = Offset(squareSize + gap, squareSize + gap),
            size = Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
    }
}

/**
 * Generate unique gradient colors based on category name hash
 */
private fun generateGradientColors(categoryName: String): List<Color> {
    val hash = abs(categoryName.hashCode())
    
    // Generate hue from hash (0-360)
    val hue = (hash % 360).toFloat()
    
    // Create two colors with same hue but different saturation/lightness
    val color1 = Color.hsl(hue, 0.7f, 0.4f)
    val color2 = Color.hsl((hue + 30) % 360, 0.8f, 0.25f)
    
    return listOf(color1, color2)
}

/**
 * Get category background image resource ID
 * Returns null if image doesn't exist
 * For series: looks for category_bg_series_[name].jpg
 * For movies: looks for category_bg_[name].jpg
 */
private fun getCategoryBackgroundImage(context: android.content.Context, categoryName: String, isSeries: Boolean = false): Int? {
    // Sanitize category name to match file naming convention
    val sanitizedName = categoryName.lowercase()
        .replace(" ", "_")
        .replace("&", "and")
        .replace("à", "a").replace("è", "e").replace("é", "e")
        .replace("ì", "i").replace("ò", "o").replace("ù", "u")
        .filter { it.isLetterOrDigit() || it == '_' }
    
    // Add series prefix for TV series
    val resourceName = if (isSeries) {
        "category_bg_series_$sanitizedName"
    } else {
        "category_bg_$sanitizedName"
    }
    
    // Try to get resource ID
    val resourceId = context.resources.getIdentifier(
        resourceName,
        "drawable",
        context.packageName
    )
    
    return if (resourceId != 0) resourceId else null
}
