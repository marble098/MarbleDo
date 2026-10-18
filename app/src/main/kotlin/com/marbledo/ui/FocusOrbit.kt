package com.marble098.marbledo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbit progress ring — a large visual anchor for Today's workload.
 * The ring fills clockwise from the top, with small orbiting dots for each open task
 * and a central percentage. RTL-aware: the fill direction flips to match reading direction.
 */
@Composable
fun FocusOrbit(
    completed: Int,
    total: Int,
    open: Int,
    numeralMode: NumeralMode,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val progress = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900),
        label = "orbitProgress",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.secondary
    val percentage = (animatedProgress * 100).toInt()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 8.dp.toPx()
            val radius = (size.toPx() - stroke) / 2f
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Fill — clockwise in LTR, counter-clockwise in RTL
            val sweep = animatedProgress * 360f * if (isRtl) -1f else 1f
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Orbit dots: one per open task, up to 12 visible
            val visibleDots = open.coerceAtMost(12)
            if (visibleDots > 0) {
                val dotRadius = 3.5.dp.toPx()
                for (i in 0 until visibleDots) {
                    val angleDeg = -90f + (360f / visibleDots) * i
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val dotR = radius - 2.dp.toPx()
                    val cx = center.x + (cos(angleRad) * dotR).toFloat()
                    val cy = center.y + (sin(angleRad) * dotR).toFloat()
                    val alpha = 0.35f + 0.65f * (i.toFloat() / visibleDots.coerceAtLeast(1))
                    drawCircle(
                        color = dotColor.copy(alpha = alpha),
                        radius = dotRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                TextNormalizer.formatDigits("$percentage٪", numeralMode),
                style = MarbleTextStyles.cardTitle.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                TextNormalizer.formatDigits("$completed / $total", numeralMode),
                style = MarbleTextStyles.metaLabel.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FocusOrbitLegend(
    todayOpen: Int,
    completed: Int,
    total: Int,
    numeralMode: NumeralMode,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).padding(1.dp).then(Modifier.width(8.dp).height(8.dp)))
            Text(
                TextNormalizer.formatDigits(
                    if (todayOpen > 0) "$todayOpen ${if (todayOpen == 1) "باز" else "باز"} · $completed انجام‌شده"
                    else "همه انجام شد",
                    numeralMode,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
