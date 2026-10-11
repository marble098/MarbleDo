package com.marbledo.feature.habits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.repository.HabitRepository
import com.marbledo.domain.util.HabitAnalytics
import com.marbledo.domain.util.HabitStats
import com.marbledo.domain.util.HabitSummary
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HabitsUiState(
    val habits: List<Habit> = emptyList(),
    val entries: List<HabitEntry> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val stats: Map<Long, HabitStats> = emptyMap(),
    val summary: HabitSummary = HabitSummary(),
)

/**
 * Habits, their records and the numbers derived from them.
 *
 * [refreshToday] is the only clock entry point: the screen calls it when it comes back to the
 * foreground, so a habit ticked just after midnight lands on the day the user is looking at.
 */
class HabitsViewModel(
    private val repository: HabitRepository,
    private val appContext: Context,
) : ViewModel() {

    private val dayTick = MutableStateFlow(0)

    val state: StateFlow<HabitsUiState> = combine(
        repository.observeHabits(),
        repository.observeEntries(),
        dayTick,
    ) { habits, entries, _ ->
        val today = LocalDate.now()
        val byHabit = entries.groupBy { it.habitId }
        HabitsUiState(
            habits = habits,
            entries = entries,
            today = today,
            stats = habits.associate { habit ->
                habit.id to HabitAnalytics.stats(habit, byHabit[habit.id].orEmpty(), today)
            },
            summary = HabitAnalytics.summary(habits, entries, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun refreshToday() {
        dayTick.value += 1
    }

    /** Ticks a build habit on or off for today. */
    fun setDoneToday(habit: Habit, done: Boolean) {
        viewModelScope.launch {
            repository.setEntry(habit.id, LocalDate.now().toString(), if (done) habit.dailyTarget else 0)
            refreshSideEffects()
        }
    }

    /** Moves a counted habit's today value by [delta]. */
    fun adjustToday(habit: Habit, delta: Int) {
        viewModelScope.launch {
            repository.addEntryValue(habit.id, LocalDate.now().toString(), delta)
            refreshSideEffects()
        }
    }

    /** Notes or clears a slip on a habit being quit. */
    fun setSlippedToday(habit: Habit, slipped: Boolean) {
        viewModelScope.launch {
            repository.setEntry(habit.id, LocalDate.now().toString(), if (slipped) 1 else 0)
            refreshSideEffects()
        }
    }

    fun save(habit: Habit) {
        viewModelScope.launch {
            repository.save(habit)
            refreshSideEffects()
        }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch {
            HabitReminderScheduler.cancel(appContext, habit.id)
            repository.delete(habit.id)
            refreshSideEffects()
        }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(habit.id, archived)
            refreshSideEffects()
        }
    }

    /** Called when the screen appears, so the widget matches what the app shows. */
    fun refreshSideEffects() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val snapshot = repository.snapshot()
                HabitReminderScheduler.reschedule(appContext, snapshot.habits)
                updateMarbleHabitWidgets(appContext)
            }
        }
    }

    /** True when the habit's control should show a counter rather than a single tick. */
    fun isCounted(habit: Habit): Boolean = habit.kind == HabitKind.BUILD && habit.tracking == HabitTracking.COUNT
}
