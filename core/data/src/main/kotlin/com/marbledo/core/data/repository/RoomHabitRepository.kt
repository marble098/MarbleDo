package com.marbledo.core.data.repository

import com.marbledo.core.data.db.HabitDao
import com.marbledo.core.data.db.HabitEntity
import com.marbledo.core.data.db.HabitEntryEntity
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitDifficulty
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitSchedule
import com.marbledo.domain.model.HabitScheduleType
import com.marbledo.domain.model.HabitTracking
import com.marbledo.domain.repository.HabitRepository
import com.marbledo.domain.repository.HabitsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHabitRepository(
    private val dao: HabitDao,
) : HabitRepository {

    override fun observeHabits(): Flow<List<Habit>> =
        dao.observeHabits().map { rows -> rows.map { it.toDomain() } }

    override fun observeEntries(): Flow<List<HabitEntry>> =
        dao.observeEntries().map { rows -> rows.map { it.toDomain() } }

    override suspend fun snapshot(): HabitsSnapshot = HabitsSnapshot(
        habits = dao.habitsSnapshot().map { it.toDomain() },
        entries = dao.entriesSnapshot().map { it.toDomain() },
    )

    override suspend fun save(habit: Habit): Long = dao.insert(habit.toEntity())

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    override suspend fun delete(id: Long) = dao.deleteWithEntries(id)

    override suspend fun setEntry(habitId: Long, date: String, value: Int) {
        if (value <= 0) {
            dao.deleteEntry(habitId, date)
        } else {
            dao.insertEntry(HabitEntryEntity(habitId, date, value, System.currentTimeMillis()))
        }
    }

    override suspend fun addEntryValue(habitId: Long, date: String, delta: Int) {
        val current = dao.entry(habitId, date)?.value ?: 0
        setEntry(habitId, date, (current + delta).coerceAtLeast(0))
    }

    override suspend fun replaceAll(habits: List<Habit>, entries: List<HabitEntry>) {
        dao.replaceAll(habits.map { it.toEntity() }, entries.map { it.toEntity() })
    }
}

private fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name.trim(),
    kind = kind.name,
    tracking = tracking.name,
    scheduleType = schedule.type.name,
    weekdaysCsv = schedule.weekdays.sorted().joinToString(","),
    timesPerWeek = schedule.timesPerWeek,
    dailyTarget = dailyTarget,
    unitLabel = unitLabel.trim(),
    icon = icon,
    themeId = themeId,
    cue = cue.trim(),
    identity = identity.trim(),
    why = why.trim(),
    difficulty = difficulty.name,
    reminderMinuteOfDay = reminderMinuteOfDay,
    startDate = startDate.trim(),
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    sortOrder = sortOrder,
)

private fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    kind = enumOrElse(kind, HabitKind.BUILD),
    tracking = enumOrElse(tracking, HabitTracking.CHECK),
    schedule = HabitSchedule(
        type = enumOrElse(scheduleType, HabitScheduleType.EVERY_DAY),
        weekdays = weekdaysCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet(),
        timesPerWeek = timesPerWeek.coerceIn(1, 7),
    ),
    dailyTarget = dailyTarget.coerceIn(1, 99),
    unitLabel = unitLabel,
    icon = icon.ifBlank { Habit.DEFAULT_ICON },
    themeId = themeId,
    cue = cue,
    identity = identity,
    why = why,
    difficulty = enumOrElse(difficulty, HabitDifficulty.MEDIUM),
    reminderMinuteOfDay = reminderMinuteOfDay.coerceIn(Habit.NO_REMINDER, 1439),
    startDate = startDate,
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    sortOrder = sortOrder,
)

private fun HabitEntryEntity.toDomain(): HabitEntry = HabitEntry(
    habitId = habitId,
    date = date,
    value = value,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun HabitEntry.toEntity(): HabitEntryEntity = HabitEntryEntity(
    habitId = habitId,
    date = date,
    value = value,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private inline fun <reified T : Enum<T>> enumOrElse(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
