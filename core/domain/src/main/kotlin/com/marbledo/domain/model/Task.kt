package com.marbledo.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }

@Serializable
enum class RepeatFrequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

@Serializable
enum class RepeatCalendar { GREGORIAN, PERSIAN }

/** Calendar used to enter and present countdown target dates. */
@Serializable
enum class CalendarDisplayMode { PERSIAN, GREGORIAN, ISLAMIC_CIVIL }

@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val isDone: Boolean = false,
)

@Serializable
data class RecurrenceRule(
    val frequency: RepeatFrequency,
    val interval: Int = 1,
    /** java.time.DayOfWeek values (Monday=1 through Sunday=7). */
    val daysOfWeek: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val calendar: RepeatCalendar = RepeatCalendar.GREGORIAN,
) {
    init {
        require(interval > 0) { "Repeat interval must be positive" }
        require(daysOfWeek.all { it in 1..7 }) { "Weekdays must be in 1..7" }
        require(monthDay == null || monthDay in 1..31) { "Month day must be in 1..31" }
    }
}

@Serializable
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAtEpochMillis: Long? = null,
    val isAllDay: Boolean = false,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val colorArgb: Long = 0xFF6E61D8,
    val tags: List<String> = emptyList(),
    val project: String = "",
    val category: String = "",
    val isPinned: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val link: String? = null,
    val attachmentUri: String? = null,
    val recurrence: RecurrenceRule? = null,
    val countdownTheme: String = "MARBLE",
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val focusMinutes: Int = 0,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(title.isNotBlank()) { "Task title cannot be blank" }
        require(focusMinutes >= 0) { "Focus time cannot be negative" }
    }
}
