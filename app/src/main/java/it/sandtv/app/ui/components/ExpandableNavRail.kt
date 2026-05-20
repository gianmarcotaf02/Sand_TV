package it.sandtv.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sandtv.app.ui.MainTab
import it.sandtv.app.R
import it.sandtv.app.ui.theme.SandTVColors

@Composable
fun ExpandableNavRail(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit,
    categories: List<String>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onContentFocusRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 220.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "railWidth"
    )
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.95f else 0.9f,
        animationSpec = tween(200),
        label = "railBgAlpha"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(150),
        label = "railTextAlpha"
    )
    
    val railFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val lastItemFocusRequester = remember { FocusRequester() }
    
    Box(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = backgroundAlpha),
                        Color.Black.copy(alpha = backgroundAlpha * 0.95f)
                    )
                )
            )
            .focusRequester(railFocusRequester)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp)
        ) {
            // Logo area
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "SandTV",
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "SandTV",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(textAlpha)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                HorizontalDivider(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "SandTV",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            // Navigation items
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                NavRailItem(
                    icon = Icons.Default.Movie,
                    label = "Film",
                    isSelected = selectedTab == MainTab.MOVIES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.MOVIES) },
                    modifier = Modifier.focusRequester(firstItemFocusRequester)
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                NavRailItem(
                    icon = Icons.Default.Tv,
                    label = "Serie TV",
                    isSelected = selectedTab == MainTab.SERIES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIES) }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                NavRailItem(
                    icon = Icons.Default.LiveTv,
                    label = "Live",
                    isSelected = selectedTab == MainTab.LIVE,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LIVE) }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                SerieARailItem(
                    isSelected = selectedTab == MainTab.SERIE_A,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIE_A) }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                NavRailItem(
                    icon = if (selectedTab == MainTab.FAVORITES) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Preferiti",
                    isSelected = selectedTab == MainTab.FAVORITES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.FAVORITES) }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                NavRailItem(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "Liste",
                    isSelected = selectedTab == MainTab.LISTS,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LISTS) }
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                NavRailItem(
                    icon = Icons.Default.History,
                    label = "Cronologia",
                    isSelected = selectedTab == MainTab.HISTORY,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.HISTORY) },
                    modifier = Modifier.focusRequester(lastItemFocusRequester)
                )
            }
            
            // Categories section (only when expanded)
            if (isExpanded && categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                HorizontalDivider(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = SandTVColors.Accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "CATEGORIE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = SandTVColors.TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(categories) { category ->
                        val cleanName = category.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
                        CategoryRailItem(
                            name = category,
                            isSelected = selectedCategory == cleanName,
                            isExpanded = isExpanded,
                            onClick = { onCategorySelected(cleanName) }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            
            // Settings at bottom
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                HorizontalDivider(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            NavRailItem(
                icon = Icons.Default.Settings,
                label = "Impostazioni",
                isSelected = false,
                isExpanded = isExpanded,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun SerieARailItem(
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = it.sandtv.app.ui.theme.AppAnimations.SpringCardFocus,
        label = "serieARailScale"
    )
    
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isSelected -> SandTVColors.Accent.copy(alpha = 0.15f)
            isFocused -> SandTVColors.BackgroundTertiary
            else -> Color.Transparent
        },
        label = "serieARailBg"
    )
    
    val textAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "serieARailTextAlpha"
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        if (isExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.serie_a),
                    contentDescription = "Serie A",
                    modifier = Modifier.size(22.dp)
                )
                
                Text(
                    text = "Serie A",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = when {
                        isSelected -> SandTVColors.Accent
                        isFocused -> SandTVColors.TextPrimary
                        else -> SandTVColors.TextSecondary
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        } else {
            Image(
                painter = painterResource(id = R.drawable.serie_a),
                contentDescription = "Serie A",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
