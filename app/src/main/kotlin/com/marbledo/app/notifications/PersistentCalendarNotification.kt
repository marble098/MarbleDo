package com.marble098.marbledo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.BidiFormatter
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.domain.model.AppSettings
import com.marbledo.domain.model.Task
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionCatalog
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.PersianDateUtils
import java.time.ZoneId
import java.util.Locale

/** Builds the quiet, user-enabled daily date and next-task notification. */
object PersistentCalendarNotification {
    const val NOTIFICATION_ID = 8_412

    fun update(
        context: Context,
        settings: AppSettings,
        tasks: List<Task>,
        catalog: OccasionCatalog,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!settings.persistentDateNotificationEnabled) {
            cancel(context)
            return
        }
        if (!canPostNotifications(context)) {
            cancel(context)
            return
        }

        val notificationContext = context.applicationContext.forLanguage(settings.languageTag)
        NotificationChannels.create(notificationContext)
        val zone = ZoneId.systemDefault()
        val today = PersianDateUtils.today(zone, nowMillis)
        val enabledCategories = buildSet {
            if (settings.officialEventsEnabled) add(OccasionCategory.OFFICIAL)
            if (settings.nationalEventsEnabled) add(OccasionCategory.NATIONAL)
            if (settings.religiousEventsEnabled) add(OccasionCategory.RELIGIOUS)
            if (settings.personalEventsEnabled) add(OccasionCategory.PERSONAL)
        }
        val index = OccasionIndex.build(
            catalog = catalog,
            jalaliYears = listOf(today.year),
            lunarOffsetDays = settings.lunarOffsetDays,
            enabledCategories = enabledCategories,
            zone = zone,
        )
        val events = index.on(today.year, today.month, today.day)
        val locale = notificationContext.resources.configuration.locales[0]
        val dateText = PersianDateUtils.tripleDate(
            epochMillis = nowMillis,
            languageTag = settings.languageTag,
            numeralMode = settings.numeralMode,
            lunarOffsetDays = settings.lunarOffsetDays,
            locale = locale,
            zone = zone,
        )
        val bidi = BidiFormatter.getInstance(settings.languageTag == "fa")
        val visibleEvents = events.take(MAX_VISIBLE_EVENTS).joinToString(" · ") { bidi.unicodeWrap(it.title(settings.languageTag)) }
        val hiddenEvents = events.size - MAX_VISIBLE_EVENTS
        val occasionSummary = when {
            events.isEmpty() -> notificationContext.getString(R.string.persistent_date_notification_no_occasion)
            hiddenEvents > 0 -> notificationContext.getString(
                R.string.persistent_date_notification_more_events,
                visibleEvents,
                TextNormalizer.formatDigits(hiddenEvents.toString(), settings.numeralMode),
            )
            else -> visibleEvents
        }
        val nextTask = nextDatedTask(tasks, nowMillis)
        val nextTaskSummary = nextTask?.let { task ->
            val dueText = task.dueAtEpochMillis?.let {
                PersianDateUtils.fullDate(it, settings.languageTag, settings.numeralMode, zone)
            }
            listOfNotNull(bidi.unicodeWrap(task.title), dueText).joinToString(" · ")
        } ?: notificationContext.getString(R.string.persistent_date_notification_no_task)
        val title = notificationContext.getString(R.string.persistent_date_notification_title)
        val occasionLine = notificationContext.getString(R.string.persistent_date_notification_occasion_label, occasionSummary)
        val taskLine = notificationContext.getString(R.string.persistent_date_notification_next_task_label, nextTaskSummary)
        val expandedText = listOf(dateText, occasionLine, taskLine).joinToString("\n")

        val contentIntent = PendingIntent.getActivity(
            notificationContext,
            NOTIFICATION_ID,
            Intent(notificationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("marbledo://calendar/today")
                putExtra(MainActivity.EXTRA_DESTINATION, "calendar")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(notificationContext, NotificationChannels.PERSISTENT_CALENDAR)
            .setSmallIcon(R.drawable.ic_stat_calendar)
            .setLargeIcon(ContextCompat.getDrawable(notificationContext, R.drawable.ic_notification_calendar_large)?.toBitmap())
            .setColor(ContextCompat.getColor(notificationContext, R.color.marble_accent))
            .setContentTitle(title)
            .setContentText(dateText)
            .setSubText(notificationContext.getString(R.string.persistent_date_notification_subtext))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setBigContentTitle(title)
                    .setSummaryText(taskLine),
            )
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                R.drawable.ic_stat_calendar,
                notificationContext.getString(R.string.calendar_notification_open),
                contentIntent,
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(notificationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID) }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun nextDatedTask(tasks: List<Task>, nowMillis: Long): Task? {
        val active = tasks.filter { !it.isCompleted && !it.isArchived && it.dueAtEpochMillis != null }
        return active.asSequence()
            .filter { (it.dueAtEpochMillis ?: Long.MIN_VALUE) >= nowMillis }
            .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
            ?: active.maxByOrNull { it.dueAtEpochMillis ?: Long.MIN_VALUE }
    }

    private fun Context.forLanguage(languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(resources.configuration).apply {
            setLocale(locale)
            @Suppress("DEPRECATION")
            setLayoutDirection(locale)
        }
        return createConfigurationContext(configuration)
    }

    private const val MAX_VISIBLE_EVENTS = 3
}
