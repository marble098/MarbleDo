package com.marbledo.domain.util

import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Five everyday planning tools, all derived from the tasks the user already has.
 *
 * They are deliberately pure: no storage, no scheduler, no invented history. Each one answers a
 * question a person actually asks during the day — *what should I start with*, *does my plan fit
 * the time I have left*, *what can I do in this ten-minute gap*, *what has been waiting too long*,
 * and *is one day of this week carrying everything* — and each is a plain function so it can be
 * unit tested and reused by widgets or notifications later.
 */

/** Why a task earned its place in the day's short list. */
enum class FocusReason { OVERDUE, DUE_TODAY, URGENT, PINNED, AGING, QUICK_WIN, NEXT_UP }

/** One task the planner suggests, the reason it was suggested, and the effort it implies. */
data class FocusPick(val task: Task, val reason: FocusReason, val minutes: Int)

object DailyFocusPlanner {
    /** Used when a task carries no planned effort, so an estimate is never silently zero. */
    const val DEFAULT_MINUTES = 15

    /** Planned effort of a task, falling back to a calm default instead of 0. */
    fun minutesOf(task: Task): Int = if (task.focusMinutes > 0) task.focusMinutes else DEFAULT_MINUTES

    /** Whole days a task has been open. */
    fun waitingDays(task: Task, now: Instant): Long =
        ((now.toEpochMilli() - task.createdAtEpochMillis).coerceAtLeast(0L)) / MILLIS_PER_DAY

    /**
     * The day's short list: at most [limit] open tasks, best first.
     *
     * Scoring is intentionally explainable — overdue beats due today, due today beats urgency,
     * urgency beats how long the task has been waiting — so the reason shown next to each pick is
     * the same thing that put it there.
     */
    fun top(
        tasks: List<Task>,
        now: Instant,
        zone: ZoneId,
        limit: Int = 3,
    ): List<FocusPick> {
        require(limit > 0) { "limit must be positive" }
        val today = now.atZone(zone).toLocalDate()
        return tasks.asSequence()
            .filter { !it.isCompleted && !it.isArchived }
            .map { task -> task to score(task, now, zone, today) }
            .sortedWith(
                compareByDescending<Pair<Task, Int>> { it.second }
                    .thenBy { it.first.dueAtEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.first.createdAtEpochMillis }
                    .thenBy { it.first.id },
            )
            .take(limit)
            .map { (task, _) -> FocusPick(task, reason(task, now, zone, today), minutesOf(task)) }
            .toList()
    }

    /** The single strongest reason this task deserves attention, in the same order as the score. */
    fun reason(task: Task, now: Instant, zone: ZoneId, today: LocalDate = now.atZone(zone).toLocalDate()): FocusReason {
        val due = task.dueAtEpochMillis
        if (due != null) {
            val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            val overdue = if (task.isAllDay) date < today else due < now.toEpochMilli()
            if (overdue) return FocusReason.OVERDUE
            if (date == today) return FocusReason.DUE_TODAY
        }
        if (task.priority == TaskPriority.URGENT || task.priority == TaskPriority.HIGH) return FocusReason.URGENT
        if (task.isPinned) return FocusReason.PINNED
        if (waitingDays(task, now) >= AGING_DAYS) return FocusReason.AGING
        if (task.focusMinutes in 1..QUICK_WIN_MINUTES) return FocusReason.QUICK_WIN
        return FocusReason.NEXT_UP
    }

    private fun score(task: Task, now: Instant, zone: ZoneId, today: LocalDate): Int {
        var score = 0
        val due = task.dueAtEpochMillis
        if (due != null) {
            val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            score += when {
                if (task.isAllDay) date < today else due < now.toEpochMilli() -> 100
                date == today -> 60
                date <= today.plusDays(3) -> 20
                else -> 6
            }
        }
        score += when (task.priority) {
            TaskPriority.URGENT -> 45
            TaskPriority.HIGH -> 25
            TaskPriority.NORMAL -> 8
            TaskPriority.LOW -> 0
        }
        if (task.isPinned) score += 12
        score += waitingDays(task, now).coerceAtMost(14L).toInt()
        if (task.focusMinutes in 1..QUICK_WIN_MINUTES) score += 5
        return score
    }

