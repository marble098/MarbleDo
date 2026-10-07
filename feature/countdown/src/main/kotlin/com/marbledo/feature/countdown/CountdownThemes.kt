package com.marbledo.feature.countdown

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.domain.util.TextNormalizer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Resolved remaining time for a countdown target. */
@Immutable
data class CountdownUnits(
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalSeconds: Long,
) {
    val isFinished: Boolean get() = totalSeconds <= 0L
}

fun countdownUnits(dueAtEpochMillis: Long, nowMillis: Long = System.currentTimeMillis()): CountdownUnits {
    val remainingMillis = (dueAtEpochMillis - nowMillis).coerceAtLeast(0L)
    val totalSeconds = remainingMillis / 1000L
    return CountdownUnits(
        days = totalSeconds / 86_400L,
        hours = ((totalSeconds / 3_600L) % 24L).toInt(),
        minutes = ((totalSeconds / 60L) % 60L).toInt(),
        seconds = (totalSeconds % 60L).toInt(),
        totalSeconds = totalSeconds,
    )
}

/** A live-updating remaining time; ticks once per second while the screen shows it. */
@Composable
fun rememberCountdownUnits(dueAtEpochMillis: Long): CountdownUnits {
    var nowMillis by remember(dueAtEpochMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dueAtEpochMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return remember(dueAtEpochMillis, nowMillis) { countdownUnits(dueAtEpochMillis, nowMillis) }
}

/**
 * Resource mapping for the redesigned gallery. Each theme resolves to its own name and caption so
 * no two entries read as the same card with a different accent colour.
 */
fun countdownThemeLabelRes(theme: CountdownTheme): Int = when (theme) {
    CountdownTheme.MARBLE_ORB -> R.string.countdown_theme_marble_orb
    CountdownTheme.AURORA_VEIL -> R.string.countdown_theme_aurora_veil
    CountdownTheme.NEON_GRID -> R.string.countdown_theme_neon_grid
    CountdownTheme.FLIP_STUDIO -> R.string.countdown_theme_flip_studio
    CountdownTheme.GLASS_LAYERS -> R.string.countdown_theme_glass_layers
    CountdownTheme.ORBITAL_RINGS -> R.string.countdown_theme_orbital_rings
    CountdownTheme.TERMINAL_MATRIX -> R.string.countdown_theme_terminal_matrix
    CountdownTheme.LIQUID_TIDE -> R.string.countdown_theme_liquid_tide
    CountdownTheme.PAPER_TYPE -> R.string.countdown_theme_paper_type
    CountdownTheme.STELLAR_NIGHT -> R.string.countdown_theme_stellar_night
    CountdownTheme.MOMENT_BLOCKS -> R.string.countdown_theme_moment_blocks
    CountdownTheme.DIAL_GAUGE -> R.string.countdown_theme_dial_gauge
}

fun countdownThemeCaptionRes(theme: CountdownTheme): Int = when (theme) {
    CountdownTheme.MARBLE_ORB -> R.string.countdown_caption_marble_orb
    CountdownTheme.AURORA_VEIL -> R.string.countdown_caption_aurora_veil
    CountdownTheme.NEON_GRID -> R.string.countdown_caption_neon_grid
    CountdownTheme.FLIP_STUDIO -> R.string.countdown_caption_flip_studio
    CountdownTheme.GLASS_LAYERS -> R.string.countdown_caption_glass_layers
    CountdownTheme.ORBITAL_RINGS -> R.string.countdown_caption_orbital_rings
    CountdownTheme.TERMINAL_MATRIX -> R.string.countdown_caption_terminal_matrix
    CountdownTheme.LIQUID_TIDE -> R.string.countdown_caption_liquid_tide
    CountdownTheme.PAPER_TYPE -> R.string.countdown_caption_paper_type
    CountdownTheme.STELLAR_NIGHT -> R.string.countdown_caption_stellar_night
    CountdownTheme.MOMENT_BLOCKS -> R.string.countdown_caption_moment_blocks
    CountdownTheme.DIAL_GAUGE -> R.string.countdown_caption_dial_gauge
}

@Composable
fun countdownThemeLabel(theme: CountdownTheme): String = stringResource(countdownThemeLabelRes(theme))

@Composable
fun countdownThemeCaption(theme: CountdownTheme): String = stringResource(countdownThemeCaptionRes(theme))

/** Signature gradient of a theme, used by pickers and swatches. */
@Composable
fun countdownThemeBrush(theme: CountdownTheme): Brush = when (theme) {
    CountdownTheme.MARBLE_ORB -> Brush.linearGradient(listOf(Color(0xFF8E7BFF), Color(0xFF2B2350)))
    CountdownTheme.AURORA_VEIL -> Brush.linearGradient(listOf(Color(0xFF1B3A63), Color(0xFF2FB8A6), Color(0xFF7B5BD6)))
    CountdownTheme.NEON_GRID -> Brush.linearGradient(listOf(Color(0xFF7A2BFF), Color(0xFF0BD3E0)))
    CountdownTheme.FLIP_STUDIO -> Brush.linearGradient(listOf(Color(0xFFE8E4F6), Color(0xFFB9AEE8)))
    CountdownTheme.GLASS_LAYERS -> Brush.linearGradient(listOf(Color(0xFF8FD4FF), Color(0xFFC3A8FF), Color(0xFF6E63C8)))
    CountdownTheme.ORBITAL_RINGS -> Brush.linearGradient(listOf(Color(0xFF0B1030), Color(0xFF5CE1E6)))
    CountdownTheme.TERMINAL_MATRIX -> Brush.linearGradient(listOf(Color(0xFF06210F), Color(0xFF63FF9B)))
    CountdownTheme.LIQUID_TIDE -> Brush.linearGradient(listOf(Color(0xFF9BE8DA), Color(0xFF1E7EA8)))
    CountdownTheme.PAPER_TYPE -> Brush.linearGradient(listOf(Color(0xFFF7F0E4), Color(0xFFD9C7A7)))
    CountdownTheme.STELLAR_NIGHT -> Brush.linearGradient(listOf(Color(0xFF0A0E27), Color(0xFF6D5BD0)))
    CountdownTheme.MOMENT_BLOCKS -> Brush.linearGradient(listOf(Color(0xFFFFB25E), Color(0xFFFF6E8A), Color(0xFF6D5BD0)))
    CountdownTheme.DIAL_GAUGE -> Brush.linearGradient(listOf(Color(0xFF1F2430), Color(0xFFFFC46B)))
}

@Composable
private fun unitLabels(): List<String> = listOf(
    stringResource(R.string.countdown_unit_days),
    stringResource(R.string.countdown_unit_hours),
    stringResource(R.string.countdown_unit_minutes),
    stringResource(R.string.countdown_unit_seconds),
)

@Composable
private fun CountdownFaceTitle(title: String, color: Color, style: TextStyle, maxLines: Int = 2) {
    Text(
        text = title,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun reducedMotion(): Boolean = LocalReduceMotion.current

/**
 * Renders the live countdown for [theme]. The caller controls width and can request the compact
 * preview used inside the theme gallery.
 */
@Composable
fun CountdownFace(
    title: String,
    dueAtEpochMillis: Long,
    theme: CountdownTheme,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val units = rememberCountdownUnits(dueAtEpochMillis)
    val labels = unitLabels()
    val finishLabel = stringResource(R.string.countdown_finished)
    val height = if (compact) 150.dp else 236.dp
    val surface = modifier.fillMaxWidth().height(height)
    when (theme) {
        CountdownTheme.MARBLE_ORB -> MarbleOrbFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.AURORA_VEIL -> AuroraVeilFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.NEON_GRID -> NeonGridFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.FLIP_STUDIO -> FlipStudioFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.GLASS_LAYERS -> GlassLayersFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.ORBITAL_RINGS -> OrbitalRingsFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.TERMINAL_MATRIX -> TerminalMatrixFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.LIQUID_TIDE -> LiquidTideFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.PAPER_TYPE -> PaperTypeFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.STELLAR_NIGHT -> StellarNightFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.MOMENT_BLOCKS -> MomentBlocksFace(title, units, labels, finishLabel, surface, compact)
        CountdownTheme.DIAL_GAUGE -> DialGaugeFace(title, units, labels, finishLabel, surface, compact)
    }
}

@Composable
private fun rememberDigits(units: CountdownUnits, selector: (CountdownUnits) -> Int): String {
    val numeralMode = LocalNumeralMode.current
    return remember(units, numeralMode) { TextNormalizer.formatDigits(selector(units).toString().padStart(2, '0'), numeralMode) }
}

@Composable
private fun daysText(units: CountdownUnits): String {
    val numeralMode = LocalNumeralMode.current
    return remember(units, numeralMode) { TextNormalizer.formatDigits(units.days.toString(), numeralMode) }
}

// ---------------------------------------------------------------- 1. Marble orb

@Composable
private fun MarbleOrbFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "marble-orb")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "marble-shift",
    )
    Box(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF221C3C), Color(0xFF090812)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val orbCenter = Offset(size.width * (0.76f + shift * 0.04f), size.height * 0.24f)
            val radius = size.minDimension * 0.34f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFD6CBFF), Color(0xFF7A66D6), Color(0xFF2A2158)),
                    center = orbCenter - Offset(radius * 0.35f, radius * 0.4f),
                    radius = radius * 1.7f,
                ),
                radius = radius,
                center = orbCenter,
            )
            repeat(3) { index ->
                drawArc(
                    color = Color.White.copy(alpha = 0.30f - index * 0.07f),
                    startAngle = 205f + index * 8f,
                    sweepAngle = 120f + index * 10f,
                    useCenter = false,
                    topLeft = Offset(orbCenter.x - radius * 0.92f, orbCenter.y - radius * 0.72f),
                    size = Size(radius * 1.7f, radius * 1.3f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawCircle(Color.White.copy(alpha = 0.18f), radius = radius * 0.32f, center = orbCenter - Offset(radius * 0.4f, radius * 0.45f))
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color.White.copy(alpha = 0.86f), MaterialTheme.typography.titleSmall)
            Column {
                Text(
                    auroraDigits(units),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        shadow = Shadow(Color(0xFF8E7BFF), blurRadius = 26f, offset = Offset.Zero),
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    labels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB7A9FF))
                    }
                }
                if (units.isFinished) {
                    Text(finish, style = MaterialTheme.typography.labelLarge, color = Color(0xFF9DF3D0), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 2. Aurora veil

@Composable
private fun AuroraVeilFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 12_000 else 6_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aurora-phase",
    )
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF0A1B36), Color(0xFF07122A))))) {
        Canvas(Modifier.fillMaxSize()) {
            val ribbons = listOf(
                Triple(Color(0xFF2FB8A6), 0.10f, 0.34f),
                Triple(Color(0xFF7B5BD6), 0.24f, 0.52f),
                Triple(Color(0xFF3F8CE0), 0.40f, 0.66f),
            )
            ribbons.forEachIndexed { index, (color, baseY, height) ->
                val offset = (phase * (0.10f + index * 0.04f)) - 0.05f
                val path = Path().apply {
                    moveTo(0f, size.height * (baseY + offset))
                    cubicTo(
                        size.width * 0.28f,
                        size.height * (baseY - height * 0.45f + offset),
                        size.width * 0.62f,
                        size.height * (baseY + height * 0.55f - offset),
                        size.width,
                        size.height * (baseY + height * 0.1f - offset),
                    )
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f - index * 0.08f), Color.Transparent)))
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color.White.copy(alpha = 0.9f), MaterialTheme.typography.titleSmall)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    auroraDigits(units),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp,
                        shadow = Shadow(Color(0xFF2FB8A6), blurRadius = 24f, offset = Offset.Zero),
                    ),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    if (units.isFinished) finish else labels.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9FE8DC),
                )
            }
        }
    }
}

