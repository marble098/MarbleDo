package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A due-date snapshot, not a fabricated completion-time history or tracked focus duration. */
data class ReviewWindow(val start: LocalDate, val end: LocalDate, val scheduled: Int, val completed: Int, val plannedFocusMinutes: Long) {
    val completionPercent: Int? get() = if (scheduled == 0) null else completed * 100 / scheduled
}
enum class ReviewAdvice { START_PLANNING, CLEAR_BACKLOG, SCHEDULE_UNDATED, REDUCE_LOAD, KEEP_RHYTHM }
data class WeeklyReview(
    val recent: ReviewWindow,
    val previous: ReviewWindow,
    val days: List<DayWorkload>,
    val overdueTasks: List<Task>,
    val unscheduledTasks: List<Task>,
    val nextWeekOpen: Int,
    val advice: ReviewAdvice,
) {
    val changePercentagePoints: Int? get() = recent.completionPercent?.let { current -> previous.completionPercent?.let { current - it } }
}

object WeeklyReviewPlanner {
    /** Seven complete civil days ending yesterday, compared with the preceding seven complete days. */
    fun review(tasks: List<Task>, now: Instant, zone: ZoneId): WeeklyReview {
        val today = now.atZone(zone).toLocalDate()
        val visible = tasks.filterNot { it.isArchived }
        fun date(task: Task): LocalDate? = task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        fun inRange(task: Task, start: LocalDate, end: LocalDate): Boolean = date(task)?.let { it >= start && it <= end } == true
        fun window(start: LocalDate, end: LocalDate): ReviewWindow {
            val rows = visible.filter { inRange(it, start, end) }
            return ReviewWindow(start, end, rows.size, rows.count { it.isCompleted }, rows.sumOf { it.focusMinutes.toLong() })
        }
        val recent = window(today.minusDays(7), today.minusDays(1))
        val previous = window(today.minusDays(14), today.minusDays(8))
        val overdue = visible.filter { task ->
            !task.isCompleted && task.dueAtEpochMillis?.let { due ->
                if (task.isAllDay) (date(task) ?: today) < today else due < now.toEpochMilli()
            } == true
        }.sortedBy { it.dueAtEpochMillis }
        val unscheduled = visible.filter { !it.isCompleted && it.dueAtEpochMillis == null }
        val next = visible.count { !it.isCompleted && inRange(it, today, today.plusDays(6)) }
        val advice = when {
            overdue.isNotEmpty() -> ReviewAdvice.CLEAR_BACKLOG
            unscheduled.isNotEmpty() -> ReviewAdvice.SCHEDULE_UNDATED
            recent.scheduled == 0 -> ReviewAdvice.START_PLANNING
            (recent.completionPercent ?: 100) < 50 && next > recent.completed -> ReviewAdvice.REDUCE_LOAD
            else -> ReviewAdvice.KEEP_RHYTHM
        }
        return WeeklyReview(recent, previous, (1L..7L).reversed().map { offset ->
            val day = today.minusDays(offset)
            val rows = visible.filter { date(it) == day }
            DayWorkload(day, rows.count { !it.isCompleted }, rows.count { it.isCompleted })
        }, overdue, unscheduled, next, advice)
    }
}
