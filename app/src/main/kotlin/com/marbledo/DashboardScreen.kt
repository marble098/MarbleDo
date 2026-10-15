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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
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

private data class DashboardOccasion(
    val title: String,
    val category: OccasionCategory,
    val isHoliday: Boolean,
)

private const val MAX_DASHBOARD_OCCASIONS = 3

/** Upcoming occasions listed on the dashboard when the user has starred or has occasions ahead. */
private const val MAX_DASHBOARD_UPCOMING = 4

/**
 * A glanceable daily planner: the date and the day's workload first, the task list right below it,
 * and the calmer context — live countdown, occasions, weekly load — further down the page.
 *
 * Everything the screen could do before is still here; what changed is the order and the weight of
 * each block. Search, category filter and sort moved into one compact toolbar above the task list
 * instead of stacking three full-width controls on top of the tasks.
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
    val groups = remember(state.visibleTasks, zone, todayDateForOccasions) { groupTasks(state.visibleTasks, zone) }
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
    val progress = if (state.activeCount + state.completedCount == 0) 0f else state.completedCount.toFloat() / (state.activeCount + state.completedCount)
    val categoryChoices = remember(settings.taskCategories) {
        settings.taskCategories.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DailyHeaderCard(
                    todayOpen = todayOpen,
                    completedCount = state.completedCount,
                    openCount = activeTasks.size,
                    progress = progress,
                    todayEpoch = todayEpoch,
                    currentHour = now.hour,
                    settings = settings,
                    numeralMode = numeralMode,
                    onOpenCalendar = onOpenCalendar,
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

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = { showDayAgenda = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.agenda_title), maxLines = 2)
                    }
                    androidx.compose.material3.OutlinedButton(onClick = { showWeeklyReview = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_title), maxLines = 2)
                    }
                }
            }

            // Tasks are the reason the screen exists, so they come first and they keep the space.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.dash_tasks), state.visibleTasks.size, Modifier.weight(1f))
                    DashboardModeSwitcher(
                        mode = settings.dashboardViewMode,
                        onSelect = { mode ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onViewModeChange(mode)
                        },
                    )
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) viewModel.setQuery("") }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.dash_search),
                            tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { categoriesOpen = !categoriesOpen }) {
                        Icon(
                            Icons.Outlined.FilterAlt,
                            contentDescription = stringResource(R.string.dash_filters),
                            tint = if (categoriesOpen || state.selectedCategory != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                Icons.Outlined.Sort,
                                contentDescription = stringResource(R.string.dash_sort_menu),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            TaskSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(sort)) },
                                    onClick = { viewModel.setSort(sort); sortMenuExpanded = false },
                                )
                            }
                        }
                    }
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                    items(TaskFilter.entries, key = { it.name }) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filterLabel(filter)) },
                        )
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
                            Modifier.fillMaxWidth().padding(22.dp),
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
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                    if (settings.dashboardViewMode == DashboardViewMode.TIMELINE) {
                        item {
                            CountdownRoad(
                                countdowns = countdowns,
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

            item {
                OccasionRadarCard(occasionIndex, listOf(todayPersian.year, todayPersian.year + 1), todayEpoch,
                    state.allTasks, favoriteIds, settings.languageTag, onOpen = { radarPlan = it })
            }

            item {
                WeeklyWorkloadCard(state.allTasks, now.toInstant(), zone, settings.languageTag,
                    onOpenTask = { editingTask = it },
                    onAdd = { date ->
                        sharedText = ""
                        addDateIso = date.toString()
                        plannedCountdown = false
                        showQuickAdd = true
                    })
            }
        }

        ExtendedFloatingActionButton(
            onClick = { sharedText = ""; showQuickAdd = true },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.dash_new_task)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        )

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
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
 * Today at a glance: greeting, the three dates, the day's counters and one progress bar. The three
 * counters are plain columns on one row instead of three separate filled cards, and the weekday is
 * part of the date line rather than a chip of its own.
 */
