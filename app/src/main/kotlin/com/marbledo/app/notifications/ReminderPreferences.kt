package com.marble098.marbledo.notifications

import android.content.Context
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.QuietHours
import java.time.Instant
import java.time.ZoneId

/**
 * The handful of reminder settings a receiver needs *before* it can read DataStore.
 *
 * The app keeps this mirror current from the settings flow. It holds the lead time, the quiet
 * hours, the briefing hour and the display language — not the channel switches, because every
 * notification is built from a full `AppSettings` and reads those from there.
 */
object ReminderPreferences {
    private const val PREFS = "marbledo_reminders"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"
    private const val KEY_OCCASION_HOUR = "occasion_hour"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_NUMERALS = "numeral_mode"
    private const val KEY_OCCASION_LEAD_DAYS = "occasion_lead_days"
    private const val DEFAULT_LEAD_MINUTES = 10
    private const val DEFAULT_QUIET_START = 22 * 60
    private const val DEFAULT_QUIET_END = 7 * 60
    private const val DEFAULT_OCCASION_HOUR = 8

    fun write(context: Context, settings: AppSettings) {
        prefs(context).edit()
            .putInt(KEY_LEAD_MINUTES, settings.reminderLeadMinutes)
            .putBoolean(KEY_QUIET_ENABLED, settings.quietHoursEnabled)
            .putInt(KEY_QUIET_START, settings.quietStartMinute)
            .putInt(KEY_QUIET_END, settings.quietEndMinute)
            .putInt(KEY_OCCASION_HOUR, settings.occasionNotificationHour)
            .putString(KEY_LANGUAGE, settings.languageTag)
            .putString(KEY_NUMERALS, settings.numeralMode.name)
            .putInt(KEY_OCCASION_LEAD_DAYS, settings.occasionReminderLeadDays)
            .apply()
    }

    /** How long before a due time the early reminder fires. Zero means no early reminder. */
    fun leadMillis(context: Context): Long =
        prefs(context).getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES).coerceIn(0, 1440) * 60_000L

    /**
     * The local hour of the morning briefing, 0..23.
     *
     * Of the three channel switches this is the only one a receiver needs: every card is built
     * from a full [AppSettings] and can therefore read the switches directly, but the boot receiver
     * re-arms a WorkManager request before Koin is ready, so the hour has to be here.
     */
    fun occasionHour(context: Context): Int = prefs(context).getInt(KEY_OCCASION_HOUR, DEFAULT_OCCASION_HOUR).coerceIn(0, 23)

    /** Days before a starred occasion to remind; 0 disables occasion reminders. */
    fun occasionLeadDays(context: Context): Int = prefs(context).getInt(KEY_OCCASION_LEAD_DAYS, 0).coerceIn(0, 30)

    fun languageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "fa")?.takeIf { it in setOf("fa", "en") } ?: "fa"

    fun numeralMode(context: Context): NumeralMode =
        prefs(context).getString(KEY_NUMERALS, null)
            ?.let { stored -> runCatching { NumeralMode.valueOf(stored) }.getOrNull() }
            ?: NumeralMode.PERSIAN

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

    /**
     * The next moment the local clock reads [hour]:00, always in the future. Used to arm the daily
     * occasion briefing at the hour the user picked instead of a fixed 09:00.
     */
    fun nextAtHour(hour: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val at = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        return (if (at.isAfter(now)) at else at.plusDays(1)).toInstant().toEpochMilli()
    }

    /** How long until [nextAtHour] from now; the initial delay of the daily work request. */
    fun delayUntilHour(hour: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
        (nextAtHour(hour, nowMillis, zone) - nowMillis).coerceAtLeast(0L)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
