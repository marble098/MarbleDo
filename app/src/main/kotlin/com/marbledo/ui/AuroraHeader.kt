package com.marble098.marbledo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.core.designsystem.MarbleTextStyles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The five moods the dashboard sky can have. It is deliberately a little finer than the greeting
 * itself: dawn and dusk deserve their own colours even when both still read as “morning” or
 * “evening” in words.
 */
enum class SkyPhase {
    DAWN, MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): SkyPhase = when (hour) {
            in 5..7 -> DAWN
            in 8..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

private data class SkyPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val glow: Color,
    val celestial: Color,
    val celestialCore: Color,
    val cloud: Color,
    val star: Color,
    val ink: Color,
)

/**
 * Palette per time of day. Light and dark themes each get their own set so the header stays
 * readable instead of turning into a bright rectangle inside a dark app (or the reverse).
 */
private fun skyPalette(phase: SkyPhase, dark: Boolean): SkyPalette = if (!dark) {
    when (phase) {
        SkyPhase.DAWN -> SkyPalette(
            top = Color(0xFFFFE3C7), middle = Color(0xFFF3D2F4), bottom = Color(0xFFC9E8FF),
            glow = Color(0xFFFFB27A), celestial = Color(0xFFFFB35C), celestialCore = Color(0xFFFF8A4C),
            cloud = Color(0xFFFFFFFF), star = Color(0xFF6B5A92), ink = Color(0xFF3B2A44),
        )
        SkyPhase.MORNING -> SkyPalette(
            top = Color(0xFFFFEBC4), middle = Color(0xFFFFF3D6), bottom = Color(0xFFCDEBFF),
            glow = Color(0xFFFFC46B), celestial = Color(0xFFFFC247), celestialCore = Color(0xFFFF9E2E),
            cloud = Color(0xFFFFFFFF), star = Color(0xFF6D7397), ink = Color(0xFF3A3550),
        )
        SkyPhase.AFTERNOON -> SkyPalette(
            top = Color(0xFFC9ECFF), middle = Color(0xFFD7F6EA), bottom = Color(0xFFFFF7CE),
            glow = Color(0xFFFFD36B), celestial = Color(0xFFFFCA3F), celestialCore = Color(0xFFFFA21F),
            cloud = Color(0xFFFFFFFF), star = Color(0xFF5E7D8C), ink = Color(0xFF2F4152),
        )
        SkyPhase.EVENING -> SkyPalette(
            top = Color(0xFFFFC7A1), middle = Color(0xFFE8A8D8), bottom = Color(0xFFA7A4EC),
            glow = Color(0xFFFF8E63), celestial = Color(0xFFFFA26B), celestialCore = Color(0xFFFF6B4A),
            cloud = Color(0xFFF6E6FF), star = Color(0xFF5C4A86), ink = Color(0xFF3B2A4E),
        )
        SkyPhase.NIGHT -> SkyPalette(
            top = Color(0xFFBBC3F4), middle = Color(0xFFC8B9F2), bottom = Color(0xFF9ED4E8),
            glow = Color(0xFF8FB3FF), celestial = Color(0xFFFFF6D8), celestialCore = Color(0xFFFFE9A8),
            cloud = Color(0xFFE4E8FF), star = Color(0xFF3D3A66), ink = Color(0xFF2B2946),
        )
    }
} else {
    when (phase) {
        SkyPhase.DAWN -> SkyPalette(
            top = Color(0xFF3A2236), middle = Color(0xFF4B2A4E), bottom = Color(0xFF1E2B4A),
            glow = Color(0xFFE9835C), celestial = Color(0xFFFFB07A), celestialCore = Color(0xFFFF8A55),
            cloud = Color(0xFF6B5570), star = Color(0xFFE8E2FF), ink = Color(0xFFEDE7F6),
        )
        SkyPhase.MORNING -> SkyPalette(
            top = Color(0xFF3B2A3F), middle = Color(0xFF4E3550), bottom = Color(0xFF233A57),
            glow = Color(0xFFF0A85E), celestial = Color(0xFFFFCB6B), celestialCore = Color(0xFFFFA53D),
            cloud = Color(0xFF7A6478), star = Color(0xFFE4DEFF), ink = Color(0xFFEFE9F5),
        )
        SkyPhase.AFTERNOON -> SkyPalette(
            top = Color(0xFF1D3A55), middle = Color(0xFF1F4A50), bottom = Color(0xFF4A3A2C),
            glow = Color(0xFFE2B05E), celestial = Color(0xFFFFD27A), celestialCore = Color(0xFFFFB13B),
            cloud = Color(0xFF5E7580), star = Color(0xFFDDE7FF), ink = Color(0xFFEAF2F8),
        )
        SkyPhase.EVENING -> SkyPalette(
            top = Color(0xFF33204F), middle = Color(0xFF552B59), bottom = Color(0xFF6E2F3F),
            glow = Color(0xFFF0785A), celestial = Color(0xFFFFB08A), celestialCore = Color(0xFFFF7A5C),
            cloud = Color(0xFF6A4A70), star = Color(0xFFF1E6FF), ink = Color(0xFFF3EAF8),
        )
        SkyPhase.NIGHT -> SkyPalette(
            top = Color(0xFF0C1128), middle = Color(0xFF1A2450), bottom = Color(0xFF2B1F46),
            glow = Color(0xFF6F8FFF), celestial = Color(0xFFF7F0D2), celestialCore = Color(0xFFFFE9A6),
            cloud = Color(0xFF3A3A63), star = Color(0xFFFFFFFF), ink = Color(0xFFF1EEFF),
        )
    }
}

