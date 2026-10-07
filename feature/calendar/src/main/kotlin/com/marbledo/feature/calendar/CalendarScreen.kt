package com.marbledo.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class CalendarViewMode { MONTH, AGENDA, OCCASIONS, YEAR }

private data class DayCell(
    val epochMillis: Long,
    val year: Int,
    val month: Int,
    val day: Int,
    val weekdayIndex: Int,
    val inDisplayedMonth: Boolean,
    val isToday: Boolean,
    val taskCount: Int,
)

private data class MonthData(val monthIndex: Int, val year: Int, val cells: List<DayCell>)

@Composable
fun CalendarScreen(
    tasks: List<Task>,
    catalog: OccasionCatalog,
    occasionState: OccasionState,
    enabledCategories: Set<OccasionCategory>,
    languageTag: String,
    weekStartsSaturday: Boolean,
    lunarOffsetDays: Int,
    onRefreshOccasions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedIsoDate by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    val selectedDate = remember(selectedIsoDate, zone) {
        runCatching { LocalDate.parse(selectedIsoDate) }.getOrDefault(LocalDate.now(zone))
    }
    val monthData = remember(monthOffset, tasks, weekStartsSaturday, zone) {
        buildMonth(monthOffset, tasks, zone, weekStartsSaturday)
    }
    val index = remember(catalog, enabledCategories, lunarOffsetDays, monthData.cells.first().year, monthData.year) {
        OccasionIndex.build(
            catalog = catalog,
            jalaliYears = buildSet {
                add(monthData.year)
                monthData.cells.forEach { add(it.year) }
            },
            lunarOffsetDays = lunarOffsetDays,
            enabledCategories = enabledCategories,
            zone = zone,
        )
    }
    val selectedPersian = PersianDateUtils.of(selectedDate.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
    val selectedOccasions = index.on(selectedPersian.year, selectedPersian.month, selectedPersian.day)
    val matchingTasks = remember(tasks, selectedDate, zone) {
        tasks.filter { task ->
            task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == selectedDate } == true
        }
    }
    val todayEpoch = remember(zone) { LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    PersianDateUtils.tripleDate(todayEpoch, languageTag, numeralMode, lunarOffsetDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OccasionSyncButton(occasionState.isRefreshing, onRefreshOccasions)
        }

        OccasionSyncStatus(
            state = occasionState,
            languageTag = languageTag,
            numeralMode = numeralMode,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            IconButton(onClick = { monthOffset -= 1 }) {
                Icon(if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous_month))
            }
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.calendar_month_year, PersianDateUtils.monthName(monthData.monthIndex, languageTag), monthData.year), numeralMode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 4.dp).clickable {
                    monthOffset = 0
                    selectedIsoDate = LocalDate.now(zone).toString()
                },
            ) {
                Text(
                    stringResource(R.string.calendar_today),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CalendarViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewMode == mode,
                    onClick = { viewMode = mode },
                    label = {
                        Text(
                            when (mode) {
                                CalendarViewMode.MONTH -> stringResource(R.string.calendar_view_month)
                                CalendarViewMode.AGENDA -> stringResource(R.string.calendar_view_agenda)
                                CalendarViewMode.OCCASIONS -> stringResource(R.string.calendar_view_occasions)
                                CalendarViewMode.YEAR -> stringResource(R.string.calendar_view_year)
                            },
                        )
                    },
                )
            }
        }

        when (viewMode) {
            CalendarViewMode.MONTH -> {
                WeekdayHeader(weekStartsSaturday)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(monthData.cells, key = { it.epochMillis }) { cell ->
                        val dayOccasions = index.on(cell.year, cell.month, cell.day)
                        DayCellView(
                            cell = cell,
                            occasions = dayOccasions,
                            selected = cell.epochMillis == selectedEpoch(selectedDate, zone),
                            languageTag = languageTag,
                            numeralMode = numeralMode,
                            onClick = { selectedIsoDate = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate().toString() },
                        )
                    }
                }
                SelectedDayCard(
                    date = selectedDate,
                    persian = selectedPersian,
                    occasions = selectedOccasions,
                    tasks = matchingTasks,
                    languageTag = languageTag,
                    numeralMode = numeralMode,
                    lunarOffsetDays = lunarOffsetDays,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            CalendarViewMode.AGENDA -> {
                val agenda = monthData.cells.filter { cell ->
                    cell.inDisplayedMonth && (cell.taskCount > 0 || index.on(cell.year, cell.month, cell.day).isNotEmpty())
                }
                if (agenda.isEmpty()) {
                    EmptyMessage(stringResource(R.string.calendar_no_events), Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(agenda, key = { it.epochMillis }) { cell ->
                            AgendaRow(
                                cell = cell,
                                occasions = index.on(cell.year, cell.month, cell.day),
                                languageTag = languageTag,
                                numeralMode = numeralMode,
                                onClick = { selectedIsoDate = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate().toString() },
                            )
                        }
                    }
                }
            }

            CalendarViewMode.OCCASIONS -> {
                val monthOccasions = index.forMonth(monthData.year, monthData.monthIndex)
                Column(Modifier.weight(1f)) {
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.calendar_month_occasions_count, monthOccasions.size), numeralMode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    )
                    if (monthOccasions.isEmpty()) {
                        EmptyMessage(stringResource(R.string.calendar_no_month_occasions), Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(monthOccasions) { (day, occasion) ->
                                OccasionRow(
                                    day = day,
                                    occasion = occasion,
                                    languageTag = languageTag,
                                    numeralMode = numeralMode,
                                    weekdayIndex = weekdayIndexFor(monthData.year, monthData.monthIndex, day, zone),
                                )
                            }
                        }
                    }
                }
            }

            CalendarViewMode.YEAR -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..12).toList()) { month ->
                        val count = index.forMonth(monthData.year, month).size
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (month == monthData.monthIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                monthOffset += month - monthData.monthIndex
                                viewMode = CalendarViewMode.MONTH
                            },
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(PersianDateUtils.monthName(month, languageTag), style = MaterialTheme.typography.titleSmall)
                                if (count > 0) {
                                    Text(
                                        TextNormalizer.formatDigits(stringResource(R.string.calendar_occasions_short, count), numeralMode),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun selectedEpoch(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

@Composable
private fun OccasionSyncButton(refreshing: Boolean, onRefresh: () -> Unit) {
    IconButton(onClick = onRefresh, enabled = !refreshing) {
        if (refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.calendar_sync_now), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OccasionSyncStatus(state: OccasionState, languageTag: String, numeralMode: NumeralMode, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val message = when {
        state.isRefreshing -> stringResource(R.string.calendar_sync_refreshing)
        state.lastError -> stringResource(R.string.calendar_sync_error)
        state.lastUpdatedEpochMillis != null -> stringResource(
            R.string.calendar_sync_last,
            PersianDateUtils.gregorianDate(state.lastUpdatedEpochMillis, locale),
        )
        else -> stringResource(R.string.calendar_sync_bundled)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
        Text(
            TextNormalizer.formatDigits(stringResource(R.string.calendar_occasions_available, state.occasionCount), numeralMode) + " · " + message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeekdayHeader(weekStartsSaturday: Boolean) {
    val order = if (weekStartsSaturday) (0..6).toList() else listOf(1, 2, 3, 4, 5, 6, 0)
    val labels = listOf(
        R.string.calendar_sat, R.string.calendar_sun, R.string.calendar_mon, R.string.calendar_tue,
        R.string.calendar_wed, R.string.calendar_thu, R.string.calendar_fri,
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        order.forEach { weekdayIndex ->
            Text(
                stringResource(labels[weekdayIndex]),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DayCellView(
    cell: DayCell,
    occasions: List<Occasion>,
    selected: Boolean,
    languageTag: String,
    numeralMode: NumeralMode,
    onClick: () -> Unit,
) {
    val isHoliday = occasions.any(Occasion::isHoliday)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        cell.isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !cell.inDisplayedMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        isHoliday -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            TextNormalizer.formatDigits(cell.day.toString(), numeralMode),
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isToday || isHoliday) FontWeight.SemiBold else FontWeight.Normal,
        )
        occasions.firstOrNull()?.let { occasion ->
            Text(
                occasion.title(languageTag),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
            )
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (cell.taskCount > 0) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
            }
            if (occasions.size > 1) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary))
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    persian: PersianDate,
    occasions: List<Occasion>,
    tasks: List<Task>,
    languageTag: String,
    numeralMode: NumeralMode,
    lunarOffsetDays: Int,
    modifier: Modifier = Modifier,
) {
    val epoch = remember(date, persian.year, persian.month, persian.day) {
        PersianDateUtils.startOfJalaliDay(persian.year, persian.month, persian.day) ?: System.currentTimeMillis()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                PersianDateUtils.fullDateWithWeekday(epoch, languageTag, numeralMode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                PersianDateUtils.tripleDate(epoch, languageTag, numeralMode, lunarOffsetDays),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (occasions.isEmpty()) {
                Text(stringResource(R.string.calendar_no_occasions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                occasions.forEach { occasion ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(categoryColor(occasion.category)))
                        Text(occasion.title(languageTag), style = MaterialTheme.typography.bodyMedium)
                        if (occasion.isHoliday) {
                            Text(
                                stringResource(R.string.calendar_holiday_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, tasks.size), numeralMode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tasks.take(4).forEach { task ->
                Text("• ${task.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AgendaRow(
    cell: DayCell,
    occasions: List<Occasion>,
    languageTag: String,
    numeralMode: NumeralMode,
    onClick: () -> Unit,
) {
    val epoch = cell.epochMillis
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TextNormalizer.formatDigits(cell.day.toString(), numeralMode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(PersianDateUtils.weekdayShort(cell.weekdayIndex, languageTag), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                occasions.forEach { occasion ->
                    Text(
                        occasion.title(languageTag),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (occasion.isHoliday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (cell.taskCount > 0) {
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, cell.taskCount), numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                PersianDateUtils.fullDate(epoch, languageTag, numeralMode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OccasionRow(day: Int, occasion: Occasion, languageTag: String, numeralMode: NumeralMode, weekdayIndex: Int) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TextNormalizer.formatDigits(day.toString(), numeralMode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(PersianDateUtils.weekdayShort(weekdayIndex, languageTag), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(occasion.title(languageTag), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(categoryLabelRes(occasion.category)),
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor(occasion.category),
                )
            }
            if (occasion.isHoliday) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        stringResource(R.string.calendar_holiday_badge),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun categoryColor(category: OccasionCategory): Color = when (category) {
    OccasionCategory.OFFICIAL -> MaterialTheme.colorScheme.primary
    OccasionCategory.NATIONAL -> MaterialTheme.colorScheme.tertiary
    OccasionCategory.RELIGIOUS -> Color(0xFF2E9E7C)
    OccasionCategory.PERSONAL -> MaterialTheme.colorScheme.secondary
}

internal fun categoryLabelRes(category: OccasionCategory): Int = when (category) {
    OccasionCategory.OFFICIAL -> R.string.calendar_category_official
    OccasionCategory.NATIONAL -> R.string.calendar_category_national
    OccasionCategory.RELIGIOUS -> R.string.calendar_category_religious
    OccasionCategory.PERSONAL -> R.string.calendar_category_personal
}

private fun weekdayIndexFor(year: Int, month: Int, day: Int, zone: ZoneId): Int {
    val epoch = PersianDateUtils.startOfJalaliDay(year, month, day, zone) ?: return 0
    return PersianDateUtils.of(epoch, zone).weekdayIndex
}

private fun buildMonth(monthOffset: Int, tasks: List<Task>, zone: ZoneId, weekStartsSaturday: Boolean, nowMillis: Long = System.currentTimeMillis()): MonthData {
    val current = JalaliCalendarMath.persianCalendar(zone).apply {
        timeInMillis = nowMillis
        add(android.icu.util.Calendar.MONTH, monthOffset)
        set(android.icu.util.Calendar.DAY_OF_MONTH, 1)
        set(android.icu.util.Calendar.HOUR_OF_DAY, 12)
        set(android.icu.util.Calendar.MINUTE, 0)
        set(android.icu.util.Calendar.SECOND, 0)
        set(android.icu.util.Calendar.MILLISECOND, 0)
    }
    val month = current.get(android.icu.util.Calendar.MONTH) + 1
    val year = current.get(android.icu.util.Calendar.YEAR)
    val firstDayOfWeek = current.get(android.icu.util.Calendar.DAY_OF_WEEK)
    val firstWeekdayIndex = JalaliCalendarMath.saturdayBasedWeekday(firstDayOfWeek)
    val leadingCells = if (weekStartsSaturday) firstWeekdayIndex else (firstWeekdayIndex + 6) % 7
    val firstCell = (current.clone() as android.icu.util.Calendar).apply { add(android.icu.util.Calendar.DAY_OF_MONTH, -leadingCells) }
    val today = LocalDate.now(zone)
    val taskCounts = tasks.mapNotNull { task -> task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } }
        .groupingBy { it }
        .eachCount()
    val cells = (0 until 42).map { offset ->
        val day = (firstCell.clone() as android.icu.util.Calendar).apply { add(android.icu.util.Calendar.DAY_OF_MONTH, offset) }
        val epoch = day.timeInMillis
        val localDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
        DayCell(
            epochMillis = epoch,
            year = day.get(android.icu.util.Calendar.YEAR),
            month = day.get(android.icu.util.Calendar.MONTH) + 1,
            day = day.get(android.icu.util.Calendar.DAY_OF_MONTH),
            weekdayIndex = JalaliCalendarMath.saturdayBasedWeekday(day.get(android.icu.util.Calendar.DAY_OF_WEEK)),
            inDisplayedMonth = day.get(android.icu.util.Calendar.MONTH) + 1 == month && day.get(android.icu.util.Calendar.YEAR) == year,
            isToday = localDate == today,
            taskCount = taskCounts[localDate] ?: 0,
        )
    }
    return MonthData(month, year, cells)
}
