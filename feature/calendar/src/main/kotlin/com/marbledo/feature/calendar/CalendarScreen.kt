package com.marbledo.feature.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.LocalReduceMotion
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.OccasionCulture
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

/** An occasion the user wants to turn into a task, a preparation task or a countdown. */
private data class PlannedOccasion(val occasion: Occasion, val epochMillis: Long)

/** One month of the browsable occasion list. */
private data class OccasionMonthGroup(val month: Int, val year: Int, val rows: List<DatedOccasion>)

/** Rows a month shows before its "show more" line, so a year of occasions stays scannable. */
private const val OCCASIONS_PER_MONTH = 10

/**
 * Wise, architectural Calendar Screen:
 *  - A unified top command card combines the month/year navigator, today's triple-date pulse,
 *    quick tools (Go to Date, Smart Workday Advisor, Jump to Today), and the 4-mode view switcher
 *    so the month grid has full vertical breathing room.
 *  - Five-week months render 5 rows instead of 6, eliminating dead trailing rows.
 *  - Selecting any day updates the Smart Day Inspector below the grid with relative day context,
 *    actionable occasions, scheduled tasks, and the compact month bloom ribbon.
 */
@Composable
fun CalendarScreen(
    tasks: List<Task>,
    catalog: OccasionCatalog,
    occasionState: OccasionState,
    enabledCategories: Set<OccasionCategory>,
    languageTag: String,
    weekStartsSaturday: Boolean,
    lunarOffsetDays: Int,
    onRefreshOccasions: () -> Unit = {},
    pickerCalendar: CalendarDisplayMode,
    onPickerCalendarChange: (CalendarDisplayMode) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onOpenTask: (Task) -> Unit,
    favoriteOccasionIds: Set<String> = emptySet(),
    occasionReminderLeadDays: Int = 0,
    onToggleFavorite: (String) -> Unit = {},
    onPlanOccasion: (OccasionPlanRequest) -> Unit = {},
    modifier: Modifier = Modifier,
    thursdayWeekendEnabled: Boolean = false,
    onExport: (CalendarExportRequest) -> Unit = {},
    exportInProgress: Boolean = false,
    enabledCultures: Set<OccasionCulture> = OccasionCulture.all,
    onCreatePreparation: (List<Task>) -> Unit = {},
) {
    val zone = remember { ZoneId.systemDefault() }
    val numeralMode = LocalNumeralMode.current
    val context = LocalContext.current
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var todayIsoDate by remember(zone) { mutableStateOf(LocalDate.now(zone).toString()) }
    var selectedIsoDate by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.MONTH) }
    var occasionQuery by rememberSaveable { mutableStateOf("") }
    var showGoToDate by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var plannedOccasion by remember { mutableStateOf<PlannedOccasion?>(null) }
    var filterCategory by rememberSaveable { mutableStateOf<OccasionCategory?>(null) }
    var filterCulture by rememberSaveable { mutableStateOf<OccasionCulture?>(null) }
    var filterCalendar by rememberSaveable { mutableStateOf<OccasionCalendar?>(null) }
    var holidaysOnly by rememberSaveable { mutableStateOf(false) }
    var culturalOnly by rememberSaveable { mutableStateOf(false) }
    var educationOnly by rememberSaveable { mutableStateOf(false) }
    var academicYear by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showPrograms by rememberSaveable { mutableStateOf(false) }
    var showWorkday by rememberSaveable { mutableStateOf(false) }
    var upcomingOnly by rememberSaveable { mutableStateOf(true) }
    var occasionMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    var occasionSearchOpen by rememberSaveable { mutableStateOf(false) }
    var expandedMonths by rememberSaveable { mutableStateOf("") }
    val educationEnabled = OccasionCategory.EDUCATIONAL in enabledCategories
    LaunchedEffect(enabledCategories, enabledCultures) {
        if (filterCulture !in enabledCultures) filterCulture = null
        if (plannedOccasion?.occasion?.cultures?.none { it in enabledCultures } == true) plannedOccasion = null
        if (filterCategory !in enabledCategories) filterCategory = null
        if (!educationEnabled) {
            educationOnly = false
            academicYear = false
            showPrograms = false
            if (plannedOccasion?.occasion?.category == OccasionCategory.EDUCATIONAL) plannedOccasion = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(zone, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                todayIsoDate = LocalDate.now(zone).toString()
                kotlinx.coroutines.delay(60_000L)
            }
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
    val index = remember(catalog, enabledCategories, enabledCultures, lunarOffsetDays, monthData.year, monthData.monthIndex) {
        OccasionIndex.build(
            catalog = catalog,
            jalaliYears = buildSet {
                add(monthData.year)
                add(monthData.year + 1)
                monthData.cells.forEach { add(it.year) }
            },
            lunarOffsetDays = lunarOffsetDays,
            enabledCategories = enabledCategories,
            enabledCultures = enabledCultures,
            zone = zone,
        )
    }
    val selectedPersian = remember(selectedDateEpoch, zone) { PersianDateUtils.of(selectedDateEpoch, zone) }
    val selectedOccasions = remember(index, selectedPersian) { index.on(selectedPersian.year, selectedPersian.month, selectedPersian.day) }
    val matchingTasks = remember(tasks, selectedDate, zone) {
        tasks.filter { task ->
            !task.isArchived && task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == selectedDate } == true
        }
    }
    val monthBloom = remember(index, monthData.year, monthData.monthIndex) {
        index.forMonth(monthData.year, monthData.monthIndex)
    }
    val yearRows = remember(index, monthData.year, educationEnabled, academicYear) {
        if (educationEnabled && academicYear) OccasionSearch.academicYear(index, monthData.year)
        else index.forYear(monthData.year)
    }
    val filter = OccasionFilter(
        query = occasionQuery, category = filterCategory, calendar = filterCalendar,
        holidaysOnly = holidaysOnly, favoritesOnly = favoritesOnly,
        educationOnly = educationEnabled && educationOnly, culturalOnly = culturalOnly, culture = filterCulture,
    )
    val yearOccasions = remember(yearRows, filter, favoriteOccasionIds) {
        OccasionSearch.filter(yearRows, filter, favoriteOccasionIds)
    }
    val favoriteOccasionsCount = remember(yearRows, favoriteOccasionIds) {
        yearRows.count { it.occasion.id in favoriteOccasionIds }
    }
    val upcomingOccasions = remember(yearOccasions, upcomingOnly, zone, todayEpoch) {
        if (!upcomingOnly) yearOccasions
        else yearOccasions.filter { row ->
            val epoch = PersianDateUtils.startOfJalaliDay(row.year, row.month, row.day, zone)
            epoch == null || epoch >= todayEpoch
        }
    }
    val monthCounts = remember(upcomingOccasions) { upcomingOccasions.groupingBy { it.month }.eachCount() }
    val monthGroups = remember(upcomingOccasions, occasionMonth, favoriteOccasionIds) {
        val scoped = if (occasionMonth == null) upcomingOccasions else upcomingOccasions.filter { it.month == occasionMonth }
        scoped.groupBy { it.month }.map { (month, rows) ->
            OccasionMonthGroup(
                month = month,
                year = rows.firstOrNull()?.year ?: monthData.year,
                rows = rows.sortedWith(compareBy({ if (it.occasion.id in favoriteOccasionIds) 0 else 1 }, { it.day })),
            )
        }
    }
    val expandedMonthSet = remember(expandedMonths) {
        expandedMonths.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
    val activeFilterCount = listOfNotNull(
        filterCategory,
        filterCalendar,
        filterCulture,
        true.takeIf { holidaysOnly },
        true.takeIf { culturalOnly },
        true.takeIf { educationOnly },
        true.takeIf { academicYear },
        true.takeIf { favoritesOnly },
    ).size

    if (showFilters) {
        OccasionFilterSheet(filterCategory, filterCalendar, enabledCategories,
            onCategory = { filterCategory = it }, onCalendar = { filterCalendar = it }, onDismiss = { showFilters = false },
            culture = filterCulture, enabledCultures = enabledCultures, onCulture = { filterCulture = it })
    }
    if (showPrograms && educationEnabled) {
        SchoolProgramsSheet(catalog, languageTag, onPlan = { showPrograms = false; onPlanOccasion(it) }, onDismiss = { showPrograms = false })
    }
    if (showWorkday) {
        WorkdaySuggestionDialog(selectedDate, catalog, lunarOffsetDays, thursdayWeekendEnabled, tasks, languageTag, zone,
            onAdd = { showWorkday = false; onAddTask(it) },
            onShow = { date ->
                selectedIsoDate = date.toString()
                monthOffset = monthOffsetFor(date, zone, todayEpoch)
                viewMode = CalendarViewMode.MONTH
                showWorkday = false
            },
            onDismiss = { showWorkday = false })
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

    plannedOccasion?.let { planned ->
        OccasionPlanSheet(
            occasion = planned.occasion,
            epochMillis = planned.epochMillis,
            languageTag = languageTag,
            isFavorite = planned.occasion.id in favoriteOccasionIds,
            prepDays = occasionReminderLeadDays.takeIf { it > 0 } ?: DEFAULT_PREP_DAYS,
            onPlan = { request ->
                plannedOccasion = null
                onPlanOccasion(request)
            },
            onToggleFavorite = {
                plannedOccasion = null
                onToggleFavorite(planned.occasion.id)
            },
            onDismiss = { plannedOccasion = null },
            references = catalog.sources.filter { it.id in planned.occasion.sourceIds },
            educationModeEnabled = educationEnabled,
            tasks = tasks,
            holidays = remember(catalog, lunarOffsetDays, planned.epochMillis) {
                val year = PersianDateUtils.of(planned.epochMillis, zone).year
                OccasionPlanner.holidayDates(catalog, listOf(year - 1, year, year + 1), lunarOffsetDays, zone)
            },
            thursdayClosed = thursdayWeekendEnabled,
            onCreatePreparation = onCreatePreparation,
        )
    }

    val isAwayFromToday = monthOffset != 0 || selectedDate != todayDate
    val yearScope = viewMode == CalendarViewMode.YEAR || viewMode == CalendarViewMode.OCCASIONS

    Column(modifier.fillMaxSize()) {
        CalendarCommandCard(
            rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
            todayEpoch = todayEpoch,
            languageTag = languageTag,
            numeralMode = numeralMode,
            lunarOffsetDays = lunarOffsetDays,
            monthLabel = if (yearScope) {
                TextNormalizer.formatDigits(monthData.year.toString(), numeralMode)
            } else {
                TextNormalizer.formatDigits(
                    stringResource(R.string.calendar_month_year, PersianDateUtils.monthName(monthData.monthIndex, languageTag), monthData.year),
                    numeralMode,
                )
            },
            yearScope = yearScope,
            isAwayFromToday = isAwayFromToday,
            selectedViewMode = viewMode,
            onSelectViewMode = { viewMode = it },
            onPrevious = { monthOffset -= if (yearScope) 12 else 1 },
            onNext = { monthOffset += if (yearScope) 12 else 1 },
            onToday = {
                monthOffset = 0
                selectedIsoDate = todayDate.toString()
            },
            onOpenGoToDate = { showGoToDate = true },
        )

        when (viewMode) {
            CalendarViewMode.MONTH -> {
                // One scroll axis for the whole month view: weekday header, the day grid and the
                // selected-day card are items of a single LazyColumn. The old layout nested a
                // vertically scrolling card under a weighted lazy grid, which trapped scroll
                // gestures and crushed the grid in landscape (docs/UI_COUNCIL_REVIEW.md, C-3).
                // 42 fixed cells do not need a lazy grid, so the month is plain rows now.
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = MarbleSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs),
                ) {
                    item { WeekdayHeader(weekStartsSaturday, thursdayWeekendEnabled) }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            tonalElevation = 0.5.dp,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                monthData.cells.chunked(7).forEach { week ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        week.forEach { cell ->
                                            Box(Modifier.weight(1f)) {
                                                val dayOccasions = index.on(cell.year, cell.month, cell.day)
                                                val isWeekend = cell.weekdayIndex == 6 || (thursdayWeekendEnabled && cell.weekdayIndex == 5)
                                                DayCellView(
                                                    cell = cell,
                                                    occasions = dayOccasions,
                                                    isWeekend = isWeekend,
                                                    hasFavorite = dayOccasions.any { it.id in favoriteOccasionIds },
                                                    selected = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate() == selectedDate,
                                                    languageTag = languageTag,
                                                    onClick = {
                                                        val clicked = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate()
                                                        selectedIsoDate = clicked.toString()
                                                        if (!cell.inDisplayedMonth) monthOffset = monthOffsetFor(clicked, zone, todayEpoch)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SelectedDayCard(
                            date = selectedDate,
                            todayDate = todayDate,
                            persian = selectedPersian,
                            occasions = selectedOccasions,
                            monthBloom = monthBloom,
                            tasks = matchingTasks,
                            languageTag = languageTag,
                            numeralMode = numeralMode,
                            lunarOffsetDays = lunarOffsetDays,
                            favoriteOccasionIds = favoriteOccasionIds,
                            onToggleFavorite = onToggleFavorite,
                            onPlanOccasion = { occasion, epoch -> plannedOccasion = PlannedOccasion(occasion, epoch) },
                            onBloomOccasionClick = { day, occasion ->
                                val epoch = PersianDateUtils.startOfJalaliDay(
                                    monthData.year, monthData.monthIndex, day, zone,
                                )
                                if (epoch != null) {
                                    selectedIsoDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate().toString()
                                    plannedOccasion = PlannedOccasion(occasion, epoch)
                                }
                            },
                            onAddTask = { onAddTask(selectedDate) },
                            onOpenWorkday = { showWorkday = true },
                            onOpenTask = onOpenTask,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            CalendarViewMode.AGENDA -> {
                val agenda = remember(monthData.cells, index) {
                    monthData.cells.filter { cell ->
                        cell.inDisplayedMonth && (cell.taskCount > 0 || index.on(cell.year, cell.month, cell.day).isNotEmpty())
                    }
                }
                val monthHolidayDays = remember(agenda, index) {
                    agenda.count { cell -> index.on(cell.year, cell.month, cell.day).any(Occasion::isHoliday) }
                }
                val monthTasksCount = remember(monthData.cells) {
                    monthData.cells.filter { it.inDisplayedMonth }.sumOf { it.taskCount }
                }
                if (agenda.isEmpty()) {
                    EmptyMessage(stringResource(R.string.calendar_no_events), Modifier.weight(1f))
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AgendaSummaryChip(
                                text = TextNormalizer.formatDigits(stringResource(R.string.calendar_month_summary, agenda.size), numeralMode),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (monthHolidayDays > 0) {
                                AgendaSummaryChip(
                                    text = TextNormalizer.formatDigits("$monthHolidayDays ${stringResource(R.string.calendar_weekend_marker)}", numeralMode),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (monthTasksCount > 0) {
                                AgendaSummaryChip(
                                    text = TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, monthTasksCount), numeralMode),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(agenda, key = { it.epochMillis }) { cell ->
                                AgendaRow(
                                    cell = cell,
                                    occasions = index.on(cell.year, cell.month, cell.day),
                                    languageTag = languageTag,
                                    numeralMode = numeralMode,
                                    onClick = {
                                        selectedIsoDate = Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate().toString()
                                        monthOffset = monthOffsetFor(
                                            Instant.ofEpochMilli(cell.epochMillis).atZone(zone).toLocalDate(),
                                            zone,
                                            todayEpoch,
                                        )
                                        viewMode = CalendarViewMode.MONTH
                                    },
                                )
                            }
                        }
                    }
                }
            }

            CalendarViewMode.OCCASIONS -> {
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    val lunarNote = stringResource(R.string.calendar_lunar_note)
                    OccasionBrowseToolbar(
                        year = monthData.year,
                        academicYear = educationEnabled && academicYear,
                        visibleCount = upcomingOccasions.size,
                        numeralMode = numeralMode,
                        searchOpen = occasionSearchOpen,
                        activeFilterCount = activeFilterCount,
                        canExport = upcomingOccasions.isNotEmpty() && !exportInProgress,
                        exportInProgress = exportInProgress,
                        onToggleSearch = {
                            occasionSearchOpen = !occasionSearchOpen
                            if (!occasionSearchOpen) occasionQuery = ""
                        },
                        onOpenFilters = { showFilters = true },
                        onExport = {
                            val entries = upcomingOccasions.mapNotNull { dated ->
                                val epoch = PersianDateUtils.startOfJalaliDay(dated.year, dated.month, dated.day, zone) ?: return@mapNotNull null
                                val occasion = dated.occasion
                                val notes = buildList {
                                    add(context.getString(evidenceLabelRes(occasion.evidence)))
                                    add(occasion.note(languageTag))
                                    add(catalog.sources.filter { it.id in occasion.sourceIds }.joinToString("\n") { it.url })
                                    if (occasion.calendar == OccasionCalendar.LUNAR) add(lunarNote)
                                }.filter { it.isNotBlank() }.joinToString("\n")
                                CalendarExportEntry(occasion.id, occasion.title(languageTag),
                                    Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate(), notes, occasion.durationDays)
                            }
                            val scopeLabel = if (educationEnabled && academicYear) "${monthData.year}-${monthData.year + 1}" else monthData.year.toString()
                            onExport(CalendarExportRequest("MarbleDo-$scopeLabel.ics", entries))
                        },
                    )
                    AnimatedVisibility(visible = occasionSearchOpen || occasionQuery.isNotBlank()) {
                        OutlinedTextField(
                            value = occasionQuery,
                            onValueChange = { occasionQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            placeholder = { Text(stringResource(R.string.calendar_search_occasions)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (occasionQuery.isNotBlank()) {
                                    IconButton(onClick = { occasionQuery = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.calendar_search_clear))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "scope") {
                            FilterChip(
                                selected = upcomingOnly,
                                onClick = { upcomingOnly = !upcomingOnly },
                                label = { Text(stringResource(R.string.calendar_filter_upcoming_only)) },
                            )
                        }
                        item(key = "favorites") {
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = !favoritesOnly },
                                label = {
                                    Text(
                                        TextNormalizer.formatDigits(
                                            stringResource(R.string.calendar_filter_favorites, favoriteOccasionsCount),
                                            numeralMode,
                                        ),
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                        item(key = "holidays") {
                            FilterChip(selected = holidaysOnly, onClick = { holidaysOnly = !holidaysOnly },
                                label = { Text(stringResource(R.string.calendar_filter_holidays)) })
                        }
                        item(key = "culture") {
                            FilterChip(selected = culturalOnly, onClick = { culturalOnly = !culturalOnly },
                                label = { Text(stringResource(R.string.calendar_filter_culture)) })
                        }
                        if (educationEnabled) {
                            item(key = "school") {
                                FilterChip(selected = educationOnly, onClick = { educationOnly = !educationOnly },
                                    label = { Text(stringResource(R.string.calendar_filter_school)) })
                            }
                            item(key = "academic") {
                                FilterChip(selected = academicYear, onClick = { academicYear = !academicYear },
                                    label = { Text(stringResource(R.string.calendar_academic_scope)) })
                            }
                            item(key = "programs") {
                                TextButton(onClick = { showPrograms = true }) { Text(stringResource(R.string.school_programs_title)) }
                            }
                        }
                        if (activeFilterCount > 0 || occasionQuery.isNotBlank()) {
                            item(key = "reset") {
                                TextButton(onClick = {
                                    occasionQuery = ""
                                    filterCategory = null
                                    filterCalendar = null
                                    filterCulture = null
                                    favoritesOnly = false
                                    holidaysOnly = false
                                    culturalOnly = false
                                    educationOnly = false
                                    academicYear = false
                                    occasionMonth = null
                                    upcomingOnly = true
                                }) { Text(stringResource(R.string.calendar_filters_reset)) }
                            }
                        }
                    }
                    if (monthCounts.size > 1) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item(key = "all-months") {
                                FilterChip(
                                    selected = occasionMonth == null,
                                    onClick = { occasionMonth = null },
                                    label = { Text(stringResource(R.string.calendar_month_all)) },
                                )
                            }
                            items((1..12).filter { (monthCounts[it] ?: 0) > 0 }, key = { it }) { month ->
                                FilterChip(
                                    selected = occasionMonth == month,
                                    onClick = { occasionMonth = if (occasionMonth == month) null else month },
                                    label = {
                                        Text(
                                            TextNormalizer.formatDigits(
                                                "${PersianDateUtils.monthName(month, languageTag)} ${monthCounts[month] ?: 0}",
                                                numeralMode,
                                            ),
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (occasionListIsEmpty(monthGroups)) {
                        EmptyMessage(
                            stringResource(
                                when {
                                    occasionQuery.isNotBlank() -> R.string.calendar_no_matching_occasions
                                    occasionMonth != null -> R.string.calendar_no_month_occasions
                                    else -> R.string.calendar_no_year_occasions
                                },
                            ),
                            Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            monthGroups.forEach { group ->
                                item(key = "occasions-month-${group.month}-${group.year}") {
                                    OccasionMonthHeader(
                                        month = group.month,
                                        languageTag = languageTag,
                                        count = group.rows.size,
                                        numeralMode = numeralMode,
                                    )
                                }
                                val expanded = group.month in expandedMonthSet || occasionQuery.isNotBlank()
                                val shown = if (expanded) group.rows else group.rows.take(OCCASIONS_PER_MONTH)
                                items(shown, key = { "${it.year}-${it.month}-${it.day}-${it.occasion.id}" }) { dated ->
                                    val rowEpoch = remember(dated.year, dated.month, dated.day, zone) {
                                        PersianDateUtils.startOfJalaliDay(dated.year, dated.month, dated.day, zone)
                                    }
                                    val rowWeekday = remember(dated.year, dated.month, dated.day, zone) {
                                        weekdayIndexFor(dated.year, dated.month, dated.day, zone)
                                    }
                                    OccasionRow(
                                        month = dated.month,
                                        day = dated.day,
                                        year = dated.year,
                                        occasion = dated.occasion,
                                        languageTag = languageTag,
                                        numeralMode = numeralMode,
                                        weekdayIndex = rowWeekday,
                                        isFavorite = dated.occasion.id in favoriteOccasionIds,
                                        daysUntil = rowEpoch?.let { epoch ->
                                            ChronoUnit.DAYS.between(
                                                todayDate,
                                                Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate(),
                                            )
                                        },
                                        onToggleFavorite = { onToggleFavorite(dated.occasion.id) },
                                        onPlan = rowEpoch?.let { epoch ->
                                            { plannedOccasion = PlannedOccasion(dated.occasion, epoch) }
                                        },
                                    )
                                }
                                if (!expanded && group.rows.size > OCCASIONS_PER_MONTH) {
                                    item(key = "occasions-more-${group.month}") {
                                        TextButton(
                                            onClick = {
                                                val next = expandedMonthSet + group.month
                                                expandedMonths = next.joinToString(",")
                                            },
                                            modifier = Modifier.align(Alignment.Start),
                                        ) {
                                            Text(
                                                TextNormalizer.formatDigits(
                                                    stringResource(R.string.calendar_show_more_occasions, group.rows.size - OCCASIONS_PER_MONTH),
                                                    numeralMode,
                                                ),
                                            )
                                        }
                                    }
                                } else if (group.month in expandedMonthSet && group.rows.size > OCCASIONS_PER_MONTH) {
                                    item(key = "occasions-less-${group.month}") {
                                        TextButton(
                                            onClick = {
                                                expandedMonths = (expandedMonthSet - group.month).joinToString(",")
                                            },
                                            modifier = Modifier.align(Alignment.Start),
                                        ) {
                                            Text(stringResource(R.string.calendar_show_less))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CalendarViewMode.YEAR -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..12).toList()) { month ->
                        val monthRows = remember(index, monthData.year, month) {
                            index.forMonth(monthData.year, month)
                        }
                        val count = monthRows.size
                        val holidayCount = monthRows.count { it.second.isHoliday }
                        val isCurrentMonth = month == monthData.monthIndex
                        val colors = MaterialTheme.colorScheme
                        Surface(
                            onClick = {
                                monthOffset += month - monthData.monthIndex
                                viewMode = CalendarViewMode.MONTH
                            },
                            shape = MaterialTheme.shapes.large,
                            color = if (isCurrentMonth) colors.primaryContainer else colors.surface,
                            shadowElevation = if (isCurrentMonth) 2.dp else 1.dp,
                            tonalElevation = if (isCurrentMonth) 2.dp else 0.5.dp,
                            modifier = Modifier
                                .heightIn(min = 88.dp)
                                .then(
                                    if (isCurrentMonth) Modifier.border(1.5.dp, colors.primary.copy(alpha = 0.6f), MaterialTheme.shapes.large)
                                    else Modifier,
                                ),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCurrentMonth) colors.primary else colors.surfaceVariant,
                                    ) {
                                        Text(
                                            TextNormalizer.formatDigits(month.toString(), numeralMode),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrentMonth) colors.onPrimary else colors.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        PersianDateUtils.monthName(month, languageTag),
                                        style = MarbleTextStyles.itemTitle,
                                        color = if (isCurrentMonth) colors.onPrimaryContainer else colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isCurrentMonth) {
                                    Text(
                                        stringResource(R.string.calendar_today_marker),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary,
                                    )
                                } else if (count > 0) {
                                    Text(
                                        TextNormalizer.formatDigits(stringResource(R.string.calendar_occasions_short, count), numeralMode),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (holidayCount > 0) {
                                    Text(
                                        TextNormalizer.formatDigits("$holidayCount ${stringResource(R.string.calendar_weekend_marker)}", numeralMode),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.error,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    monthRows.take(7).forEach { (_, occasion) ->
                                        Box(
                                            Modifier
                                                .size(if (occasion.isHoliday) 5.dp else 4.dp)
                                                .clip(CircleShape)
                                                .background(if (occasion.isHoliday) colors.error else categoryColor(occasion.category)),
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
}

/** True when the browsable list has nothing left to show. */
private fun occasionListIsEmpty(groups: List<OccasionMonthGroup>): Boolean = groups.all { it.rows.isEmpty() }

@Composable
private fun AgendaSummaryChip(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

/**
 * Unified Calendar Command Card: combines the Month/Year navigator, today's triple-date line,
 * quick-action tools, and the 4-way view switch into one compact, intelligent header.
 */
@Composable
private fun CalendarCommandCard(
    rtl: Boolean,
    todayEpoch: Long,
    languageTag: String,
    numeralMode: NumeralMode,
    lunarOffsetDays: Int,
    monthLabel: String,
    yearScope: Boolean,
    isAwayFromToday: Boolean,
    selectedViewMode: CalendarViewMode,
    onSelectViewMode: (CalendarViewMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenGoToDate: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = shape,
        color = colors.surface,
        shadowElevation = 2.dp,
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer.copy(alpha = 0.65f),
                        colors.surface,
                        colors.secondaryContainer.copy(alpha = 0.55f),
                    ),
                ),
                shape,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CalendarStepButton(
                        icon = if (rtl) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
                        description = stringResource(if (yearScope) R.string.calendar_previous_year else R.string.calendar_previous_month),
                        onClick = onPrevious,
                    )
                    // Tapping the month title is the go-to-date action; it is labelled as such
                    // instead of also carrying a duplicate edit button in this row (C-1).
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(
                                onClickLabel = stringResource(R.string.calendar_go_to_date),
                                role = Role.Button,
                                onClick = onOpenGoToDate,
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Crossfade(
                            targetState = monthLabel,
                            animationSpec = tween(220),
                            label = "calendarMonthLabel",
                        ) { label ->
                            Text(
                                label,
                                style = MarbleTextStyles.cardTitle,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            PersianDateUtils.tripleDate(todayEpoch, languageTag, numeralMode, lunarOffsetDays),
                            style = MarbleTextStyles.microLabel,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CalendarStepButton(
                        icon = if (rtl) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight,
                        description = stringResource(if (yearScope) R.string.calendar_next_year else R.string.calendar_next_month),
                        onClick = onNext,
                    )
                    Surface(
                        onClick = onToday,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        shape = CircleShape,
                        color = if (isAwayFromToday) colors.primary else colors.primaryContainer,
                        shadowElevation = if (isAwayFromToday) 2.dp else 0.dp,
                    ) {
                        Text(
                            stringResource(R.string.calendar_today),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (isAwayFromToday) colors.onPrimary else colors.onPrimaryContainer,
                            style = MarbleTextStyles.metaLabel,
                        )
                    }
                }

                CalendarViewSwitch(
                    selected = selectedViewMode,
                    onSelect = onSelectViewMode,
                )
            }
        }
    }
}

@Composable
private fun CalendarStepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Four equally weighted segments inside the command card. */
@Composable
private fun CalendarViewSwitch(selected: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    val colorSpec: androidx.compose.animation.core.AnimationSpec<Color> =
        if (LocalReduceMotion.current) snap() else tween(220)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val isSelected = mode == selected
                val background by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = colorSpec,
                    label = "viewSwitchBackground",
                )
                val foreground by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = colorSpec,
                    label = "viewSwitchForeground",
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = background,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(mode) },
                ) {
                    Text(
                        when (mode) {
                            CalendarViewMode.MONTH -> stringResource(R.string.calendar_view_month)
                            CalendarViewMode.AGENDA -> stringResource(R.string.calendar_view_agenda)
                            CalendarViewMode.OCCASIONS -> stringResource(R.string.calendar_view_occasions)
                            CalendarViewMode.YEAR -> stringResource(R.string.calendar_view_year)
                        },
                        style = MarbleTextStyles.metaLabel,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OccasionBrowseToolbar(
    year: Int,
    academicYear: Boolean,
    visibleCount: Int,
    numeralMode: NumeralMode,
    searchOpen: Boolean,
    activeFilterCount: Int,
    canExport: Boolean,
    exportInProgress: Boolean,
    onToggleSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                TextNormalizer.formatDigits(
                    if (academicYear) stringResource(R.string.calendar_academic_year, year, year + 1)
                    else stringResource(R.string.calendar_all_year_occasions, year),
                    numeralMode,
                ),
                style = MarbleTextStyles.itemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.calendar_year_occasions_count, visibleCount), numeralMode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleSearch) {
            Icon(
                if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                contentDescription = stringResource(R.string.calendar_search_occasions),
                tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    Icons.Outlined.FilterAlt,
                    contentDescription = stringResource(R.string.calendar_filters_title),
                    tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeFilterCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp),
                ) {
                    Text(
                        TextNormalizer.formatDigits(activeFilterCount.toString(), numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp).padding(bottom = 1.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        IconButton(enabled = canExport && !exportInProgress, onClick = onExport) {
            if (exportInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.calendar_export_ics))
            }
        }
    }
}

@Composable
private fun WeekdayHeader(weekStartsSaturday: Boolean, thursdayWeekendEnabled: Boolean) {
    val order = if (weekStartsSaturday) (0..6).toList() else listOf(1, 2, 3, 4, 5, 6, 0)
    val labels = listOf(
        R.string.calendar_sat, R.string.calendar_sun, R.string.calendar_mon, R.string.calendar_tue,
        R.string.calendar_wed, R.string.calendar_thu, R.string.calendar_fri,
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        // Same insets and column gaps as the day grid below, so each label sits over its column.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            order.forEach { weekdayIndex ->
                val isWeekend = weekdayIndex == 6 || (thursdayWeekendEnabled && weekdayIndex == 5)
                Text(
                    stringResource(labels[weekdayIndex]),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (isWeekend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isWeekend) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DayCellView(
    cell: DayCell,
    occasions: List<Occasion>,
    isWeekend: Boolean,
    hasFavorite: Boolean,
    selected: Boolean,
    languageTag: String,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val isHoliday = occasions.any(Occasion::isHoliday)
    val colors = MaterialTheme.colorScheme
    val targetBackground = when {
        selected -> colors.primary
        cell.isToday -> colors.primaryContainer
        cell.inDisplayedMonth && isHoliday -> colors.errorContainer.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    val targetForeground = when {
        selected -> colors.onPrimary
        !cell.inDisplayedMonth -> colors.onSurface.copy(alpha = 0.30f)
        isHoliday -> colors.error
        isWeekend -> colors.error.copy(alpha = 0.82f)
        else -> colors.onSurface
    }
    val animatedBackground by animateColorAsState(targetBackground, tween(180), label = "dayCellBackground")
    val animatedForeground by animateColorAsState(targetForeground, tween(180), label = "dayCellForeground")
    val background = if (reduceMotion) targetBackground else animatedBackground
    val foreground = if (reduceMotion) targetForeground else animatedForeground
    val shape = MaterialTheme.shapes.small
    val holidayLabel = stringResource(R.string.calendar_holiday_badge)
    val tasksLabel = stringResource(R.string.calendar_cell_tasks, cell.taskCount)
    val todayLabel = stringResource(R.string.calendar_today_marker)
    val monthLabel = PersianDateUtils.monthName(cell.month, languageTag)
    val isSelected = selected
    // Full spoken date ("12 Farvardin, today, official holiday, 2 tasks"), not a bare number (C-6).
    val cellDescription = listOfNotNull(
        TextNormalizer.formatDigits("${cell.day} $monthLabel", LocalNumeralMode.current),
        todayLabel.takeIf { cell.isToday },
        holidayLabel.takeIf { isHoliday },
        tasksLabel.takeIf { cell.taskCount > 0 },
    ).joinToString(", ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MarbleSpacing.touchTarget)
            .clip(shape)
            .background(background)
            .then(
                if (cell.isToday) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = if (selected) colors.onPrimary.copy(alpha = 0.85f)
                        else colors.primary.copy(alpha = 0.65f),
                        shape = shape,
                    )
                } else if (hasFavorite && cell.inDisplayedMonth && !selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.primary.copy(alpha = 0.35f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = cellDescription; selected = isSelected }
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            TextNormalizer.formatDigits(cell.day.toString(), LocalNumeralMode.current),
            color = foreground,
            style = MarbleTextStyles.cellNumber,
            fontWeight = if (cell.isToday || isHoliday || selected) FontWeight.SemiBold else FontWeight.Light,
        )
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cell.inDisplayedMonth) {
                if (cell.taskCount > 0) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.onPrimary else colors.primary),
                    )
                }
                EnhancedDayBloomDot(occasions = occasions, selected = selected)
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    todayDate: LocalDate,
    persian: PersianDate,
    occasions: List<Occasion>,
    monthBloom: List<Pair<Int, Occasion>>,
    tasks: List<Task>,
    languageTag: String,
    numeralMode: NumeralMode,
    lunarOffsetDays: Int,
    favoriteOccasionIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPlanOccasion: (Occasion, Long) -> Unit,
    onBloomOccasionClick: (Int, Occasion) -> Unit,
    onAddTask: () -> Unit,
    onOpenWorkday: () -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val epoch = remember(date, persian.year, persian.month, persian.day) {
        PersianDateUtils.startOfJalaliDay(persian.year, persian.month, persian.day) ?: System.currentTimeMillis()
    }
    val secondaryDates = remember(epoch, languageTag, numeralMode, lunarOffsetDays, zone) {
        val gregorian = PersianDateUtils.gregorianDate(epoch, Locale.forLanguageTag(languageTag), zone)
        val lunar = PersianDateUtils.lunarDate(epoch, languageTag, NumeralMode.LATIN, lunarOffsetDays, zone)
        TextNormalizer.formatDigits("$gregorian  ·  $lunar", numeralMode)
    }
    val daysDelta = remember(date, todayDate) { ChronoUnit.DAYS.between(todayDate, date) }
    val isHoliday = occasions.any(Occasion::isHoliday)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
    ) {
        // No nested scroll and no height cap: the month view's single LazyColumn scrolls this
        // card together with the page (docs/UI_COUNCIL_REVIEW.md, C-3).
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            PersianDateUtils.fullDateWithWeekday(epoch, languageTag, numeralMode),
                            style = MarbleTextStyles.itemTitle,
                        )
                        if (daysDelta >= 0L) {
                            Surface(
                                shape = CircleShape,
                                color = if (daysDelta == 0L) colors.primary.copy(alpha = 0.16f) else colors.secondaryContainer,
                            ) {
                                Text(
                                    TextNormalizer.formatDigits(daysLeftLabel(daysDelta), numeralMode),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (daysDelta == 0L) colors.primary else colors.onSecondaryContainer,
                                )
                            }
                        }
                        if (isHoliday) {
                            Surface(shape = CircleShape, color = colors.errorContainer) {
                                Text(
                                    stringResource(R.string.calendar_holiday_badge),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onErrorContainer,
                                )
                            }
                        }
                    }
                    Text(
                        secondaryDates,
                        style = MarbleTextStyles.metaLabel,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = onOpenWorkday,
                    shape = CircleShape,
                    color = colors.secondaryContainer,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.WorkOutline,
                            contentDescription = stringResource(R.string.calendar_workday_title),
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = onAddTask,
                    shape = CircleShape,
                    color = colors.primary,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.calendar_add_task),
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (occasions.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_no_occasions),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                occasions.forEach { occasion ->
                    val isFavorite = occasion.id in favoriteOccasionIds
                    val accent = categoryColor(occasion.category)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colors.surface.copy(alpha = 0.78f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = stringResource(R.string.calendar_plan_action),
                                role = Role.Button,
                            ) { onPlanOccasion(occasion, epoch) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accent),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    occasion.title(languageTag),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(categoryLabelRes(occasion.category)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                )
                            }
                            if (occasion.isHoliday) {
                                Surface(shape = CircleShape, color = colors.error.copy(alpha = 0.12f)) {
                                    Text(
                                        stringResource(R.string.calendar_holiday_badge),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.error,
                                    )
                                }
                            }
                            IconButton(onClick = { onToggleFavorite(occasion.id) }) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = stringResource(
                                        if (isFavorite) R.string.calendar_favorite_remove else R.string.calendar_favorite_add,
                                    ),
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            // Visual affordance only — the whole row already plans the occasion,
                            // so a second nested button would double-focus in TalkBack (C-5).
                            Icon(
                                Icons.Outlined.AddTask,
                                contentDescription = null,
                                tint = colors.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 6.dp).size(20.dp),
                            )
                        }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, tasks.size), numeralMode),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
                val sortedTasks = remember(tasks) { tasks.sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE } }
                val allDayLabel = stringResource(R.string.calendar_all_day)
                sortedTasks.take(MAX_DAY_CARD_TASKS).forEach { task ->
                    val due = task.dueAtEpochMillis
                    val timeLabel = when {
                        due == null -> null
                        task.isAllDay -> allDayLabel
                        else -> TextNormalizer.formatDigits(
                            formatClock(Instant.ofEpochMilli(due).atZone(zone).toLocalTime()),
                            numeralMode,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenTask(task) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (timeLabel != null) {
                            Surface(shape = CircleShape, color = colors.primary.copy(alpha = 0.12f)) {
                                Text(
                                    timeLabel,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    maxLines = 1,
                                )
                            }
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
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (monthBloom.isNotEmpty()) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.14f))
                OccasionBloomStrip(
                    monthOccasions = monthBloom,
                    languageTag = languageTag,
                    onOccasionClick = onBloomOccasionClick,
                )
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
    val colors = MaterialTheme.colorScheme
    val isHoliday = occasions.any(Occasion::isHoliday)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cell.isToday) Modifier.border(1.5.dp, colors.primary, MaterialTheme.shapes.large)
                else Modifier,
            ),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when {
                    cell.isToday -> colors.primary
                    isHoliday -> colors.error.copy(alpha = 0.14f)
                    else -> colors.primaryContainer
                },
            ) {
                Column(
                    Modifier.widthIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        TextNormalizer.formatDigits(cell.day.toString(), numeralMode),
                        style = MarbleTextStyles.dayNumber,
                        color = when {
                            cell.isToday -> colors.onPrimary
                            isHoliday -> colors.error
                            else -> colors.onPrimaryContainer
                        },
                    )
                    Text(
                        PersianDateUtils.weekdayShort(cell.weekdayIndex, languageTag),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            cell.isToday -> colors.onPrimary.copy(alpha = 0.85f)
                            isHoliday -> colors.error.copy(alpha = 0.85f)
                            else -> colors.onPrimaryContainer.copy(alpha = 0.75f)
                        },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                occasions.take(3).forEach { occasion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (occasion.isHoliday) colors.error else categoryColor(occasion.category)),
                        )
                        Text(
                            occasion.title(languageTag),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (occasion.isHoliday) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (occasion.isHoliday) colors.error else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (occasions.size > 3) {
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.calendar_more_occasions, occasions.size - 3), numeralMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (cell.taskCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            TextNormalizer.formatDigits(stringResource(R.string.calendar_tasks_count, cell.taskCount), numeralMode),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (cell.isToday) {
                    Surface(shape = CircleShape, color = colors.primaryContainer) {
                        Text(
                            stringResource(R.string.calendar_today_marker),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    PersianDateUtils.fullDate(epoch, languageTag, numeralMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Month header inside the occasion list; it is what turns a thousand rows into readable chapters. */
@Composable
private fun OccasionMonthHeader(
    month: Int,
    languageTag: String,
    count: Int,
    numeralMode: NumeralMode,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            PersianDateUtils.monthName(month, languageTag),
            style = MarbleTextStyles.sectionTitle,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Text(
                TextNormalizer.formatDigits(stringResource(R.string.calendar_occasions_short, count), numeralMode),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * One occasion in the list: two-line date chip, title, metadata chips, and favorite/plan actions.
 */
@Composable
private fun OccasionRow(
    month: Int,
    day: Int,
    year: Int,
    occasion: Occasion,
    languageTag: String,
    numeralMode: NumeralMode,
    weekdayIndex: Int,
    isFavorite: Boolean,
    daysUntil: Long?,
    onToggleFavorite: () -> Unit,
    onPlan: (() -> Unit)?,
) {
    val accent = categoryColor(occasion.category)
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val planLabel = stringResource(R.string.calendar_plan_action)
    Surface(
        shape = shape,
        color = colors.surface,
        tonalElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (occasion.isHoliday) Modifier.border(1.dp, colors.error.copy(alpha = 0.22f), shape)
                else Modifier,
            )
            .then(if (onPlan != null) Modifier.clickable(onClickLabel = planLabel, role = Role.Button, onClick = onPlan) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (occasion.isHoliday) colors.errorContainer.copy(alpha = 0.45f) else colors.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(42.dp).padding(vertical = 4.dp),
                ) {
                    Text(
                        TextNormalizer.formatDigits(day.toString(), numeralMode),
                        style = MarbleTextStyles.dayNumber,
                        color = if (occasion.isHoliday) colors.error else colors.onSurface,
                    )
                    Text(
                        PersianDateUtils.weekdayShort(weekdayIndex, languageTag),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    occasion.title(languageTag),
                    style = MarbleTextStyles.itemTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    OccasionChip(
                        text = stringResource(categoryLabelRes(occasion.category)),
                        color = accent,
                    )
                    if (daysUntil != null && daysUntil >= 0) {
                        OccasionChip(
                            text = TextNormalizer.formatDigits(daysLeftLabel(daysUntil), numeralMode),
                            color = colors.primary,
                        )
                    }
                    if (occasion.isHoliday) {
                        OccasionChip(
                            text = stringResource(R.string.calendar_holiday_badge),
                            color = colors.error,
                        )
                    }
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.calendar_favorite_remove else R.string.calendar_favorite_add,
                    ),
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (onPlan != null) {
                // Visual affordance only — the whole row is the plan action (C-5).
                Icon(
                    Icons.Outlined.AddTask,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun OccasionChip(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun daysLeftLabel(days: Long): String = when (days) {
    0L -> stringResource(R.string.calendar_days_today)
    1L -> stringResource(R.string.calendar_days_tomorrow)
    else -> stringResource(R.string.calendar_days_left, days.toInt())
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

/**
 * Accent per occasion category. The three hues without a Material role come in light/dark pairs
 * picked for legibility of the small category labels that use them as text color — the previous
 * single hard-coded values failed contrast on one of the two themes (docs/UI_COUNCIL_REVIEW.md, DS-4).
 */
@Composable
fun categoryColor(category: OccasionCategory): Color {
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (category) {
        OccasionCategory.OFFICIAL -> MaterialTheme.colorScheme.primary
        OccasionCategory.NATIONAL -> MaterialTheme.colorScheme.tertiary
        OccasionCategory.RELIGIOUS -> if (darkSurface) Color(0xFF5FD3AE) else Color(0xFF1F7A5C)
        OccasionCategory.PERSONAL -> MaterialTheme.colorScheme.secondary
        OccasionCategory.INTERNATIONAL -> if (darkSurface) Color(0xFF8FB8F8) else Color(0xFF2F62B8)
        OccasionCategory.EDUCATIONAL -> if (darkSurface) Color(0xFFE3B65C) else Color(0xFF8A5A12)
    }
}

internal fun categoryLabelRes(category: OccasionCategory): Int = when (category) {
    OccasionCategory.OFFICIAL -> R.string.calendar_category_official
    OccasionCategory.NATIONAL -> R.string.calendar_category_national
    OccasionCategory.RELIGIOUS -> R.string.calendar_category_religious
    OccasionCategory.PERSONAL -> R.string.calendar_category_personal
    OccasionCategory.INTERNATIONAL -> R.string.calendar_category_international
    OccasionCategory.EDUCATIONAL -> R.string.calendar_category_educational
}

private fun weekdayIndexFor(year: Int, month: Int, day: Int, zone: ZoneId): Int {
    val epoch = PersianDateUtils.startOfJalaliDay(year, month, day, zone) ?: return 0
    return PersianDateUtils.of(epoch, zone).weekdayIndex
}

/** Number of tasks listed in the day card before the "+N more" line. */
private const val MAX_DAY_CARD_TASKS = 6

/** Preparation task lead time used when the user has not picked an occasion reminder lead. */
private const val DEFAULT_PREP_DAYS = 3

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
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val taskCounts = tasks.asSequence()
        .filter { !it.isArchived }
        .mapNotNull { task -> task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } }
        .groupingBy { it }
        .eachCount()
    val allCells = (0 until 42).map { offset ->
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
    val cells = if (allCells.subList(35, 42).none { it.inDisplayedMonth }) allCells.take(35) else allCells
    return MonthData(month, year, cells)
}
