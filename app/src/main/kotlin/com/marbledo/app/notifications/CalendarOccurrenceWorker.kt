package com.marble098.marbledo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.BidiFormatter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.domain.model.NumeralMode
import com.marbledo.domain.util.TextNormalizer
import com.marbledo.feature.calendar.Occasion
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.feature.calendar.PersianDateUtils
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CalendarOccurrenceWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (!settings.calendarNotificationsEnabled) return@withContext Result.success()
            val repository = OccasionRepository(applicationContext)
            repository.load()
            val today = PersianDateUtils.today()
            val enabledCategories = buildSet {
                if (settings.officialEventsEnabled) add(OccasionCategory.OFFICIAL)
                if (settings.nationalEventsEnabled) add(OccasionCategory.NATIONAL)
                if (settings.religiousEventsEnabled) add(OccasionCategory.RELIGIOUS)
                if (settings.personalEventsEnabled) add(OccasionCategory.PERSONAL)
                if (settings.internationalEventsEnabled) add(OccasionCategory.INTERNATIONAL)
            }
            val index = OccasionIndex.build(
                catalog = repository.state.value.catalog,
                jalaliYears = listOf(today.year),
                lunarOffsetDays = settings.lunarOffsetDays,
                enabledCategories = enabledCategories,
            )
            val events = index.on(today.year, today.month, today.day)
            if (events.isNotEmpty()) {
                val notificationContext = applicationContext.forLanguage(settings.languageTag)
                // Channel labels are updated in the same language used for this notification.
                NotificationChannels.create(notificationContext)
                notifyOccurrence(
                    context = notificationContext,
                    events = events,
                    year = today.year,
                    month = today.month,
                    day = today.day,
                    languageTag = settings.languageTag,
                    numeralMode = settings.numeralMode,
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyOccurrence(
        context: Context,
        events: List<Occasion>,
        year: Int,
        month: Int,
        day: Int,
        languageTag: String,
        numeralMode: NumeralMode,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val isRtl = languageTag == "fa"
        val bidi = BidiFormatter.getInstance(isRtl)
        val dayStart = PersianDateUtils.startOfJalaliDay(year, month, day) ?: System.currentTimeMillis()
        val dateLabel = PersianDateUtils.fullDateWithWeekday(
            epochMillis = dayStart,
            languageTag = languageTag,
            numeralMode = numeralMode,
        )
        val eventCount = context.resources.getQuantityString(
            R.plurals.calendar_notification_count,
            events.size,
            TextNormalizer.formatDigits(events.size.toString(), numeralMode),
        )
        val summary = context.getString(
            R.string.calendar_notification_summary,
            bidi.unicodeWrap(dateLabel),
            bidi.unicodeWrap(eventCount),
        )
        val eventTitles = events.map { bidi.unicodeWrap(it.title(languageTag)) }
        val title = context.getString(R.string.calendar_notification_title)
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(summary)
        eventTitles.take(MAX_VISIBLE_EVENTS).forEach { inbox.addLine(it) }
        val hiddenCount = eventTitles.size - MAX_VISIBLE_EVENTS
        if (hiddenCount > 0) {
            inbox.addLine(
                context.resources.getQuantityString(
                    R.plurals.calendar_notification_more,
                    hiddenCount,
                    TextNormalizer.formatDigits(hiddenCount.toString(), numeralMode),
                ),
            )
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("marbledo://calendar/$year/$month/$day")
            putExtra(MainActivity.EXTRA_DESTINATION, "calendar")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val requestCode = year * 10_000 + month * 100 + day
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CALENDAR)
            .setSmallIcon(R.drawable.ic_stat_calendar)
            .setLargeIcon(ContextCompat.getDrawable(context, R.drawable.ic_notification_calendar_large)?.toBitmap())
            .setColor(ContextCompat.getColor(context, R.color.marble_accent))
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(dateLabel)
            .setNumber(events.size)
            .setStyle(inbox)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_stat_calendar,
                context.getString(R.string.calendar_notification_open),
                pending,
            )
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    companion object {
        const val WORK_NAME = "marbledo-calendar-occasions"
        const val WORK_TAG = "marbledo-calendar"
        private const val MAX_VISIBLE_EVENTS = 5
    }
}

object CalendarNotificationWorkInitializer {
    fun ensureDaily(context: Context) {
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        val request = PeriodicWorkRequestBuilder<CalendarOccurrenceWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now, TimeUnit.MILLISECONDS)
            .addTag(CalendarOccurrenceWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            CalendarOccurrenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
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
