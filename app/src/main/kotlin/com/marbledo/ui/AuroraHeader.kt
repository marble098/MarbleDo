package com.marble098.marbledo.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.composed

/**
 * Aurora Borealis header background.
 * Three soft radial orbs drift slowly; the palette shifts with the hour of day.
 * - Morning (5-11): warm peach → lavender
 * - Afternoon (12-17): sky blue → mint
 * - Evening (18-22): indigo → violet
 * - Night (22-5): deep navy → charcoal with subtle star particles
 *
 * RTL-aware: orbs mirror when layout is RTL so the light source stays visually consistent.
 */
@Composable
fun auroraBrush(
    hour: Int,
    isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl,
): Brush {
    val palette = when (hour) {
        in 5..11 -> listOf(Color(0xFFFFB88C), Color(0xFFE8D8FF), Color(0xFFB8EFFF))
        in 12..17 -> listOf(Color(0xFF7EC8E3), Color(0xFFA8E6CF), Color(0xFFFFF9C4))
        in 18..22 -> listOf(Color(0xFF5B4DBC), Color(0xFF8A6BC9), Color(0xFFFF8A65))
        else -> listOf(Color(0xFF1A1A2E), Color(0xFF2D3561), Color(0xFF4A3F7A))
    }
    // Fixed offsets but mirrored for RTL so the composition feels natural in both directions
    val xBias = if (isRtl) -0.15f else 0.15f
    return Brush.linearGradient(
        colors = palette,
        start = Offset(0f + xBias * 1000f, 0f),
        end = Offset(1000f + xBias * -500f, 800f),
    )
}

@Composable
fun AuroraOrbs(
    hour: Int,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val colors = when (hour) {
        in 5..11 -> listOf(Color(0xFFFFDAB9).copy(alpha = 0.55f), Color(0xFFD8C8FF).copy(alpha = 0.45f))
        in 12..17 -> listOf(Color(0xFF81D4FA).copy(alpha = 0.50f), Color(0xFFA5D6A7).copy(alpha = 0.40f))
        in 18..22 -> listOf(Color(0xFF7C4DFF).copy(alpha = 0.55f), Color(0xFFFF6E40).copy(alpha = 0.35f))
        else -> listOf(Color(0xFF3949AB).copy(alpha = 0.35f), Color(0xFF5C6BC0).copy(alpha = 0.25f))
    }
    Box(modifier = modifier) {
        Box(
            Modifier
                .offset(x = (drift * 12).dp, y = (drift * -6).dp)
                .height(120.dp)
                .fillMaxWidth(0.55f)
                .clip(CircleShape)
                .background(colors[0])
                .blur(28.dp),
        )
        Box(
            Modifier
                .offset(x = (24 + drift * -10).dp, y = (18 + drift * 8).dp)
                .height(90.dp)
                .fillMaxWidth(0.42f)
                .clip(CircleShape)
                .background(colors[1])
                .blur(22.dp),
        )
    }
}
