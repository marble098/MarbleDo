package com.marbledo.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored habit. The schedule lives in three plain columns instead of a JSON blob so the database
 * stays readable and a future migration can still reason about it.
 */
@Entity(tableName = "habits", indices = [Index(value = ["isArchived"])])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = "BUILD",
    val tracking: String = "CHECK",
    val scheduleType: String = "EVERY_DAY",
    /** DayOfWeek values joined by commas, empty for the other schedule types. */
    val weekdaysCsv: String = "",
    val timesPerWeek: Int = 3,
    val dailyTarget: Int = 1,
    val unitLabel: String = "",
    val icon: String = DEFAULT_ICON,
    val themeId: String = "AURORA",
    val cue: String = "",
    val identity: String = "",
    val why: String = "",
    val difficulty: String = "MEDIUM",
    /** Minute of day for the daily nudge; -1 keeps reminders off. */
    val reminderMinuteOfDay: Int = -1,
    /** ISO date the user started tracking; blank means "derive it from the records". */
    val startDate: String = "",
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val sortOrder: Int = 0,
) {
    private companion object {
        const val DEFAULT_ICON = "\uD83C\uDF31"
    }
}
