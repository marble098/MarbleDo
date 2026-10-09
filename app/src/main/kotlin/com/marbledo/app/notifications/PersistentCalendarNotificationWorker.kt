package com.marble098.marbledo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.feature.calendar.OccasionRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Rebuilds the status-bar date icon. The hourly periodic run keeps it fresh, every normal run arms a one-time
 * refresh for the next local midnight so the day number changes on time, and resuming the app refreshes it too.
 * A midnight run never arms another midnight run; the next normal run does that.
 */
class PersistentCalendarNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (!settings.persistentDateNotificationEnabled) {
                PersistentCalendarNotification.cancel(applicationContext)
                WorkManager.getInstance(applicationContext).cancelUniqueWork(MIDNIGHT_WORK_NAME)
                return@withContext Result.success()
            }
            val database = MarbleDatabase.create(applicationContext)
            val tasks = try {
                RoomTaskRepository(database.taskDao()).snapshot()
            } finally {
                database.close()
            }
            val occasions = OccasionRepository(applicationContext).also { it.load() }.state.value.catalog
            PersistentCalendarNotification.update(applicationContext, settings, tasks, occasions)
            if (!inputData.getBoolean(KEY_MIDNIGHT_RUN, false)) {
                scheduleMidnightRefresh(applicationContext)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "marbledo-persistent-calendar-refresh"
        const val IMMEDIATE_WORK_NAME = "marbledo-persistent-calendar-refresh-now"
        const val MIDNIGHT_WORK_NAME = "marbledo-persistent-calendar-midnight"
        private const val KEY_MIDNIGHT_RUN = "midnight_run"

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PersistentCalendarNotificationWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PersistentCalendarNotificationWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Queues one refresh for a minute after the next local midnight, replacing any earlier midnight job. */
        fun scheduleMidnightRefresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val zone = ZoneId.systemDefault()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            val delayMillis = Duration.between(now, nextMidnight).toMillis() + 60_000L
            val request = OneTimeWorkRequestBuilder<PersistentCalendarNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putBoolean(KEY_MIDNIGHT_RUN, true).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                MIDNIGHT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
