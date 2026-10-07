package com.marble098.marbledo.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.feature.calendar.Occasion
import com.marbledo.feature.calendar.OccasionCategory
import com.marbledo.feature.calendar.OccasionIndex
import com.marbledo.feature.calendar.OccasionRepository
import com.marbledo.feature.calendar.PersianDateUtils
import java.util.Calendar
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
            }
            val index = OccasionIndex.build(
                catalog = repository.state.value.catalog,
                jalaliYears = listOf(today.year),
                lunarOffsetDays = settings.lunarOffsetDays,
                enabledCategories = enabledCategories,
            )
            val events = index.on(today.year, today.month, today.day)
            if (events.isNotEmpty()) {
                notifyOccurrence(applicationContext, events, today.year, today.month, today.day, settings.languageTag == "fa")
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
        persian: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val titles = events.joinToString("، ") { event -> event.title(if (persian) "fa" else "en") }
        val content = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("marbledo://calendar/$year/$month/$day")
            putExtra(MainActivity.EXTRA_DESTINATION, "calendar")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            year * 10_000 + month * 100 + day,
            content,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CALENDAR)
            .setSmallIcon(R.drawable.ic_stat_marble)
            .setColor(ContextCompat.getColor(context, R.color.marble_accent))
            .setContentTitle(titles)
            .setContentText(context.getString(R.string.calendar_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles))
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(year * 10_000 + month * 100 + day, notification)
    }

    companion object {
        const val WORK_NAME = "marbledo-calendar-occasions"
        const val WORK_TAG = "marbledo-calendar"
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