    internal const val AGING_DAYS = 7L
    internal const val QUICK_WIN_MINUTES = 10
    internal const val MILLIS_PER_DAY = 86_400_000L
}

/** How the day's planned effort compares with the time it has left. */
enum class BudgetVerdict { EMPTY, FITS, TIGHT, OVER }

data class FocusBudget(
    val plannedMinutes: Int,
    val freeMinutes: Int,
    val openToday: Int,
    val estimatedTasks: Int,
    val unestimatedTasks: Int,
) {
    /** Up to 80% of the remaining time is comfortable; up to 100% is tight; beyond it, over. */
    val verdict: BudgetVerdict
        get() = when {
            openToday == 0 -> BudgetVerdict.EMPTY
            freeMinutes <= 0 -> BudgetVerdict.OVER
            plannedMinutes * 10 <= freeMinutes * 8 -> BudgetVerdict.FITS
            plannedMinutes <= freeMinutes -> BudgetVerdict.TIGHT
            else -> BudgetVerdict.OVER
        }

    /** Negative when the plan asks for more time than the day has left. */
    val leftoverMinutes: Int get() = freeMinutes - plannedMinutes
}

object FocusBudgetPlanner {
    /** The day is measured until this local time; no invented work hours beyond it. */
    const val DEFAULT_DAY_END_MINUTE = 22 * 60

    /**
     * Today's plate: every open task that is overdue or due today, the effort it implies, and the
     * minutes that are actually left before [dayEndMinute].
     */
    fun today(
        tasks: List<Task>,
        now: Instant,
        zone: ZoneId,
        dayEndMinute: Int = DEFAULT_DAY_END_MINUTE,
    ): FocusBudget {
        require(dayEndMinute in 0..24 * 60)
        val today = now.atZone(zone).toLocalDate()
        val plate = tasks.filter { task ->
            if (task.isCompleted || task.isArchived) return@filter false
            val due = task.dueAtEpochMillis ?: return@filter false
            Instant.ofEpochMilli(due).atZone(zone).toLocalDate() <= today
        }
        val estimated = plate.count { it.focusMinutes > 0 }
        return FocusBudget(
            plannedMinutes = plate.sumOf { DailyFocusPlanner.minutesOf(it) },
            freeMinutes = freeMinutesLeft(now, zone, dayEndMinute),
            openToday = plate.size,
            estimatedTasks = estimated,
            unestimatedTasks = plate.size - estimated,
        )
    }

    /** Minutes between [now] and [dayEndMinute] on the same local day, never negative. */
    fun freeMinutesLeft(now: Instant, zone: ZoneId, dayEndMinute: Int = DEFAULT_DAY_END_MINUTE): Int {
        val zoned = now.atZone(zone)
        val nowMinute = zoned.hour * 60 + zoned.minute
        return (dayEndMinute - nowMinute).coerceAtLeast(0)
    }
}

/** A stretch of free time before the next timed item. */
data class TimeGap(val minutes: Int, val endsAtEpochMillis: Long)

data class GapSuggestion(val gap: TimeGap, val task: Task?)

object GapFinder {
    /** Gaps shorter than this are not worth interrupting anyone for. */
    const val MIN_GAP_MINUTES = 10

    /** A gap is not advertised as "all afternoon" even when the next item is far away. */
    const val MAX_GAP_MINUTES = 120

    /**
     * The free stretch that starts now: it ends at the next timed, still-open task, or at the end
     * of the day when nothing is scheduled.
     */
    fun nextGap(
        tasks: List<Task>,
        now: Instant,
        zone: ZoneId,
        dayEndMinute: Int = FocusBudgetPlanner.DEFAULT_DAY_END_MINUTE,
    ): TimeGap? {
        val nowMillis = now.toEpochMilli()
        val zoned = now.atZone(zone)
        val dayEnd = zoned.toLocalDate().atStartOfDay(zone).plusMinutes(dayEndMinute.toLong()).toInstant().toEpochMilli()
        val nextTimed = tasks.asSequence()
            .filter { task -> !task.isCompleted && !task.isArchived && !task.isAllDay }
            .mapNotNull { it.dueAtEpochMillis }
            .filter { it > nowMillis }
            .minOrNull()
        val end = minOf(nextTimed ?: dayEnd, dayEnd)
        val minutes = ((end - nowMillis) / 60_000L).toInt()
        return if (minutes >= MIN_GAP_MINUTES) TimeGap(minOf(minutes, MAX_GAP_MINUTES), end) else null
    }

