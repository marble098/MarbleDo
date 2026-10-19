package com.marbledo.feature.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitTheme
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.HabitDay
import com.marbledo.domain.util.HabitDayState
import com.marbledo.domain.util.HabitInsight
import com.marbledo.domain.util.HabitInsightKey
import com.marbledo.domain.util.HabitPhase
import com.marbledo.domain.util.HabitStats
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.PersianDateUtils
import kotlin.math.roundToInt

/** Whether themed habit surfaces should draw their dark or light variant. */
@Composable
fun habitDarkMode(): Boolean {
    val background = MaterialTheme.colorScheme.background
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return luminance < 0.5f
}

fun habitBackgroundBrush(palette: HabitPalette): Brush =
    Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom))

/** Digits follow the app's numeral preference, so Persian numerals stay Persian here too. */
@Composable
fun habitNumber(value: Int, numeralMode: NumeralMode): String =
    TextNormalizer.formatDigits(value.toString(), numeralMode)

/**
 * The ring that carries every "how am I doing" number: today's completion, the 30-day rate or a
 * single habit's score. It is drawn by hand so the same look can be reused at any size.
 */
@Composable
fun HabitRing(
    progress: Float,
    palette: HabitPalette,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    content: @Composable () -> Unit = {},
) {
    val stroke = with(LocalDensity.current) { strokeWidth.toPx() }
    val clamped = progress.coerceIn(0f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 700),
        label = "habitRing",
    )
    val animated = if (LocalReduceMotion.current) clamped else animatedValue
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2f, size.height / 2f)
            drawArc(
                color = palette.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                // sweepGradient starts at 3 o'clock; rotate it so the highlight begins at the top
                // with the arc instead of jumping halfway around the ring.
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(palette.accent.copy(alpha = 0.55f), palette.accent)),
                        startAngle = 0f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/**
 * A month-and-a-half of days in columns: weeks left to right, weekdays top to bottom. Days that
 * were not planned (and days before tracking began) stay neutral instead of counting as failures.
 */
@Composable
fun HabitHeatmap(
    days: List<HabitDay>,
    palette: HabitPalette,
    weeks: Int = 12,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { week ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { day ->
                    val cell = days.getOrNull(week * 7 + day)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(heatColorFor(cell, palette)),
                    )
                }
            }
        }
    }
}

private fun heatColorFor(day: HabitDay?, palette: HabitPalette): Color {
    if (day == null) return Color.Transparent
    return when (day.state) {
        HabitDayState.DONE -> palette.heat[palette.heat.lastIndex]
        HabitDayState.PARTIAL -> palette.heat[(palette.heat.size - 1) / 2]
        HabitDayState.MISSED -> palette.track.copy(alpha = 0.85f)
        HabitDayState.TODAY -> palette.accent.copy(alpha = 0.35f)
        HabitDayState.FUTURE -> Color.Transparent
        HabitDayState.OFF -> palette.track.copy(alpha = 0.35f)
    }
}

