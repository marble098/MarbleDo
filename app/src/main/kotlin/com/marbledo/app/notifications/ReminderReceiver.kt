package com.marble098.marbledo.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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

/**
 * Runs every button a reminder offers, without opening the app.
 *
 * The redesign's promise is "one tap and it is gone": `ACTION_DONE` completes the task, cancels
 * its alarm, closes its notification and answers with a short haptic and a toast; `ACTION_SNOOZE_AT`
 * reschedules the alarm to an exact moment the picker chose; `ACTION_REMIND` posts the card.
 *
 * The channel is chosen from the *task's* relationship to today — overdue, today, or a future
 * reminder — so the three switches in Settings each silence exactly their own kind of alert.
 */
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
                        confirmDone(context)
                        // The group summary is rebuilt from the surviving tasks, so finishing one of
                        // five late tasks leaves a summary that says four.
                        runCatching { GroupedNotification.refresh(context) }
                    }
                    ACTION_SNOOZE_AT -> {
                        val at = intent.getLongExtra(EXTRA_SNOOZE_AT, System.currentTimeMillis() + SNOOZE_MILLIS)
                        AndroidTaskReminderScheduler(context).remindAgainAt(id, at)
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

    private suspend fun showNotification(baseContext: Context, task: Task, settings: AppSettings) {
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
        // The task's own deep link: tapping the reminder opens that task's editor sheet, not the
        // home screen it would otherwise land on.
        val contentIntent = NotificationActions.openTask(context, id, requestCode = id.hashCode())
        val doneIntent = NotificationActions.done(context, id, requestCode = id.hashCode() * 2)
        val snoozeIntent = NotificationActions.snoozePicker(context, id, requestCode = id.hashCode() * 2 + 1)
        val themeAccent = notificationAccent(context.applicationContext, settings)
        val largeIcon = NotificationArtwork.tintedFallbackIcon(context, R.drawable.ic_notification_large, themeAccent)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_notification_large)?.toBitmap()
        // Classification of the reminder decides the channel and the state chip: a task that
        // slipped goes to the loud "overdue" channel, today's work to the calm "today" one.
        val classification = ReminderClassification.of(task, System.currentTimeMillis(), zone)
        if (classification == ReminderState.OVERDUE && !settings.overdueNotificationsEnabled) return
        if (classification == ReminderState.TODAY && !settings.todayRemindersEnabled) return
        val channel = when (classification) {
            ReminderState.OVERDUE -> NotificationChannels.OVERDUE
            ReminderState.TODAY -> NotificationChannels.TODAY
            ReminderState.LATER -> NotificationChannels.TASKS
        }
        val stateWord = when (classification) {
            ReminderState.OVERDUE -> context.getString(
                R.string.notification_overdue_chip,
                TextNormalizer.formatDigits(classification.overdueCountFor(task).toString(), settings.numeralMode),
            )
            ReminderState.TODAY -> context.getString(R.string.notification_today_chip)
            ReminderState.LATER -> detail
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_marble)
            .setLargeIcon(largeIcon)
            .setColor(themeAccent)
            .setContentTitle(task.title)
            .setContentText(stateWord)
            .setSubText(listOf(priority, task.category).filter { it.isNotBlank() }.joinToString(" · "))
            .setStyle(NotificationCompat.InboxStyle().also { inbox ->
                inbox.setBigContentTitle(task.title)
                inbox.addLine(stateWord)
                inbox.addLine(detail)
                if (task.checklist.isNotEmpty()) inbox.addLine(TextNormalizer.formatDigits(context.getString(R.string.notification_checklist,
                    task.checklist.count { it.isDone }, task.checklist.size), settings.numeralMode))
                if (task.description.isNotBlank()) inbox.addLine(task.description.take(200))
                inbox.setSummaryText(priority)
            })
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_marble).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_private)).build())
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(dueAtMillis)
            .setUsesChronometer(!task.isAllDay && dueAtMillis > System.currentTimeMillis())
            .setChronometerCountDown(true)
            // Three actions at most, and every one of them carries an icon and a full label: the
            // old pair of 10 dp-less buttons was the lowest-contrast thing on the card.
            .addAction(R.drawable.ic_notification_done, context.getString(R.string.notification_done), doneIntent)
            .addAction(R.drawable.ic_notification_snooze, context.getString(R.string.notification_snooze), snoozeIntent)
            .addAction(R.drawable.ic_notification_open, context.getString(R.string.notification_open_task), contentIntent)
        // Overdue tasks also join the grouped summary, so five late tasks are one entry in the
        // shade with "۳ عقب‌افتاده" on it rather than five separate alerts.
        if (classification == ReminderState.OVERDUE) {
            builder.setGroup(GroupedNotification.GROUP_OVERDUE)
        }
        NotificationManagerCompat.from(context).notify(notificationId(id), builder.build())
        if (classification == ReminderState.OVERDUE) {
            // The grouped summary is rebuilt from the whole list, so it always states the real
            // number of late tasks instead of guessing from the one that just arrived.
            val all = GlobalContext.getOrNull()?.get<TaskRepository>()?.observeTasks()?.first().orEmpty()
            if (all.isNotEmpty()) GroupedNotification.postSummary(context, settings, all)
        }
    }

    /** A short, unmistakable confirmation that the tap worked, with the app still closed. */
    private fun confirmDone(context: Context) {
        runCatching { vibrate(context) }
        runCatching {
            Toast.makeText(context.applicationContext, R.string.notification_done_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        // A single 30 ms tick, not a pattern: confirmation, not an alarm.
        vibrator.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private val completionMutex = Mutex()
        const val EXTRA_TASK_ID = "com.marble098.marbledo.extra.TASK_ID"
        const val EXTRA_SNOOZE_AT = "com.marble098.marbledo.extra.SNOOZE_AT"
        const val ACTION_REMIND = "com.marble098.marbledo.action.REMIND"
        const val ACTION_DONE = "com.marble098.marbledo.action.DONE"
        const val ACTION_SNOOZE = "com.marble098.marbledo.action.SNOOZE"
        const val ACTION_SNOOZE_AT = "com.marble098.marbledo.action.SNOOZE_AT"
        const val ACTION_SNOOZED = "com.marble098.marbledo.action.SNOOZED"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L

        /** The notification id a task owns, so the picker can dismiss exactly its own card. */
        internal fun notificationId(taskId: Long): Int = (taskId xor (taskId ushr 32)).toInt()
    }
}

/** Where a task sits relative to today, which decides its channel and the card's chip. */
internal enum class ReminderState { OVERDUE, TODAY, LATER }

internal object ReminderClassification {

    fun of(task: Task, nowMillis: Long, zone: ZoneId): ReminderState {
        val due = task.dueAtEpochMillis ?: return ReminderState.LATER
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when {
            dueDate.isBefore(today) -> ReminderState.OVERDUE
            dueDate == today && !task.isAllDay && due < nowMillis -> ReminderState.OVERDUE
            dueDate == today -> ReminderState.TODAY
            else -> ReminderState.LATER
        }
    }

    /**
     * A single task cannot count its siblings, and it must not pretend to: the grouped summary is
     * the only card that states a total, so one late task reads "۱ عقب‌افتاده" and the group's
     * summary carries the real number.
     */
    fun ReminderState.overdueCountFor(@Suppress("UNUSED_PARAMETER") task: Task): Int =
        if (this == ReminderState.OVERDUE) 1 else 0
}
