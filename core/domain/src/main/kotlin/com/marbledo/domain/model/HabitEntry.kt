package com.marbledo.domain.model

import kotlinx.serialization.Serializable

/**
 * What happened on one day of one habit.
 *
 * The meaning of [value] follows the habit kind:
 * - a **build** habit with [HabitTracking.CHECK] stores `1` when the day is done;
 * - a **build** habit with [HabitTracking.COUNT] stores how much was done (glasses, minutes…);
 * - a **quit** habit stores `1` on the days the user slipped, and nothing on clean days.
 *
 * A row only exists for days the user actually touched, so "no row" is a meaningful state
 * (not yet done, or clean for a quit habit) rather than a missing record.
 */
@Serializable
data class HabitEntry(
    val habitId: Long,
    /** ISO local date, `yyyy-MM-dd`. */
    val date: String,
    val value: Int = 0,
    val updatedAtEpochMillis: Long = 0L,
) {
    init {
        require(date.matches(ISO_DATE)) { "date must be an ISO local date" }
        require(value in 0..999) { "value is out of range" }
    }

    private companion object {
        val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
