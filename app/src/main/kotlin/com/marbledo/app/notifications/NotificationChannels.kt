package com.marble098.marbledo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.marble098.marbledo.R

/**
 * One channel per *kind* of interruption, so Settings can keep exactly one promise per switch.
 *
 * The old build had a single "tasks" channel: a task that was three days late and a task due in two
 * hours arrived the same way, and switching off either switched off both. The redesign splits them
 * by how loud the app is allowed to be:
 *
 *  * [OVERDUE] — the grouped, high-importance reminder for what slipped;
 *  * [TODAY] — today's reminders, default importance, with Done and Later;
 *  * [CALENDAR] — the once-a-day occasion briefing: low importance, no sound, no vibration;
 *  * [PERSISTENT_DATE] — the silent status-bar date icon.
 *
 * Channels are created on every start because a channel's *name* is re-read by the system settings
 * screen; changing the app language without recreating them leaves the old language's labels there.
 * Only the id is immutable, which is why the ids are stable strings.
 */
object NotificationChannels {
    const val TASKS = "marbledo_tasks"
    const val COUNTDOWN = "marbledo_countdown"

    /**
     * The per-day occasion alert, kept for a specific date the calendar screen opens.
     *
     * A channel's importance is frozen when it is created, which is why the *silent* briefing
     * below has a new id of its own: reusing this one would have left every existing install with
     * a ringing channel for a briefing the redesign promises is silent.
     */
    const val CALENDAR = "marbledo_calendar"

    /** The once-a-day occasion briefing: low importance, silent, once each morning. */
    const val OCCASIONS = "marbledo_occasions"
    const val BACKUP = "marbledo_backup"
    const val DAILY = "marbledo_daily_summary"
    /** Status-bar date icon. Default importance keeps it visible on launchers that hide silent or low channels. */
    const val PERSISTENT_DATE = "marbledo_status_date"

    /** Grouped, loud reminders for tasks that slipped. */
    const val OVERDUE = "marbledo_overdue"

    /** Today's reminders: calm, with the three quick actions. */
    const val TODAY = "marbledo_today"

    /** Channel used by earlier builds. It is deleted so the user does not see two date channels. */
    const val LEGACY_PERSISTENT_CALENDAR = "marbledo_persistent_calendar"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(OVERDUE, context.getString(R.string.channel_overdue), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_overdue_description)
                // The summary is silent on purpose, but each task card is allowed one short buzz so
                // the group is noticed. The group summary itself calls setSilent(true).
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(TODAY, context.getString(R.string.channel_today), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_today_description)
                enableVibration(true)
            },
            NotificationChannel(TASKS, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_tasks_description)
                enableVibration(true)
            },
            NotificationChannel(OCCASIONS, context.getString(R.string.channel_occasions_silent), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_occasions_silent_description)
                // The morning briefing is information, not an alarm: on the low channel it never
                // makes a sound, and it never buzzes or badges a launcher.
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
            NotificationChannel(CALENDAR, context.getString(R.string.channel_calendar), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_calendar_description)
            },
            NotificationChannel(COUNTDOWN, context.getString(R.string.channel_countdown), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_countdown_description)
                setShowBadge(false)
            },
            NotificationChannel(BACKUP, context.getString(R.string.channel_backup), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_backup_description)
            },
            NotificationChannel(DAILY, context.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_daily_description)
            },
            NotificationChannel(PERSISTENT_DATE, context.getString(R.string.channel_persistent_calendar), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_persistent_calendar_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannels(channels)
        manager.deleteNotificationChannel(LEGACY_PERSISTENT_CALENDAR)
    }
}
