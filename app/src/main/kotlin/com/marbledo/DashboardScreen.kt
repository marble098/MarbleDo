package com.marble098.marbledo

import com.marbledo.domain.model.CalendarDisplayMode
import com.marbledo.domain.model.DashboardViewMode
import com.marbledo.feature.tasks.TaskChange
import com.marbledo.feature.tasks.TaskChangeKind
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import com.marble098.marbledo.ui.GreetingHeadline
import com.marble098.marbledo.ui.GreetingSky
import com.marble098.marbledo.ui.FocusOrbit
import com.marble098.marbledo.ui.auroraBrush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.core.designsystem.MarbleSpacing
import com.marbledo.core.designsystem.MarbleTextStyles
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.enabledOccasionCultures
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionPlanRequest
import com.marbledo.feature.calendar.OccasionPlanner
import com.marbledo.feature.calendar.OccasionTemplates
import com.marbledo.feature.calendar.OccasionPlanSheet
import com.marbledo.feature.calendar.OccasionRadarCard
import com.marbledo.feature.calendar.OccasionRecommendation
import com.marbledo.feature.calendar.UpcomingOccasion
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.calendar.categoryColor
import com.marbledo.feature.countdown.CountdownCreateSheet
import com.marbledo.feature.countdown.CountdownFace
import com.marbledo.feature.countdown.CountdownMiniCard
import com.marbledo.feature.countdown.ThemeSwatchRow
import com.marbledo.feature.countdown.countdownThemeLabel as themeLabelOf
import com.marbledo.feature.tasks.SectionHeader
import com.marbledo.feature.tasks.TaskEditorSheet
import com.marbledo.feature.tasks.TaskFilter
import com.marbledo.feature.tasks.TaskQuickAddSheet
import com.marbledo.feature.tasks.TaskRowCard
import com.marbledo.feature.tasks.TaskSort
import com.marbledo.feature.tasks.TasksViewModel
import com.marbledo.feature.tasks.localizedTaskDate
import com.marbledo.feature.tasks.localizedTaskDateTime
import com.marbledo.feature.tasks.priorityColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

private enum class TaskGroup(val labelRes: Int) {
    OVERDUE(R.string.dash_group_overdue),
    TODAY(R.string.dash_group_today),
    TOMORROW(R.string.dash_group_tomorrow),
    WEEK(R.string.dash_group_week),
    LATER(R.string.dash_group_later),
    NO_DATE(R.string.dash_group_no_date),
}

private data class GroupedTasks(val group: TaskGroup, val tasks: List<Task>)

/**
 * The three stops of the home page. The screen used to be one endless stack of eleven cards;
 * now each stop is short and has one job, so the list the user came for starts immediately.
 */
private enum class DashboardTab(val labelRes: Int) {
    TODAY(R.string.dash_tab_today),
    WEEK(R.string.dash_tab_week),
    OCCASIONS(R.string.dash_tab_occasions),
}

private data class DashboardOccasion(
    val title: String,
    val category: OccasionCategory,
    val isHoliday: Boolean,
)

private const val MAX_DASHBOARD_OCCASIONS = 3

/** Upcoming occasions listed on the dashboard when the user has starred or has occasions ahead. */
private const val MAX_DASHBOARD_UPCOMING = 4

/**
 * The home page is three short stops, not one endless stack of cards.
 *
 * A compact hero carries the greeting, the three dates and the day's counters, and it stays put
 * above the stops so the date and the progress ring are always one glance away. Under it, one
 * segmented row picks the stop:
 *
 *  * **Today** — the task toolbar and the task list, then the live countdown. Tasks are the reason
 *    the screen exists, so they start immediately and they keep the space.
 *  * **Week** — how the coming days are loaded, plus the day agenda.
 *  * **Occasions** — today's occasions, the ones ahead, and the occasion radar.
 *
 * Nothing was removed. What changed is that the three occasion surfaces no longer sit on top of
 * each other on one screen, and the weekly load no longer pushes the task list below the fold.
 */
