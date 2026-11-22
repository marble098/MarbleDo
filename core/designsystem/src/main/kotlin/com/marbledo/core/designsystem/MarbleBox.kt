package com.marbledo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one box every screen block lives in — M3 Expressive's minimal card language applied to the
 * whole app. Cards are flat white surfaces on a soft neutral page, so nothing floats loose on the
 * background and every item reads as an object of its own — without the hairline frame that turns
 * a screen into a grid of boxed tiles.
 *
 * New layout code must put its blocks in a [MarbleBox] instead of stacking bare rows on the
 * screen background; take gaps and padding from [MarbleSpacing].
 */
@Composable
fun MarbleBox(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    tonalElevation: Dp = 0.dp,
    padding: Dp = MarbleSpacing.lg,
    gap: Dp = MarbleSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

/**
 * The hairline that outlines a box, a divider or a standalone control that is not a [MarbleBox].
 *
 * The minimal card language is flat, so this is opt-in — used for the explicit outlines, hairline
 * dividers and framed controls that sit outside the box family. Wherever it is drawn, the line
 * still has to exist: on the AMOLED scheme 55% of a near-black outline over a near-black card
 * composites to 1.2:1, so the app lifts the line until it reads — [MarbleContrast.separator] keeps
 * the light themes' calm value and only moves the ones whose composite would vanish.
 */
@Composable
fun MarbleBoxBorder(containerColor: Color): Color {
    val outline = MaterialTheme.colorScheme.outlineVariant
    return remember(containerColor, outline) {
        Color(
            MarbleContrast.separator(
                colour = outline.toArgbLong(),
                background = containerColor.toArgbLong(),
                alpha = 0.55,
            ),
        )
    }
}

/**
 * A labelled zone inside a page: the same [MarbleBox] chrome with a section title on top, so a
 * group of controls (appearance, notifications, backup) is boxed and named like everything else.
 */
@Composable
fun MarbleSectionBox(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MarbleBox(modifier = modifier, content = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs),
        ) {
            Text(
                title,
                style = MarbleTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    })
}
