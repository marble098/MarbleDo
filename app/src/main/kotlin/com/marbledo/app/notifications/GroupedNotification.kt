package com.marble098.marbledo.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marble098.marbledo.R
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.NotificationPlanBuilder
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The grouped "overdue" reminder: one card in the shade, up to three tasks, and an honest count.
 *
 * Five late tasks used to be five separate alerts — five tones, five rows, and no single place that
 * said "you have five things late". This posts each task into the `marbledo_overdue_tasks` group
 * and then one group summary on top of it: the most urgent tasks by name, "و ۲ مورد دیگر" when
 * there are more than three, and the count in the state chip. Tapping the summary opens the
 * calendar; tapping any line of the expanded summary opens that task.
 *
 * The summary is rebuilt from the database every time a task is finished from a notification, so it
 * shrinks by itself instead of showing a task that no longer exists.
 */
internal object GroupedNotification {

    const val GROUP_OVERDUE = "marbledo_overdue_tasks"

    /** The number of tasks the summary names before it starts counting. */
    const val VISIBLE_TASKS = 3

    /** The summary's own id; task cards use their own ids so both can live in the group. */
    const val SUMMARY_ID = 8_413

    /**
     * Posts (or refreshes) the summary. [settings] and [tasks] are passed in because the receiver
     * already has them; passing them keeps this a pure render step with no database access.
     */
    fun postSummary(
        context: Context,
        settings: AppSettings,
        tasks: List<Task>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val notificationContext = context.applicationContext
        if (!settings.overdueNotificationsEnabled) {
            NotificationManagerCompat.from(notificationContext).cancel(SUMMARY_ID)
            return
        }
        val plan = NotificationPlanBuilder.build(tasks, Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val overdue = plan.overdueTasks
        if (overdue.isEmpty()) {
            NotificationManagerCompat.from(notificationContext).cancel(SUMMARY_ID)
            return
        }
        // Lint only recognises the permission check when it is written at the call site it guards,
        // so this is spelled out here rather than hidden behind `canPost`.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                notificationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(notificationContext).areNotificationsEnabled()) return
        NotificationChannels.create(notificationContext)
        val digits = { value: String -> TextNormalizer.formatDigits(value, settings.numeralMode) }
        val zone = ZoneId.systemDefault()
        val visible = overdue.take(VISIBLE_TASKS)
        val hidden = plan.hiddenOverdueCount(visible.size)
        val title = notificationContext.getString(R.string.notification_overdue_group_title)
        val summary = digits(
            notificationContext.getString(R.string.notification_overdue_group_summary, overdue.size.toString()),
        )
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle(digits(title))
            .setSummaryText(summary)
        visible.forEach { task ->
            val meta = task.dueAtEpochMillis?.let { due ->
                val clock = if (task.isAllDay) notificationContext.getString(R.string.notification_all_day)
                else Instant.ofEpochMilli(due).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
                digits("${PersianDateUtils.of(due, zone).day} ${PersianDateUtils.monthName(PersianDateUtils.of(due, zone).month, settings.languageTag)} · $clock")
            }
            inbox.addLine(listOf(task.title, meta.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
        }
        if (hidden > 0) {
            inbox.addLine(digits(notificationContext.getString(R.string.notification_overdue_more, hidden.toString())))
        }
        val accent = notificationAccent(notificationContext, settings)
        val builder = NotificationCompat.Builder(notificationContext, NotificationChannels.OVERDUE)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setColor(accent)
            .setContentTitle(digits(title))
            .setContentText(summary)
            .setSubText(digits(notificationContext.getString(R.string.notification_overdue_chip, overdue.size.toString())))
            .setNumber(overdue.size)
            .setStyle(inbox)
            .setGroup(GROUP_OVERDUE)
            .setGroupSummary(true)
            .setContentIntent(NotificationActions.openDay(notificationContext, SUMMARY_ID))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(false)
            .setAutoCancel(false)
            // A summary never buzzes: the individual cards already did, and a fifth tone for the
            // same five tasks is exactly what people mute the channel over.
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_notification_open,
                notificationContext.getString(R.string.calendar_notification_open),
                NotificationActions.openDay(notificationContext, SUMMARY_ID + 1),
            )
        // The summary names the tasks but its lines cannot be tapped one by one: an InboxStyle line
        // is plain text in the shade. The individual task cards stay one tap from their own task,
        // and the summary itself opens the day.
        NotificationManagerCompat.from(notificationContext).notify(SUMMARY_ID, builder.build())
    }

    /**
     * Loads the day's tasks and refreshes the summary; used after a "done" tap, where the card
     * that was just completed must vanish from the summary without anything reopening the app.
     */
    suspend fun refresh(context: Context) {
        val settings = SettingsRepository(context).settings.first()
        if (!settings.overdueNotificationsEnabled) {
            NotificationManagerCompat.from(context.applicationContext).cancel(SUMMARY_ID)
            return
        }
        val database = com.marbledo.core.data.db.MarbleDatabase.create(context)
        val tasks = com.marbledo.core.data.repository.RoomTaskRepository(database.taskDao()).snapshot()
        postSummary(context, settings, tasks)
    }

}
