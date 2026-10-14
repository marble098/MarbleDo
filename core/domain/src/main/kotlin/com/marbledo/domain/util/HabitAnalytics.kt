package com.marbledo.domain.util

import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitDifficulty
import com.marbledo.domain.model.HabitEntry
import com.marbledo.domain.model.HabitKind
import com.marbledo.domain.model.HabitScheduleType
import com.marbledo.domain.model.HabitTracking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One cell of the habit heatmap. */
enum class HabitDayState {
    /** The day's target was met (or the day stayed clean for a quit habit). */
    DONE,

    /** Some progress was logged but the target was not reached. */
    PARTIAL,

    /** A planned day that passed without being done, or a slip on a quit habit. */
    MISSED,

    /** Today, planned, and not finished yet — still open. */
    TODAY,

    /** Ahead of today, so nothing can be said about it. */
    FUTURE,

    /** Off the schedule (or before tracking began), so it is not judged. */
    OFF,
}

data class HabitDay(val date: LocalDate, val value: Int, val state: HabitDayState)

/**
 * Where a habit sits on the road to automaticity. The boundaries follow the automaticity study by
 * Lally et al. (2010), where reaching automaticity took a median of 66 days: the phases are there
 * to set expectations, never to judge a missed day.
 */
enum class HabitPhase { STARTING, REPEATING, SETTLING, AUTOMATIC }

/** Coaching notes the tracker shows for a habit. The UI turns each key into localised advice. */
enum class HabitInsightKey {
    /** Nothing is recorded yet: start with a version so small it cannot fail. */
    START_SMALL,

    /** A slip happened recently: treat it as data, not as a verdict. */
    RELAPSE_KIND,

    /** "Never miss twice" — a streak is protected by showing up today. */
    NEVER_MISS_TWICE,

    /** A milestone is a day or two away. */
    MILESTONE_CLOSE,

    /** A weekly target is not met yet. */
    WEEKLY_TARGET_BEHIND,

    /** The step is too big for the current completion rate: shrink it. */
    SHRINK_STEP,

    /** A quit habit has stayed clean for a week or more. */
    SLIP_FREE,

    /** The last week went better than the one before. */
    STRONG_WEEK,

    /** One weekday is reliably weaker than the others. */
    WEAK_WEEKDAY,

    /** The identity sentence the user wrote for themselves. */
    IDENTITY,

    /** The cue (implementation intention) the user attached to the habit. */
    STACK_IT,
}

data class HabitInsight(
    val key: HabitInsightKey,
    /** Day count or remaining amount the advice refers to, when it needs one. */
    val value: Int = 0,
    /** [DayOfWeek] value when the advice is about a specific weekday. */
    val weekday: Int? = null,
)

data class HabitStats(
    val todayDone: Boolean = false,
    val todayValue: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val doneDays: Int = 0,
    val scheduledDays: Int = 0,
    val completionRate: Float = 0f,
    val lastWeekRate: Float = 0f,
    val previousWeekRate: Float = 0f,
    val momentum: Float = 0f,
    val doneThisWeek: Int = 0,
    val targetThisWeek: Int = 0,
    val totalDone: Int = 0,
    val totalValue: Int = 0,
    /** Slips recorded for a quit habit. */
    val slips: Int = 0,
    /** Consecutive clean days for a quit habit; equal to [currentStreak]. */
    val cleanDays: Int = 0,
    val bestWeekday: Int? = null,
    val weakestWeekday: Int? = null,
    val score: Int = 0,
    val phase: HabitPhase = HabitPhase.STARTING,
    val nextMilestone: Int? = null,
    val daysToNextMilestone: Int = 0,
)

data class HabitSummary(
    val habitCount: Int = 0,
    val scheduledToday: Int = 0,
    val doneToday: Int = 0,
    val buildCount: Int = 0,
    val quitCount: Int = 0,
    val bestStreak: Int = 0,
    val score: Int = 0,
)

/**
 * Every number the habit tracker shows, computed from the habit and its raw records.
 *
 * The module is deliberately free of Android and of the clock: callers pass [today] in, which
 * keeps the rules testable and keeps a late-night check-in on the day the user means.
 */
object HabitAnalytics {

