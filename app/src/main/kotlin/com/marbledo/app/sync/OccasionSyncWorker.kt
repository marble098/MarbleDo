package com.marble098.marbledo.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marbledo.core.data.settings.SettingsRepository
import com.marbledo.feature.calendar.OccasionRefreshResult
import com.marbledo.feature.calendar.OccasionRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Periodically refreshes the occasion catalog from the internet. The bundled asset stays the
 * offline baseline, so a failed run never removes occasions from the calendar.
 */
class OccasionSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = runCatching { SettingsRepository(applicationContext).settings.first() }.getOrNull()
        if (settings != null && !settings.occasionAutoUpdateEnabled) return@withContext Result.success()
        val repository = OccasionRepository(applicationContext)
        when (repository.refreshIfStale()) {
            OccasionRefreshResult.FAILED -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "marbledo-occasion-sync"
        const val WORK_TAG = "marbledo-occasions"

        fun ensureDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<OccasionSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