@Composable
private fun auroraDigits(units: CountdownUnits): String {
    val mode = LocalNumeralMode.current
    return remember(units, mode) {
        listOf(units.days.toString(), units.hours.toString().padStart(2, '0'), units.minutes.toString().padStart(2, '0'), units.seconds.toString().padStart(2, '0'))
            .joinToString(" : ") { TextNormalizer.formatDigits(it, mode) }
    }
}

// ---------------------------------------------------------------- 3. Neon grid

@Composable
private fun NeonGridFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "neon-grid")
    val glow by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 6_000 else 2_200), RepeatMode.Reverse),
        label = "neon-glow",
    )
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Color(0xFF25064B), Color(0xFF0B0B2B))))) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * 0.62f
            val sunCenter = Offset(size.width * 0.5f, horizon)
            val sunRadius = size.minDimension * 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF3DA6), Color(0xFFFF8A3D).copy(alpha = 0.55f), Color.Transparent),
                    center = sunCenter,
                    radius = sunRadius * 1.6f,
                ),
                radius = sunRadius,
                center = sunCenter,
            )
            var stripe = 0
            while (stripe * 9f < sunRadius) {
                val y = horizon - sunRadius + stripe * 9f
                val thickness = 1.5f + stripe * 0.9f
                drawRect(
                    color = Color(0xFF25064B).copy(alpha = 0.9f),
                    topLeft = Offset(sunCenter.x - sunRadius * 1.2f, y),
                    size = Size(sunRadius * 2.4f, thickness),
                )
                stripe++
            }
            drawLine(Color(0xFF0BD3E0).copy(alpha = 0.5f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx())
            repeat(12) { index ->
                val fraction = index / 11f
                val y = horizon + (size.height - horizon) * fraction * fraction
                drawLine(Color(0xFF0BD3E0).copy(alpha = 0.30f + 0.35f * glow * fraction), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            repeat(11) { index ->
                val x = size.width * index / 10f
                drawLine(
                    Color(0xFF0BD3E0).copy(alpha = 0.22f),
                    Offset(size.width * 0.5f + (x - size.width * 0.5f) * 0.18f, horizon),
                    Offset(x, size.height),
                    1.dp.toPx(),
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color(0xFFB9F6FF), MaterialTheme.typography.titleSmall)
            Column {
                Text(
                    auroraDigits(units),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(Color(0xFF00F5D4).copy(alpha = glow), blurRadius = 22f, offset = Offset.Zero),
                    ),
                    color = Color(0xFFEAFFFF),
                    maxLines = 1,
                )
                Text(
                    if (units.isFinished) finish else labels.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF8AD8),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 4. Flip studio

@Composable
private fun FlipStudioFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val values = listOf(
        daysText(units),
        rememberDigits(units) { it.hours },
        rememberDigits(units) { it.minutes },
        rememberDigits(units) { it.seconds },
    )
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFF4F1FB), Color(0xFFE2DCF3)))),
    ) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CountdownFaceTitle(title, Color(0xFF2A2450), MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
                values.forEachIndexed { index, value ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = if (compact) 38.dp else 54.dp, height = if (compact) 50.dp else 66.dp)
                                .shadow(6.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDE9FA)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22231E4B)).align(Alignment.Center))
                            Text(
                                value,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color(0xFF231E4B),
                            )
                        }
                        if (!compact) {
                            Text(labels.getOrElse(index) { "" }, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6C6590), modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
            Text(
                if (units.isFinished) finish else labels.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6C6590),
            )
        }
    }
}

