package it.sandtv.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.Divider
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

/**
 * Navigation Rail espandibile per SandTV
 * 
 * Layout:
 * - Collassato (72dp): Solo icone
 * - Espanso (240dp): Icone + nomi + categorie sotto
 * 
 * Navigazione D-Pad:
 * - LEFT sul primo elemento collassato → espande
 * - RIGHT sull'ultimo elemento espanso → collassa e vai a contenuto
 * - UP/DOWN → naviga tra elementi
 */
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
        targetValue = if (isExpanded) 240.dp else 72.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "railWidth"
    )
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.95f else 0.85f,
        animationSpec = tween(200),
        label = "railBgAlpha"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(150),
        label = "railTextAlpha"
    )
    
    // Focus requesters per navigazione
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
                        Color.Black.copy(alpha = backgroundAlpha * 0.9f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = SandTVColors.BackgroundTertiary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .focusRequester(railFocusRequester)
            .onFocusChanged { focusState ->
                // Se il rail perde focus e non è espanso, assicurati che sia collassato
                if (!focusState.hasFocus && isExpanded) {
                    // Non collassare automaticamente, lascia che l'utente lo faccia con RIGHT
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            // Logo/Title area (solo quando espanso)
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SandTVColors.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "SandTV",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = SandTVColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(textAlpha)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Divider(
                    color = SandTVColors.BackgroundTertiary,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                // Solo icona quando collassato
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SandTVColors.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Sezione principale: Icone navigazione
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Film
                NavRailItem(
                    icon = Icons.Default.Movie,
                    label = "Film",
                    isSelected = selectedTab == MainTab.MOVIES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.MOVIES) },
                    modifier = Modifier.focusRequester(firstItemFocusRequester)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Serie TV
                NavRailItem(
                    icon = Icons.Default.Tv,
                    label = "Serie TV",
                    isSelected = selectedTab == MainTab.SERIES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIES) }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Live TV
                NavRailItem(
                    icon = Icons.Default.LiveTv,
                    label = "Live",
                    isSelected = selectedTab == MainTab.LIVE,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LIVE) }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Serie A (con logo PNG)
                SerieARailItem(
                    isSelected = selectedTab == MainTab.SERIE_A,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.SERIE_A) }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Preferiti
                NavRailItem(
                    icon = if (selectedTab == MainTab.FAVORITES) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = "Preferiti",
                    isSelected = selectedTab == MainTab.FAVORITES,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.FAVORITES) }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Liste
                NavRailItem(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "Liste",
                    isSelected = selectedTab == MainTab.LISTS,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.LISTS) }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Cronologia
                NavRailItem(
                    icon = Icons.Default.History,
                    label = "Cronologia",
                    isSelected = selectedTab == MainTab.HISTORY,
                    isExpanded = isExpanded,
                    onClick = { onTabSelected(MainTab.HISTORY) },
                    modifier = Modifier.focusRequester(lastItemFocusRequester)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Divider prima delle categorie
            if (isExpanded && categories.isNotEmpty()) {
                Divider(
                    color = SandTVColors.BackgroundTertiary,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Titolo sezione categorie
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = SandTVColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "CATEGORIE",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = SandTVColors.TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Lista categorie scrollabile
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    
                    // Spacer per scroll
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            
            // Impostazioni (sempre in fondo)
            Spacer(modifier = Modifier.height(8.dp))
            
            Divider(
                color = SandTVColors.BackgroundTertiary,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            NavRailItem(
                icon = Icons.Default.Settings,
                label = "Impostazioni",
                isSelected = false,
                isExpanded = isExpanded,
                onClick = onSettingsClick
            )
        }
        
        // Gestione navigazione D-Pad per espansione/collasso
        LaunchedEffect(Unit) {
            // Focus sul primo elemento all'avvio
            kotlinx.coroutines.delay(500)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
}

/**
 * Elemento speciale per Serie A con logo PNG
 */
@Composable
private fun SerieARailItem(
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
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
    
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isFocused -> SandTVColors.Accent
            else -> Color.Transparent
        },
        label = "serieARailBorder"
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
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = if (isExpanded) 16.dp else 0.dp),
        contentAlignment = if (isExpanded) Alignment.CenterStart else Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo Serie A PNG
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.serie_a),
                contentDescription = "Serie A",
                modifier = Modifier.size(24.dp)
            )
            
            if (isExpanded) {
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
        }
    }
}
