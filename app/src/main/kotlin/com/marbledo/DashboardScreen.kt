package com.marble098.marbledo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.CountdownTheme
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
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
 * The merged home: a live countdown hero, today's occasions and the task groups, all in one
 * scrolling surface with a floating create button that fades away while the list moves.
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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val numeralMode = LocalNumeralMode.current
    val zone = remember { ZoneId.systemDefault() }
    val listState = rememberLazyListState()
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var sharedText by rememberSaveable { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var showCreateCountdown by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showUndo by remember { mutableStateOf(false) }

    LaunchedEffect(initialShareText, initialOpenAdd) {
        if (initialOpenAdd || !initialShareText.isNullOrBlank()) {
            sharedText = initialShareText.orEmpty()
            showQuickAdd = true
            onInitialIntentConsumed()
        }
    }

    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            now = ZonedDateTime.now(zone)
            delay(60_000L)
        }
    }
    val todayPersian = remember(zone, now) { PersianDateUtils.today(zone) }
    val todayEpoch = remember(zone, now) { now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() }
    val occasionIndex = remember(occasionCatalog, enabledOccasionCategories, settings.lunarOffsetDays, todayPersian.year) {
        OccasionIndex.build(
            catalog = occasionCatalog,
            jalaliYears = listOf(todayPersian.year, todayPersian.year + 1),
            lunarOffsetDays = settings.lunarOffsetDays,
            enabledCategories = enabledOccasionCategories,
            zone = zone,
        )
    }
    val todayOccasions = occasionIndex.on(todayPersian.year, todayPersian.month, todayPersian.day)
    val activeTasks = remember(state.allTasks) { state.allTasks.filter { !it.isCompleted && !it.isArchived } }
    val countdowns = remember(state.allTasks) {
        state.allTasks
            .filter { !it.isCompleted && !it.isArchived && it.countdownEnabled && it.dueAtEpochMillis != null }
            .sortedBy { it.dueAtEpochMillis }
    }
    val heroCountdown = countdowns.firstOrNull()
    val groups = remember(state.visibleTasks, zone, now) { groupTasks(state.visibleTasks, zone) }
    val todayOpen = remember(activeTasks, zone, now) {
        activeTasks.count { task ->
            val date = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            date == null || !date.isAfter(now.toLocalDate())
        }
    }
    val progress = if (state.activeCount + state.completedCount == 0) 0f else state.completedCount.toFloat() / (state.activeCount + state.completedCount)
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 260 }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DashboardHeader(state.query, viewModel::setQuery, todayOpen, state.completedCount, activeTasks.size, progress, todayEpoch, now.hour, settings, numeralMode, onOpenCalendar) }

            item { SectionHeader(stringResource(R.string.dash_countdown_hero)) }

            if (heroCountdown == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.dash_countdown_none_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.dash_countdown_none_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { showCreateCountdown = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.dash_create_countdown))
                            }
                        }
                    }
                }
            } else {
                item {
                    CountdownHero(
                        task = heroCountdown,
                        themeId = heroCountdown.countdownTheme.takeIf { CountdownTheme.isKnown(it) } ?: settings.countdownTheme,
                        calendarLabel = { epoch -> localizedTaskDate(epoch) },
                        onToggleCountdown = { enabled ->
                            viewModel.editTask(heroCountdown.copy(countdownEnabled = enabled))
                        },
                        onOpenFocus = { onOpenFocus(heroCountdown) },
                        onDisableRequested = {
                            viewModel.editTask(heroCountdown.copy(countdownEnabled = false))
                            showUndo = true
                        },
                    )
                }
            }

            if (countdowns.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(stringResource(R.string.dash_upcoming_countdowns), countdowns.size - 1)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(countdowns.drop(1), key = { it.id }) { task ->
                                CountdownMiniCard(
                                    title = task.title,
                                    dueAtEpochMillis = task.dueAtEpochMillis ?: 0L,
                                    themeId = task.countdownTheme,
                                    onClick = { editingTask = task },
                                )
                            }
                        }
                    }
                }
            }

            item {
                OccasionsCard(
                    occasions = todayOccasions.map { it.title(settings.languageTag) to it.category },
                    onClick = onOpenCalendar,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(stringResource(R.string.dash_tasks), state.visibleTasks.size, Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(stringResource(R.string.dash_sort_menu))
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 1.dp)) {
                        items(TaskFilter.entries, key = { it.name }) { filter ->
                            FilterChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filterLabel(filter)) },
                            )
                        }
                    }
                    if (showUndo) {
                        TextButton(onClick = { viewModel.undoLastChange(); showUndo = false }) {
                            Text(stringResource(R.string.dash_undo))
                        }
                    }
                    if (state.selectedIds.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                TextNormalizer.formatDigits(stringResource(R.string.dash_selected_count, state.selectedIds.size), numeralMode),
                                style = MaterialTheme.typography.labelLarge,
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
                        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.dash_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.dash_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                groups.forEach { grouped ->
                    item(key = "header-${grouped.group.name}") {
                        SectionHeader(stringResource(grouped.group.labelRes), grouped.tasks.size)
                    }
                    items(grouped.tasks, key = { it.id }) { task ->
                        TaskRowCard(
                            task = task,
                            selected = task.id in state.selectedIds,
                            onToggle = { viewModel.toggleCompleted(task) },
                            onOpenCountdown = { onOpenFocus(task) },
                            onEdit = { editingTask = task },
                            onArchive = { viewModel.archive(task) },
                            onDelete = { viewModel.delete(task); showUndo = true },
                            onPin = { viewModel.editTask(task.copy(isPinned = !task.isPinned)) },
                            onToggleCountdown = {
                                viewModel.editTask(task.copy(countdownEnabled = !task.countdownEnabled))
                                showUndo = true
                            },
                            onLongPress = { viewModel.toggleSelection(task.id) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showFab,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { sharedText = ""; showQuickAdd = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.dash_new_task)) },
            )
        }

        AnimatedVisibility(
            visible = showUndo,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dash_undo), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { viewModel.undoLastChange(); showUndo = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        TaskQuickAddSheet(
            categories = settings.taskCategories,
            languageTag = settings.languageTag,
            countdownThemeId = settings.countdownTheme,
            initialText = sharedText,
            onAddCategory = onAddCategory,
            onSubmit = { task ->
                viewModel.addTask(task)
                showQuickAdd = false
                sharedText = ""
            },
            onDismiss = { showQuickAdd = false; sharedText = "" },
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
            onAddCategory = onAddCategory,
            onSave = { updated -> viewModel.editTask(updated); editingTask = null },
            onDelete = { viewModel.delete(task); editingTask = null; showUndo = true },
            onArchive = { viewModel.archive(task); editingTask = null },
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
            onCalendarDisplaySelected = { },
            onDismiss = { showCreateCountdown = false },
            onCreate = { task -> viewModel.addTask(task); showCreateCountdown = false; showUndo = true },
        )
    }
}