// ---------------------------------------------------------------- 5. Glass layers

@Composable
private fun GlassLayersFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "glass")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 14_000 else 8_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glass-drift",
    )
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B2F73), Color(0xFF171436))))) {
        Canvas(Modifier.fillMaxSize()) {
            val orbs = listOf(
                Triple(Color(0xFF8FD4FF), Offset(size.width * (0.18f + drift * 0.06f), size.height * 0.24f), size.minDimension * 0.30f),
                Triple(Color(0xFFC3A8FF), Offset(size.width * (0.80f - drift * 0.05f), size.height * 0.34f), size.minDimension * 0.26f),
                Triple(Color(0xFF6E63C8), Offset(size.width * (0.52f + drift * 0.08f), size.height * 0.86f), size.minDimension * 0.34f),
            )
            orbs.forEach { (color, center, radius) ->
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = 0.75f), Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassPanel(
                    value = daysText(units),
                    label = labels.getOrElse(0) { "" },
                    modifier = Modifier.weight(1.1f),
                    compact = compact,
                )
                GlassPanel(
                    value = "${rememberDigits(units) { it.hours }}:${rememberDigits(units) { it.minutes }}",
                    label = "${labels.getOrElse(1) { "" }} · ${labels.getOrElse(2) { "" }}",
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
                GlassPanel(
                    value = rememberDigits(units) { it.seconds },
                    label = labels.getOrElse(3) { "" },
                    modifier = Modifier.weight(0.7f),
                    compact = compact,
                )
            }
            Text(
                if (units.isFinished) finish else labels.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun GlassPanel(value: String, label: String, modifier: Modifier, compact: Boolean) {
    Box(
        modifier
            .height(if (compact) 62.dp else 84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- 6. Orbital rings

@Composable
private fun OrbitalRingsFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "orbital")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 90_000 else 24_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "orbital-spin",
    )
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(listOf(Color(0xFF16204C), Color(0xFF05060F))))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.52f)
            val base = size.minDimension * 0.34f
            val rings = listOf(
                base to Pair(Color(0xFF5CE1E6), units.seconds / 60f),
                (base - 16.dp.toPx()) to Pair(Color(0xFF8C7BFF), units.minutes / 60f),
                (base - 32.dp.toPx()) to Pair(Color(0xFFFFB25E), units.hours / 24f),
                (base - 48.dp.toPx()) to Pair(Color(0xFFFF6E8A), (units.days % 30L) / 30f),
            )
            rings.forEach { (radius, pair) ->
                if (radius <= 0f) return@forEach
                val (color, fraction) = pair
                drawCircle(color.copy(alpha = 0.16f), radius = radius, center = center, style = Stroke(2.dp.toPx()))
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0.01f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val outer = base + 12.dp.toPx()
            val radians = Math.toRadians((spin - 90f).toDouble())
            val dot = Offset(center.x + cos(radians).toFloat() * outer, center.y + sin(radians).toFloat() * outer)
            drawCircle(Color.White, radius = 3.5.dp.toPx(), center = dot)
            drawCircle(Color(0xFF5CE1E6).copy(alpha = 0.35f), radius = 8.dp.toPx(), center = dot)
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color(0xFFA8B6FF), MaterialTheme.typography.titleSmall)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    daysText(units),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(labels.getOrElse(0) { "" }, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5CE1E6))
                Spacer(Modifier.height(2.dp))
                Text(
                    "${rememberDigits(units) { it.hours }}:${rememberDigits(units) { it.minutes }}:${rememberDigits(units) { it.seconds }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFA8B6FF),
                )
                if (units.isFinished) Text(finish, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB25E))
            }
        }
    }
}

