package it.sandtv.app.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.TvLazyListScope
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import it.sandtv.app.ui.home.CarouselItem
import it.sandtv.app.ui.home.CarouselRow
import it.sandtv.app.ui.components.CategoryCard
import it.sandtv.app.ui.theme.SandTVColors

/**
 * TV-optimized carousel row using TvLazyRow
 * Replaces ListRowPresenter with proper focus handling for D-pad navigation
 * 
 * Features:
 * - Uses TvLazyRow for horizontal scrolling
 * - FocusRestorer to remember last focused item when returning to row
 * - Proper focus traversal between rows
 * - onUpPressed callback for scrolling to hero when UP pressed
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvCarouselRow(
    row: CarouselRow,
    onItemClick: (CarouselItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onUpPressed: (() -> Unit)? = null,  // Called when UP pressed on this row
    onFocusChanged: ((Boolean) -> Unit)? = null  // Called when row gains/loses focus
) {
    val listState = rememberTvLazyListState()
    
    // Track if any item in this row is focused
    var isRowFocused by remember { mutableStateOf(false) }
    
    // Report focus changes to parent
    LaunchedEffect(isRowFocused) {
        onFocusChanged?.invoke(isRowFocused)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .onFocusChanged { focusState ->
                isRowFocused = focusState.hasFocus
            }
    ) {
        // Row header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = SandTVColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            if (row.showSeeAll) {
                TvSeeAllButton(onClick = onSeeAllClick)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Horizontal carousel with TvLazyRow
        // Note: Removed focusRestorer() which causes crashes when items are recycled
        // The crash: "Cannot read CompositionLocal because the Modifier node is not currently attached"
        // This is a known Compose TV bug. Focus state is now managed by TvLazyListState.
        TvLazyRow(
            state = listState,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            pivotOffsets = androidx.tv.foundation.PivotOffsets(parentFraction = 0.1f),
            modifier = Modifier
                .focusRequester(focusRequester)
        ) {
            items(
                items = row.items,
                key = { "${it.contentType}_${it.id}" }
            ) { item ->
                // Render CategoryCard for category types, TvContentCard for content
                when {
                    item.contentType.startsWith("CATEGORY_") -> {
                        CategoryCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                    else -> {
                        TvContentCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
            
            // "Vedi tutto" as last item in carousel for D-pad accessibility
            if (row.showSeeAll) {
                item(key = "see_all_${row.title}") {
                    TvSeeAllCard(onClick = onSeeAllClick)
                }
            }
        }
    }
}

/**
 * See all button for row header
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSeeAllButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val textColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextTertiary,
        label = "seeAllColor"
    )
    
    Text(
        text = "Vedi tutto →",
        style = MaterialTheme.typography.labelLarge,
        color = textColor,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(8.dp)
    )
}

/**
 * See all card - displayed as last item in carousel for D-pad accessibility
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSeeAllCard(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "seeAllCardScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent else SandTVColors.TextTertiary.copy(alpha = 0.3f),
        label = "seeAllCardBorder"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.Accent.copy(alpha = 0.2f) else SandTVColors.BackgroundTertiary,
        label = "seeAllCardBg"
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale 
            }
            .width(130.dp)
            .height(195.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && 
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Arrow symbol as text - simpler and always works
            Text(
                text = "→",
                style = MaterialTheme.typography.headlineLarge,
                color = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Vedi tutto",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) SandTVColors.Accent else SandTVColors.TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