@Composable
fun DashboardScreen(
    viewModel: TasksViewModel,
    settings: AppSettings,
    occasionCatalog: OccasionCatalog,
    enabledOccasionCategories: Set<OccasionCategory>,
    onOpenFocus: (Task) -> Unit,
    onOpenCalendar: () -> Unit,
    onAddCategory: (String) -> Unit,
    initialShareText: String? = null,
    initialOpenAdd: Boolean = false,
    onInitialIntentConsumed: () -> Unit = {},
    initialAddDateIso: String? = null,
    onInitialAddDateConsumed: () -> Unit = {},
    pendingEditTaskId: Long? = null,
    onPendingEditTaskConsumed: () -> Unit = {},
    onCalendarDisplaySelected: (CalendarDisplayMode) -> Unit = {},
    onViewModeChange: (DashboardViewMode) -> Unit = {},
    favoriteOccasionIds: Set<String> = emptySet(),
    onToggleFavoriteOccasion: (String) -> Unit = {},
    initialPlanRequest: OccasionPlanRequest? = null,
    onInitialPlanConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val numeralMode = LocalNumeralMode.current
    val zone = remember { ZoneId.systemDefault() }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var sharedText by rememberSaveable { mutableStateOf("") }
    var editingTask by rememberSaveable(stateSaver = Saver<Task?, String>(
        save = { it?.let { task -> Json.encodeToString(task) }.orEmpty() },
        restore = { if (it.isBlank()) null else runCatching { Json.decodeFromString<Task>(it) }.getOrNull() },
    )) { mutableStateOf<Task?>(null) }
    var showCreateCountdown by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var addDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var plannedCountdown by rememberSaveable { mutableStateOf(true) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var collapsedTaskGroups by rememberSaveable { mutableStateOf("LATER,NO_DATE") }
    // The search field and the category chips stay folded away until they are needed, so the task
    // list starts right under its title instead of under three rows of controls.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var categoriesOpen by rememberSaveable { mutableStateOf(false) }
    var showDayAgenda by rememberSaveable { mutableStateOf(false) }
    var showWeeklyReview by rememberSaveable { mutableStateOf(false) }
    var radarPlan by remember { mutableStateOf<OccasionRecommendation?>(null) }
    // Which stop of the home page is showing. Stored as a name so the value survives process
    // death without leaning on enum serialisation.
    var selectedTabName by rememberSaveable { mutableStateOf(DashboardTab.TODAY.name) }
    val selectedTab = DashboardTab.entries.firstOrNull { it.name == selectedTabName } ?: DashboardTab.TODAY

    LaunchedEffect(initialShareText, initialOpenAdd) {
        if (initialOpenAdd || !initialShareText.isNullOrBlank()) {
            sharedText = initialShareText.orEmpty()
            showQuickAdd = true
            onInitialIntentConsumed()
        }
    }

    LaunchedEffect(initialAddDateIso) {
        if (initialAddDateIso != null) {
            sharedText = ""
            addDateIso = initialAddDateIso
            showQuickAdd = true
            onInitialAddDateConsumed()
        }
    }
    LaunchedEffect(initialPlanRequest) {
        if (initialPlanRequest != null) {
            if (initialPlanRequest.checklist.isNotEmpty() || initialPlanRequest.planningTag != null) {
                editingTask = OccasionTemplates.draft(initialPlanRequest, zone).copy(countdownTheme = settings.countdownTheme)
            } else {
                sharedText = initialPlanRequest.title
                addDateIso = initialPlanRequest.dateIso
                plannedCountdown = initialPlanRequest.asCountdown
                showQuickAdd = true
            }
            onInitialPlanConsumed()
        }
    }

    LaunchedEffect(pendingEditTaskId, state.allTasks) {
        if (pendingEditTaskId != null) {
            val target = state.allTasks.firstOrNull { it.id == pendingEditTaskId }
            if (target != null) {
                editingTask = target
                onPendingEditTaskConsumed()
            }
        }
    }

    // Every change is confirmed with a short snackbar. Its Undo action reverts the most recent change.
    val undoActionLabel by rememberUpdatedState(stringResource(R.string.dash_undo_action))
    LaunchedEffect(viewModel) {
        viewModel.changes.collect { change ->
            val result = snackbarHost.showSnackbar(
                message = taskChangeMessage(context, change),
                actionLabel = if (change.kind == TaskChangeKind.ADD_FAILED) null else undoActionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoLastChange()
        }
    }

    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(zone, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = ZonedDateTime.now(zone)
                delay(60_000L)
            }
        }
    }
    val todayDateForOccasions = remember(now) { now.toLocalDate() }
    val todayEpoch = remember(zone, todayDateForOccasions) { todayDateForOccasions.atStartOfDay(zone).toInstant().toEpochMilli() }
    val todayPersian = remember(zone, todayEpoch) { PersianDateUtils.today(zone, todayEpoch) }
    val occasionIndex = remember(occasionCatalog, enabledOccasionCategories, settings.lunarOffsetDays, settings.occasionCultureIds, todayPersian.year) {
        OccasionIndex.build(
            catalog = occasionCatalog,
            jalaliYears = listOf(todayPersian.year, todayPersian.year + 1),
            lunarOffsetDays = settings.lunarOffsetDays,
            enabledCategories = enabledOccasionCategories,
            enabledCultures = settings.enabledOccasionCultures(),
            zone = zone,
        )
    }
    val todayOccasions = remember(occasionIndex, todayPersian) {
        occasionIndex.on(todayPersian.year, todayPersian.month, todayPersian.day)
    }
    val activeTasks = remember(state.allTasks) { state.allTasks.filter { !it.isCompleted && !it.isArchived } }
    val nowEpoch = remember(now) { now.toInstant().toEpochMilli() }
    val countdowns = remember(state.allTasks, nowEpoch) {
        state.allTasks
            .filter { !it.isCompleted && !it.isArchived && it.countdownEnabled && (it.dueAtEpochMillis ?: Long.MIN_VALUE) >= nowEpoch }
            .sortedBy { it.dueAtEpochMillis }
    }
    val heroCountdown = countdowns.firstOrNull()
    val favoriteIds = remember(occasionCatalog, settings.favoriteOccasionIds) { occasionCatalog.canonicalFavoriteIds(settings.favoriteOccasionIds) }
    val upcomingOccasions = remember(occasionIndex, todayDateForOccasions, favoriteIds, settings.languageTag) {
        OccasionPlanner.upcoming(
            index = occasionIndex,
            jalaliYears = listOf(todayPersian.year, todayPersian.year + 1),
            nowEpochMillis = todayEpoch,
            favoriteIds = favoriteIds,
            favoritesOnly = favoriteIds.isNotEmpty(),
            limit = MAX_DASHBOARD_UPCOMING,
            zone = zone,
        )
    }
    val groups = remember(state.visibleTasks, zone, todayDateForOccasions) { groupTasks(state.visibleTasks, zone, todayDateForOccasions) }
    val todayOpen = remember(activeTasks, zone, todayDateForOccasions) {
        activeTasks.count { task ->
            task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == todayDateForOccasions } == true
        }
    }
    val todayTasks = remember(activeTasks, zone, todayDateForOccasions) {
        activeTasks.filter { task ->
            task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == todayDateForOccasions } == true
        }
    }
    // Finishing the last open task of the day deserves a little celebration of its own.
    var previousTodayOpen by remember { mutableIntStateOf(todayOpen) }
    val allDoneMessage = stringResource(R.string.dash_all_done_today)
    LaunchedEffect(todayOpen) {
        if (todayOpen == 0 && previousTodayOpen > 0) {
            snackbarHost.showSnackbar(
                message = allDoneMessage.toString(),
                withDismissAction = true,
            )
        }
        previousTodayOpen = todayOpen
    }
    // stringResource needs composable scope, so the pieces are read here and only the
    // plain-text assembly happens inside the remember block below.
    val shareTitle = stringResource(R.string.dash_share_title)
    val shareSummary = stringResource(R.string.dash_summary, todayOpen, state.completedCount)
    val shareTodayLine = PersianDateUtils.fullDateWithWeekday(todayEpoch, settings.languageTag, numeralMode)
    val shareText = remember(todayTasks, shareTitle, shareSummary, shareTodayLine) {
        buildString {
            append(shareTitle).append("\n")
            append(shareTodayLine).append("\n")
            append(shareSummary)
            todayTasks.forEach { task -> append("\n· ").append(task.title) }
        }
    }
    val categoryChoices = remember(settings.taskCategories) {
        settings.taskCategories.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The list ends above the compact FAB with room to spare, so the last card can be
            // read in full instead of sitting underneath a wide pill (docs/UI_REFRESH.md, V-4).
            contentPadding = PaddingValues(start = MarbleSpacing.lg, end = MarbleSpacing.lg, top = MarbleSpacing.md, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
        ) {
            item {
                DailyHeaderCard(
                    todayOpen = todayOpen,
                    completedCount = state.completedCount,
                    openCount = activeTasks.size,
                    todayEpoch = todayEpoch,
                    currentHour = now.hour,
                    settings = settings,
                    numeralMode = numeralMode,
                    onOpenCalendar = onOpenCalendar,
                    onOpenAgenda = { showDayAgenda = true },
                    onShare = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    },
                                    null,
                                ),
                            )
                        }
                    },
                )
            }

            // Three short stops instead of one endless stack: the tasks the user came for start
            // right under this row, and the calmer context lives one tap away on its own stop.
            item {
                DashboardTabRow(
                    selected = selectedTab,
                    onSelect = { tab ->
                        if (tab != selectedTab) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabName = tab.name
                    },
                )
            }

            when (selectedTab) {
                DashboardTab.TODAY -> {
                    // Tasks are the reason the screen exists, so they come first and they keep the space.
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SectionHeader(stringResource(R.string.dash_tasks), state.visibleTasks.size, Modifier.weight(1f))
                            DashboardModeSwitcher(
                                mode = settings.dashboardViewMode,
                                onSelect = { mode ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onViewModeChange(mode)
                                },
                            )
                        }
                    }
                    if (searchOpen) {
                        item {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.dash_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (state.query.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setQuery("") }) {
                                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.dash_clear_search))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                            )
                        }
                    }
                    if (categoriesOpen && categoryChoices.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                                contentPadding = PaddingValues(start = MarbleSpacing.xxs, end = MarbleSpacing.md),
                            ) {
                                item {
                                    FilterChip(
                                        selected = state.selectedCategory == null,
                                        onClick = { viewModel.setCategory(null) },
                                        label = { Text(stringResource(R.string.dash_category_all)) },
                                    )
                                }
                                items(categoryChoices) { name ->
                                    FilterChip(
                                        selected = state.selectedCategory == name,
                                        onClick = { viewModel.setCategory(name) },
                                        label = { Text(name, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                    // The quick-filter chips share their row with the search/filter/sort toolbar. The title
                    // row above keeps only the section title and the view switch, so all six actions keep
                    // full-size touch targets on narrow screens (docs/UI_COUNCIL_REVIEW.md, D-1/D-2).
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                                contentPadding = PaddingValues(start = MarbleSpacing.xxs, end = MarbleSpacing.md),
                            ) {
                                items(TaskFilter.entries, key = { it.name }) { filter ->
                                    FilterChip(
                                        selected = state.filter == filter,
                                        onClick = { viewModel.setFilter(filter) },
                                        label = { Text(filterLabel(filter)) },
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) viewModel.setQuery("") }) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = stringResource(R.string.dash_search),
                                            tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    IconButton(onClick = { categoriesOpen = !categoriesOpen }) {
                                        Icon(
                                            Icons.Outlined.FilterAlt,
                                            contentDescription = stringResource(R.string.dash_filters),
                                            tint = if (categoriesOpen || state.selectedCategory != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { sortMenuExpanded = true }) {
                                            Icon(
                                                Icons.Outlined.Sort,
                                                contentDescription = stringResource(R.string.dash_sort_menu),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                            TaskSort.entries.forEach { sort ->
                                                DropdownMenuItem(
                                                    text = { Text(sortLabel(sort)) },
                                                    trailingIcon = {
                                                        if (state.sortBy == sort) Icon(Icons.Outlined.Check, contentDescription = null)
                                                    },
                                                    onClick = { viewModel.setSort(sort); sortMenuExpanded = false },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.selectedIds.isNotEmpty()) {
                        item {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        TextNormalizer.formatDigits(stringResource(R.string.dash_selected_count, state.selectedIds.size), numeralMode),
                                        style = MarbleTextStyles.metaLabel,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { viewModel.completeSelected() }) { Text(stringResource(R.string.dash_complete_selected)) }
                                    TextButton(onClick = { viewModel.clearSelection() }) { Text(stringResource(R.string.dash_clear_selection)) }
                                }
                            }
                        }
                    }

                    if (groups.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(MarbleSpacing.xl),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.dash_empty_title), style = MarbleTextStyles.itemTitle)
                                    Text(
                                        stringResource(R.string.dash_empty_body),
                                        style = MarbleTextStyles.bodyPrimary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    } else {
                        when (settings.dashboardViewMode) {
                            DashboardViewMode.GRID -> {
                                val gridRows = state.visibleTasks.chunked(2)
                                items(gridRows, key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        row.forEach { task ->
                                            TaskGridCard(
                                                task = task,
                                                selected = task.id in state.selectedIds,
                                                onToggle = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.toggleCompleted(task)
                                                },
                                                onEdit = { editingTask = task },
                                                onLongPress = { viewModel.toggleSelection(task.id) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (row.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            else -> {
                                groups.forEach { grouped ->
                                    val isCollapsed = grouped.group.name in collapsedTaskGroups.split(',').filter(String::isNotBlank)
                                    item(key = "header-${grouped.group.name}") {
                                        TaskGroupHeader(
                                            group = grouped.group,
                                            count = grouped.tasks.size,
                                            expanded = !isCollapsed,
                                            onClick = {
                                                val next = collapsedTaskGroups.split(',').filter(String::isNotBlank).toMutableSet()
                                                if (!next.add(grouped.group.name)) next.remove(grouped.group.name)
                                                collapsedTaskGroups = next.joinToString(",")
                                            },
                                        )
                                    }
                                    if (!isCollapsed) {
                                        if (settings.dashboardViewMode == DashboardViewMode.LIST) {
                                            items(grouped.tasks, key = { it.id }) { task ->
                                                TaskRowCard(
                                                    task = task,
                                                    selected = task.id in state.selectedIds,
                                                    onToggle = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.toggleCompleted(task)
                                                    },
                                                    onOpenCountdown = { onOpenFocus(task) },
                                                    onEdit = { editingTask = task },
                                                    onArchive = { if (task.isArchived) viewModel.unarchive(task) else viewModel.archive(task) },
                                                    onDelete = { viewModel.delete(task) },
                                                    onPin = { viewModel.editTask(task.copy(isPinned = !task.isPinned)) },
                                                    onToggleCountdown = {
                                                        viewModel.editTask(task.copy(countdownEnabled = !task.countdownEnabled))
                                                    },
                                                    onLongPress = { viewModel.toggleSelection(task.id) },
                                                )
                                            }
                                        } else {
                                            grouped.tasks.forEachIndexed { index, task ->
                                                item(key = "timeline-${task.id}") {
                                                    TaskTimelineItem(
                                                        task = task,
                                                        isLast = index == grouped.tasks.lastIndex,
                                                        selected = task.id in state.selectedIds,
                                                        onToggle = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.toggleCompleted(task)
                                                        },
                                                        onOpenCountdown = { onOpenFocus(task) },
                                                        onEdit = { editingTask = task },
                                                        onArchive = { if (task.isArchived) viewModel.unarchive(task) else viewModel.archive(task) },
                                                        onDelete = { viewModel.delete(task) },
                                                        onPin = { viewModel.editTask(task.copy(isPinned = !task.isPinned)) },
                                                        onToggleCountdown = {
                                                            viewModel.editTask(task.copy(countdownEnabled = !task.countdownEnabled))
                                                        },
                                                        onLongPress = { viewModel.toggleSelection(task.id) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(MarbleSpacing.md)) }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.dash_countdown_hero), countdowns.size.takeIf { it > 0 }, Modifier.weight(1f))
                            IconButton(onClick = { showCreateCountdown = true }) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.dash_create_countdown),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (heroCountdown == null) {
                        item {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        stringResource(R.string.dash_countdown_none_title),
                                        style = MarbleTextStyles.itemTitle,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { showCreateCountdown = true }) {
                                        Text(stringResource(R.string.dash_create_countdown))
                                    }
                                }
                            }
                        }
                    } else when (settings.dashboardViewMode) {
                        DashboardViewMode.GRID -> {
                            // Every countdown becomes a card; the first one is just the most urgent stop.
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    countdowns.forEach { task ->
                                        CountdownMiniCard(
                                            title = task.title,
                                            dueAtEpochMillis = task.dueAtEpochMillis ?: 0L,
                                            themeId = task.countdownTheme,
                                            onClick = { editingTask = task },
                                            onLongPress = { onOpenFocus(task) },
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                ) {
                                    CountdownHero(
                                        task = heroCountdown,
                                        themeId = heroCountdown.countdownTheme.takeIf { CountdownTheme.isKnown(it) } ?: settings.countdownTheme,
                                        calendarLabel = { epoch -> localizedTaskDate(epoch) },
                                        onOpenFocus = { onOpenFocus(heroCountdown) },
                                        onDisableRequested = {
                                            viewModel.editTask(heroCountdown.copy(countdownEnabled = false))
                                        },
                                        modifier = Modifier.padding(MarbleSpacing.lg),
                                    )
                                }
                            }
                            if (settings.dashboardViewMode == DashboardViewMode.TIMELINE) {
                                item {
                                    CountdownRoad(
                                        countdowns = countdowns,
                                        nowEpochMillis = nowEpoch,
                                        onOpenFocus = onOpenFocus,
                                        onEdit = { editingTask = it },
                                    )
                                }
                            } else if (countdowns.size > 1) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SectionHeader(stringResource(R.string.dash_upcoming_countdowns), countdowns.size - 1)
                                        countdowns.drop(1).forEach { task ->
                                            CountdownMiniCard(
                                                title = task.title,
                                                dueAtEpochMillis = task.dueAtEpochMillis ?: 0L,
                                                themeId = task.countdownTheme,
                                                fillWidth = true,
                                                onClick = { editingTask = task },
                                                onLongPress = { onOpenFocus(task) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                DashboardTab.WEEK -> {
                    // The week is the second stop: how the coming days are loaded, plus the two
                    // entry points that used to sit above the task list as loose pill buttons.
                    item {
                        WeeklyWorkloadCard(state.allTasks, now.toInstant(), zone, settings.languageTag,
                            onOpenTask = { editingTask = it },
                            // The week card owns the weekly-review entry point now; the dashboard
                            // header no longer stacks two extra action pills on top of the tasks.
                            onOpenReview = { showWeeklyReview = true },
                            onAdd = { date ->
                                sharedText = ""
                                addDateIso = date.toString()
                                plannedCountdown = false
                                showQuickAdd = true
                            })
                    }
                    item { Spacer(Modifier.height(MarbleSpacing.md)) }
                    // One labelled row instead of a second pill button: the agenda is a place you
                    // go, so the whole row is the target.
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable(
                                    onClickLabel = stringResource(R.string.agenda_title),
                                    role = Role.Button,
                                    onClick = { showDayAgenda = true },
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        stringResource(R.string.agenda_title),
                                        style = MarbleTextStyles.itemTitle,
                                        maxLines = 1,
                                    )
                                    Text(
                                        PersianDateUtils.fullDateWithWeekday(todayEpoch, settings.languageTag, numeralMode),
                                        style = MarbleTextStyles.microLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                DashboardTab.OCCASIONS -> {
                    // Everything occasion-shaped lives here now. The header no longer repeats
                    // today's occasion above a card that lists today's occasions.
                    item {
                        OccasionsCard(
                            todayOccasions = todayOccasions.map {
                                DashboardOccasion(it.title(settings.languageTag), it.category, it.isHoliday)
                            },
                            upcoming = upcomingOccasions,
                            favoritesOnly = favoriteIds.isNotEmpty(),
                            languageTag = settings.languageTag,
                            numeralMode = numeralMode,
                            onOpenCalendar = onOpenCalendar,
                        )
                    }
                    item { Spacer(Modifier.height(MarbleSpacing.md)) }
                    item {
                        OccasionRadarCard(occasionIndex, listOf(todayPersian.year, todayPersian.year + 1), todayEpoch,
                            state.allTasks, favoriteIds, settings.languageTag, onOpen = { radarPlan = it })
                    }
                }
            }
        }

        // A compact, clearly floating FAB: the old extended pill stretched over the last card
        // and read as part of the bottom navigation.
        FloatingActionButton(
            onClick = { sharedText = ""; showQuickAdd = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MarbleSpacing.lg),
            shape = CircleShape,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.dash_new_task))
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
        )
    }

    if (showDayAgenda) DayAgendaSheet(state.allTasks, now.toInstant(), settings,
        onDraft = { editingTask = it }, onOpenTask = { editingTask = it }, onDismiss = { showDayAgenda = false })
    if (showWeeklyReview) WeeklyReviewSheet(state.allTasks, now.toInstant(), settings,
        onOpenTask = { editingTask = it }, onDismiss = { showWeeklyReview = false })
    radarPlan?.let { recommendation ->
        val upcoming = recommendation.upcoming
        OccasionPlanSheet(upcoming.occasion, upcoming.epochMillis, settings.languageTag, upcoming.isFavorite,
            recommendation.preparationDays,
            onPlan = { editingTask = OccasionTemplates.draft(it, zone).copy(countdownTheme = settings.countdownTheme); radarPlan = null },
            onToggleFavorite = { onToggleFavoriteOccasion(upcoming.occasion.id); radarPlan = null },
            onDismiss = { radarPlan = null },
            references = occasionCatalog.sources.filter { it.id in upcoming.occasion.sourceIds },
            educationModeEnabled = settings.educationModeEnabled, tasks = state.allTasks,
            holidays = remember(occasionCatalog, todayPersian.year, settings.lunarOffsetDays) {
                OccasionPlanner.holidayDates(occasionCatalog, listOf(todayPersian.year - 1, todayPersian.year, todayPersian.year + 1), settings.lunarOffsetDays, zone)
            }, thursdayClosed = settings.thursdayWeekendEnabled, onCreatePreparation = viewModel::addTasks)
    }

    if (showQuickAdd) {
        TaskQuickAddSheet(
            categories = settings.taskCategories,
            languageTag = settings.languageTag,
            countdownThemeId = settings.countdownTheme,
            initialText = sharedText,
            initialDateIso = addDateIso,
            initialCountdownEnabled = plannedCountdown,
            weekStartsSaturday = settings.weekStartsSaturday,
            pickerCalendar = settings.countdownCalendar,
            onPickerCalendarChange = onCalendarDisplaySelected,
            onAddCategory = onAddCategory,
            onSubmit = { task ->
                viewModel.addTask(task)
                showQuickAdd = false
                sharedText = ""
                addDateIso = null
                plannedCountdown = true
            },
            onDismiss = { showQuickAdd = false; sharedText = ""; addDateIso = null; plannedCountdown = true },
            countdownThemePicker = { selected, onSelect ->
                ThemeSwatchRow(selected = CountdownTheme.from(selected), onSelect = { theme -> onSelect(theme.id) })
            },
            countdownThemeLabel = { id -> themeLabelOf(CountdownTheme.from(id)) },
        )
    }

    editingTask?.let { task ->
        TaskEditorSheet(
            task = task,
            categories = settings.taskCategories,
            languageTag = settings.languageTag,
            weekStartsSaturday = settings.weekStartsSaturday,
            pickerCalendar = settings.countdownCalendar,
            onPickerCalendarChange = onCalendarDisplaySelected,
            onAddCategory = onAddCategory,
            onSave = { updated ->
                if (updated.id == 0L) viewModel.addTask(updated) else viewModel.editTask(updated)
                editingTask = null
            },
            onDelete = { viewModel.delete(task); editingTask = null },
            onArchive = { viewModel.archive(task); editingTask = null },
            onUnarchive = { viewModel.unarchive(task); editingTask = null },
            onDismiss = { editingTask = null },
            countdownThemePicker = { selected, onSelect ->
                ThemeSwatchRow(selected = CountdownTheme.from(selected), onSelect = { theme -> onSelect(theme.id) })
            },
            countdownThemeLabel = { id -> themeLabelOf(CountdownTheme.from(id)) },
        )
    }

    if (showCreateCountdown) {
        CountdownCreateSheet(
            calendarDisplay = settings.countdownCalendar,
            defaultThemeId = settings.countdownTheme,
            weekStartsSaturday = settings.weekStartsSaturday,
            onCalendarDisplaySelected = onCalendarDisplaySelected,
            onDismiss = { showCreateCountdown = false },
            onCreate = { task -> viewModel.addTask(task); showCreateCountdown = false },
        )
    }
}

/**
 * The three stops of the home page as one segmented row. The selected stop is a real tab for
 * accessibility services, and every stop keeps a full-size touch target (docs/UI_COUNCIL_REVIEW.md).
 */
@Composable
private fun DashboardTabRow(
    selected: DashboardTab,
    onSelect: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DashboardTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val label = stringResource(tab.labelRes)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(MaterialTheme.shapes.large)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) },
                shape = MaterialTheme.shapes.large,
                color = if (isSelected) colors.surface else colors.surfaceVariant.copy(alpha = 0f),
            ) {
                Text(
                    text = label,
                    style = MarbleTextStyles.metaLabel,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Today at a glance: greeting, the three dates, the day's counters and one progress bar. The three
 * counters are plain columns on one row instead of three separate filled cards, and the weekday is
 * part of the date line rather than a chip of its own.
 */
@Composable
private fun DailyHeaderCard(
    todayOpen: Int,
    completedCount: Int,
    openCount: Int,
    todayEpoch: Long,
    currentHour: Int,
    settings: AppSettings,
    numeralMode: NumeralMode,
    onOpenCalendar: () -> Unit,
    onOpenAgenda: () -> Unit,
    onShare: () -> Unit,
) {
    // A 2 a.m. dashboard is not "good morning": the night has its own greeting and sky.
    val greeting = when (currentHour) {
        in 5..11 -> stringResource(R.string.dash_greeting_morning)
        in 12..17 -> stringResource(R.string.dash_greeting_afternoon)
        in 18..22 -> stringResource(R.string.dash_greeting_evening)
        else -> stringResource(R.string.dash_greeting_night)
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val aurora = auroraBrush(currentHour, isRtl)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
    ) {
        Box(
            Modifier
                .background(aurora, MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surface.copy(alpha = 0.40f),
                            colors.surface.copy(alpha = 0.18f),
                        ),
                    ),
                    MaterialTheme.shapes.extraLarge,
                ),
        ) {
            GreetingSky(hour = currentHour, modifier = Modifier.matchParentSize())
            // Compacted by the UI council (D-3): tighter padding and gaps so the task list starts
            // above the fold on short phones instead of below a ~300dp header.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MarbleSpacing.lg, vertical = MarbleSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val calendarLabel = stringResource(R.string.nav_calendar)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClickLabel = calendarLabel, role = Role.Button, onClick = onOpenCalendar),
                        verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs),
                    ) {
                        GreetingHeadline(greeting, Modifier.fillMaxWidth())
                        Text(
                            PersianDateUtils.fullDateWithWeekday(todayEpoch, settings.languageTag, numeralMode),
                            style = MarbleTextStyles.dateCaption,
                            color = colors.onSurface,
                            maxLines = 2,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
                        Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.78f)) {
                            IconButton(onClick = onOpenCalendar) {
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    contentDescription = stringResource(R.string.nav_calendar),
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Surface(shape = CircleShape, color = colors.surface.copy(alpha = 0.78f)) {
                            IconButton(onClick = onShare) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.dash_share_today),
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                // The triple date gets its own full-width line. Inside the greeting column it had
                // to share the row with two 48dp buttons, so the lunar date was cut with an
                // ellipsis while the width below it stayed empty.
                Text(
                    PersianDateUtils.tripleDate(todayEpoch, settings.languageTag, numeralMode, settings.lunarOffsetDays),
                    style = MarbleTextStyles.microLabel,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
                HorizontalDivider(color = colors.outline.copy(alpha = 0.16f))
                // Flat counters next to the single FocusOrbit ring: the three nested stat cards
                // are gone, and the whole row opens the day's agenda, so the header no longer
                // needs a second row of pill buttons (docs/UI_REFRESH.md, V-2/V-3).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable(
                            onClickLabel = stringResource(R.string.agenda_title),
                            role = Role.Button,
                            onClick = onOpenAgenda,
                        )
                        .padding(vertical = MarbleSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.md),
                ) {
                    FocusOrbit(
                        completed = completedCount,
                        total = (todayOpen + completedCount).coerceAtLeast(1),
                        open = todayOpen,
                        numeralMode = numeralMode,
                        isRtl = isRtl,
                        orbitSize = 72.dp,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
                        ) {
                            Icon(
                                Icons.Outlined.AccessTime,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                stringResource(R.string.agenda_title),
                                style = MarbleTextStyles.metaLabel,
                                color = colors.primary,
                                maxLines = 2,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HeaderMetric(
                                label = stringResource(R.string.dash_stat_today),
                                value = todayOpen,
                                accent = colors.primary,
                                numeralMode = numeralMode,
                                modifier = Modifier.weight(1f),
                            )
                            HeaderStatDivider()
                            HeaderMetric(
                                label = stringResource(R.string.dash_stat_done),
                                value = completedCount,
                                accent = colors.tertiary,
                                numeralMode = numeralMode,
                                modifier = Modifier.weight(1f),
                            )
                            HeaderStatDivider()
                            HeaderMetric(
                                label = stringResource(R.string.dash_stat_all),
                                value = openCount,
                                accent = colors.secondary,
                                numeralMode = numeralMode,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One header counter: a number and a wrapping label, with no card of its own. */
@Composable
private fun HeaderMetric(
    label: String,
    value: Int,
    accent: Color,
    numeralMode: NumeralMode,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xxs)) {
        Text(
            TextNormalizer.formatDigits(value.toString(), numeralMode),
            style = MarbleTextStyles.cardTitle,
            color = accent,
            maxLines = 1,
        )
        Text(
            label,
            style = MarbleTextStyles.metaLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** Hairline between two header counters; keeps the row readable without boxes. */
@Composable
private fun HeaderStatDivider() {
    Box(
        Modifier
            .padding(horizontal = MarbleSpacing.xs)
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    )
}

@Composable
private fun CountdownHero(
    task: Task,
    themeId: String,
    calendarLabel: @Composable (Long) -> String,
    onOpenFocus: () -> Unit,
    onDisableRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val due = task.dueAtEpochMillis ?: return
    val theme = CountdownTheme.from(themeId)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_next_moment), style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.primary)
                Text(task.title, style = MarbleTextStyles.itemTitle, maxLines = 2)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
            }
        }
        CountdownFace(title = task.title, dueAtEpochMillis = due, theme = theme, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
            Text(
                calendarLabel(due),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onOpenFocus) {
                Text(stringResource(R.string.dash_focus_mode))
            }
            TextButton(onClick = onDisableRequested) { Text(stringResource(R.string.dash_turn_off)) }
        }
    }
}

/**
 * Today's occasions and the occasions ahead in one card. The two lists used to be two stacked
 * cards with two identical headers; now they share one header and one switch.
 */
@Composable
private fun OccasionsCard(
    todayOccasions: List<DashboardOccasion>,
    upcoming: List<UpcomingOccasion>,
    favoritesOnly: Boolean,
    languageTag: String,
    numeralMode: NumeralMode,
    onOpenCalendar: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val upcomingLabel = stringResource(if (favoritesOnly) R.string.dash_occasions_mine_short else R.string.dash_occasions_upcoming_short)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MarbleSpacing.lg), verticalArrangement = Arrangement.spacedBy(MarbleSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                SegmentedSwitch(
                    options = listOf(
                        stringResource(R.string.dash_stat_today) to todayOccasions.size,
                        upcomingLabel to upcoming.size,
                    ),
                    selectedIndex = selectedTab,
                    numeralMode = numeralMode,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.weight(1f),
                )
            }
            if (selectedTab == 0) {
                if (todayOccasions.isEmpty()) {
                    Text(
                        stringResource(R.string.dash_no_occasion),
                        style = MarbleTextStyles.bodyPrimary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    todayOccasions.take(MAX_DASHBOARD_OCCASIONS).forEach { occasion ->
                        OccasionLine(
                            title = occasion.title,
                            accent = categoryColor(occasion.category),
                            badge = stringResource(R.string.dash_holiday_badge).takeIf { occasion.isHoliday },
                            trailing = null,
                            onClick = onOpenCalendar,
                        )
                    }
                    val remaining = todayOccasions.size - MAX_DASHBOARD_OCCASIONS
                    if (remaining > 0) {
                        Text(
                            TextNormalizer.formatDigits(stringResource(R.string.dash_more_occasions, remaining), numeralMode),
                            style = MarbleTextStyles.metaLabel,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                if (upcoming.isEmpty()) {
                    Text(
                        stringResource(R.string.dash_no_upcoming_occasion),
                        style = MarbleTextStyles.bodyPrimary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    upcoming.forEach { item ->
                        OccasionLine(
                            title = item.occasion.title(languageTag),
                            accent = categoryColor(item.occasion.category),
                            badge = null,
                            trailing = TextNormalizer.formatDigits(daysLeftLabel(item.daysUntil), numeralMode),
                            onClick = onOpenCalendar,
                        )
                    }
                }
            }
            TextButton(onClick = onOpenCalendar, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.nav_calendar))
            }
        }
    }
}

@Composable
private fun OccasionLine(
    title: String,
    accent: Color,
    badge: String?,
    trailing: String?,
    onClick: (() -> Unit)? = null,
) {
    // A plain row inside the card instead of a tinted sub-card: three stacked pills inside one
    // card was the clearest case of "card inside a card" on this screen (docs/UI_REFRESH.md, V-2).
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = MarbleSpacing.xs, vertical = MarbleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.sm),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Text(
            title,
            style = MarbleTextStyles.itemTitle,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    badge,
                    style = MarbleTextStyles.metaLabel,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

/**
 * Two-labelled switch with the counts inside the labels; smaller than two stacked cards.
 * Segments are proper tabs for accessibility services, not bare clickable surfaces
 * (docs/UI_COUNCIL_REVIEW.md, D-4).
 */
@Composable
private fun SegmentedSwitch(
    options: List<Pair<String, Int>>,
    selectedIndex: Int,
    numeralMode: NumeralMode,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, (label, count) ->
            val selected = index == selectedIndex
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
            ) {
                Text(
                    TextNormalizer.formatDigits("$label $count", numeralMode),
                    style = MarbleTextStyles.metaLabel,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun daysLeftLabel(days: Long): String = when (days) {
    0L -> stringResource(R.string.dash_days_today)
    1L -> stringResource(R.string.dash_days_tomorrow)
    else -> stringResource(R.string.dash_days_left, days.toInt())
}

/** One collapsible task group: a semantic pill row with accent dot, name, count badge and chevron. */
@Composable
private fun TaskGroupHeader(group: TaskGroup, count: Int, expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = when (group) {
        TaskGroup.OVERDUE -> colors.error
        TaskGroup.TODAY -> colors.primary
        TaskGroup.TOMORROW -> colors.tertiary
        TaskGroup.WEEK -> colors.secondary
        TaskGroup.LATER, TaskGroup.NO_DATE -> colors.outline
    }
    val stateLabel = stringResource(if (expanded) R.string.dash_state_expanded else R.string.dash_state_collapsed)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = stateLabel },
        shape = MaterialTheme.shapes.large,
        color = if (group == TaskGroup.OVERDUE) colors.errorContainer.copy(alpha = 0.35f)
        else colors.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                stringResource(group.labelRes),
                style = MarbleTextStyles.sectionTitle,
                color = if (group == TaskGroup.OVERDUE) colors.error else colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
            ) {
                Text(
                    TextNormalizer.formatDigits(count.toString(), LocalNumeralMode.current),
                    style = MarbleTextStyles.metaLabel,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Three-icon switcher for the dashboard view mode; the choice is persisted in settings.
 * Segments are 40dp tabs inside a 48dp-tall pill — the old 30dp IconButtons had their Material
 * touch targets shrunk away with an explicit size (docs/UI_COUNCIL_REVIEW.md, D-1).
 */
@Composable
private fun DashboardModeSwitcher(mode: DashboardViewMode, onSelect: (DashboardViewMode) -> Unit) {
    val choices = listOf(
        DashboardViewMode.LIST to (Icons.Outlined.ViewList to stringResource(R.string.dash_view_list)),
        DashboardViewMode.TIMELINE to (Icons.Outlined.Timeline to stringResource(R.string.dash_view_timeline)),
        DashboardViewMode.GRID to (Icons.Outlined.GridView to stringResource(R.string.dash_view_grid)),
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.selectableGroup().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            choices.forEach { (value, iconAndLabel) ->
                val selected = value == mode
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        iconAndLabel.first,
                        contentDescription = iconAndLabel.second,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * One task on the vertical timeline: a coloured node with a continuous connecting line, then the
 * full task card with all its interactions.
 */
@Composable
private fun TaskTimelineItem(
    task: Task,
    isLast: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenCountdown: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onToggleCountdown: () -> Unit,
    onLongPress: () -> Unit,
) {
    val railColor = priorityColor(task.priority)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(24.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(railColor))
            if (!isLast) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                )
            }
        }
        TaskRowCard(
            task = task,
            selected = selected,
            onToggle = onToggle,
            onOpenCountdown = onOpenCountdown,
            onEdit = onEdit,
            onArchive = onArchive,
            onDelete = onDelete,
            onPin = onPin,
            onToggleCountdown = onToggleCountdown,
            onLongPress = onLongPress,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Compact card used by the box (grid) layout: title, due line and category dot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskGridCard(
    task: Task,
    selected: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = if (task.colorArgb == com.marbledo.feature.tasks.DEFAULT_TASK_COLOR_ARGB) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(task.colorArgb.toInt())
    }
    Card(
        modifier = modifier.combinedClickable(onClick = onEdit, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MarbleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MarbleSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A real checkbox for accessibility services, not a 22dp bare clickable box
                // (docs/UI_COUNCIL_REVIEW.md, D-6).
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .toggleable(value = task.isCompleted, role = Role.Checkbox) { onToggle() }
                        .semantics(mergeDescendants = true) { contentDescription = task.title },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (task.isCompleted) accentColor else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                task.dueAtEpochMillis?.let { localizedTaskDateTime(it, task.isAllDay) }
                    ?: stringResource(R.string.dash_group_no_date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.category.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                    Text(
                        task.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The timeline layout for the countdowns: "now" on the leading edge, every countdown a stop on the
 * road. Tapping a stop opens the focus view; holding it opens the editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountdownRoad(
    countdowns: List<Task>,
    nowEpochMillis: Long,
    onOpenFocus: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numeralMode = LocalNumeralMode.current
    // "Now" comes from the screen's ticking state, not a fresh clock read mid-composition (D-8).
    val nowEpoch = nowEpochMillis
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().padding(MarbleSpacing.lg)) {
            Box(
                Modifier
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                RoadNode(
                    title = stringResource(R.string.dash_days_today),
                    detail = "",
                    accent = MaterialTheme.colorScheme.tertiary,
                    isNow = true,
                    onClick = {},
                    onLongPress = {},
                )
                countdowns.forEach { task ->
                    val days = (((task.dueAtEpochMillis ?: nowEpoch) - nowEpoch) / 86_400_000L).coerceAtLeast(0L)
                    RoadNode(
                        title = task.title,
                        detail = TextNormalizer.formatDigits(daysLeftLabel(days), numeralMode),
                        accent = MaterialTheme.colorScheme.primary,
                        isNow = false,
                        onClick = { onOpenFocus(task) },
                        onLongPress = { onEdit(task) },
                    )
                }
            }
        }
    }
}

/** One stop on the countdown road: a node on the track with the title and the time left. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoadNode(
    title: String,
    detail: String,
    accent: Color,
    isNow: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(if (isNow) 12.dp else 16.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            title,
            style = MarbleTextStyles.metaLabel,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 96.dp),
            textAlign = TextAlign.Center,
        )
        if (detail.isNotBlank()) {
            Text(
                detail,
                style = MarbleTextStyles.metaLabel,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun filterLabel(filter: TaskFilter): String = when (filter) {
    TaskFilter.TODAY -> stringResource(R.string.dash_filter_today)
    TaskFilter.TOMORROW -> stringResource(R.string.dash_filter_tomorrow)
    TaskFilter.UPCOMING -> stringResource(R.string.dash_filter_upcoming)
    TaskFilter.ALL -> stringResource(R.string.dash_filter_all)
    TaskFilter.COMPLETED -> stringResource(R.string.dash_filter_completed)
    TaskFilter.ARCHIVED -> stringResource(R.string.dash_filter_archived)
}

@Composable
private fun sortLabel(sort: TaskSort): String = when (sort) {
    TaskSort.DUE_DATE -> stringResource(R.string.dash_sort_due)
    TaskSort.PRIORITY -> stringResource(R.string.dash_sort_priority)
    TaskSort.TITLE -> stringResource(R.string.dash_sort_title)
    TaskSort.RECENT -> stringResource(R.string.dash_sort_recent)
}

private fun taskChangeMessage(context: Context, change: TaskChange): String = when (change.kind) {
    TaskChangeKind.ADDED -> if (change.count > 1) context.getString(R.string.planning_added_count, change.count) else context.getString(R.string.dash_change_added)
    TaskChangeKind.EDITED -> context.getString(R.string.dash_change_saved)
    TaskChangeKind.COMPLETED -> context.getString(R.string.dash_change_completed)
    TaskChangeKind.REOPENED -> context.getString(R.string.dash_change_reopened)
    TaskChangeKind.BULK_COMPLETED -> context.getString(R.string.dash_change_bulk, change.count)
    TaskChangeKind.ARCHIVED -> context.getString(R.string.dash_change_archived)
    TaskChangeKind.UNARCHIVED -> context.getString(R.string.dash_change_unarchived)
    TaskChangeKind.DELETED -> context.getString(R.string.dash_change_deleted)
    TaskChangeKind.ADD_FAILED -> context.getString(R.string.planning_save_failed)
}

private fun groupTasks(tasks: List<Task>, zone: ZoneId, today: LocalDate): List<GroupedTasks> {
    // "Today" is the screen's single source of truth; re-reading the clock here could disagree
    // with the header around midnight (docs/UI_COUNCIL_REVIEW.md, D-8).
    val buckets = linkedMapOf<TaskGroup, MutableList<Task>>()
    tasks.forEach { task ->
        val date = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val group = when {
            date == null -> TaskGroup.NO_DATE
            date.isBefore(today) -> TaskGroup.OVERDUE
            date == today -> TaskGroup.TODAY
            date == today.plusDays(1) -> TaskGroup.TOMORROW
            date.isBefore(today.plusDays(8)) -> TaskGroup.WEEK
            else -> TaskGroup.LATER
        }
        buckets.getOrPut(group) { mutableListOf() }.add(task)
    }
    return TaskGroup.entries.mapNotNull { group -> buckets[group]?.let { GroupedTasks(group, it) } }
}
