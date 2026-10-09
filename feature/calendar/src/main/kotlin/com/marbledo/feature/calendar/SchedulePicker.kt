package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which parts of a schedule the picker edits. */
enum class SchedulePickerMode { DATE_AND_TIME, DATE_ONLY, TIME_ONLY }

private data class PickerDay(val date: LocalDate, val dayNumber: Int)

/**
 * Full date and time picker. Dates can be chosen in the Persian, Gregorian or Islamic civil calendar, and the
 * month and year can be jumped to directly. Times use the 24-hour dial or keyboard input, or can be left unset
 * ("anytime") when [allowAnyTime] is true.
 *
 * The caller owns [calendarMode] and receives changes through [onCalendarModeChange], so the choice can be saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePickerDialog(
    title: String,
    mode: SchedulePickerMode,
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    languageTag: String,
    numeralMode: NumeralMode,
    weekStartsSaturday: Boolean,
    calendarMode: CalendarDisplayMode,
    onCalendarModeChange: (CalendarDisplayMode) -> Unit,
    onConfirm: (LocalDate, LocalTime?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowAnyTime: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val showDate = mode != SchedulePickerMode.TIME_ONLY
    val showTime = mode != SchedulePickerMode.DATE_ONLY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var selectedIso by rememberSaveable { mutableStateOf((initialDate ?: today).toString()) }
    var viewIso by rememberSaveable { mutableStateOf((initialDate ?: today).toString()) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var hasTime by rememberSaveable {
        mutableStateOf(!allowAnyTime || initialTime != null || mode == SchedulePickerMode.TIME_ONLY)
    }
    var keyboardTime by rememberSaveable { mutableStateOf(false) }

    val selectedDate = LocalDate.parse(selectedIso)
    val viewDate = LocalDate.parse(viewIso)
    val startTime = initialTime ?: LocalTime.of(9, 0)
    val timeState = rememberTimePickerState(
        initialHour = startTime.hour,
        initialMinute = startTime.minute,
        is24Hour = true,
    )
    val chosenTime = LocalTime.of(timeState.hour, timeState.minute)
    val effectiveTime: LocalTime? = if (showTime && hasTime) chosenTime else null

    val viewFields = SchedulingCalendar.fieldsOf(viewDate, calendarMode, zone)
    val monthNames = calendarMonthNames(calendarMode, languageTag)
    val monthTitle = TextNormalizer.formatDigits(
        "${monthNames.getOrElse(viewFields.month) { "" }} ${viewFields.year}",
        numeralMode,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(0.94f).widthIn(max = 440.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (showDate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarDisplayMode.entries.forEach { option ->
                            FilterChip(
                                selected = calendarMode == option,
                                onClick = { onCalendarModeChange(option) },
                                label = { Text(calendarModeLabel(option)) },
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            viewIso = SchedulingCalendar.shiftMonths(viewDate, -1, calendarMode, zone).toString()
                        }) {
                            Icon(
                                if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
                                contentDescription = stringResource(R.string.calendar_previous_month),
                            )
                        }
                        Text(
                            monthTitle,
                            style = MarbleTextStyles.itemTitle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showMonthPicker = !showMonthPicker }
                                .padding(vertical = 8.dp),
                        )
                        IconButton(onClick = {
                            viewIso = SchedulingCalendar.shiftMonths(viewDate, 1, calendarMode, zone).toString()
                        }) {
                            Icon(
                                if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                                contentDescription = stringResource(R.string.calendar_next_month),
                            )
                        }
                    }

                    if (showMonthPicker) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            YearStep(-10) { months -> viewIso = SchedulingCalendar.shiftMonths(viewDate, months, calendarMode, zone).toString() }
                            YearStep(-1) { months -> viewIso = SchedulingCalendar.shiftMonths(viewDate, months, calendarMode, zone).toString() }
                            Text(
                                TextNormalizer.formatDigits(viewFields.year.toString(), numeralMode),
                                style = MarbleTextStyles.yearDigits.copy(fontSize = 22.sp, lineHeight = 28.sp),
                            )
                            YearStep(1) { months -> viewIso = SchedulingCalendar.shiftMonths(viewDate, months, calendarMode, zone).toString() }
                            YearStep(10) { months -> viewIso = SchedulingCalendar.shiftMonths(viewDate, months, calendarMode, zone).toString() }
                        }
                        monthNames.chunked(4).forEachIndexed { rowIndex, rowNames ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowNames.forEachIndexed { column, name ->
                                    val monthIndex = rowIndex * 4 + column
                                    FilterChip(
                                        selected = monthIndex == viewFields.month,
                                        onClick = {
                                            SchedulingCalendar.dateOf(
                                                CalendarFields(viewFields.year, monthIndex, 1),
                                                calendarMode,
                                                zone,
                                            )?.let { viewIso = it.toString() }
                                            showMonthPicker = false
                                        },
                                        label = { Text(name, maxLines = 1) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    } else {
                        DayGrid(
                            viewFields = viewFields,
                            calendarMode = calendarMode,
                            zone = zone,
                            weekStartsSaturday = weekStartsSaturday,
                            numeralMode = numeralMode,
                            selectedDate = selectedDate,
                            today = today,
                            onSelect = { date -> selectedIso = date.toString() },
                        )
                        TextButton(
                            onClick = {
                                selectedIso = today.toString()
                                viewIso = today.toString()
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.picker_today))
                        }
                    }
                }

                if (showTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.picker_time),
                            style = MarbleTextStyles.itemTitle,
                            modifier = Modifier.weight(1f),
                        )
                        if (allowAnyTime) {
                            FilterChip(
                                selected = !hasTime,
                                onClick = { hasTime = !hasTime },
                                label = { Text(stringResource(R.string.picker_all_day)) },
                            )
                        }
                    }
                    if (hasTime) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !keyboardTime,
                                onClick = { keyboardTime = false },
                                label = { Text(stringResource(R.string.picker_dial)) },
                            )
                            FilterChip(
                                selected = keyboardTime,
                                onClick = { keyboardTime = true },
                                label = { Text(stringResource(R.string.picker_keyboard)) },
                            )
                        }
                        if (keyboardTime) {
                            TimeInput(state = timeState, modifier = Modifier.fillMaxWidth())
                        } else {
                            TimePicker(state = timeState, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                val summary = when (mode) {
                    SchedulePickerMode.TIME_ONLY -> effectiveTime?.let { TextNormalizer.formatDigits(formatClock(it), numeralMode) }
                        ?: stringResource(R.string.picker_no_time)
                    else -> formatScheduleSummary(selectedDate, effectiveTime, calendarMode, languageTag, numeralMode, zone)
                }
                Text(
                    stringResource(R.string.picker_selected, summary),
                    style = MarbleTextStyles.metaLabel,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onClear != null) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.picker_clear)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
                    Spacer(Modifier.size(6.dp))
                    Button(onClick = { onConfirm(selectedDate, effectiveTime) }) {
                        Text(stringResource(R.string.picker_confirm))
                    }
                }
            }
        }
    }
}

/**
 * A single text field-like button that shows a schedule and opens [SchedulePickerDialog] when tapped.
 * Kept here so every feature presents schedules the same way.
 */