// ---------------------------------------------------------------- 7. Terminal matrix

@Composable
private fun TerminalMatrixFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "terminal")
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 3_000 else 900), RepeatMode.Reverse),
        label = "cursor-blink",
    )
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF03130A))) {
        Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawRect(Color(0xFF68FF9A).copy(alpha = 0.045f), topLeft = Offset(0f, y), size = Size(size.width, 1.2f))
                y += 4.dp.toPx()
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "> marbledo --countdown",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF68FF9A).copy(alpha = 0.8f),
                maxLines = 1,
            )
            Column {
                Text(
                    auroraDigits(units),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color(0xFF7CFFAB),
                    maxLines = 1,
                )
                Text(
                    (if (units.isFinished) finish else labels.joinToString("/")) + (if (blink > 0.5f) " _" else "  "),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF37C46C),
                    maxLines = 1,
                )
                Text(
                    "# ${title}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF1E8C4B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 8. Liquid tide

@Composable
private fun LiquidTideFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "tide")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 12_000 else 4_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tide-phase",
    )
    val level = 0.42f + ((units.totalSeconds % 86_400L) / 86_400f) * 0.34f
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFEAF8F6), Color(0xFFCDEDF3))))) {
        Canvas(Modifier.fillMaxSize()) {
            val waterTop = size.height * (1f - level)
            val amplitude = size.height * 0.045f * (1f + phase * 0.4f)
            val wave = Path().apply {
                moveTo(0f, waterTop)
                var x = 0f
                var up = true
                while (x < size.width) {
                    val nextX = x + size.width / 6f
                    val controlY = if (up) waterTop - amplitude else waterTop + amplitude
                    quadraticBezierTo(x + size.width / 12f, controlY, nextX, waterTop)
                    x = nextX
                    up = !up
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(Color(0xFF6ED3C9), Color(0xFF1E7EA8))))
            drawCircle(Color.White.copy(alpha = 0.18f), radius = size.minDimension * 0.22f, center = Offset(size.width * 0.82f, size.height * 0.2f))
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color(0xFF12556B), MaterialTheme.typography.titleSmall)
            Column {
                Text(
                    auroraDigits(units),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0B3B4D),
                    maxLines = 1,
                )
                Text(
                    if (units.isFinished) finish else labels.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1E6C86),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 9. Paper type

@Composable
private fun PaperTypeFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFF8F2E7))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFE0D3BC), topLeft = Offset(0f, size.height * 0.72f), size = Size(size.width, 1.5f))
            drawRect(Color(0xFFE0D3BC), topLeft = Offset(0f, size.height * 0.78f), size = Size(size.width, 1.5f))
            drawRect(Color(0xFF1F1B16), topLeft = Offset(0f, 0f), size = Size(size.width * 0.22f, 4.dp.toPx()))
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp),
                color = Color(0xFF6B6151),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                Text(
                    daysText(units),
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
                    color = Color(0xFF1F1B16),
                    maxLines = 1,
                )
                Text(
                    "— ${labels.getOrElse(0) { "" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8A7E69),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${rememberDigits(units) { it.hours }}:${rememberDigits(units) { it.minutes }}:${rememberDigits(units) { it.seconds }}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF3A342A),
                )
                if (units.isFinished) {
                    Text(finish, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB4562F))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 10. Stellar night

