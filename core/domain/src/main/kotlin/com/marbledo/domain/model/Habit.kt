package com.marbledo.domain.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/** Whether a habit is something the user wants to grow or something they want to leave behind. */
@Serializable
enum class HabitKind { BUILD, QUIT }

/** How a day is recorded: a yes/no tick, or a counted amount (glasses, minutes, pages). */
@Serializable
enum class HabitTracking { CHECK, COUNT }

/** How often the habit is expected. */
@Serializable
enum class HabitScheduleType {
    /** Every single day. */
    EVERY_DAY,

    /** Only on the chosen weekdays. */
    SPECIFIC_DAYS,

    /** Any day counts; the weekly target is what matters. */
    TIMES_PER_WEEK,
}

/**
 * Self-rated difficulty. It never changes what is counted, only what the coach suggests: a habit
 * rated hard with a low completion rate gets told to shrink the step, not to try harder.
 */
@Serializable
enum class HabitDifficulty { EASY, MEDIUM, HARD }

@Serializable
data class HabitSchedule(
    val type: HabitScheduleType = HabitScheduleType.EVERY_DAY,
    /** [DayOfWeek] values (1 = Monday … 7 = Sunday) the habit is planned on. */
    val weekdays: Set<Int> = emptySet(),
    val timesPerWeek: Int = 3,
) {
    init {
        require(timesPerWeek in 1..7) { "timesPerWeek must be between 1 and 7" }
        require(weekdays.all { it in 1..7 }) { "weekdays must be DayOfWeek values" }
    }

    /** Whether [date] is a day this habit asks something of the user. */
    fun isScheduled(date: LocalDate): Boolean = when (type) {
        HabitScheduleType.EVERY_DAY -> true
        HabitScheduleType.SPECIFIC_DAYS -> date.dayOfWeek.value in weekdays
        // With a weekly target every day is a chance to move the counter.
        HabitScheduleType.TIMES_PER_WEEK -> true
    }

    /** How many days of the seven-day week ending on [date] are planned. */
    fun plannedDaysInWeekEnding(date: LocalDate): Int = when (type) {
        HabitScheduleType.EVERY_DAY -> 7
        HabitScheduleType.SPECIFIC_DAYS -> weekdays.size
        HabitScheduleType.TIMES_PER_WEEK -> timesPerWeek
    }

    companion object {
        /** Monday-to-Friday, the most common specific-days schedule. */
        fun weekdays(): HabitSchedule = HabitSchedule(
            type = HabitScheduleType.SPECIFIC_DAYS,
            weekdays = setOf(1, 2, 3, 4, 5),
        )
    }
}

/**
 * A habit the user is building or quitting, together with the psychology scaffolding that makes
 * it stick: a cue for the implementation intention ("after I …"), an identity sentence
 * ("I am someone who …") and a reason to come back to on hard days.
 *
 * @param dailyTarget how much counts as a done day; 1 for a tick, more for a counted habit.
 * @param reminderMinuteOfDay minute of day for the daily nudge, or -1 when reminders are off.
 * @param startDate ISO date the user began tracking; blank means "from the first record".
 */
@Serializable
data class Habit(
    val id: Long = 0,
    val name: String,
    val kind: HabitKind = HabitKind.BUILD,
    val tracking: HabitTracking = HabitTracking.CHECK,
    val schedule: HabitSchedule = HabitSchedule(),
    val dailyTarget: Int = 1,
    val unitLabel: String = "",
    val icon: String = DEFAULT_ICON,
    val themeId: String = HabitTheme.DEFAULT.id,
    val cue: String = "",
    val identity: String = "",
    val why: String = "",
    val difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
    val reminderMinuteOfDay: Int = NO_REMINDER,
    val startDate: String = "",
    val isArchived: Boolean = false,
    val createdAtEpochMillis: Long = 0L,
    val sortOrder: Int = 0,
) {
    init {
        require(name.isNotBlank()) { "A habit needs a name" }
        require(dailyTarget in 1..99) { "dailyTarget must be between 1 and 99" }
        require(reminderMinuteOfDay in NO_REMINDER..1439) { "reminderMinuteOfDay is out of range" }
    }

    /** The date tracking started, or null when it can be derived from the earliest record. */
    fun startDateOrNull(): LocalDate? =
        startDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        const val DEFAULT_ICON = "\uD83C\uDF31"
        const val NO_REMINDER = -1
    }
}
