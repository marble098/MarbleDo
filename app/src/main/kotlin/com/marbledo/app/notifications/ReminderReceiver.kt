package com.marble098.marbledo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.domain.model.RepeatFrequency
import com.marbledo.domain.repository.TaskBackupScheduler
import com.marbledo.domain.repository.TaskReminderScheduler
import com.marbledo.domain.repository.TaskRepository
import com.marbledo.feature.tasks.AndroidRecurrenceCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val repository = koin.get<TaskRepository>()
                val task = repository.getTask(id) ?: return@launch
                when (intent.action) {
                    ACTION_DONE -> {
                        repository.setCompleted(id, true)
                        val scheduler = koin.get<TaskReminderScheduler>()
                        scheduler.cancel(id)
                        val repeat = task.recurrence
                        val previousDue = task.dueAtEpochMillis
                        if (repeat != null && repeat.frequency != RepeatFrequency.NONE && previousDue != null) {
                            val now = System.currentTimeMillis()
                            var nextDue = AndroidRecurrenceCalculator.nextAfter(previousDue, repeat)
                            var attempts = 0
                            while (nextDue <= now && attempts++ < 500) {
                                nextDue = AndroidRecurrenceCalculator.nextAfter(nextDue, repeat)
                            }
                            if (nextDue > now) {
                                val next = task.copy(
                                    id = 0,
                                    dueAtEpochMillis = nextDue,
                                    isCompleted = false,
                                    isArchived = false,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now,
                                )
                                val nextId = repository.save(next)
                                scheduler.schedule(next.copy(id = nextId))
                            }
                        }
                        koin.get<TaskBackupScheduler>().scheduleDebounced()
                        NotificationManagerCompat.from(context).cancel(notificationId(id))
                    }
                    ACTION_SNOOZE -> {
                        AndroidTaskReminderScheduler(context).remindAgainAt(id, System.currentTimeMillis() + SNOOZE_MILLIS)
                        NotificationManagerCompat.from(context).cancel(notificationId(id))
                    }
                    else -> {
                        val deferUntil = ReminderPreferences.deferUntilQuietEnds(context, System.currentTimeMillis())
                        if (deferUntil != null) {
                            AndroidTaskReminderScheduler(context).remindAgainAt(id, deferUntil)
                        } else {
                            showNotification(context, task.title, id, task.dueAtEpochMillis ?: System.currentTimeMillis())
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, id: Long, dueAtMillis: Long) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val contentIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK_ID, id).apply {
                data = Uri.parse("marbledo://task/$id")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() * 2,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE).setData(Uri.parse("marbledo://action/done/$id")).putExtra(EXTRA_TASK_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode() * 2 + 1,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).setData(Uri.parse("marbledo://action/snooze/$id")).putExtra(EXTRA_TASK_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val largeIcon = ContextCompat.getDrawable(context, R.drawable.ic_notification_large)?.toBitmap()
        val builder = NotificationCompat.Builder(context, NotificationChannels.TASKS)
            .setSmallIcon(R.drawable.ic_stat_marble)
            .setLargeIcon(largeIcon)
            .setColor(ContextCompat.getColor(context, R.color.marble_accent))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_task_reminder))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(dueAtMillis)
            .setUsesChronometer(dueAtMillis > System.currentTimeMillis())
            .setChronometerCountDown(true)
            .addAction(R.drawable.ic_stat_marble, context.getString(R.string.notification_done), doneIntent)
            .addAction(R.drawable.ic_stat_marble, context.getString(R.string.notification_snooze), snoozeIntent)
        NotificationManagerCompat.from(context).notify(notificationId(id), builder.build())
    }

    private fun notificationId(taskId: Long): Int = (taskId xor (taskId ushr 32)).toInt()

    companion object {
        const val EXTRA_TASK_ID = "com.marble098.marbledo.extra.TASK_ID"
        const val ACTION_REMIND = "com.marble098.marbledo.action.REMIND"
        const val ACTION_DONE = "com.marble098.marbledo.action.DONE"
        const val ACTION_SNOOZE = "com.marble098.marbledo.action.SNOOZE"
        const val ACTION_SNOOZED = "com.marble098.marbledo.action.SNOOZED"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L
    }
}
