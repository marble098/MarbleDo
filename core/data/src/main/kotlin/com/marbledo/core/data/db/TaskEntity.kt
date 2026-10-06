package com.marbledo.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["dueAtEpochMillis"]), Index(value = ["isCompleted", "isArchived"])],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val isAllDay: Boolean = false,
    val priority: String = "NORMAL",
    @ColumnInfo(defaultValue = "4285424088") val colorArgb: Long = 0xFF6E61D8,
    @ColumnInfo(defaultValue = "'[]'") val tagsJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val project: String = "",
    @ColumnInfo(defaultValue = "''") val category: String = "",
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'") val checklistJson: String = "[]",
    val link: String? = null,
    val attachmentUri: String? = null,
    val recurrenceJson: String? = null,
    @ColumnInfo(defaultValue = "'MARBLE'") val countdownTheme: String = "MARBLE",
    val isCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val focusMinutes: Int = 0,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long,
)
