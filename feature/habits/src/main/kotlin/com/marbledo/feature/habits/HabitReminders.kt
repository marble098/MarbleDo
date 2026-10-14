package com.marbledo.feature.habits

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomHabitRepository
import com.marbledo.domain.model.Habit
import com.marbledo.domain.model.HabitKind
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val CHANNEL_HABITS = "marbledo_habits"
private const val ACTION_REMIND = "com.marbledo.feature.habits.action.REMIND"
private const val ACTION_DONE = "com.marbledo.feature.habits.action.DONE"
private const val ACTION_SLIP = "com.marbledo.feature.habits.action.SLIP"
private const val EXTRA_HABIT_ID = "habitId"

/**
 * One quiet daily nudge per habit, at the minute the user chose. Alarms are one-shot and the
 * receiver re-arms the next day, so the schedule survives a reboot only because the app's boot
 * receiver asks this scheduler to run again.
 */
object HabitReminderScheduler {
    private val scheduledTriggers = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /** Re-arms every habit that has a time set and removes the ones that do not. */
    fun reschedule(context: Context, habits: List<Habit>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        habits.forEach { habit ->
            if (habit.isArchived || habit.reminderMinuteOfDay < 0) {
                if (scheduledTriggers.remove(habit.id) != -1L) {
                    scheduledTriggers[habit.id] = -1L
                    cancel(alarmManager, context, habit.id)
                }
            } else {
                val trigger = nextTriggerMillis(habit.reminderMinuteOfDay, now)
                if (scheduledTriggers[habit.id] != trigger) {
                    schedule(alarmManager, context, habit, trigger)
                    scheduledTriggers[habit.id] = trigger
                }
            }
        }
    }

    fun cancel(context: Context, habitId: Long) {
        scheduledTriggers.remove(habitId)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        cancel(alarmManager, context, habitId)
    }

    private fun schedule(
        alarmManager: AlarmManager,
        context: Context,
        habit: Habit,
        trigger: Long = nextTriggerMillis(habit.reminderMinuteOfDay),
    ) {
        val pending = pendingIntent(context, habit.id, ACTION_REMIND)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    private fun cancel(alarmManager: AlarmManager, context: Context, habitId: Long) {
        runCatching { alarmManager.cancel(pendingIntent(context, habitId, ACTION_REMIND)) }
    }

    /** The next moment the clock reads [minuteOfDay], always in the future. */
    fun nextTriggerMillis(minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val at = now
            .withHour((minuteOfDay / 60) % 24)
            .withMinute(minuteOfDay % 60)
            .withSecond(0)
            .withNano(0)
        return (if (at.isAfter(now)) at else at.plusDays(1)).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, habitId: Long, action: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        val requestCode = (habitId * 7 + action.hashCode()).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Posts the nudge and handles its two buttons without opening the app. */
class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId < 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> record(context, habitId, done = true)
                    ACTION_SLIP -> record(context, habitId, done = false)
                    else -> remind(context, habitId)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // The receiver is being torn down; nothing left to do.
            } catch (_: Exception) {
                // A failed habit nudge must never crash the app process.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun remind(context: Context, habitId: Long) {
        val snapshot = withDatabase(context) { repository -> repository.snapshot() } ?: return
        val habit = snapshot.habits.firstOrNull { it.id == habitId } ?: return
        if (habit.isArchived || habit.reminderMinuteOfDay < 0) return
        if (!canPostNotifications(context)) return
        createChannel(context)
        val title = context.getString(R.string.habit_notification_title, habit.name)
        val body = if (habit.kind == HabitKind.QUIT) {
            context.getString(R.string.habit_notification_body_quit)
        } else {
            context.getString(R.string.habit_notification_body_build)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (habit.kind == HabitKind.QUIT) {
            builder.addAction(0, context.getString(R.string.habit_notification_slipped), actionIntent(context, habitId, ACTION_SLIP))
            builder.addAction(0, context.getString(R.string.habit_notification_done), actionIntent(context, habitId, ACTION_DONE))
        } else {
            builder.addAction(0, context.getString(R.string.habit_notification_done), actionIntent(context, habitId, ACTION_DONE))
        }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(habitId), builder.build()) }
        // One-shot alarms have to re-arm themselves for tomorrow.
        HabitReminderScheduler.reschedule(context, listOf(habit))
    }

    private suspend fun record(context: Context, habitId: Long, done: Boolean) {
        val today = LocalDate.now().toString()
        val habit = withDatabase(context) { repository ->
            val snapshot = repository.snapshot()
            snapshot.habits.firstOrNull { it.id == habitId }?.let { found ->
                val value = if (found.kind == HabitKind.QUIT) {
                    if (done) 0 else 1
                } else {
                    if (done) found.dailyTarget else 0
                }
                repository.setEntry(found.id, today, value)
            }
            snapshot.habits.firstOrNull { it.id == habitId }
        }
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(habitId)) }
        updateMarbleHabitWidgets(context)
        if (habit != null) HabitReminderScheduler.reschedule(context, listOf(habit))
    }

    private suspend fun <T> withDatabase(context: Context, block: suspend (RoomHabitRepository) -> T): T? {
        val database = MarbleDatabase.create(context)
        return block(RoomHabitRepository(database.habitDao()))
    }

    private fun actionIntent(context: Context, habitId: Long, action: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            (habitId * 13 + action.hashCode()).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(habitId: Long): Int = (NOTIFICATION_BASE + habitId).toInt()

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_HABITS,
            context.getString(R.string.channel_habits),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_habits_description) }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        /** Keeps habit notifications clear of the other channels' ids. */
        const val NOTIFICATION_BASE = 7_300
    }
}