@Composable
private fun StellarNightFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val transition = rememberInfiniteTransition(label = "stellar")
    val twinkle by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reducedMotion()) 8_000 else 2_600), RepeatMode.Reverse),
        label = "twinkle",
    )
    val stars = remember { List(46) { Offset(Random(7 * it + 3).nextFloat(), Random(11 * it + 5).nextFloat()) } }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(listOf(Color(0xFF1A1F4C), Color(0xFF06070F))))) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEachIndexed { index, star ->
                val alpha = if (index % 3 == 0) twinkle else 1f - twinkle * 0.5f
                drawCircle(Color.White.copy(alpha = alpha.coerceIn(0.15f, 0.95f)), radius = (0.6f + (index % 3) * 0.5f).dp.toPx(), center = Offset(star.x * size.width, star.y * size.height))
            }
            val anchors = listOf(Offset(0.18f, 0.30f), Offset(0.42f, 0.20f), Offset(0.62f, 0.36f), Offset(0.84f, 0.22f))
            anchors.zipWithNext().forEach { (start, end) ->
                drawLine(
                    Color(0xFF8C7BFF).copy(alpha = 0.35f),
                    Offset(start.x * size.width, start.y * size.height),
                    Offset(end.x * size.width, end.y * size.height),
                    1.dp.toPx(),
                )
            }
            anchors.forEach { anchor ->
                drawCircle(Color(0xFFB7A9FF), radius = 3.dp.toPx(), center = Offset(anchor.x * size.width, anchor.y * size.height))
            }
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 14.dp else 22.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFFC9D2FF), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                auroraDigits(units),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, letterSpacing = 2.sp),
                color = Color.White,
                maxLines = 1,
            )
            Text(
                if (units.isFinished) finish else labels.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9BA7E8),
            )
        }
    }
}

