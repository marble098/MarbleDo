package com.marbledo.domain.repository

import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitEntry
import kotlinx.coroutines.flow.Flow

/** Local storage for habits and their daily records. */
interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>

    fun observeEntries(): Flow<List<HabitEntry>>

    /** One-shot read used by the app widget and by background workers. */
    suspend fun snapshot(): HabitsSnapshot

    suspend fun save(habit: Habit): Long

    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun delete(id: Long)

    /** Writes the record for one day; a value of 0 removes it again. */
    suspend fun setEntry(habitId: Long, date: String, value: Int)

    /** Adds [delta] to the record for one day, clamped at 0. */
    suspend fun addEntryValue(habitId: Long, date: String, delta: Int)

    /** Replaces everything, used when a backup is restored. */
    suspend fun replaceAll(habits: List<Habit>, entries: List<HabitEntry>)
}

data class HabitsSnapshot(
    val habits: List<Habit> = emptyList(),
    val entries: List<HabitEntry> = emptyList(),
)
