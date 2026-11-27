package com.marbledo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * The app's in-app notice surface: the same liquid-glass capsule as the tab bar, so an undo
 * message and the navigation read as one notification language (docs/UI_OVERHAUL_2025.md,
 * "In-app notices"). Renders [SnackbarData], so call sites keep the plain `SnackbarHostState`
 * API — only the look and the contrast guarantees change. Announced politely through a live
 * region; the action is a real button and the dismiss affordance appears only when the message
 * asked for one. [dismissLabel] carries the localized screen-reader label for that button — the
 * design system module holds no per-language string table of its own.
 */
@Composable
fun MarbleGlassSnackbar(
    data: SnackbarData,
    dismissLabel: String,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val recipe = glassRecipe()
    val samples = listOf(
        scheme.background,
        scheme.surface,
        scheme.surfaceContainer,
        scheme.primaryContainer,
    ).map { it.toArgbLong() }
    val ink = remember(samples, recipe) {
        MarbleGlass.inkOnGlass(
            preferred = scheme.onSurface.toArgbLong(),
            recipe = recipe,
            backdrops = samples,
            minimum = MarbleContrast.TEXT_MINIMUM,
        )
    }
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(MarbleGlass.barHeight / 2),
        recipe = recipe,
        contentPadding = PaddingValues(start = MarbleSpacing.md, end = MarbleSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
        ) {
            Text(
                text = data.visuals.message,
                style = MarbleTextStyles.itemTitle,
                color = Color(ink),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MarbleSpacing.sm),
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = scheme.primary)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = dismissLabel,
                        tint = Color(ink),
                    )
                }
            }
        }
    }
}
