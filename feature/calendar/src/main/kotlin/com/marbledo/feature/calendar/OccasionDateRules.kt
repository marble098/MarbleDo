package com.marbledo.feature.calendar

import java.time.LocalDate

/** Weekdays use java.time's Monday=1 … Sunday=7, never ICU's numbering. */
enum class OccasionDateRule(val key: String) {
    FIXED("fixed"), MONTH_END("month_end"), LAST_WEEKDAY("last_weekday"), NTH_WEEKDAY("nth_weekday"),
    WEEKDAY_ON_OR_BEFORE("weekday_on_or_before"), SUNDAY_TO_SATURDAY("sunday_to_saturday"),
    WESTERN_EASTER("western_easter"), ORTHODOX_EASTER("orthodox_easter");
}

/** Pure civil-date arithmetic. Lunar callers supply the month's already-adjusted first day. */
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
        require(dayOffset in -60..60)
        val last = firstDay.plusDays(monthLength - 1L)
        val date = when (rule) {
            OccasionDateRule.FIXED -> if (day in 1..monthLength) firstDay.plusDays(day - 1L) else return null
            OccasionDateRule.SUNDAY_TO_SATURDAY -> {
                if (day !in 1..monthLength) return null
                val anchor = firstDay.plusDays(day - 1L)
                if (anchor.dayOfWeek.value == 7) anchor.minusDays(1) else anchor
            }
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
            OccasionDateRule.WEEKDAY_ON_OR_BEFORE -> {
                if (weekday == null || weekday !in 1..7 || day !in 1..monthLength) return null
                val anchor = firstDay.plusDays(day - 1L)
                anchor.minusDays(Math.floorMod(anchor.dayOfWeek.value - weekday, 7).toLong())
            }
            OccasionDateRule.WESTERN_EASTER -> if (firstDay.year in 1583..4099) westernEaster(firstDay.year) else return null
            OccasionDateRule.ORTHODOX_EASTER -> if (firstDay.year in 1583..4099) orthodoxEaster(firstDay.year) else return null
        }
        return date.plusDays(dayOffset.toLong())
    }

    /** Meeus/Jones/Butcher Gregorian computus, not last year's fixed Gregorian date. */
    fun westernEaster(year: Int): LocalDate {
        require(year in 1583..4099)
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val n = h + l - 7 * m + 114
        return LocalDate.of(year, n / 31, n % 31 + 1)
    }

    /** Julian computus converted by Julian day number; the 13-day gap is NOT hard-coded. */
    fun orthodoxEaster(year: Int): LocalDate {
        require(year in 1583..4099)
        val d = (19 * (year % 19) + 15) % 30
        val e = (2 * (year % 4) + 4 * (year % 7) - d + 34) % 7
        val n = d + e + 114
        val month = n / 31
        val day = n % 31 + 1
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        val julianDay = day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return LocalDate.ofEpochDay(julianDay - 2440588L)
    }
}
