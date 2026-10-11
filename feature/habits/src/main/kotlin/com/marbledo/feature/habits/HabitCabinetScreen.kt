package com.marbledo.feature.habits

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marbledo.core.designsystem.LocalNumeralMode
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitTheme
import com.marbledo.domain.util.HabitStats

/**
 * The habit cabinet: two drawers, one for the habits the user is building and one for the habits
 * they are leaving behind. Each drawer opens onto its folders, and every folder records today in a
 * single tap.
 */
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel,
    settings: AppSettings,
    onThemeChange: (HabitTheme) -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val numeralMode = LocalNumeralMode.current
    val dark = habitDarkMode()
    val cabinet = habitPalette(HabitTheme.from(settings.habitTheme), dark)
    val languageTag = if (LocalConfiguration.current.locales[0].language == "fa") "fa" else "en"
    var buildOpen by rememberSaveable { mutableStateOf(true) }
    var quitOpen by rememberSaveable { mutableStateOf(true) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }
    var creatingKind by rememberSaveable { mutableStateOf<HabitKind?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Coming back to the tab is the moment a day change must be picked up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshToday()
                viewModel.refreshSideEffects()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val active = state.habits.filter { !it.isArchived }
    val buildHabits = active.filter { it.kind == HabitKind.BUILD }
    val quitHabits = active.filter { it.kind == HabitKind.QUIT }
    val archived = state.habits.filter { it.isArchived }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HabitTodayHeader(
                done = state.summary.doneToday,
                scheduled = state.summary.scheduledToday,
                score = state.summary.score,
                bestStreak = state.summary.bestStreak,
                palette = cabinet,
                numeralMode = numeralMode,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.habit_field_theme),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = onAddWidget) { Text(stringResource(R.string.habit_add_widget)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    HabitThemeSwatchRow(
                        selected = HabitTheme.from(settings.habitTheme),
                        onSelect = onThemeChange,
                        dark = dark,
                    )
                }
            }
        }
        item {
            HabitDrawer(
                title = stringResource(R.string.habit_drawer_build),
                hint = stringResource(R.string.habit_drawer_build_hint),
                badge = drawerBadge(buildHabits.size, buildHabits.count { isDoneToday(it, state) }, numeralMode),
                icon = Icons.Outlined.Inventory2,
                expanded = buildOpen,
                reduceMotion = settings.reduceMotion,
                palette = cabinet,
                onToggle = { buildOpen = !buildOpen },
            ) {
                if (buildHabits.isEmpty()) {
                    EmptyDrawerText(stringResource(R.string.habit_empty_build), cabinet)
                } else {
                    buildHabits.forEach { habit -> HabitFolder(habit, state, viewModel, numeralMode) { detailId = it } }
                }
                AddRow(text = stringResource(R.string.habit_add_in_drawer), tint = cabinet.accent) {
                    creatingKind = HabitKind.BUILD
                }
            }
        }
        item {
            HabitDrawer(
                title = stringResource(R.string.habit_drawer_quit),
                hint = stringResource(R.string.habit_drawer_quit_hint),
                badge = drawerBadge(quitHabits.size, quitHabits.count { isDoneToday(it, state) }, numeralMode),
                icon = Icons.Outlined.Block,
                expanded = quitOpen,
                reduceMotion = settings.reduceMotion,
                palette = cabinet,
                onToggle = { quitOpen = !quitOpen },
            ) {
                if (quitHabits.isEmpty()) {
                    EmptyDrawerText(stringResource(R.string.habit_empty_quit), cabinet)
                } else {
                    quitHabits.forEach { habit -> HabitFolder(habit, state, viewModel, numeralMode) { detailId = it } }
                }
                Text(
                    stringResource(R.string.habit_quit_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = cabinet.muted,
                )
                AddRow(text = stringResource(R.string.habit_add_in_drawer), tint = cabinet.accent) {
                    creatingKind = HabitKind.QUIT
                }
            }
        }
        if (archived.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        TextButton(onClick = { archivedOpen = !archivedOpen }) {
                            Text(stringResource(R.string.habit_archived_section))
                        }
                        if (archivedOpen) {
                            archived.forEach { habit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(habit.icon, modifier = Modifier.padding(end = 8.dp))
                                    Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { viewModel.setArchived(habit, false) }) {
                                        Text(stringResource(R.string.habit_unarchive))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    val editingHabit = editingId?.let { id -> state.habits.firstOrNull { it.id == id } }
    if (creatingKind != null || editingHabit != null) {
        HabitEditorSheet(
            habit = editingHabit,
            kind = creatingKind ?: HabitKind.BUILD,
            palette = editingHabit?.let { habitPaletteFor(it) } ?: cabinet,
            dark = dark,
            onSave = { habit ->
                viewModel.save(habit)
                creatingKind = null
                editingId = null
            },
            onDelete = { habit ->
                viewModel.delete(habit)
                creatingKind = null
                editingId = null
            },
            onDismiss = {
                creatingKind = null
                editingId = null
            },
        )
    }

    detailId?.let { id ->
        val habit = state.habits.firstOrNull { it.id == id }
        if (habit != null) {
            HabitDetailSheet(
                habit = habit,
                entries = state.entries.filter { it.habitId == id },
                stats = state.stats[id] ?: HabitStats(),
                today = state.today,
                weekStartsSaturday = settings.weekStartsSaturday,
                languageTag = languageTag,
                onEdit = {
                    editingId = id
                    detailId = null
                },
                onArchive = { archivedValue -> viewModel.setArchived(habit, archivedValue) },
                onDelete = {
                    viewModel.delete(habit)
                    detailId = null
                },
                onDismiss = { detailId = null },
            )
        }
    }
}

@Composable
private fun HabitFolder(
    habit: Habit,
    state: HabitsUiState,
    viewModel: HabitsViewModel,
    numeralMode: com.marbledo.domain.model.NumeralMode,
    onOpen: (Long) -> Unit,
) {
    val stats = state.stats[habit.id] ?: HabitStats()
    HabitCard(
        habit = habit,
        stats = stats,
        palette = habitPaletteFor(habit),
        numeralMode = numeralMode,
        onOpen = { onOpen(habit.id) },
        onToggleDone = {
            if (habit.kind == HabitKind.QUIT) viewModel.setSlippedToday(habit, stats.todayValue == 0)
            else viewModel.setDoneToday(habit, !stats.todayDone)
        },
        onAdjust = { delta -> viewModel.adjustToday(habit, delta) },
    )
}

@Composable
private fun EmptyDrawerText(text: String, palette: HabitPalette) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = palette.muted,
    )
}

@Composable
private fun AddRow(text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint)
    }
}

@Composable
private fun drawerBadge(
    count: Int,
    done: Int,
    numeralMode: com.marbledo.domain.model.NumeralMode,
): String = if (count == 0) {
    habitNumber(0, numeralMode)
} else {
    "${habitNumber(done, numeralMode)}/${habitNumber(count, numeralMode)}"
}

private fun isDoneToday(habit: Habit, state: HabitsUiState): Boolean =
    (state.stats[habit.id] ?: HabitStats()).todayDone