    /**
     * The task that best uses [gap]: the most urgent open task whose planned effort fits the gap.
     * Overdue first, then due today, then priority, and among equals the longer task, so the gap is
     * actually filled rather than nibbled.
     */
    fun fitting(tasks: List<Task>, gap: TimeGap, now: Instant, zone: ZoneId): Task? {
        val today = now.atZone(zone).toLocalDate()
        return tasks.asSequence()
            .filter { !it.isCompleted && !it.isArchived }
            .filter { DailyFocusPlanner.minutesOf(it) <= gap.minutes }
            .sortedWith(
                compareBy<Task> { task ->
                    when (DailyFocusPlanner.reason(task, now, zone, today)) {
                        FocusReason.OVERDUE -> 0
                        FocusReason.DUE_TODAY -> 1
                        FocusReason.URGENT -> 2
                        FocusReason.PINNED -> 3
                        FocusReason.AGING -> 4
                        FocusReason.QUICK_WIN -> 5
                        FocusReason.NEXT_UP -> 6
                    }
                }
                    .thenByDescending { it.priority.ordinal }
                    .thenByDescending { DailyFocusPlanner.minutesOf(it) }
                    .thenBy { it.id },
            )
            .firstOrNull()
    }
}

/** How long the backlog has been waiting. */
data class AgingSummary(val overWeek: Int, val overMonth: Int, val oldest: Task?, val oldestDays: Long) {
    val nudging: Boolean get() = overWeek > 0
}

object TaskAging {
    /** A task counts as "waiting" from the day it was written, not from its due date. */
    const val WEEK_DAYS = 7L
    const val MONTH_DAYS = 30L

    fun summary(tasks: List<Task>, now: Instant): AgingSummary {
        val open = tasks.filter { !it.isCompleted && !it.isArchived }
        val ages = open.map { it to DailyFocusPlanner.waitingDays(it, now) }
        val oldestPair = ages.filter { it.second >= WEEK_DAYS }.maxByOrNull { it.second }
        return AgingSummary(
            overWeek = ages.count { it.second >= WEEK_DAYS },
            overMonth = ages.count { it.second >= MONTH_DAYS },
            oldest = oldestPair?.first,
            oldestDays = oldestPair?.second ?: 0L,
        )
    }
}

/** How the next seven days are loaded, and whether one day carries everything. */
data class WeekBalance(
    val heaviestDay: LocalDate?,
    val heaviestCount: Int,
    val lightestDay: LocalDate?,
    val lightestCount: Int,
) {
    /** One day holding clearly more than the calmest day is worth mentioning. */
    val imbalanced: Boolean
        get() = heaviestCount >= 3 && lightestDay != null && lightestDay != heaviestDay &&
            heaviestCount - lightestCount >= 2
}

object WeekBalancePlanner {
    /**
     * Counts open tasks by due date for today and the six days after it. The lightest day is
     * searched from tomorrow onward, because rebalancing always moves work forward.
     */
    fun balance(tasks: List<Task>, now: Instant, zone: ZoneId): WeekBalance {
        val today = now.atZone(zone).toLocalDate()
        val counts = (0L..6L).associateWith { offset ->
            val day = today.plusDays(offset)
            tasks.count { task ->
                !task.isCompleted && !task.isArchived &&
                    task.dueAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true
            }
        }
        val heaviestOffset = counts.maxByOrNull { it.value }?.key ?: 0L
        val lightestOffset = (1L..6L).minByOrNull { counts.getValue(it) } ?: 1L
        return WeekBalance(
            heaviestDay = today.plusDays(heaviestOffset),
            heaviestCount = counts.getValue(heaviestOffset),
            lightestDay = today.plusDays(lightestOffset),
            lightestCount = counts.getValue(lightestOffset),
        )
    }
}
