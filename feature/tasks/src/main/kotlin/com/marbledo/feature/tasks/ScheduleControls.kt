package com.marbledo.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.formatClock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Common times of day offered as one-tap chips. "Anytime" is handled separately. */
internal val schedulePresetTimes: List<LocalTime> = listOf(
    LocalTime.of(8, 0),
    LocalTime.of(12, 0),
    LocalTime.of(18, 0),
    LocalTime.of(21, 0),
)

/** Day offsets stay relative to today so the chips never go stale while a sheet is open. */
internal val schedulePresetDays: List<Pair<Int, Long>> = listOf(
    R.string.tasks_today to 0L,
    R.string.tasks_tomorrow to 1L,
    R.string.tasks_date_next_week to 7L,
    R.string.tasks_date_next_month to 30L,
)

/**
 * One-tap date and time chips shared by the quick-add and editor sheets. The first chip in each row clears
 * or anytime-selects; the rest pick a relative day or a common hour.
 */
@Composable
internal fun ScheduleQuickChips(
    schedule: TaskSchedule,
    zone: ZoneId,
    numeralMode: NumeralMode,
    onPickDate: (LocalDate) -> Unit,
    onPickTime: (LocalTime?) -> Unit,
    onClear: () -> Unit,
) {
    val hasSchedule = !schedule.isEmpty
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
        item {
            FilterChip(
                selected = !hasSchedule,
                onClick = onClear,
                label = { Text(stringResource(R.string.tasks_date_none)) },
            )
        }
        items(schedulePresetDays) { (labelRes, dayOffset) ->
            val target = LocalDate.now(zone).plusDays(dayOffset)
            FilterChip(
                selected = schedule.date == target,
                onClick = { onPickDate(target) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
        item {
            FilterChip(
                selected = hasSchedule && schedule.time == null,
                onClick = { onPickTime(null) },
                label = { Text(stringResource(R.string.tasks_anytime)) },
            )
        }
        items(schedulePresetTimes) { preset ->
            FilterChip(
                selected = schedule.time == preset,
                onClick = { onPickTime(preset) },
                label = { Text(TextNormalizer.formatDigits(formatClock(preset), numeralMode)) },
            )
        }
    }
}
