package com.marble098.marbledo.notifications

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import com.marble098.marbledo.R
import com.marbledo.domain.util.TextNormalizer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The "بعداً" action: three quick choices, no app, no clock fiddling.
 *
 * A full-screen task editor for a one-second decision is the wrong shape, so this is a
 * transparent, cancel-on-touch-outside activity that finishes as soon as a choice is made. The
 * task's alarm is rescheduled through the ordinary [ReminderReceiver] broadcast, which also
 * dismisses the notification — the same path a background snooze takes, so the two cannot drift.
 *
 * The dialog is a plain AppCompat `AlertDialog` rather than Compose: it has to be able to appear
 * straight from a notification action, with no activity transition and no ViewModel, and it must
 * cost nothing to inflate. The two settings it needs (digits, language) come from the
 * [ReminderPreferences] mirror, which is a synchronous SharedPreferences read rather than a
 * suspending DataStore round-trip on the main thread.
 */
class SnoozePickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setFinishOnTouchOutside(true)
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) {
            finish()
            return
        }
        val numeralMode = ReminderPreferences.numeralMode(this)
        val options = SnoozeOptions.choices(ZonedDateTime.now(ZoneId.systemDefault()))
        val labels = options.map { TextNormalizer.formatDigits(getString(it.labelRes), numeralMode) }
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_snooze_title)
            .setItems(labels.toTypedArray()) { _, index ->
                val choice = options[index]
                NotificationActions.snoozeAt(
                    applicationContext,
                    taskId,
                    choice.atMillis,
                    requestCode = (taskId * 31 + index).toInt(),
                ).send()
                NotificationManagerCompat.from(applicationContext).cancel(ReminderReceiver.notificationId(taskId))
                val clock = TextNormalizer.formatDigits(
                    Instant.ofEpochMilli(choice.atMillis).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)),
                    numeralMode,
                )
                // The toast is the only feedback the user gets without the app in front of them,
                // so it says *when* the reminder will come back, not just that it was postponed.
                Toast.makeText(
                    this,
                    getString(R.string.notification_snooze_done, clock),
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}

/**
 * The three "بعداً" moments, as moments rather than strings, so the arithmetic is testable and the
 * dialog only has to label them.
 */
internal object SnoozeOptions {

    internal data class Choice(val labelRes: Int, val atMillis: Long)

    /** An hour, this evening, or tomorrow morning — in that order of urgency. */
    fun choices(now: ZonedDateTime): List<Choice> = listOf(
        Choice(R.string.notification_snooze_hour, now.plusHours(1).toInstant().toEpochMilli()),
        // "This evening" is 18:00, the moment the working day is over and a reminder is still
        // actionable. Late at night that moment is already gone, so the honest reading is "in an
        // hour" — a reminder that arrives at 23:40 is the one people swipe away.
        Choice(R.string.notification_snooze_evening, evening(now).toInstant().toEpochMilli()),
        // Tomorrow morning is a fixed 08:00 rather than "24 hours from now", for the same reason.
        Choice(
            R.string.notification_snooze_tomorrow,
            now.toLocalDate().plusDays(1).atTime(8, 0).atZone(now.zone).toInstant().toEpochMilli(),
        ),
    )

    private fun evening(now: ZonedDateTime): ZonedDateTime {
        val todayEvening = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
        return if (todayEvening.isAfter(now.plusMinutes(30))) todayEvening else now.plusHours(1)
    }
}
