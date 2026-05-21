package it.sandtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onContentFocusRequest: () -> Unit,
    onCollapseRequest: () -> Unit = {},
    onExploreCategoriesClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val railWidth by animateDpAsState(
        targetValue = if (isExpanded) 200.dp else 64.dp,
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
    
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            firstItemFocusRequester.requestFocus()
        }
    }
    
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
            .drawBehind {
                drawLine(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.3f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .focusRequester(railFocusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && 
                    keyEvent.key == Key.DirectionRight && isExpanded) {
                    onCollapseRequest()
                    onContentFocusRequest()
                    true
                } else if (keyEvent.type == KeyEventType.KeyDown && 
                           keyEvent.key == Key.DirectionLeft && !isExpanded) {
                    onExpandedChange(true)
                    true
                } else {
                    false
                }
            }
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "SandTV",
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "SandTV",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.alpha(textAlpha)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HorizontalDivider(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
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
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                    onClick = { 
                        onTabSelected(MainTab.MOVIES)
                        if (isExpanded) onCollapseRequest()
                    },
                    modifier = Modifier.focusRequester(firstItemFocusRequester)
                )
                
                if (isExpanded && selectedTab == MainTab.MOVIES) {
                    ExploreCategoriesItem(
                        isMovies = true,
                        onClick = { onExploreCategoriesClick(true) }
                    )
                }
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = Icons.Default.Tv,
                    label = "Serie TV",
                    isSelected = selectedTab == MainTab.SERIES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIES) }
                )
                
                if (isExpanded && selectedTab == MainTab.SERIES) {
                    ExploreCategoriesItem(
                        isMovies = false,
                        onClick = { onExploreCategoriesClick(false) }
                    )
                }
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = Icons.Default.LiveTv,
                    label = "Live",
                    isSelected = selectedTab == MainTab.LIVE,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LIVE) }
                )
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = Icons.Default.Movie,
                    label = "Serie A",
                    isSelected = selectedTab == MainTab.SERIE_A,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIE_A) },
                    iconPainter = painterResource(id = R.drawable.serie_a)
                )
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = if (selectedTab == MainTab.FAVORITES) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Preferiti",
                    isSelected = selectedTab == MainTab.FAVORITES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.FAVORITES) }
                )
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "Liste",
                    isSelected = selectedTab == MainTab.LISTS,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LISTS) }
                )
                
                Spacer(modifier = Modifier.height(1.dp))
                
                NavRailItem(
                    icon = Icons.Default.History,
                    label = "Cronologia",
                    isSelected = selectedTab == MainTab.HISTORY,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.HISTORY) },
                    modifier = Modifier.focusRequester(lastItemFocusRequester)
                )
            }
            
            // Settings at bottom
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                
                HorizontalDivider(
                    color = SandTVColors.BackgroundTertiary.copy(alpha = 0.4f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
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
private fun ExploreCategoriesItem(
    isMovies: Boolean,
    isExpanded: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.BackgroundTertiary else Color.Transparent,
        label = "exploreCategoriesBg"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isFocused) SandTVColors.TextPrimary else SandTVColors.TextTertiary,
        label = "exploreCategoriesText"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color = backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FourSquaresIcon(
            modifier = Modifier.size(16.dp),
            color = textColor
        )
        Text(
            text = "Esplora tutte le categorie",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun FourSquaresIcon(
    modifier: Modifier = Modifier,
    color: Color = SandTVColors.Accent
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val squareSize = size.minDimension / 2.5f
        val gap = size.minDimension / 10f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(squareSize / 4, squareSize / 4)
        
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        drawRoundRect(
            color = color.copy(alpha = 0.7f),
            topLeft = androidx.compose.ui.geometry.Offset(squareSize + gap, 0f),
            size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        drawRoundRect(
            color = color.copy(alpha = 0.7f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, squareSize + gap),
            size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
        
        drawRoundRect(
            color = color.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(squareSize + gap, squareSize + gap),
            size = androidx.compose.ui.geometry.Size(squareSize, squareSize),
            cornerRadius = cornerRadius
        )
    }
}