@Composable
fun SchedulePickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MarbleTextStyles.itemTitle)
            }
        }
    }
}

@Composable
private fun YearStep(years: Int, onStep: (Int) -> Unit) {
    val label = (if (years > 0) "+" else "−") + kotlin.math.abs(years).toString()
    TextButton(onClick = { onStep(years * 12) }) {
        Text(label, style = MarbleTextStyles.metaLabel)
    }
}

@Composable
private fun DayGrid(
    viewFields: CalendarFields,
    calendarMode: CalendarDisplayMode,
    zone: ZoneId,
    weekStartsSaturday: Boolean,
    numeralMode: NumeralMode,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val firstWeekday = SchedulingCalendar.firstDayOfWeek(viewFields.year, viewFields.month, calendarMode, zone)
    val blanks = leadingBlankCells(firstWeekday, weekStartsSaturday)
    val dayCount = SchedulingCalendar.daysInMonth(viewFields.year, viewFields.month, calendarMode, zone)
    val days: List<PickerDay?> = List<PickerDay?>(blanks) { null } + (1..dayCount).map { day ->
        SchedulingCalendar.dateOf(CalendarFields(viewFields.year, viewFields.month, day), calendarMode, zone)
            ?.let { PickerDay(it, day) }
    }
    val headers = listOf(
        stringResource(R.string.calendar_sat),
        stringResource(R.string.calendar_sun),
        stringResource(R.string.calendar_mon),
        stringResource(R.string.calendar_tue),
        stringResource(R.string.calendar_wed),
        stringResource(R.string.calendar_thu),
        stringResource(R.string.calendar_fri),
    )

    Row(Modifier.fillMaxWidth()) {
        weekdayColumnOrder(weekStartsSaturday).forEach { index ->
            Text(
                headers[index],
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MarbleTextStyles.metaLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    days.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            for (column in 0 until 7) {
                val cell = week.getOrNull(column)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cell != null) {
                        val selected = cell.date == selectedDate
                        val isToday = cell.date == today
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onSelect(cell.date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                TextNormalizer.formatDigits(cell.dayNumber.toString(), numeralMode),
                                style = MarbleTextStyles.itemTitle,
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isToday) FontWeight.Bold else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun calendarModeLabel(mode: CalendarDisplayMode): String = when (mode) {
    CalendarDisplayMode.PERSIAN -> stringResource(R.string.picker_calendar_persian)
    CalendarDisplayMode.GREGORIAN -> stringResource(R.string.picker_calendar_gregorian)
    CalendarDisplayMode.ISLAMIC_CIVIL -> stringResource(R.string.picker_calendar_islamic)
}
