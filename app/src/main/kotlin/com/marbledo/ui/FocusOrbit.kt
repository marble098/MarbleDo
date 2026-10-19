package com.marble098.marbledo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marble098.marbledo.R
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbit progress ring — a large visual anchor for Today's workload.
 * The ring fills clockwise from the top, with small orbiting dots for each open task
 * and a central percentage. RTL-aware: the fill direction and the orbit flip to match reading
 * direction. Motion stops completely when the user enables reduced motion.
 */
@Composable
fun FocusOrbit(
    completed: Int,
    total: Int,
    open: Int,
    numeralMode: NumeralMode,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier,
    orbitSize: Dp = 96.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val progress = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val animatedProgressValue by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900),
        label = "orbitProgress",
    )
    val animatedProgress = if (reduceMotion) progress else animatedProgressValue
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.secondary
    val centerScrim = MaterialTheme.colorScheme.surface
    val percentage = (animatedProgress * 100).toInt()

    val transition = rememberInfiniteTransition(label = "orbitDots")
    val spinValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitSpin",
    )
    val spin = if (reduceMotion) 0f else spinValue * (if (isRtl) -1f else 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(orbitSize)) {
            val stroke = 8.dp.toPx()
            val radius = (orbitSize.toPx() - stroke) / 2f
            val center = Offset(orbitSize.toPx() / 2f, orbitSize.toPx() / 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // A faint disc keeps the percentage readable even over the animated sky.
            drawCircle(
                color = centerScrim.copy(alpha = 0.55f),
                radius = radius - stroke * 0.6f,
                center = center,
            )
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Soft glow behind the completed part.
            if (animatedProgress > 0f) {
                drawArc(
                    color = fillColor.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f * if (isRtl) -1f else 1f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + 7.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            // Fill — clockwise in LTR, counter-clockwise in RTL
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f * if (isRtl) -1f else 1f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Orbit dots: one per open task, up to 12 visible, slowly circling the ring.
            val visibleDots = open.coerceAtMost(12)
            if (visibleDots > 0) {
                val dotRadius = 3.2.dp.toPx()
                for (i in 0 until visibleDots) {
                    val angleDeg = -90f + (360f / visibleDots) * i + spin
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val dotR = radius - 1.dp.toPx()
                    val cx = center.x + (cos(angleRad) * dotR).toFloat()
                    val cy = center.y + (sin(angleRad) * dotR).toFloat()
                    val alpha = 0.45f + 0.55f * (i.toFloat() / visibleDots.coerceAtLeast(1))
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
                TextNormalizer.formatDigits(
                    stringResource(R.string.dash_percent, percentage),
                    numeralMode,
                ),
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
