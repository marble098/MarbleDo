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
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.model.TaskPriority
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.PersianDateUtils
import com.marbledo.feature.tasks.R as TaskR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.res.Configuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                if (task.isArchived || (task.isCompleted && intent.action != ACTION_DONE)) return@launch
                when (intent.action) {
                    ACTION_DONE -> completionMutex.withLock {
                        val current = repository.getTask(id) ?: return@withLock
                        if (current.isCompleted || current.isArchived) return@withLock
                        repository.setCompleted(id, true)
                        val scheduler = koin.get<TaskReminderScheduler>()
                        scheduler.cancel(id)
                        val repeat = current.recurrence
                        val previousDue = current.dueAtEpochMillis
                        if (repeat != null && repeat.frequency != RepeatFrequency.NONE && previousDue != null) {
                            val now = System.currentTimeMillis()
                            var nextDue = AndroidRecurrenceCalculator.nextAfter(previousDue, repeat)
                            var attempts = 0
                            while (nextDue <= now && attempts++ < 500) {
                                nextDue = AndroidRecurrenceCalculator.nextAfter(nextDue, repeat)
                            }
                            if (nextDue > now) {
                                val next = current.copy(
                                    checklist = current.checklist.map { it.copy(isDone = false) },
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
                    ACTION_REMIND, ACTION_SNOOZED -> {
                        val deferUntil = ReminderPreferences.deferUntilQuietEnds(context, System.currentTimeMillis())
                        if (deferUntil != null) {
                            AndroidTaskReminderScheduler(context).remindAgainAt(id, deferUntil)
                        } else {
                            showNotification(context, task, SettingsRepository(context).settings.first())
                        }
                    }
                    else -> return@launch
                }
                if (intent.action == ACTION_DONE) PersistentCalendarNotificationWorker.refreshNow(context)
            } catch (_: kotlinx.coroutines.CancellationException) {
                // The receiver is being torn down; nothing left to show.
            } catch (_: Exception) {
                // A failed reminder must never crash the app process.
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(baseContext: Context, task: Task, settings: AppSettings) {
        val locale = Locale.forLanguageTag(settings.languageTag)
        val context = baseContext.createConfigurationContext(Configuration(baseContext.resources.configuration).apply {
            setLocale(locale); setLayoutDirection(locale)
        })
        val id = task.id
        val dueAtMillis = task.dueAtEpochMillis ?: System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val clock = if (task.isAllDay) context.getString(R.string.notification_all_day)
            else Instant.ofEpochMilli(dueAtMillis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
        val dueLabel = TextNormalizer.formatDigits("${PersianDateUtils.fullDate(dueAtMillis, settings.languageTag, settings.numeralMode, zone)} · $clock", settings.numeralMode)
        val detail = context.getString(R.string.notification_reminder_detail, dueLabel)
        val priority = context.getString(when (task.priority) {
            TaskPriority.LOW -> TaskR.string.tasks_priority_low
            TaskPriority.NORMAL -> TaskR.string.tasks_priority_normal
            TaskPriority.HIGH -> TaskR.string.tasks_priority_high
            TaskPriority.URGENT -> TaskR.string.tasks_priority_urgent
        })
        NotificationChannels.create(context)
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
            .setContentTitle(task.title)
            .setContentText(detail)
            .setSubText(listOf(priority, task.category).filter { it.isNotBlank() }.joinToString(" · "))
            .setStyle(NotificationCompat.InboxStyle().apply {
                setBigContentTitle(task.title)
                addLine(detail)
                if (task.checklist.isNotEmpty()) addLine(TextNormalizer.formatDigits(context.getString(R.string.notification_checklist,
                    task.checklist.count { it.isDone }, task.checklist.size), settings.numeralMode))
                if (task.description.isNotBlank()) addLine(task.description.take(200))
                setSummaryText(priority)
            })
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, NotificationChannels.TASKS)
                .setSmallIcon(R.drawable.ic_stat_marble).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_private)).build())
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(dueAtMillis)
            .setUsesChronometer(!task.isAllDay && dueAtMillis > System.currentTimeMillis())
            .setChronometerCountDown(true)
            .addAction(R.drawable.ic_stat_marble, context.getString(R.string.notification_done), doneIntent)
            .addAction(R.drawable.ic_stat_marble, context.getString(R.string.notification_snooze), snoozeIntent)
        NotificationManagerCompat.from(context).notify(notificationId(id), builder.build())
    }

    private fun notificationId(taskId: Long): Int = (taskId xor (taskId ushr 32)).toInt()

    companion object {
        private val completionMutex = Mutex()
        const val EXTRA_TASK_ID = "com.marble098.marbledo.extra.TASK_ID"
        const val ACTION_REMIND = "com.marble098.marbledo.action.REMIND"
        const val ACTION_DONE = "com.marble098.marbledo.action.DONE"
        const val ACTION_SNOOZE = "com.marble098.marbledo.action.SNOOZE"
        const val ACTION_SNOOZED = "com.marble098.marbledo.action.SNOOZED"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L
    }
}
