package com.marbledo.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitTheme
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.HabitDay
import com.marbledo.domain.util.HabitDayState
import com.marbledo.domain.util.HabitStats
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Everything known about one habit: where it stands today, the shape of its last months, the
 * milestone it is walking towards, and the coaching that fits what actually happened — not a
 * generic pep talk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailSheet(
    habit: Habit,
    entries: List<HabitEntry>,
    stats: HabitStats,
    today: LocalDate,
    weekStartsSaturday: Boolean,
    languageTag: String,
    onEdit: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val numeralMode = LocalNumeralMode.current
    val darkMode = habitDarkMode()
    val palette = remember(habit.themeId, darkMode) {
        habitPalette(HabitTheme.from(habit.themeId), darkMode)
    }
    val heat = remember(habit.id, entries, today, weekStartsSaturday) {
        HabitAnalytics.heatmap(habit, entries, today, weeks = 12, weekStartsSaturday = weekStartsSaturday)
    }
    val week = remember(heat) { heat.takeLast(7) }
    val recent = remember(heat) { heat.filter { !it.date.isAfter(today) }.takeLast(14).reversed() }
    val insights = remember(habit, stats) { HabitAnalytics.insights(habit, stats) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(habit.icon, fontSize = 25.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        habitPhaseLabel(stats.phase),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = palette.card,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HabitRing(
                            progress = stats.score / 100f,
                            palette = palette,
                            modifier = Modifier.size(96.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    habitNumber(stats.score, numeralMode),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = palette.onCard,
                                )
                                Text(
                                    stringResource(R.string.habit_stat_score),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                habitPhaseHint(stats.phase),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onCard,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val run = if (habit.kind == HabitKind.QUIT) stats.cleanDays else stats.currentStreak
                                Text(
                                    "\uD83D\uDD25 ${habitNumber(run, numeralMode)} ${stringResource(R.string.habit_stat_days)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accent,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HabitStatTile(
                            label = stringResource(R.string.habit_stat_longest),
                            value = habitNumber(stats.longestStreak, numeralMode),
                            palette = palette,
                            modifier = Modifier.weight(1f),
                        )
                        HabitStatTile(
                            label = stringResource(R.string.habit_stat_rate),
                            value = "${habitNumber((stats.completionRate * 100).roundToInt(), numeralMode)}%",
                            palette = palette,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (habit.kind == HabitKind.QUIT) {
                            HabitStatTile(
                                label = stringResource(R.string.habit_stat_clean),
                                value = habitNumber(stats.cleanDays, numeralMode),
                                palette = palette,
                                modifier = Modifier.weight(1f),
                            )
                            HabitStatTile(
                                label = stringResource(R.string.habit_stat_slips),
                                value = habitNumber(stats.slips, numeralMode),
                                palette = palette,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            HabitStatTile(
                                label = stringResource(R.string.habit_stat_total),
                                value = habitNumber(if (habit.tracking == HabitTracking.COUNT) stats.totalValue else stats.totalDone, numeralMode),
                                palette = palette,
                                modifier = Modifier.weight(1f),
                            )
                            HabitStatTile(
                                label = stringResource(R.string.habit_stat_streak),
                                value = habitNumber(stats.currentStreak, numeralMode),
                                palette = palette,
                                modifier = Modifier.weight(1f),
                                emphasize = true,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            SectionLabel(stringResource(R.string.habit_history_title))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = palette.card,
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    HabitWeekStrip(week, palette, languageTag)
                    Spacer(Modifier.height(14.dp))
                    HabitHeatmap(days = heat, palette = palette, weeks = 12)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.habit_heatmap_legend_less),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            fontSize = 10.sp,
                        )
                        palette.heat.forEach { shade ->
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .size(width = 12.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(shade),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.habit_heatmap_legend_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            SectionLabel(stringResource(R.string.habit_milestones_title))
            HabitMilestoneRail(stats = stats, palette = palette, numeralMode = numeralMode)
            Spacer(Modifier.height(6.dp))
            stats.nextMilestone?.let { milestone ->
                Text(
                    stringResource(
                        R.string.habit_milestone_remaining,
                        habitNumber(stats.daysToNextMilestone, numeralMode),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(palette.accentSoft.copy(alpha = 0.55f))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.habit_insights_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                insights.forEach { insight ->
                    HabitInsightRow(
                        text = habitInsightText(habit, insight, numeralMode),
                        palette = palette,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (recent.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recent.take(7).forEach { day -> RecentDayRow(day, palette, numeralMode, languageTag) }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (habit.cue.isNotBlank() || habit.identity.isNotBlank() || habit.why.isNotBlank()) {
                SectionLabel(stringResource(R.string.habit_field_why))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (habit.cue.isNotBlank()) {
                        Text(stringResource(R.string.habit_field_cue), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(habit.cue, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (habit.identity.isNotBlank()) {
                        Text(stringResource(R.string.habit_field_identity), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(habit.identity, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (habit.why.isNotBlank()) {
                        Text(stringResource(R.string.habit_field_why), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(habit.why, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = {
                    onArchive(!habit.isArchived)
                    onDismiss()
                }) {
                    Icon(
                        if (habit.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (habit.isArchived) R.string.habit_unarchive else R.string.habit_archive))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.habit_delete))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.habit_editor_edit))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun RecentDayRow(
    day: HabitDay,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    languageTag: String,
) {
    val dayOfMonth = remember(day.date, languageTag) {
        if (languageTag == "fa") {
            val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))
            cal.timeInMillis = day.date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
        } else {
            day.date.dayOfMonth
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when (day.state) {
                        HabitDayState.DONE -> palette.accent
                        HabitDayState.PARTIAL -> palette.accent.copy(alpha = 0.45f)
                        HabitDayState.MISSED -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
                        HabitDayState.TODAY -> palette.accent.copy(alpha = 0.25f)
                        else -> palette.track
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${weekdayName(day.date.dayOfWeek.value, languageTag)} ${habitNumber(dayOfMonth, numeralMode)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            habitDayStateLabel(day.state),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The palette a habit's own detail screen draws with, resolved once per habit. */
@Composable
internal fun habitPaletteFor(habit: Habit): HabitPalette =
    habitPalette(HabitTheme.from(habit.themeId), habitDarkMode())
