package com.marbledo.feature.tasks

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale

/** Use Android's public ICU locale factory instead of the non-SDK PersianCalendar class. */
private val persianCalendarLocale = ULocale("fa_IR@calendar=persian")
private val islamicCivilCalendarLocale = ULocale("ar@calendar=islamic-civil")

internal fun persianCalendar(timeZone: TimeZone): Calendar =
    Calendar.getInstance(timeZone, persianCalendarLocale)

internal fun islamicCivilCalendar(timeZone: TimeZone): Calendar =
    Calendar.getInstance(timeZone, islamicCivilCalendarLocale)
