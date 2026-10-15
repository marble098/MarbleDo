package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class AgendaConflict(val first: Task, val second: Task)
data class FreeFocusSlot(val start: Instant, val end: Instant)
data class DayAgenda(
    val date: LocalDate,
    val timedTasks: List<Task>,
    val allDayTasks: List<Task>,
    val conflicts: List<AgendaConflict>,
    val freeSlots: List<FreeFocusSlot>,
    val estimatedMinutes: Int,
)

/**
 * A suggestion engine, never an automatic rescheduler. A timed due date is a DEADLINE: its estimated
 * focus block ends there. Unestimated tasks reserve 25 minutes; all-day tasks have no invented time.
 * Civil work-window boundaries are resolved in the supplied zone, including DST transitions.
 */
object DayAgendaPlanner {
    private data class Block(val task: Task, val start: Instant, val end: Instant)

    fun plan(
        tasks: List<Task>,
        date: LocalDate,
        now: Instant,
        zone: ZoneId,
        durationMinutes: Int = 25,
        workStart: LocalTime = LocalTime.of(9, 0),
        workEnd: LocalTime = LocalTime.of(18, 0),
        excludedTaskId: Long? = null,
    ): DayAgenda {
        require(durationMinutes in 5..240)
        require(workStart < workEnd)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val start = date.atTime(workStart).atZone(zone).toInstant()
        val end = date.atTime(workEnd).atZone(zone).toInstant()
        val active = tasks.filter { !it.isCompleted && !it.isArchived && (excludedTaskId == null || it.id != excludedTaskId) }
        fun due(task: Task) = task.dueAtEpochMillis?.let(Instant::ofEpochMilli)
        val blocks = active.filterNot { it.isAllDay }.mapNotNull { task ->
            val deadline = due(task) ?: return@mapNotNull null
            Block(task, deadline.minusSeconds(estimate(task) * 60L), deadline)
        }.filter { it.start < dayEnd && it.end > dayStart }.sortedBy { it.start }
        val conflicts = buildList {
            blocks.forEachIndexed { i, block ->
                blocks.drop(i + 1).takeWhile { it.start < block.end }.forEach { other ->
                    if (size < 50) add(AgendaConflict(block.task, other.task))
                }
            }
        }
        var cursor = maxOf(start, now)
        // Round up in elapsed time, not a guessed wall-clock duration across DST.
        val remainder = Math.floorMod(cursor.epochSecond, 15 * 60L)
        if (remainder != 0L || cursor.nano != 0) cursor = cursor.plusSeconds(15 * 60L - remainder).with(java.time.temporal.ChronoField.NANO_OF_SECOND, 0)
        val slots = mutableListOf<FreeFocusSlot>()
        val length = Duration.ofMinutes(durationMinutes.toLong())
        fun offerGap(until: Instant) {
            if (cursor.plus(length) <= until && slots.size < 5) slots += FreeFocusSlot(cursor, cursor.plus(length))
        }
        blocks.filter { it.end > start && it.start < end }.forEach { block ->
            offerGap(minOf(block.start, end))
            cursor = maxOf(cursor, block.end)
        }
        offerGap(end)
        return DayAgenda(
            date = date,
            timedTasks = blocks.map { it.task },
            allDayTasks = active.filter { it.isAllDay && due(it)?.let { instant -> instant >= dayStart && instant < dayEnd } == true },
            conflicts = conflicts,
            freeSlots = slots,
            estimatedMinutes = blocks.sumOf { estimate(it.task) },
        )
    }

    private fun estimate(task: Task): Int = task.focusMinutes.takeIf { it > 0 }?.coerceAtMost(24 * 60) ?: 25
}