    /** Milestones that research and practice made popular; encouragement, not a law. */
    val MILESTONES: List<Int> = listOf(3, 7, 14, 21, 30, 66, 100, 180, 365)

    /** Window used for the headline consistency figure. */
    const val WINDOW_DAYS = 30

    /** Days inspected when looking for a weak weekday. */
    private const val WEEKDAY_SAMPLE_DAYS = 60

    fun entriesByDate(entries: List<HabitEntry>): Map<String, Int> =
        entries.associate { entry -> entry.date to entry.value }

    fun valueOn(values: Map<String, Int>, date: LocalDate): Int = values[date.toString()] ?: 0

    /** Whether [value] means the day is complete for this habit. */
    fun isDone(habit: Habit, value: Int): Boolean = when (habit.kind) {
        HabitKind.BUILD -> value >= habit.dailyTarget
        // For a habit being quit, a clean day is a won day; only a slip breaks it.
        HabitKind.QUIT -> value == 0
    }

    /** The day tracking started: what the user declared, else their first record. */
    fun trackingStart(habit: Habit, entries: List<HabitEntry>): LocalDate {
        val declared = habit.startDateOrNull()
        val firstRecord = entries.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull()
        val created = habit.createdAtEpochMillis.takeIf { it > 0 }?.let { millis ->
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return listOfNotNull(declared, firstRecord, created).minOrNull()
            ?: LocalDate.now(ZoneId.systemDefault())
    }

    /**
     * Days in a row the habit is kept. Today being unfinished does not break the chain yet —
     * the day is still open — so the count simply starts from yesterday.
     */
    fun currentStreak(habit: Habit, values: Map<String, Int>, today: LocalDate, start: LocalDate): Int {
        var streak = 0
        var cursor = today
        while (!cursor.isBefore(start)) {
            if (!habit.schedule.isScheduled(cursor)) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (isDone(habit, valueOn(values, cursor))) {
                streak++
            } else if (cursor != today) {
                break
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(habit: Habit, values: Map<String, Int>, today: LocalDate, start: LocalDate): Int {
        var longest = 0
        var running = 0
        var cursor = start
        while (!cursor.isAfter(today)) {
            if (!habit.schedule.isScheduled(cursor)) {
                cursor = cursor.plusDays(1)
                continue
            }
            if (isDone(habit, valueOn(values, cursor))) {
                running++
                if (running > longest) longest = running
            } else if (cursor != today) {
                running = 0
            }
            cursor = cursor.plusDays(1)
        }
        return longest
    }

    /** Completed share of the planned days inside the last [days] days, today included. */
    fun completionRate(habit: Habit, values: Map<String, Int>, today: LocalDate, start: LocalDate, days: Int): Float {
        val from = maxOf(start, today.minusDays(days.toLong() - 1))
        var planned = 0
        var done = 0
        var cursor = from
        while (!cursor.isAfter(today)) {
            if (habit.schedule.isScheduled(cursor)) {
                planned++
                if (isDone(habit, valueOn(values, cursor))) done++
            }
            cursor = cursor.plusDays(1)
        }
        return if (planned == 0) 0f else done.toFloat() / planned.toFloat()
    }

    /** Completed share of the seven days ending on [weekEnd]; weekly targets are capped at 1. */
    fun weekRate(habit: Habit, values: Map<String, Int>, weekEnd: LocalDate, start: LocalDate): Float {
        val weeklyTarget = habit.schedule.type == HabitScheduleType.TIMES_PER_WEEK
        val plannedCap = habit.schedule.plannedDaysInWeekEnding(weekEnd)
        var planned = 0
        var done = 0
        var cursor = weekEnd.minusDays(6)
        while (!cursor.isAfter(weekEnd)) {
            if (cursor.isBefore(start)) {
                cursor = cursor.plusDays(1)
                continue
            }
            if (habit.schedule.isScheduled(cursor)) {
                planned++
                if (isDone(habit, valueOn(values, cursor))) done++
            }
            cursor = cursor.plusDays(1)
        }
        if (planned == 0) return 0f
        return if (weeklyTarget) {
            (done.toFloat() / plannedCap.toFloat()).coerceIn(0f, 1f)
        } else {
            (done.toFloat() / planned.toFloat()).coerceIn(0f, 1f)
        }
    }

    /** The weekday that goes best and worst, from the last [WEEKDAY_SAMPLE_DAYS] planned days. */
    fun weekdayExtremes(
        habit: Habit,
        values: Map<String, Int>,
        today: LocalDate,
        start: LocalDate,
    ): Pair<Int?, Int?> {
        val from = maxOf(start, today.minusDays(WEEKDAY_SAMPLE_DAYS.toLong() - 1))
        val planned = IntArray(8)
        val done = IntArray(8)
        var cursor = from
        while (!cursor.isAfter(today)) {
            if (habit.schedule.isScheduled(cursor)) {
                val slot = cursor.dayOfWeek.value
                planned[slot]++
                if (isDone(habit, valueOn(values, cursor))) done[slot]++
            }
            cursor = cursor.plusDays(1)
        }
        // A weekday only earns advice once there are at least two of them to compare.
        val usable = (1..7).mapNotNull { slot ->
            if (planned[slot] < 2) null else slot to (done[slot].toFloat() / planned[slot])
        }
        if (usable.size < 2) return null to null
        val best = usable.maxWith(compareBy<Pair<Int, Float>> { it.second }.thenByDescending { it.first })
        val worst = usable.minWith(compareBy<Pair<Int, Float>> { it.second }.thenBy { it.first })
        val spread = best.second - worst.second
        return if (spread < 0.25f) null to null else best.first to worst.first
    }

    /** Everything the detail sheet shows, in one pass over the records. */
    fun stats(habit: Habit, entries: List<HabitEntry>, today: LocalDate = LocalDate.now()): HabitStats {
        val values = entriesByDate(entries)
        val start = trackingStart(habit, entries)
        val todayValue = valueOn(values, today)
        val currentStreak = currentStreak(habit, values, today, start)
        val longest = longestStreak(habit, values, today, start)
        val rate = completionRate(habit, values, today, start, WINDOW_DAYS)
        val lastWeek = weekRate(habit, values, today, start)
        val previousWeek = weekRate(habit, values, today.minusDays(7), start)
        val (best, worst) = weekdayExtremes(habit, values, today, start)

        var doneDays = 0
        var scheduledDays = 0
        var cursor = start
        while (!cursor.isAfter(today)) {
            if (habit.schedule.isScheduled(cursor)) {
                scheduledDays++
                if (isDone(habit, valueOn(values, cursor))) doneDays++
            }
            cursor = cursor.plusDays(1)
        }
        val totalValue = entries.sumOf { it.value }
        val slips = if (habit.kind == HabitKind.QUIT) entries.count { it.value > 0 } else 0
        val doneThisWeek = countDoneInWeek(habit, values, today, start)
        val targetThisWeek = if (habit.schedule.type == HabitScheduleType.TIMES_PER_WEEK) {
            habit.schedule.timesPerWeek
        } else {
            habit.schedule.plannedDaysInWeekEnding(today)
        }
        val momentum = (lastWeek - previousWeek).coerceIn(-1f, 1f)
        val score = score(rate, longest, momentum)
        val nextMilestone = MILESTONES.firstOrNull { it > currentStreak }

        return HabitStats(
            todayDone = isDone(habit, todayValue),
            todayValue = todayValue,
            currentStreak = currentStreak,
            longestStreak = longest,
            doneDays = doneDays,
            scheduledDays = scheduledDays,
            completionRate = rate,
            lastWeekRate = lastWeek,
            previousWeekRate = previousWeek,
            momentum = momentum,
            doneThisWeek = doneThisWeek,
            targetThisWeek = targetThisWeek,
            totalDone = doneDays,
            totalValue = totalValue,
            slips = slips,
            cleanDays = if (habit.kind == HabitKind.QUIT) currentStreak else 0,
            bestWeekday = best,
            weakestWeekday = worst,
            score = score,
            phase = phase(longest),
            nextMilestone = nextMilestone,
            daysToNextMilestone = nextMilestone?.let { it - currentStreak } ?: 0,
        )
    }

    private fun countDoneInWeek(habit: Habit, values: Map<String, Int>, today: LocalDate, start: LocalDate): Int {
        var done = 0
        var cursor = today.minusDays(6)
        while (!cursor.isAfter(today)) {
            if (!cursor.isBefore(start) && habit.schedule.isScheduled(cursor) && isDone(habit, valueOn(values, cursor))) done++
            cursor = cursor.plusDays(1)
        }
        return done
    }

    /**
     * One 0-100 number for "how is this going": half consistency, a third the best run so far and
     * a fifth the direction of travel. Direction matters because a habit improving from 30% to 60%
     * is healthier than one coasting down from 90% to 70%, and the score should say so.
     */
    fun score(completionRate: Float, longestStreak: Int, momentum: Float): Int {
        val consistency = completionRate.coerceIn(0f, 1f)
        val streak = (longestStreak / 21f).coerceIn(0f, 1f)
        val direction = ((momentum + 1f) / 2f).coerceIn(0f, 1f)
        return (100f * (0.5f * consistency + 0.3f * streak + 0.2f * direction)).roundToInt().coerceIn(0, 100)
    }

    fun phase(longestStreak: Int): HabitPhase = when {
        longestStreak >= 66 -> HabitPhase.AUTOMATIC
        longestStreak >= 21 -> HabitPhase.SETTLING
        longestStreak >= 7 -> HabitPhase.REPEATING
        else -> HabitPhase.STARTING
    }

    /** Position of [date] inside its week, 0 being the first day. */
    fun weekdayOffset(date: LocalDate, weekStartsSaturday: Boolean = true): Int = when {
        weekStartsSaturday -> (date.dayOfWeek.value + 1) % 7
        else -> (date.dayOfWeek.value) % 7
    }

    /**
     * `weeks * 7` days laid out column by column, ending with the week that contains today, so the
     * heatmap always ends on the right edge and never shifts under the user's finger.
     */
    fun heatmap(
        habit: Habit,
        entries: List<HabitEntry>,
        today: LocalDate = LocalDate.now(),
        weeks: Int = 16,
        weekStartsSaturday: Boolean = true,
    ): List<HabitDay> {
        val values = entriesByDate(entries)
        val start = trackingStart(habit, entries)
        val endOfWeek = today.plusDays((6 - weekdayOffset(today, weekStartsSaturday)).toLong())
        val first = endOfWeek.minusDays((weeks * 7 - 1).toLong())
        val days = ArrayList<HabitDay>(weeks * 7)
        var cursor = first
        repeat(weeks * 7) {
            val value = valueOn(values, cursor)
            val state = when {
                cursor.isAfter(today) -> HabitDayState.FUTURE
                cursor.isBefore(start) -> HabitDayState.OFF
                !habit.schedule.isScheduled(cursor) -> HabitDayState.OFF
                habit.kind == HabitKind.QUIT -> if (value > 0) HabitDayState.MISSED else HabitDayState.DONE
                value >= habit.dailyTarget -> HabitDayState.DONE
                value > 0 -> HabitDayState.PARTIAL
                cursor.isBefore(today) -> HabitDayState.MISSED
                else -> HabitDayState.TODAY
            }
            days += HabitDay(cursor, value, state)
            cursor = cursor.plusDays(1)
        }
        return days
    }

    /** Weekly completion rates, oldest first, for the trend chart. */
    fun weeklySeries(
        habit: Habit,
        entries: List<HabitEntry>,
        today: LocalDate = LocalDate.now(),
        weeks: Int = 8,
    ): List<Float> {
        val values = entriesByDate(entries)
        val start = trackingStart(habit, entries)
        return (weeks - 1 downTo 0).map { weeksAgo ->
            weekRate(habit, values, today.minusDays(weeksAgo * 7L), start)
        }
    }

    /**
     * Coaching for one habit, best first. Every line comes from a plain rule that a reader can
     * check: nothing here is personalised beyond what the user typed and what they actually did.
     */
    fun insights(habit: Habit, stats: HabitStats): List<HabitInsight> {
        val notes = ArrayList<HabitInsight>()
        if (habit.cue.isNotBlank()) notes += HabitInsight(HabitInsightKey.STACK_IT)
        if (habit.identity.isNotBlank()) notes += HabitInsight(HabitInsightKey.IDENTITY)
        when {
            habit.kind == HabitKind.QUIT && stats.slips > 0 && stats.cleanDays < 7 ->
                notes += HabitInsight(HabitInsightKey.RELAPSE_KIND, stats.slips)
            habit.kind == HabitKind.QUIT && stats.cleanDays >= 7 ->
                notes += HabitInsight(HabitInsightKey.SLIP_FREE, stats.cleanDays)
        }
        if (!stats.todayDone && stats.currentStreak >= 3) {
            notes += HabitInsight(HabitInsightKey.NEVER_MISS_TWICE, stats.currentStreak)
        }
        if (stats.daysToNextMilestone in 1..3) {
            notes += HabitInsight(HabitInsightKey.MILESTONE_CLOSE, stats.nextMilestone ?: 0)
        }
        if (habit.schedule.type == HabitScheduleType.TIMES_PER_WEEK && stats.doneThisWeek < stats.targetThisWeek) {
            notes += HabitInsight(HabitInsightKey.WEEKLY_TARGET_BEHIND, stats.targetThisWeek - stats.doneThisWeek)
        }
        val tooBig = habit.difficulty == HabitDifficulty.HARD || habit.tracking == HabitTracking.COUNT
        if (stats.completionRate < 0.4f && tooBig) {
            notes += HabitInsight(HabitInsightKey.SHRINK_STEP)
        }
        if (stats.momentum >= 0.15f && stats.completionRate >= 0.6f) {
            notes += HabitInsight(HabitInsightKey.STRONG_WEEK)
        }
        if (stats.weakestWeekday != null && stats.completionRate < 0.85f) {
            notes += HabitInsight(HabitInsightKey.WEAK_WEEKDAY, weekday = stats.weakestWeekday)
        }
        if (stats.doneDays == 0 && stats.slips == 0) {
            notes += HabitInsight(HabitInsightKey.START_SMALL)
        }
        val priority = { key: HabitInsightKey ->
            when (key) {
                HabitInsightKey.START_SMALL -> 0
                HabitInsightKey.RELAPSE_KIND -> 1
                HabitInsightKey.NEVER_MISS_TWICE -> 2
                HabitInsightKey.MILESTONE_CLOSE -> 3
                HabitInsightKey.WEEKLY_TARGET_BEHIND -> 4
                HabitInsightKey.SHRINK_STEP -> 5
                HabitInsightKey.SLIP_FREE -> 6
                HabitInsightKey.STRONG_WEEK -> 7
                HabitInsightKey.WEAK_WEEKDAY -> 8
                HabitInsightKey.IDENTITY -> 9
                HabitInsightKey.STACK_IT -> 10
            }
        }
        return notes.sortedBy { priority(it.key) }.take(4)
    }

    /** Header numbers for the whole cabinet: how today is going across every habit. */
    fun summary(
        habits: List<Habit>,
        entries: List<HabitEntry>,
        today: LocalDate = LocalDate.now(),
        precomputedStats: Map<Long, HabitStats>? = null,
    ): HabitSummary {
        val active = habits.filter { !it.isArchived }
        val valuesByHabit = if (precomputedStats == null) entries.groupBy { it.habitId } else emptyMap()
        var scheduledToday = 0
        var doneToday = 0
        var bestStreak = 0
        var scoreTotal = 0
        active.forEach { habit ->
            val stats = precomputedStats?.get(habit.id)
                ?: stats(habit, valuesByHabit[habit.id].orEmpty(), today)
            if (habit.schedule.isScheduled(today)) {
                scheduledToday++
                if (stats.todayDone) doneToday++
            }
            bestStreak = maxOf(bestStreak, stats.currentStreak)
            scoreTotal += stats.score
        }
        return HabitSummary(
            habitCount = active.size,
            scheduledToday = scheduledToday,
            doneToday = doneToday,
            buildCount = active.count { it.kind == HabitKind.BUILD },
            quitCount = active.count { it.kind == HabitKind.QUIT },
            bestStreak = bestStreak,
            score = if (active.isEmpty()) 0 else (scoreTotal.toFloat() / active.size).roundToInt(),
        )
    }

    /** [DayOfWeek] from a stored weekday value, safe for the 1..7 range habits use. */
    fun dayOfWeek(value: Int): DayOfWeek = DayOfWeek.of(value.coerceIn(1, 7))
}
