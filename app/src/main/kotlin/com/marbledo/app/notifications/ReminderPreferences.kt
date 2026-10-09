package com.marble098.marbledo.notifications

import android.content.Context
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.util.QuietHours
import java.time.Instant
import java.time.ZoneId

/**
 * Reminder settings mirrored into SharedPreferences. Alarm receivers run without a suspend context, so they read
 * the lead time and quiet hours from here. The app keeps the mirror current from the settings flow.
 */
object ReminderPreferences {
    private const val PREFS = "marbledo_reminders"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"
    private const val DEFAULT_LEAD_MINUTES = 10
    private const val DEFAULT_QUIET_START = 22 * 60
    private const val DEFAULT_QUIET_END = 7 * 60

    fun write(context: Context, settings: AppSettings) {
        prefs(context).edit()
            .putInt(KEY_LEAD_MINUTES, settings.reminderLeadMinutes)
            .putBoolean(KEY_QUIET_ENABLED, settings.quietHoursEnabled)
            .putInt(KEY_QUIET_START, settings.quietStartMinute)
            .putInt(KEY_QUIET_END, settings.quietEndMinute)
            .apply()
    }

    /** How long before a due time the early reminder fires. Zero means no early reminder. */
    fun leadMillis(context: Context): Long =
        prefs(context).getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(0, 1440) * 60_000L

    /**
     * When quiet hours are on and [nowMillis] falls inside them, returns the moment they end. Reminders that
     * would fire during quiet hours are moved to that moment. Returns null when no deferral is needed.
     */
    fun deferUntilQuietEnds(context: Context, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_QUIET_ENABLED, false)) return null
        val start = prefs.getInt(KEY_QUIET_START, DEFAULT_QUIET_START)
        val end = prefs.getInt(KEY_QUIET_END, DEFAULT_QUIET_END)
        val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minuteOfDay = local.hour * 60 + local.minute
        if (!QuietHours.contains(minuteOfDay, start, end)) return null
        return nowMillis + QuietHours.minutesUntilEnd(minuteOfDay, start, end) * 60_000L
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
