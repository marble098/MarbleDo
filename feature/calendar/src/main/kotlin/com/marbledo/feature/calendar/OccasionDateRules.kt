package com.marbledo.feature.calendar

import java.time.LocalDate

/** Weekdays use java.time's Monday=1 … Sunday=7, never ICU's numbering. */
enum class OccasionDateRule(val key: String) {
    FIXED("fixed"), MONTH_END("month_end"), LAST_WEEKDAY("last_weekday"), NTH_WEEKDAY("nth_weekday");
}

/** Pure civil-date arithmetic: the caller supplies the calendar month's *adjusted* first day. */
object OccasionDateRules {
    fun resolve(
        firstDay: LocalDate,
        monthLength: Int,
        day: Int,
        rule: OccasionDateRule = OccasionDateRule.FIXED,
        weekday: Int? = null,
        ordinal: Int? = null,
        dayOffset: Int = 0,
    ): LocalDate? {
        require(monthLength in 28..31)
        val last = firstDay.plusDays(monthLength - 1L)
        val date = when (rule) {
            OccasionDateRule.FIXED -> if (day in 1..monthLength) firstDay.plusDays(day - 1L) else return null
            OccasionDateRule.MONTH_END -> last
            OccasionDateRule.LAST_WEEKDAY -> {
                if (weekday == null || weekday !in 1..7) return null
                last.minusDays(Math.floorMod(last.dayOfWeek.value - weekday, 7).toLong())
            }
            OccasionDateRule.NTH_WEEKDAY -> {
                if (weekday == null || weekday !in 1..7 || ordinal == null || ordinal !in 1..5) return null
                firstDay.plusDays((Math.floorMod(weekday - firstDay.dayOfWeek.value, 7) + 7 * (ordinal - 1)).toLong())
                    .takeUnless { it > last } ?: return null
            }
        }
        return date.plusDays(dayOffset.toLong())
    }
}