/** The current week at a glance: seven dots, today outlined. */
@Composable
fun HabitWeekStrip(
    days: List<HabitDay>,
    palette: HabitPalette,
    languageTag: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val isToday = day.state == HabitDayState.TODAY
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    weekdayShortLabel(day.date.dayOfWeek.value, languageTag),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(if (isToday) Modifier.border(1.5.dp, palette.accent, CircleShape) else Modifier)
                        .background(heatColorFor(day, palette)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day.state == HabitDayState.DONE) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.backgroundTop,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One number with a caption, used in the two-by-two stat grids. */
@Composable
fun HabitStatTile(
    label: String,
    value: String,
    palette: HabitPalette,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (emphasize) palette.accentSoft else palette.track.copy(alpha = 0.45f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (emphasize) palette.accent else palette.onCard,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
    }
}

@Composable
fun HabitMilestoneRail(
    stats: HabitStats,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HabitAnalytics.MILESTONES.take(7).forEach { milestone ->
            val reached = stats.longestStreak >= milestone
            val isNext = stats.nextMilestone == milestone
            val weight = if (isNext) 1.4f else 1f
            Column(
                modifier = Modifier.weight(weight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (isNext) Modifier.border(1.5.dp, palette.accent, RoundedCornerShape(10.dp)) else Modifier)
                        .background(
                            when {
                                reached -> palette.accent
                                isNext -> palette.accentSoft
                                else -> palette.track.copy(alpha = 0.5f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        habitNumber(milestone, numeralMode),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (reached) palette.backgroundTop else palette.onCard,
                    )
                }
                Text(
                    if (reached) stringResource(R.string.habit_milestone_reached) else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = palette.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun HabitInsightRow(text: String, palette: HabitPalette, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(palette.accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onCard,
        )
    }
}

/** Emoji symbols offered when naming a habit; kept short so the row stays scannable. */
val HABIT_ICONS: List<String> = listOf(
    "\uD83C\uDF31", "\uD83D\uDCDA", "\uD83C\uDFC3", "\uD83D\uDCA7", "\uD83E\uDDD8", "\uD83C\uDF5E",
    "\uD83D\uDCDD", "\uD83D\uDD52", "\uD83C\uDFB5", "\uD83D\uDCAA", "\uD83E\uDDE0", "\uD83D\uDEB6",
    "\uD83D\uDCA4", "\uD83C\uDFAF", "\uD83C\uDFD5", "\uD83D\uDECD", "\uD83D\uDCF1", "\uD83D\uDEAA",
)

@Composable
fun HabitIconPicker(
    selected: String,
    onSelect: (String) -> Unit,
    palette: HabitPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HABIT_ICONS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { icon ->
                    val isSelected = icon == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (isSelected) Modifier.border(2.dp, palette.accent, RoundedCornerShape(14.dp)) else Modifier)
                            .background(if (isSelected) palette.accentSoft else palette.track.copy(alpha = 0.4f))
                            .clickable { onSelect(icon) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

/** Row of theme swatches used to pick the look of the progress screens. */
@Composable
fun HabitThemeSwatchRow(
    selected: HabitTheme,
    onSelect: (HabitTheme) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(HabitTheme.entries, key = { it.id }) { theme ->
            HabitThemeSwatch(theme = theme, selected = theme == selected, dark = dark, onClick = { onSelect(theme) })
        }
    }
}

@Composable
fun HabitThemeSwatch(
    theme: HabitTheme,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = habitPalette(theme, dark)
    Column(
        modifier = modifier
            .width(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.border(2.dp, palette.accent, RoundedCornerShape(18.dp)) else Modifier)
            .background(palette.card)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HabitRing(progress = 0.68f, palette = palette, modifier = Modifier.size(46.dp), strokeWidth = 7.dp) {
            Text(
                habitNumber(68, LocalNumeralMode.current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.onCard,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            palette.heat.forEach { shade ->
                Box(
                    Modifier
                        .size(width = 10.dp, height = 16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(shade),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            habitThemeLabel(theme),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.accent else palette.onCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One drawer of the cabinet. The front carries the label and the day's tally; tapping it slides
 * the drawer open onto the habit folders inside, exactly like a file drawer.
 */
@Composable
fun HabitDrawer(
    title: String,
    hint: String,
    badge: String,
    icon: ImageVector,
    expanded: Boolean,
    reduceMotion: Boolean,
    palette: HabitPalette,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.card,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onCard,
                    )
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 2)
                }
                Surface(shape = CircleShape, color = palette.accentSoft) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.habit_drawer_close) else stringResource(R.string.habit_drawer_open),
                    tint = palette.muted,
                    modifier = Modifier.padding(start = 4.dp).size(22.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(160)) + expandVertically(tween(240)),
                exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(140)) + shrinkVertically(tween(200)),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HabitRowDivider(palette)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * A habit folder as it sits inside its drawer: symbol, name, where it stands, and the control that
 * records today in one tap. Drawers paint the surface, so a folder is a flat row with a divider
 * rather than another card inside a card.
 */
@Composable
fun HabitFolderRow(
    habit: Habit,
    stats: HabitStats,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(habit.icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    habitMetaLine(habit, stats, numeralMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HabitDayControl(
                habit = habit,
                stats = stats,
                palette = palette,
                numeralMode = numeralMode,
                onToggleDone = onToggleDone,
                onAdjust = onAdjust,
            )
        }
        if (habit.tracking == HabitTracking.COUNT && habit.kind == HabitKind.BUILD) {
            HabitProgressBar(
                progress = (stats.todayValue.toFloat() / habit.dailyTarget.toFloat()).coerceIn(0f, 1f),
                palette = palette,
            )
        }
    }
}

/** Hairline between habit folders inside a drawer. */
@Composable
fun HabitRowDivider(palette: HabitPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(palette.track.copy(alpha = 0.7f)),
    )
}

@Composable
private fun HabitProgressBar(progress: Float, palette: HabitPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(palette.track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.accent),
        )
    }
}

@Composable
private fun HabitDayControl(
    habit: Habit,
    stats: HabitStats,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    onToggleDone: () -> Unit,
    onAdjust: (Int) -> Unit,
) {
    when (habit.kind) {
        HabitKind.QUIT -> {
            val slipped = stats.todayValue > 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (slipped) palette.track else palette.accentSoft)
                    .clickable(onClick = onToggleDone)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (slipped) stringResource(R.string.habit_slip_today) else stringResource(R.string.habit_clean_today),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (slipped) palette.onCard else palette.accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
        HabitKind.BUILD -> if (habit.tracking == HabitTracking.COUNT) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundIconButton(Icons.Outlined.Remove, stringResource(R.string.habit_decrease), palette) { onAdjust(-1) }
                Text(
                    stringResource(
                        R.string.habit_target_progress,
                        habitNumber(stats.todayValue, numeralMode),
                        habitNumber(habit.dailyTarget, numeralMode),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onCard,
                )
                RoundIconButton(Icons.Outlined.Add, stringResource(R.string.habit_increase), palette) { onAdjust(1) }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .then(if (!stats.todayDone) Modifier.border(1.5.dp, palette.track, CircleShape) else Modifier)
                    .background(if (stats.todayDone) palette.accent else palette.track.copy(alpha = 0.5f))
                    .clickable(onClick = onToggleDone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.habit_mark_done),
                    tint = if (stats.todayDone) palette.backgroundTop else palette.muted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    palette: HabitPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(palette.track.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = palette.onCard, modifier = Modifier.size(18.dp))
    }
}

/**
 * Today's headline: the ring, the count and the score, on the cabinet's themed surface. [footer]
 * carries the cabinet's secondary controls (theme look, widget) so the screen needs one card
 * instead of two stacked ones.
 */
@Composable
fun HabitTodayHeader(
    done: Int,
    scheduled: Int,
    score: Int,
    bestStreak: Int,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val progress = if (scheduled == 0) 0f else done.toFloat() / scheduled.toFloat()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.card,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitRing(progress = progress, palette = palette, modifier = Modifier.size(86.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${habitNumber(done, numeralMode)}/${habitNumber(scheduled, numeralMode)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = palette.onCard,
                        )
                        Text(
                            "${habitNumber((progress * 100).roundToInt(), numeralMode)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.habit_cabinet_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onCard,
                    )
                    Text(
                        if (scheduled == 0) {
                            stringResource(R.string.habit_summary_empty)
                        } else if (done >= scheduled) {
                            stringResource(R.string.habit_all_done)
                        } else {
                            stringResource(R.string.habit_today_progress, habitNumber(done, numeralMode), habitNumber(scheduled, numeralMode))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitOverviewStat(
                            value = habitNumber(score, numeralMode),
                            label = stringResource(R.string.habit_score_label),
                            palette = palette,
                            emphasize = true,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.height(26.dp).width(1.dp).background(palette.track))
                        Spacer(Modifier.width(12.dp))
                        HabitOverviewStat(
                            value = habitNumber(bestStreak, numeralMode),
                            label = stringResource(R.string.habit_best_streak_label),
                            palette = palette,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            footer()
        }
    }
}

/** One number over its caption; the header uses three of them instead of filled tiles. */
@Composable
private fun HabitOverviewStat(
    value: String,
    label: String,
    palette: HabitPalette,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) palette.accent else palette.onCard,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Compact rule under the header: the chosen look, its name, and the widget shortcut. */
@Composable
fun HabitThemeDotRow(
    selected: HabitTheme,
    onSelect: (HabitTheme) -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(HabitTheme.entries, key = { it.id }) { theme ->
            val palette = habitPalette(theme, dark)
            val isSelected = theme == selected
            // Resolved here: a composable label cannot be read inside the semantics lambda below.
            val label = habitThemeLabel(theme)
            val shape = CircleShape
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape)
                    .then(if (isSelected) Modifier.border(2.dp, palette.accent, shape) else Modifier)
                    .background(Brush.linearGradient(listOf(palette.accentSoft, palette.heat.last())))
                    .clickable { onSelect(theme) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
        }
    }
}

@Composable
private fun habitMetaLine(
    habit: Habit,
    stats: HabitStats,
    numeralMode: NumeralMode,
): String {
    val streak = habitNumber(if (habit.kind == HabitKind.QUIT) stats.cleanDays else stats.currentStreak, numeralMode)
    val unit = if (habit.kind == HabitKind.QUIT) {
        stringResource(R.string.habit_stat_clean)
    } else {
        stringResource(R.string.habit_stat_streak)
    }
    val phase = habitPhaseLabel(stats.phase)
    return "$unit $streak · $phase"
}

@Composable
fun habitPhaseLabel(phase: HabitPhase): String = stringResource(
    when (phase) {
        HabitPhase.STARTING -> R.string.habit_phase_starting
        HabitPhase.REPEATING -> R.string.habit_phase_repeating
        HabitPhase.SETTLING -> R.string.habit_phase_settling
        HabitPhase.AUTOMATIC -> R.string.habit_phase_automatic
    },
)

@Composable
fun habitPhaseHint(phase: HabitPhase): String = stringResource(
    when (phase) {
        HabitPhase.STARTING -> R.string.habit_phase_hint_starting
        HabitPhase.REPEATING -> R.string.habit_phase_hint_repeating
        HabitPhase.SETTLING -> R.string.habit_phase_hint_settling
        HabitPhase.AUTOMATIC -> R.string.habit_phase_hint_automatic
    },
)

@Composable
fun habitDayStateLabel(state: HabitDayState): String = stringResource(
    when (state) {
        HabitDayState.DONE -> R.string.habit_day_state_done
        HabitDayState.PARTIAL -> R.string.habit_day_state_partial
        HabitDayState.MISSED -> R.string.habit_day_state_missed
        HabitDayState.TODAY -> R.string.habit_day_state_today
        HabitDayState.FUTURE -> R.string.habit_day_state_today
        HabitDayState.OFF -> R.string.habit_day_state_off
    },
)

/** Turns a coaching key plus the habit's own words into one localised sentence. */
@Composable
fun habitInsightText(habit: Habit, insight: HabitInsight, numeralMode: NumeralMode): String {
    val value = habitNumber(insight.value, numeralMode)
    return when (insight.key) {
        HabitInsightKey.START_SMALL -> stringResource(R.string.habit_insight_start_small)
        HabitInsightKey.RELAPSE_KIND -> stringResource(R.string.habit_insight_relapse_kind, value)
        HabitInsightKey.NEVER_MISS_TWICE -> stringResource(R.string.habit_insight_never_miss_twice, value)
        HabitInsightKey.MILESTONE_CLOSE -> stringResource(R.string.habit_insight_milestone_close, value)
        HabitInsightKey.WEEKLY_TARGET_BEHIND -> stringResource(R.string.habit_insight_weekly_behind, value)
        HabitInsightKey.SHRINK_STEP -> stringResource(R.string.habit_insight_shrink_step)
        HabitInsightKey.SLIP_FREE -> stringResource(R.string.habit_insight_slip_free, value)
        HabitInsightKey.STRONG_WEEK -> stringResource(R.string.habit_insight_strong_week)
        HabitInsightKey.WEAK_WEEKDAY -> stringResource(
            R.string.habit_insight_weak_weekday,
            weekdayName(insight.weekday ?: 1),
        )
        HabitInsightKey.IDENTITY -> stringResource(R.string.habit_insight_identity, habit.identity)
        HabitInsightKey.STACK_IT -> stringResource(R.string.habit_insight_stack, habit.cue)
    }
}

/** Weekday name in the app's language; habits store Java's 1..7 (Monday..Sunday). */
@Composable
private fun weekdayShortLabel(dayOfWeekValue: Int, languageTag: String): String =
    weekdayName(dayOfWeekValue, languageTag, short = true)

@Composable
fun weekdayName(dayOfWeekValue: Int, languageTag: String = "fa", short: Boolean = false): String {
    // PersianDateUtils counts Saturday as 0, Java's DayOfWeek counts Monday as 1.
    val saturdayBasedIndex = (dayOfWeekValue + 1) % 7
    return if (short) {
        PersianDateUtils.weekdayShort(saturdayBasedIndex, languageTag)
    } else {
        PersianDateUtils.weekdayName(saturdayBasedIndex, languageTag)
    }
}
