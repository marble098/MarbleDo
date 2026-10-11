package com.marbledo.core.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * One recorded day. A row exists only for days the user touched, so "no row" stays meaningful:
 * not finished yet for a build habit, clean for a habit being quit.
 */
@Entity(
    tableName = "habit_entries",
    primaryKeys = ["habitId", "date"],
    indices = [Index(value = ["habitId"])],
)
data class HabitEntryEntity(
    val habitId: Long,
    /** ISO local date, `yyyy-MM-dd`. */
    val date: String,
    val value: Int,
    val updatedAtEpochMillis: Long,
)
