package com.marbledo.domain.util

/** Pure quiet-hours arithmetic shared by the reminder code and unit tests. Values are minutes since local midnight. */
object QuietHours {
    /**
     * True when [minuteOfDay] falls inside the quiet window. The start is inclusive and the end is exclusive.
     * A window whose start is later than its end wraps past midnight, so 22:00 to 07:00 is quiet at 23:30 and at 06:59.
     * Equal start and end values mean there is no quiet window.
     */
    fun contains(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        require(minuteOfDay in 0..1439 && startMinute in 0..1439 && endMinute in 0..1439) { "Minutes must be within one day" }
        if (startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    /** Minutes from [minuteOfDay] until the quiet window ends. Returns 0 when the moment is outside the window. */
    fun minutesUntilEnd(minuteOfDay: Int, startMinute: Int, endMinute: Int): Int {
        if (!contains(minuteOfDay, startMinute, endMinute)) return 0
        return if (minuteOfDay <= endMinute) endMinute - minuteOfDay else MINUTES_PER_DAY - minuteOfDay + endMinute
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