// ---------------------------------------------------------------- 11. Moment blocks

@Composable
private fun MomentBlocksFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    val blocks = listOf(
        Triple(daysText(units), labels.getOrElse(0) { "" }, (units.days % 30L) / 30f),
        Triple(rememberDigits(units) { it.hours }, labels.getOrElse(1) { "" }, units.hours / 24f),
        Triple(rememberDigits(units) { it.minutes }, labels.getOrElse(2) { "" }, units.minutes / 60f),
        Triple(rememberDigits(units) { it.seconds }, labels.getOrElse(3) { "" }, units.seconds / 60f),
    )
    val accents = listOf(Color(0xFFFFB25E), Color(0xFFFF6E8A), Color(0xFF6D5BD0), Color(0xFF2FB8A6))
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFF14141C))) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 13.dp else 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color.White.copy(alpha = 0.85f), MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp), modifier = Modifier.fillMaxWidth()) {
                blocks.forEachIndexed { index, (value, label, fraction) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (compact) 52.dp else 68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accents[index].copy(alpha = 0.16f))
                                .border(1.dp, accents[index].copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = accents[index], maxLines = 1)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(accents[index]),
                            )
                        }
                        if (!compact) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                        }
                    }
                }
            }
            Text(
                if (units.isFinished) finish else labels.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

// ---------------------------------------------------------------- 12. Dial gauge

@Composable
private fun DialGaugeFace(title: String, units: CountdownUnits, labels: List<String>, finish: String, modifier: Modifier, compact: Boolean) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF20242F), Color(0xFF101219))))) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.58f)
            val radius = size.minDimension * 0.36f
            drawCircle(Color(0xFF2B3040), radius = radius, center = center)
            drawCircle(Color(0xFF3C4356), radius = radius, center = center, style = Stroke(1.5.dp.toPx()))
            repeat(60) { index ->
                val angle = Math.toRadians((index * 6 - 90).toDouble())
                val isMajor = index % 5 == 0
                val inner = radius * (if (isMajor) 0.80f else 0.87f)
                val outer = radius * 0.95f
                drawLine(
                    color = if (isMajor) Color(0xFFFFC46B) else Color(0xFF6C7690),
                    start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                    end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val minuteAngle = Math.toRadians((units.minutes * 6 + units.seconds * 0.1 - 90).toDouble())
            drawLine(
                color = Color(0xFFFFC46B),
                start = center,
                end = Offset(center.x + cos(minuteAngle).toFloat() * radius * 0.72f, center.y + sin(minuteAngle).toFloat() * radius * 0.72f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val secondAngle = Math.toRadians((units.seconds * 6 - 90).toDouble())
            drawLine(
                color = Color(0xFF7CFFAB),
                start = center,
                end = Offset(center.x + cos(secondAngle).toFloat() * radius * 0.88f, center.y + sin(secondAngle).toFloat() * radius * 0.88f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(Color(0xFFFFC46B), radius = 5.dp.toPx(), center = center)
        }
        Column(
            Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CountdownFaceTitle(title, Color(0xFFC9CFE0), MaterialTheme.typography.titleSmall)
            Column {
                Text(
                    "${daysText(units)}  ${rememberDigits(units) { it.hours }}:${rememberDigits(units) { it.minutes }}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color(0xFFECF0FA),
                    maxLines = 1,
                )
                Text(
                    if (units.isFinished) finish else "${labels.getOrElse(0) { "" }} · ${labels.getOrElse(1) { "" }} · ${labels.getOrElse(2) { "" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFC46B),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- picker swatch

/** Compact colour signature used by the theme picker rows and the quick-switch strip. */
@Composable
fun CountdownThemeSwatch(
    theme: CountdownTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = if (showLabel) 62.dp else 40.dp, height = if (showLabel) 46.dp else 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(countdownThemeBrush(theme))
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp),
                ),
        )
        if (showLabel) {
            Text(
                countdownThemeLabel(theme),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp).width(66.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small helper used by the hero card to show the target day under the face. */
@Composable
fun CountdownCaption(theme: CountdownTheme, modifier: Modifier = Modifier) {
    Text(
        countdownThemeCaption(theme),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
