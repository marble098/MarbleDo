package com.marbledo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One destination of the floating glass tab bar. */
data class GlassTabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * The floating liquid-glass tab bar (docs/UI_OVERHAUL_2025.md, "Tab bar — the chosen option"):
 * a capsule of real glass floating above the bottom edge, with a selection pill that morphs
 * between slots like a drop of liquid and a selected icon that lifts slightly as it lands.
 *
 * * **Glass:** [LiquidGlassSurface] blurs whatever scrolls underneath (Android 12+), tints to the
 *   theme, and keeps a specular edge and a hairline on every palette including AMOLED.
 * * **Ink:** label and icon colours are computed with [MarbleGlass.inkOnGlass] against every
 *   theme surface, so text on the glass clears its WCAG bar over any content behind it.
 * * **Motion:** every animation rides [MarbleMotion], so the indicator, the icon lift and the
 *   hide/reveal obey the app's one motion vocabulary and collapse to `snap()` under reduced
 *   motion. The auto-hide decision itself is [TabBarAutoHide] — pure and unit-tested.
 * * **Accessibility:** each tab is a `Role.Tab` selectable of at least 48 dp, the icons carry no
 *   redundant descriptions (the label names the tab), and the app shell never auto-hides the bar
 *   while touch exploration is on.
 * * **RTL:** the indicator geometry mirrors through [MarbleGlass.indicatorCenterFraction].
 *
 * The bar is dumb on purpose: the shell owns placement, safe-area insets and the scroll
 * connection, and reports visibility through [visible].
 */
@Composable
fun FloatingGlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero,
    visible: Boolean = true,
) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val recipe = glassRecipe()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val activeIndex = selectedIndex.coerceIn(items.indices)
    val samples = listOf(
        scheme.background,
        scheme.surface,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.primaryContainer,
        scheme.secondaryContainer,
    ).map { it.toArgbLong() }
    val labelInk = remember(samples, recipe) {
        MarbleGlass.inkOnGlass(
            preferred = scheme.onSurfaceVariant.toArgbLong(),
            recipe = recipe,
            backdrops = samples,
            minimum = MarbleContrast.TEXT_MINIMUM,
        )
    }
    val iconInk = remember(samples, recipe) {
        MarbleGlass.inkOnGlass(
            preferred = scheme.onSurfaceVariant.toArgbLong(),
            recipe = recipe,
            backdrops = samples,
            minimum = MarbleContrast.UI_MINIMUM,
        )
    }

    // Slide fully below the screen edge (plus its own margins and the system bars) when hidden.
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hideDistance = MarbleGlass.barHeight + MarbleGlass.floatGap * 2 + navBarsBottom + MarbleGlass.clearanceGap
    val hiddenProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = MarbleMotion.spatial(),
        label = "glassTabBarVisibility",
    )

    val shape = RoundedCornerShape(MarbleGlass.barHeight / 2)
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(MarbleGlass.barHeight)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .graphicsLayer {
                translationY = hiddenProgress * hideDistance.toPx()
                alpha = 1f - hiddenProgress
            },
        shape = shape,
        recipe = recipe,
        backdrop = backdrop,
        backdropOrigin = backdropOrigin,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val targetCenter = MarbleGlass.indicatorCenterFraction(activeIndex, items.size, rtl)
            val center by animateFloatAsState(
                targetValue = targetCenter,
                animationSpec = MarbleMotion.spatial(),
                label = "glassTabIndicator",
            )
            val pillWidth = maxWidth * MarbleGlass.indicatorWidthFraction(center, items.size)
            // The pill is centered first, so translate from that anchor in physical screen
            // coordinates. `absoluteOffset` is important here: a direction-aware offset would
            // mirror the already RTL-mirrored slot a second time.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .absoluteOffset {
                        IntOffset(
                            (MarbleGlass.indicatorOffsetFraction(center) * constraints.maxWidth).roundToInt(),
                            0,
                        )
                    }
                    .size(width = pillWidth, height = MarbleGlass.indicatorHeight)
                    .clip(RoundedCornerShape(MarbleGlass.indicatorHeight / 2))
                    .background(scheme.primaryContainer),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    val selected = index == activeIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) MarbleGlass.SELECTED_ICON_SCALE else 1f,
                        animationSpec = MarbleMotion.effects(),
                        label = "glassTabIcon",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                            .padding(vertical = MarbleSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            tint = if (selected) scheme.primary else Color(iconInk),
                            modifier = Modifier
                                .size(MarbleGlass.iconSize)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                        Text(
                            text = item.label,
                            style = MarbleTextStyles.metaLabel,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) scheme.onPrimaryContainer else Color(labelInk),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