@Composable
private fun isDarkSurface(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.45f

/**
 * The header gradient. Colours animate gently whenever the hour moves into a new phase, so the
 * card never snaps from peach to navy between two recompositions.
 *
 * RTL-aware: the light comes from the trailing edge, mirroring the reading direction.
 */
@Composable
fun auroraBrush(
    hour: Int,
    isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl,
): Brush {
    val phase = SkyPhase.of(hour)
    val palette = skyPalette(phase, isDarkSurface())
    val top by animateColorAsState(palette.top, tween(1400), label = "skyTop")
    val middle by animateColorAsState(palette.middle, tween(1400), label = "skyMiddle")
    val bottom by animateColorAsState(palette.bottom, tween(1400), label = "skyBottom")
    val startX = if (isRtl) 1100f else 0f
    val endX = if (isRtl) 0f else 1100f
    return Brush.linearGradient(
        colors = listOf(top, middle, bottom),
        start = Offset(startX, 0f),
        end = Offset(endX, 900f),
    )
}

/**
 * A small living sky behind the greeting: drifting clouds in the morning and afternoon, a low sun
 * with slow rays at dawn and dusk, and a crescent moon with twinkling stars at night. Everything
 * is drawn from one Canvas so it stays cheap, and every motion collapses to a still image when the
 * user asks for reduced motion.
 */
@Composable
fun GreetingSky(
    hour: Int,
    modifier: Modifier = Modifier,
) {
    val phase = SkyPhase.of(hour)
    val dark = isDarkSurface()
    val palette = skyPalette(phase, dark)
    val reduceMotion = LocalReduceMotion.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val transition = rememberInfiniteTransition(label = "greetingSky")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skyDrift",
    )
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skyTwinkle",
    )
    val time = if (reduceMotion) 0.32f else drift
    val twinklePhase = if (reduceMotion) 0.45f else twinkle
    val starColor by animateColorAsState(palette.star, tween(900), label = "skyStar")
    val celestial by animateColorAsState(palette.celestial, tween(900), label = "skyCelestial")
    val core by animateColorAsState(palette.celestialCore, tween(900), label = "skyCore")
    val glow by animateColorAsState(palette.glow, tween(900), label = "skyGlow")
    val cloud by animateColorAsState(palette.cloud, tween(900), label = "skyCloud")

    Canvas(modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        // The light source sits on the action side of the header, where the icon buttons cover it.
        val anchorX = if (isRtl) width * 0.10f else width * 0.90f
        val anchorY = height * if (phase == SkyPhase.EVENING) 0.34f else 0.22f
        val direction = if (isRtl) 1f else -1f

        // Horizon glow: warm at dawn and dusk, cool at night.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, glow.copy(alpha = if (dark) 0.20f else 0.26f)),
                startY = height * 0.45f,
                endY = height,
            ),
            topLeft = Offset.Zero,
            size = Size(width, height),
        )

        when (phase) {
            SkyPhase.NIGHT -> {
                drawNightSky(
                    width = width,
                    height = height,
                    time = time,
                    twinkle = twinklePhase,
                    starColor = starColor,
                    moonColor = celestial,
                    moonCore = core,
                    glowColor = glow,
                    anchorX = anchorX,
                    anchorY = anchorY * 1.05f,
                )
            }
            SkyPhase.DAWN, SkyPhase.EVENING -> {
                drawSun(
                    anchorX = anchorX,
                    anchorY = anchorY,
                    radius = 20.dp.toPx(),
                    glowColor = glow,
                    sunColor = celestial,
                    coreColor = core,
                    rayAlpha = 0.28f,
                    time = time,
                    dark = dark,
                )
                // A couple of early stars are visible while the light is still low.
                drawStars(
                    width = width,
                    height = height,
                    count = 7,
                    phase = twinklePhase,
                    color = starColor,
                    maxY = height * 0.55f,
                    alphaScale = 0.55f,
                )
                drawClouds(
                    width = width,
                    height = height,
                    time = time,
                    direction = direction,
                    color = cloud,
                    dark = dark,
                    alphaScale = 0.5f,
                    yStart = 0.58f,
                    count = 2,
                )
            }
            SkyPhase.MORNING, SkyPhase.AFTERNOON -> {
                drawSun(
                    anchorX = anchorX,
                    anchorY = anchorY,
                    radius = 17.dp.toPx(),
                    glowColor = glow,
                    sunColor = celestial,
                    coreColor = core,
                    rayAlpha = 0.34f,
                    time = time,
                    dark = dark,
                )
                drawClouds(
                    width = width,
                    height = height,
                    time = time,
                    direction = direction,
                    color = cloud,
                    dark = dark,
                    alphaScale = 0.75f,
                    yStart = 0.20f,
                    count = 3,
                )
            }
        }
    }
}

