package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/**
 * What the shade should say about the day, computed once so the grouped overdue reminder, the
 * today-reminder card, the live preview in Settings and the persistent date card all describe the
 * same list.
 *
 * [overdueTasks] is ordered the way the reminder reads it: the most urgent first, then the oldest
 * due time. [todayTasks] is ordered by the clock, so "next" really is next.
 */
data class NotificationPlan(
    val nextTask: Task?,
    val nextIsOverdue: Boolean,
    val todayOpen: Int,
    val todayCompleted: Int,
    val overdueCount: Int,
    val overdueTasks: List<Task> = emptyList(),
    val todayTasks: List<Task> = emptyList(),
) {
    val todayTotal: Int get() = todayOpen + todayCompleted

    /** True when the grouped reminder has more tasks than its summary shows. */
    fun hiddenOverdueCount(visible: Int): Int = (overdueCount - visible).coerceAtLeast(0)
}

/** Shared glanceable content policy, independent of Android rendering and the status-bar artwork. */
object NotificationPlanBuilder {
    fun previewTitle(text: String, maxCodePoints: Int = 160): String {
        require(maxCodePoints >= 2)
        if (text.codePointCount(0, text.length) <= maxCodePoints) return text
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints - 1)) + "…"
    }

    fun build(tasks: List<Task>, now: Instant, zone: ZoneId): NotificationPlan {
        val today = now.atZone(zone).toLocalDate()
        val visible = tasks.filterNot { it.isArchived }
        fun isToday(task: Task) = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        fun overdue(task: Task): Boolean = !task.isCompleted && task.dueAtEpochMillis?.let {
            if (task.isAllDay) Instant.ofEpochMilli(it).atZone(zone).toLocalDate() < today else it < now.toEpochMilli()
        } == true
        val active = visible.filter { !it.isCompleted && it.dueAtEpochMillis != null }
        val late = active.filter(::overdue).sortedWith(
            compareByDescending<Task> { it.priority.ordinal }
                .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )
        val next = late.firstOrNull() ?: active.minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
        val todayOpen = visible.filter { !it.isCompleted && isToday(it) }
        // Today's list is the clock order of what is still open; an overdue task already owns a row
        // in the overdue group, so it is not repeated here.
        val todayTasks = todayOpen
            .filterNot { task -> late.any { it.id == task.id } }
            .sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
        return NotificationPlan(
            nextTask = next,
            nextIsOverdue = next != null && overdue(next),
            todayOpen = todayOpen.size,
            todayCompleted = visible.count { it.isCompleted && isToday(it) },
            overdueCount = late.size,
            overdueTasks = late,
            todayTasks = todayTasks,
        )
    }
}
