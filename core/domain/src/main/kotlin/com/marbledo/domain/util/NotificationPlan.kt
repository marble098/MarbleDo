package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import java.time.Instant
import java.time.ZoneId

data class NotificationPlan(
    val nextTask: Task?,
    val nextIsOverdue: Boolean,
    val todayOpen: Int,
    val todayCompleted: Int,
    val overdueCount: Int,
) {
    val todayTotal: Int get() = todayOpen + todayCompleted
}

/** Shared glanceable content policy, independent of Android rendering and the status-bar artwork. */
object NotificationPlanBuilder {
    fun build(tasks: List<Task>, now: Instant, zone: ZoneId): NotificationPlan {
        val today = now.atZone(zone).toLocalDate()
        val visible = tasks.filterNot { it.isArchived }
        fun isToday(task: Task) = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        fun overdue(task: Task): Boolean = !task.isCompleted && task.dueAtEpochMillis?.let {
            if (task.isAllDay) Instant.ofEpochMilli(it).atZone(zone).toLocalDate() < today else it < now.toEpochMilli()
        } == true
        val active = visible.filter { !it.isCompleted && it.dueAtEpochMillis != null }
        val late = active.filter(::overdue)
        val next = late.sortedWith(compareByDescending<Task> { it.priority.ordinal }.thenBy { it.dueAtEpochMillis }).firstOrNull()
            ?: active.minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
        return NotificationPlan(next, next != null && overdue(next), visible.count { !it.isCompleted && isToday(it) },
            visible.count { it.isCompleted && isToday(it) }, late.size)
    }
}
