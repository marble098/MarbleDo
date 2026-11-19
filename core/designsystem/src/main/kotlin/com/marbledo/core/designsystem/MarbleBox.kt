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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one box every screen block lives in — Material 3 Expressive's card language applied to the
 * whole app. Each page element (a header, a tool row, a list, a form) sits inside its own rounded,
 * tonal surface with a hairline border, so nothing floats loose on the background and every item
 * reads as an object of its own.
 *
 * New layout code must put its blocks in a [MarbleBox] instead of stacking bare rows on the
 * screen background; take gaps and padding from [MarbleSpacing].
 */
@Composable
fun MarbleBox(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    shape: Shape = MaterialTheme.shapes.extraLarge,
    tonalElevation: Dp = 1.dp,
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
        border = BorderStroke(1.dp, borderColor),
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
