package com.marble098.marbledo.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.marble098.marbledo.MainActivity

/**
 * The three verbs every MarbleDo task notification offers, built in one place.
 *
 * The point of the redesign is that a reminder is quick to *finish*: "انجام شد" completes the task
 * and closes the notification without ever bringing the app forward, "بعداً" opens a tiny
 * translucent picker and reschedules the alarm, and tapping the card itself lands on that task's
 * editor sheet. All three start from the same task id, so the card, the grouped summary and the
 * single reminder cannot disagree about what they point at.
 *
 * `requestCode` differs per site because Android compares pending intents **with their extras**:
 * two ids built from the same intent without distinct request codes collide and the second one
 * silently wins.
 */
internal object NotificationActions {

    /** Deep link to one task's editor sheet, both as an extra and as a `marbledo://task/{id}` URI. */
    fun openTask(context: Context, taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("marbledo://task/$taskId")
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Completes the task in a broadcast receiver; the app is never brought to the front. */
    fun done(context: Context, taskId: Long, requestCode: Int): PendingIntent = broadcast(
        context,
        ReminderReceiver.ACTION_DONE,
        "marbledo://action/done/$taskId",
        taskId,
        requestCode,
    )

    /** Opens the translucent snooze picker (an hour / this evening / tomorrow morning). */
    fun snoozePicker(context: Context, taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, SnoozePickerActivity::class.java).apply {
            data = Uri.parse("marbledo://action/snooze-picker/$taskId")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Reschedules the task at [atMillis] without showing a picker; the picker sends this. */
    fun snoozeAt(context: Context, taskId: Long, atMillis: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SNOOZE_AT
            data = Uri.parse("marbledo://action/snooze/$taskId/$atMillis")
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_SNOOZE_AT, atMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The group summary's own "open the calendar" verb. */
    fun openDay(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("marbledo://calendar/today")
            putExtra(MainActivity.EXTRA_DESTINATION, "calendar")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(context: Context, action: String, uri: String, taskId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse(uri)
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