@Composable
private fun DashboardHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    todayOpen: Int,
    completedCount: Int,
    openCount: Int,
    progress: Float,
    todayEpoch: Long,
    currentHour: Int,
    settings: AppSettings,
    numeralMode: NumeralMode,
    onOpenCalendar: () -> Unit,
) {
    val hour = currentHour
    val greeting = when {
        hour < 12 -> stringResource(R.string.dash_greeting_morning)
        hour < 18 -> stringResource(R.string.dash_greeting_afternoon)
        else -> stringResource(R.string.dash_greeting_evening)
    }
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
    )
    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(gradient, MaterialTheme.shapes.extraLarge)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        TextNormalizer.formatDigits(stringResource(R.string.dash_summary, openCount, completedCount), numeralMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), modifier = Modifier.padding(end = 4.dp)) {
                    TextButton(onClick = onOpenCalendar) {
                        Text(
                            PersianDateUtils.fullDate(todayEpoch, settings.languageTag, numeralMode),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatPill(stringResource(R.string.dash_stat_today), todayOpen, Modifier.weight(1f))
                StatPill(stringResource(R.string.dash_stat_done), completedCount, Modifier.weight(1f))
                StatPill(stringResource(R.string.dash_stat_all), openCount, Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
                Text(
                    TextNormalizer.formatDigits(stringResource(R.string.dash_progress, (progress * 100).toInt()), numeralMode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.dash_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = null) }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                TextNormalizer.formatDigits(value.toString(), LocalNumeralMode.current),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun CountdownHero(
    task: Task,
    themeId: String,
    calendarLabel: @Composable (Long) -> String,
    onToggleCountdown: (Boolean) -> Unit,
    onOpenFocus: () -> Unit,
    onDisableRequested: () -> Unit,
) {
    val due = task.dueAtEpochMillis ?: return
    val theme = CountdownTheme.from(themeId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dash_next_moment), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = task.countdownEnabled, onCheckedChange = onToggleCountdown)
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
                Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.dash_focus_mode))
            }
            TextButton(onClick = onDisableRequested) { Text(stringResource(R.string.dash_turn_off)) }
        }
    }
}

@Composable
private fun OccasionsCard(occasions: List<Pair<String, OccasionCategory>>, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.dash_occasions_today), modifier = Modifier.weight(1f))
                TextButton(onClick = onClick) { Text(stringResource(R.string.nav_calendar)) }
            }
            if (occasions.isEmpty()) {
                Text(stringResource(R.string.dash_no_occasion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                occasions.forEach { (title, category) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(categoryColor(category)))
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
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
