package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayWorkload(val date: LocalDate, val open: Int, val completed: Int)
data class WeeklyWorkload(val days: List<DayWorkload>, val overdue: Int, val unscheduled: Int) {
    val scheduledOpen: Int get() = days.sumOf { it.open }
}

object PlanningInsights {
    /** Counts are by due date, not a fabricated completion history (Task has no completedAt). */
    fun week(tasks: List<Task>, now: Instant, zone: ZoneId): WeeklyWorkload {
        val today = now.atZone(zone).toLocalDate()
        val visible = tasks.filterNot { it.isArchived }
        fun dueDate(task: Task) = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val grouped = visible.filter { it.dueAtEpochMillis != null }.groupBy { dueDate(it) }
        return WeeklyWorkload(
            days = (0L..6L).map { offset ->
                val day = today.plusDays(offset)
                val scheduled = grouped[day].orEmpty()
                DayWorkload(day, scheduled.count { !it.isCompleted }, scheduled.count { it.isCompleted })
            },
            overdue = visible.count { task ->
                !task.isCompleted && task.dueAtEpochMillis != null &&
                    if (task.isAllDay) dueDate(task)!! < today else task.dueAtEpochMillis < now.toEpochMilli()
            },
            unscheduled = visible.count { !it.isCompleted && it.dueAtEpochMillis == null },
        )
    }
}

object WorkdayPlanner {
    /** Iranian Friday weekend. Does not assume every observance or school closure is a holiday. */
    fun nextWorkingDate(
        after: LocalDate,
        holidays: Set<LocalDate>,
        thursdayClosed: Boolean = false,
        maxSearchDays: Int = 366,
    ): LocalDate? {
        require(maxSearchDays in 1..366)
        return (1L..maxSearchDays.toLong()).asSequence().map(after::plusDays).firstOrNull { date ->
            date.dayOfWeek != DayOfWeek.FRIDAY && (!thursdayClosed || date.dayOfWeek != DayOfWeek.THURSDAY) && date !in holidays
        }
    }
}
