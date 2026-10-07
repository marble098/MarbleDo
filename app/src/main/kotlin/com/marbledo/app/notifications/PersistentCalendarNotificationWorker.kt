package com.marble098.marbledo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.feature.calendar.OccasionRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Rebuilds the daily glance after a reboot or while MarbleDo is not open. */
class PersistentCalendarNotificationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (!settings.persistentDateNotificationEnabled) {
                PersistentCalendarNotification.cancel(applicationContext)
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

        fun ensurePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PersistentCalendarNotificationWorker>(1, TimeUnit.HOURS)
                .build()
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
    }
}