@Composable
private fun DailyHeaderCard(
    todayOpen: Int,
    completedCount: Int,
    openCount: Int,
    progress: Float,
    todayEpoch: Long,
    currentHour: Int,
    settings: AppSettings,
    numeralMode: NumeralMode,
    onOpenCalendar: () -> Unit,
    onShare: () -> Unit,
) {
    val greeting = when {
        currentHour < 12 -> stringResource(R.string.dash_greeting_morning)
        currentHour < 18 -> stringResource(R.string.dash_greeting_afternoon)
        else -> stringResource(R.string.dash_greeting_evening)
    }
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surface,
        ),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
    ) {
        Box(Modifier.background(gradient, MaterialTheme.shapes.extraLarge)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Screen hero greeting in Vazirmatn Black, with the weekday above a Light date line.
                        Text(greeting, style = MarbleTextStyles.screenTitle)
                        Text(
                            PersianDateUtils.fullDateWithWeekday(todayEpoch, settings.languageTag, numeralMode),
                            style = MarbleTextStyles.dateDigits.copy(fontSize = 15.sp, lineHeight = 21.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            PersianDateUtils.tripleDate(todayEpoch, settings.languageTag, numeralMode, settings.lunarOffsetDays),
                            style = MarbleTextStyles.metaLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                            IconButton(onClick = onOpenCalendar) {
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    contentDescription = stringResource(R.string.nav_calendar),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                            IconButton(onClick = onShare) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.dash_share_today),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeaderStat(stringResource(R.string.dash_stat_today), todayOpen, numeralMode, Modifier.weight(1f))
                    HeaderStatDivider()
                    HeaderStat(stringResource(R.string.dash_stat_done), completedCount, numeralMode, Modifier.weight(1f))
                    HeaderStatDivider()
                    HeaderStat(stringResource(R.string.dash_stat_all), openCount, numeralMode, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    )
                    Text(
                        TextNormalizer.formatDigits("${(progress * 100).toInt()}%", numeralMode),
                        style = MarbleTextStyles.metaLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: Int, numeralMode: NumeralMode, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            TextNormalizer.formatDigits(value.toString(), numeralMode),
            style = MarbleTextStyles.cardTitle.copy(fontSize = 21.sp, lineHeight = 27.sp),
        )
        Text(label, style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun HeaderStatDivider() {
    Box(
        Modifier
            .height(28.dp)
            .width(1.dp)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_next_moment), style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.primary)
                Text(task.title, style = MarbleTextStyles.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(20.dp))
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
    val upcomingLabel = stringResource(if (favoritesOnly) R.string.dash_my_occasions else R.string.dash_upcoming_occasions)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(
                title,
                style = MarbleTextStyles.itemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                Text(trailing, style = MarbleTextStyles.metaLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Two-labelled switch with the counts inside the labels; smaller than two stacked cards. */
@Composable
private fun SegmentedSwitch(
    options: List<Pair<String, Int>>,
    selectedIndex: Int,
    numeralMode: NumeralMode,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, (label, count) ->
            val selected = index == selectedIndex
            Surface(
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).clickable { onSelect(index) },
            ) {
                Text(
                    TextNormalizer.formatDigits("$label $count", numeralMode),
                    style = MarbleTextStyles.metaLabel,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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

/** One collapsible task group: a light row with the name, the count and a chevron. */
@Composable
private fun TaskGroupHeader(group: TaskGroup, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(group.labelRes), style = MarbleTextStyles.sectionTitle, modifier = Modifier.weight(1f))
            Text(
                TextNormalizer.formatDigits(count.toString(), LocalNumeralMode.current),
                style = MarbleTextStyles.metaLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(20.dp),
            )
        }
    }
}

/** Three-icon switcher for the dashboard view mode; the choice is persisted in settings. */
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
            Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            choices.forEach { (value, iconAndLabel) ->
                val selected = value == mode
                IconButton(
                    onClick = { onSelect(value) },
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                ) {
                    Icon(
                        iconAndLabel.first,
                        contentDescription = iconAndLabel.second,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
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
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = task.title,
                        tint = if (task.isCompleted) accentColor else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
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
    onOpenFocus: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numeralMode = LocalNumeralMode.current
    val nowEpoch = System.currentTimeMillis()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
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
                    val days = ((task.dueAtEpochMillis ?: nowEpoch) - nowEpoch) / 86_400_000L
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
                .size(if (isNow) 13.dp else 17.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            title,
            style = MarbleTextStyles.metaLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 72.dp),
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

private fun groupTasks(tasks: List<Task>, zone: ZoneId): List<GroupedTasks> {
    val today = LocalDate.now(zone)
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
