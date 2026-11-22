package com.marble098.marbledo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.marbledo.core.designsystem.MarbleTextStyles

/**
 * The dashboard hero — a calm, theme-derived gradient with one soft accent glow.
 *
 * Deliberately minimal, modern and Google-Material-3-Expressive in language: no fixed pastel
 * sky and no drifting clouds, stars or sun. The wash is derived from the theme's own slots
 * (`primaryContainer` dissolving into `surface`), so the named palettes and dynamic colour repaint
 * the hero through the same colour table as every other surface. RTL mirrors the gradient along
 * the reading direction.
 */
@Composable
fun auroraBrush(
    isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl,
): Brush {
    val colors = MaterialTheme.colorScheme
    val top by animateColorAsState(colors.primaryContainer, tween(900), label = "heroWash")
    val startX = if (isRtl) 1200f else 0f
    val endX = if (isRtl) 0f else 1200f
    return Brush.linearGradient(
        colors = listOf(top, colors.surface),
        start = Offset(startX, 0f),
        end = Offset(endX, 900f),
    )
}

/**
 * A single soft accent glow behind the greeting, anchored to the top so the text always reads on
 * calm paper. One Canvas, no per-frame animation — it stays cheap and flat.
 */
@Composable
fun GreetingSky(
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val glow = colors.primary
    Canvas(modifier.fillMaxSize()) {
        val anchorX = if (isRtl) size.width * 0.82f else size.width * 0.18f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(anchorX, -size.height * 0.25f),
                radius = size.width * 0.85f,
            ),
            radius = size.width * 0.85f,
            center = Offset(anchorX, -size.height * 0.25f),
        )
    }
}

/**
 * The greeting itself — a flat screen-title line on calm paper. No shimmer, no gradient sweep.
 */
@Composable
fun GreetingHeadline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MarbleTextStyles.screenTitle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
