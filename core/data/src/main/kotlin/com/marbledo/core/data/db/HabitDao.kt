package com.marbledo.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_entries ORDER BY date ASC")
    fun observeEntries(): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    suspend fun habitsSnapshot(): List<HabitEntity>

    @Query("SELECT * FROM habit_entries")
    suspend fun entriesSnapshot(): List<HabitEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HabitEntryEntity)

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun entry(habitId: Long, date: String): HabitEntryEntity?

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun deleteEntry(habitId: Long, date: String)

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId")
    suspend fun deleteEntriesFor(habitId: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM habit_entries")
    suspend fun clearEntries()

    @Transaction
    suspend fun deleteWithEntries(id: Long) {
        deleteEntriesFor(id)
        delete(id)
    }

    /** Used when a backup is restored: the old habits and records are dropped first. */
    @Transaction
    suspend fun replaceAll(habits: List<HabitEntity>, entries: List<HabitEntryEntity>) {
        clearEntries()
        clearHabits()
        habits.forEach { insert(it) }
        entries.forEach { insertEntry(it) }
    }
}
