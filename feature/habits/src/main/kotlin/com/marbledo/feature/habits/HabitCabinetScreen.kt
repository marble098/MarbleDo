package com.marbledo.feature.habits

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Widgets
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.HabitDay
import com.marbledo.domain.util.HabitDayState
import com.marbledo.domain.util.HabitStats

/**
 * The habit cabinet, in five zones that each do one job:
 *
 *  1. **Today** — the ring, how far today has come, and the two headline numbers. Nothing else:
 *     the appearance controls used to sit in here, which turned a status card into a settings card.
 *  2. **This week** — one cell per day for the whole cabinet, so the week's rhythm is visible
 *     without opening a single habit.
 *  3. **Two drawers** — *Building* and *Letting go*. Inside a drawer the habits that still need
 *     the user come first, then the ones already done, each a flat row with a hairline.
 *  4. **Archive** — the same drawer component, so a retired habit is not a different animal.
 *  5. **Look and widget** — the progress look and the home-screen shortcut, together and labelled.
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
    val languageTag = if (settings.languageTag == "fa") "fa" else "en"
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
    // Inside a drawer, what still needs the user comes first and what is already done sinks to the
    // bottom: the list answers "what is left today" before it answers "what did I do".
    val orderedBuild = remember(buildHabits, state) { byAttention(buildHabits, state) }
    val orderedQuit = remember(quitHabits, state) { byAttention(quitHabits, state) }
    val weekOverview = remember(state.habits, state.entries, state.today) {
        HabitAnalytics.weekOverview(state.habits.filter { !it.isArchived }, state.entries, state.today)
    }
    val weekPlanned = remember(weekOverview) { weekOverview.count { it.state != HabitDayState.OFF } }
    val weekDone = remember(weekOverview) {
        weekOverview.count { it.state == HabitDayState.DONE || it.state == HabitDayState.PARTIAL }
    }

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

        // The week before the drawers: one glance at seven days beats opening every habit to see
        // the same seven days again.
        item {
            HabitWeekOverview(
                week = weekOverview,
                done = weekDone,
                planned = weekPlanned,
                palette = cabinet,
                numeralMode = numeralMode,
                languageTag = languageTag,
            )
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
                caption = stringResource(R.string.habit_order_hint),
            ) {
                if (buildHabits.isEmpty()) {
                    EmptyDrawerText(stringResource(R.string.habit_empty_build), cabinet)
                } else {
                    orderedBuild.forEachIndexed { index, habit ->
                        if (index > 0) HabitRowDivider(cabinet)
                        HabitFolder(habit, state, viewModel, numeralMode) { detailId = it }
                    }
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
                caption = stringResource(R.string.habit_order_hint),
            ) {
                if (quitHabits.isEmpty()) {
                    EmptyDrawerText(stringResource(R.string.habit_empty_quit), cabinet)
                } else {
                    orderedQuit.forEachIndexed { index, habit ->
                        if (index > 0) HabitRowDivider(cabinet)
                        HabitFolder(habit, state, viewModel, numeralMode) { detailId = it }
                    }
                    Text(
                        stringResource(R.string.habit_quit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = cabinet.muted,
                    )
                }
                AddRow(text = stringResource(R.string.habit_add_in_drawer), tint = cabinet.accent) {
                    creatingKind = HabitKind.QUIT
                }
            }
        }
        if (archived.isNotEmpty()) {
            // The archive is a drawer like any other, so a retired habit looks and behaves the same.
            item {
                HabitDrawer(
                    title = stringResource(R.string.habit_archived_section),
                    hint = stringResource(R.string.habit_archived_hint),
                    badge = habitNumber(archived.size, numeralMode),
                    icon = Icons.Outlined.Inventory2,
                    expanded = archivedOpen,
                    reduceMotion = settings.reduceMotion,
                    palette = cabinet,
                    onToggle = { archivedOpen = !archivedOpen },
                ) {
                    if (archived.isEmpty()) {
                        EmptyDrawerText(stringResource(R.string.habit_empty_archived), cabinet)
                    } else {
                        archived.forEachIndexed { index, habit ->
                            if (index > 0) HabitRowDivider(cabinet)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(habit.icon, modifier = Modifier.padding(end = 10.dp))
                                Text(
                                    habit.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { viewModel.setArchived(habit, false) }) {
                                    Text(stringResource(R.string.habit_unarchive))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Appearance and the widget shortcut, together and labelled, instead of hiding the theme
        // picker inside the "today" card.
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = cabinet.card,
                shadowElevation = 2.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(cabinet.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = cabinet.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                stringResource(R.string.habit_look_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = cabinet.onCard,
                            )
                            Text(
                                stringResource(R.string.habit_look_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = cabinet.muted,
                                maxLines = 2,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                stringResource(R.string.habit_field_theme),
                                style = MaterialTheme.typography.labelSmall,
                                color = cabinet.muted,
                            )
                            Text(
                                habitThemeLabel(HabitTheme.from(settings.habitTheme)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = cabinet.onCard,
                            )
                        }
                        TextButton(onClick = onAddWidget) {
                            Icon(
                                Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = cabinet.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.habit_add_widget), color = cabinet.accent)
                        }
                    }
                    HabitThemeDotRow(
                        selected = HabitTheme.from(settings.habitTheme),
                        onSelect = onThemeChange,
                        dark = dark,
                    )
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
    numeralMode: NumeralMode,
    onOpen: (Long) -> Unit,
) {
    val stats = state.stats[habit.id] ?: HabitStats()
    HabitFolderRow(
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

/**
 * What still needs the user first: open days at the top, finished ones at the bottom, and a longer
 * run above a shorter one inside each of those two groups so the habit worth protecting is the one
 * that is easiest to reach.
 */
private fun byAttention(habits: List<Habit>, state: HabitsUiState): List<Habit> =
    habits.sortedWith(
        compareByDescending<Habit> { isDoneToday(it, state) }
            .thenByDescending { (state.stats[it.id] ?: HabitStats()).currentStreak }
            .thenBy { it.name },
    )

/**
 * The week above the drawers: one cell per day for the whole cabinet, with the run of kept days
 * spelled out underneath. The cells reuse [HabitWeekStrip], so a day looks the same here and in a
 * habit's own history.
 */
@Composable
private fun HabitWeekOverview(
    week: List<HabitDay>,
    done: Int,
    planned: Int,
    palette: HabitPalette,
    numeralMode: NumeralMode,
    languageTag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.card,
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        stringResource(R.string.habit_week_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onCard,
                    )
                    Text(
                        if (planned == 0) {
                            stringResource(R.string.habit_week_none)
                        } else {
                            stringResource(
                                R.string.habit_week_done,
                                habitNumber(done, numeralMode),
                                habitNumber(planned, numeralMode),
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                    )
                }
            }
            if (week.isEmpty()) {
                Text(
                    stringResource(R.string.habit_week_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            } else {
                HabitWeekStrip(week, palette, languageTag)
            }
        }
    }
}

@Composable
private fun EmptyDrawerText(text: String, palette: HabitPalette) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
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
    numeralMode: NumeralMode,
): String = if (count == 0) {
    habitNumber(0, numeralMode)
} else {
    "${habitNumber(done, numeralMode)}/${habitNumber(count, numeralMode)}"
}

private fun isDoneToday(habit: Habit, state: HabitsUiState): Boolean =
    (state.stats[habit.id] ?: HabitStats()).todayDone
