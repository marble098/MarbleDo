package com.marbledo.feature.calendar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Add
import com.marbledo.domain.model.CalendarDisplayMode
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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
    pickerCalendar: CalendarDisplayMode,
    onPickerCalendarChange: (CalendarDisplayMode) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var todayIsoDate by remember(zone) { mutableStateOf(LocalDate.now(zone).toString()) }
    var selectedIsoDate by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    var occasionQuery by rememberSaveable { mutableStateOf("") }
    var showGoToDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(zone) {
        while (true) {
            todayIsoDate = LocalDate.now(zone).toString()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val todayDate = remember(todayIsoDate) { LocalDate.parse(todayIsoDate) }
    val todayEpoch = remember(todayDate, zone) { todayDate.atStartOfDay(zone).toInstant().toEpochMilli() }
    val selectedDate = remember(selectedIsoDate, zone, todayDate) {
        runCatching { LocalDate.parse(selectedIsoDate) }.getOrDefault(todayDate)
    }
    val selectedDateEpoch = remember(selectedDate, zone) { selectedDate.atStartOfDay(zone).toInstant().toEpochMilli() }
    val monthData = remember(monthOffset, tasks, weekStartsSaturday, zone, todayDate) {
        buildMonth(monthOffset, tasks, zone, weekStartsSaturday, todayEpoch)
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
    val selectedPersian = PersianDateUtils.of(selectedDateEpoch, zone)
    val selectedOccasions = index.on(selectedPersian.year, selectedPersian.month, selectedPersian.day)
    val matchingTasks = remember(tasks, selectedDate, zone) {
        tasks.filter { task ->
            task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == selectedDate } == true
        }
    }
    val yearOccasions = remember(index, monthData.year, occasionQuery, languageTag) {
        val query = TextNormalizer.searchKey(occasionQuery.trim())
        index.forYear(monthData.year).filter { dated ->
            query.isBlank() || TextNormalizer.searchKey(dated.occasion.title(languageTag)).contains(query)
        }
    }

    if (showGoToDate) {
        SchedulePickerDialog(
            title = stringResource(R.string.calendar_go_to_date),
            mode = SchedulePickerMode.DATE_ONLY,
            initialDate = selectedDate,
            initialTime = null,
            languageTag = languageTag,
            numeralMode = numeralMode,
            weekStartsSaturday = weekStartsSaturday,
            calendarMode = pickerCalendar,
            onCalendarModeChange = onPickerCalendarChange,
            onConfirm = { date, _ ->
                selectedIsoDate = date.toString()
                monthOffset = monthOffsetFor(date, zone, todayEpoch)
                showGoToDate = false
            },
            onDismiss = { showGoToDate = false },
            allowAnyTime = false,
        )
    }

    Column(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.calendar_title),
                        style = MarbleTextStyles.cardTitle.copy(fontSize = 21.sp, lineHeight = 28.sp),
                    )
                    Text(
                        PersianDateUtils.tripleDate(todayEpoch, languageTag, numeralMode, lunarOffsetDays),
                        style = MarbleTextStyles.metaLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showGoToDate = true }) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.calendar_go_to_date))
                }
                OccasionSyncButton(occasionState.isRefreshing, onRefreshOccasions)
            }
        }

        OccasionSyncStatus(
            state = occasionState,
            languageTag = languageTag,
            numeralMode = numeralMode,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 1.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val isYearView = viewMode == CalendarViewMode.YEAR
                val step = if (isYearView) 12 else 1
                IconButton(onClick = { monthOffset -= step }) {
                    Icon(
                        if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
                        contentDescription = stringResource(if (isYearView) R.string.calendar_previous_year else R.string.calendar_previous_month),
                    )
                }
                Text(
                    if (isYearView) {
                        TextNormalizer.formatDigits(monthData.year.toString(), numeralMode)
                    } else {
                        TextNormalizer.formatDigits(
                            stringResource(R.string.calendar_month_year, PersianDateUtils.monthName(monthData.monthIndex, languageTag), monthData.year),
                            numeralMode,
                        )
                    },
                    style = MarbleTextStyles.yearDigits.copy(fontSize = 19.sp, lineHeight = 26.sp),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { monthOffset += step }) {
                    Icon(
                        if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                        contentDescription = stringResource(if (isYearView) R.string.calendar_next_year else R.string.calendar_next_month),
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        monthOffset = 0
                        selectedIsoDate = todayDate.toString()
                    },
                ) {
                    Text(
                        stringResource(R.string.calendar_today),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MarbleTextStyles.metaLabel,
                    )
                }
            }
        }

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CalendarViewMode.entries, key = { it.name }) { mode ->
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
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    WeekdayHeader(weekStartsSaturday)
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 2.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                            contentPadding = PaddingValues(bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            items(monthData.cells, key = { it.epochMillis }) { cell ->
                                val dayOccasions = index.on(cell.year, cell.month, cell.day)
                                DayCellView(
                                    cell = cell,
                                    occasions = dayOccasions,
                                    selected = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate() == selectedDate,
                                    onClick = { selectedIsoDate = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate().toString() },
                                )
                            }
                        }
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
                    onAddTask = { onAddTask(selectedDate) },
                    onOpenTask = onOpenTask,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
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
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            TextNormalizer.formatDigits(stringResource(R.string.calendar_all_year_occasions, monthData.year), numeralMode),
                            modifier = Modifier.weight(1f),
                            style = MarbleTextStyles.itemTitle,
                        )
                        Text(
                            TextNormalizer.formatDigits(stringResource(R.string.calendar_year_occasions_count, yearOccasions.size), numeralMode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = occasionQuery,
                        onValueChange = { occasionQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.calendar_search_occasions)) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                    )
                    if (yearOccasions.isEmpty()) {
                        EmptyMessage(
                            stringResource(if (occasionQuery.isBlank()) R.string.calendar_no_year_occasions else R.string.calendar_no_matching_occasions),
                            Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(yearOccasions, key = { "${it.month}-${it.day}-${it.occasion.id}" }) { dated ->
                                OccasionRow(
                                    month = dated.month,
                                    day = dated.day,
                                    occasion = dated.occasion,
                                    languageTag = languageTag,
                                    numeralMode = numeralMode,
                                    weekdayIndex = weekdayIndexFor(monthData.year, dated.month, dated.day, zone),
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
                    contentPadding = PaddingValues(vertical = 12.dp),
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
                            Column(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(PersianDateUtils.monthName(month, languageTag), style = MarbleTextStyles.itemTitle)
                                if (count > 0) {
                                    Text(
                                        TextNormalizer.formatDigits(stringResource(R.string.calendar_occasions_short, count), numeralMode),
                                        style = MarbleTextStyles.metaLabel,
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
    val locale = Locale.forLanguageTag(languageTag)
    val message = when {
        state.isRefreshing -> stringResource(R.string.calendar_sync_refreshing)
        state.lastRefreshWasOffline -> stringResource(R.string.calendar_sync_offline)
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
    val holidayLabel = stringResource(R.string.calendar_holiday_badge)
    val tasksLabel = stringResource(R.string.calendar_cell_tasks, cell.taskCount)
    val cellDescription = listOfNotNull(
        TextNormalizer.formatDigits(cell.day.toString(), LocalNumeralMode.current),
        holidayLabel.takeIf { isHoliday },
        tasksLabel.takeIf { cell.taskCount > 0 },
    ).joinToString(", ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .semantics(mergeDescendants = true) { contentDescription = cellDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            TextNormalizer.formatDigits(cell.day.toString(), LocalNumeralMode.current),
            color = foreground,
            style = if (cell.isToday || isHoliday || selected) {
                MarbleTextStyles.itemTitle.copy(fontSize = 15.sp, lineHeight = 18.sp)
            } else {
                MarbleTextStyles.dateDigits.copy(fontSize = 15.sp, lineHeight = 18.sp)
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (cell.taskCount > 0) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
            }
            if (occasions.isNotEmpty()) {
                Box(
                    Modifier.size(4.dp).clip(CircleShape).background(
                        when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            isHoliday -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                    ),
                )
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
    onAddTask: () -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
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
                style = MarbleTextStyles.itemTitle,
            )
            Text(
                PersianDateUtils.tripleDate(epoch, languageTag, numeralMode, lunarOffsetDays),
                style = MarbleTextStyles.metaLabel,
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
            val sortedTasks = remember(tasks) { tasks.sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE } }
            val allDayLabel = stringResource(R.string.calendar_all_day)
            sortedTasks.take(MAX_DAY_CARD_TASKS).forEach { task ->
                val timeLabel = when {
                    task.dueAtEpochMillis == null -> null
                    task.isAllDay -> allDayLabel
                    else -> TextNormalizer.formatDigits(
                        formatClock(Instant.ofEpochMilli(task.dueAtEpochMillis).atZone(zone).toLocalTime()),
                        numeralMode,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenTask(task) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (timeLabel != null) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (sortedTasks.size > MAX_DAY_CARD_TASKS) {
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.calendar_more_tasks, sortedTasks.size - MAX_DAY_CARD_TASKS), numeralMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAddTask) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.calendar_add_task))
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
                    Text(
                        TextNormalizer.formatDigits(cell.day.toString(), numeralMode),
                        style = MarbleTextStyles.cardTitle.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    )
                    Text(PersianDateUtils.weekdayShort(cell.weekdayIndex, languageTag), style = MarbleTextStyles.metaLabel)
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
private fun OccasionRow(
    month: Int,
    day: Int,
    occasion: Occasion,
    languageTag: String,
    numeralMode: NumeralMode,
    weekdayIndex: Int,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        TextNormalizer.formatDigits(day.toString(), numeralMode),
                        style = MarbleTextStyles.cardTitle.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    )
                    Text(PersianDateUtils.monthName(month, languageTag), style = MarbleTextStyles.metaLabel, maxLines = 1)
                    Text(PersianDateUtils.weekdayShort(weekdayIndex, languageTag), style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(occasion.title(languageTag), style = MarbleTextStyles.itemTitle)
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

/** Number of tasks listed in the day card before the "+N more" line. */
private const val MAX_DAY_CARD_TASKS = 6

/** Persian months between today and [date], used to bring the month grid to the chosen day. */
private fun monthOffsetFor(date: LocalDate, zone: ZoneId, todayEpoch: Long): Int {
    val target = PersianDateUtils.of(date.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
    val today = PersianDateUtils.of(todayEpoch, zone)
    return (target.year * 12 + target.month) - (today.year * 12 + today.month)
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