/** Sun disc, halo and slowly turning rays. */
private fun DrawScope.drawSun(
    anchorX: Float,
    anchorY: Float,
    radius: Float,
    glowColor: Color,
    sunColor: Color,
    coreColor: Color,
    rayAlpha: Float,
    time: Float,
    dark: Boolean,
) {
    val center = Offset(anchorX, anchorY)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = if (dark) 0.32f else 0.45f), Color.Transparent),
            center = center,
            radius = radius * 3.1f,
        ),
        radius = radius * 3.1f,
        center = center,
    )
    rotate(degrees = time * 360f, pivot = center) {
        repeat(9) { index ->
            val angle = (index * 40f) * (PI / 180f).toFloat()
            val inner = radius * 1.35f
            val outer = radius * (1.75f + (index % 3) * 0.16f)
            drawLine(
                color = glowColor.copy(alpha = rayAlpha),
                start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                end = Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(color = sunColor.copy(alpha = 0.85f), radius = radius, center = center)
    drawCircle(color = coreColor.copy(alpha = 0.55f), radius = radius * 0.65f, center = center)
}

/** Soft clouds built from overlapping ovals; they cross the card at different depths. */
private fun DrawScope.drawClouds(
    width: Float,
    height: Float,
    time: Float,
    direction: Float,
    color: Color,
    dark: Boolean,
    alphaScale: Float,
    yStart: Float,
    count: Int,
) {
    val baseAlpha = (if (dark) 0.22f else 0.55f) * alphaScale
    repeat(count) { index ->
        val speed = 0.55f + index * 0.22f
        val travel = (time * speed + index * 0.31f) % 1.15f
        val fraction = if (direction < 0) travel else 1.15f - travel
        val cloudWidth = width * (0.30f - index * 0.045f)
        val cloudHeight = cloudWidth * 0.30f
        val x = fraction * width - cloudWidth * 0.2f
        val y = height * (yStart + index * 0.10f)
        val alpha = baseAlpha * (1f - index * 0.18f)
        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(cloudWidth, cloudHeight),
        )
        drawOval(
            color = color.copy(alpha = alpha * 0.9f),
            topLeft = Offset(x + cloudWidth * 0.22f, y - cloudHeight * 0.38f),
            size = Size(cloudWidth * 0.46f, cloudHeight * 1.05f),
        )
        drawOval(
            color = color.copy(alpha = alpha * 0.8f),
            topLeft = Offset(x + cloudWidth * 0.52f, y - cloudHeight * 0.20f),
            size = Size(cloudWidth * 0.34f, cloudHeight * 0.9f),
        )
    }
}

/** Deterministic stars: no allocation per frame, and they never pop between recompositions. */
private fun DrawScope.drawStars(
    width: Float,
    height: Float,
    count: Int,
    phase: Float,
    color: Color,
    maxY: Float,
    alphaScale: Float,
) {
    repeat(count) { index ->
        val x = ((index * 0.618034f) % 1f) * width
        val y = ((index * 0.754877f) % 1f) * maxY
        val pulse = 0.35f + 0.65f * abs(sin((phase * PI * 2 + index * 1.7).toFloat()))
        val radius = (1.1f + (index % 3) * 0.55f).dp.toPx()
        drawCircle(
            color = color.copy(alpha = (0.30f + 0.55f * pulse) * alphaScale),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/** Crescent moon, a wide halo and a rare shooting star. */
private fun DrawScope.drawNightSky(
    width: Float,
    height: Float,
    time: Float,
    twinkle: Float,
    starColor: Color,
    moonColor: Color,
    moonCore: Color,
    glowColor: Color,
    anchorX: Float,
    anchorY: Float,
) {
    drawStars(
        width = width,
        height = height,
        count = 20,
        phase = twinkle,
        color = starColor,
        maxY = height * 0.88f,
        alphaScale = 1f,
    )
    val center = Offset(anchorX, anchorY)
    val radius = 18.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.28f), Color.Transparent),
            center = center,
            radius = radius * 3.6f,
        ),
        radius = radius * 3.6f,
        center = center,
    )
    val moon = Path().apply {
        addOval(Rect(center = center, radius = radius))
        val bite = Path().apply {
            addOval(Rect(center = center + Offset(radius * 0.52f, -radius * 0.26f), radius = radius * 0.94f))
        }
        op(this, bite, PathOperation.Difference)
    }
    drawPath(moon, color = moonColor)
    drawCircle(
        color = moonCore.copy(alpha = 0.30f),
        radius = radius * 0.42f,
        center = center + Offset(-radius * 0.28f, radius * 0.18f),
    )
    // One shooting star per cycle keeps the night alive without becoming a light show.
    val window = (time * 1.6f) % 1f
    if (window in 0.70f..0.78f) {
        val progress = (window - 0.70f) / 0.08f
        val startX = width * (if (anchorX < width / 2f) 0.32f else 0.16f) + width * 0.42f * progress
        val startY = height * 0.08f + height * 0.22f * progress
        val fade = (1f - progress).coerceIn(0f, 1f)
        drawLine(
            color = starColor.copy(alpha = 0.75f * fade),
            start = Offset(startX, startY),
            end = Offset(startX - 34.dp.toPx(), startY - 17.dp.toPx()),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The greeting itself. It carries a slow, low-contrast shimmer that sweeps across the letters like
 * the first light of the day, and falls back to a plain solid colour with reduced motion.
 */
@Composable
fun GreetingHeadline(
    text: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) {
        Text(
            text,
            style = MarbleTextStyles.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "greetingShimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "greetingShift",
    )
    val base = MaterialTheme.colorScheme.onSurface
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val sweep = shift * 640f - 180f
    val brush = Brush.linearGradient(
        colors = listOf(base, base, highlight, base, base),
        start = Offset(sweep, 0f),
        end = Offset(sweep + 320f, 46f),
    )
    Text(
        text,
        style = MarbleTextStyles.screenTitle.copy(brush = brush),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
