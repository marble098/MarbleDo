package com.marbledo.core.data.backup

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.marbledo.domain.repository.TaskBackupScheduler
import java.util.concurrent.TimeUnit

class WorkManagerBackupScheduler(context: Context) : TaskBackupScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun scheduleDebounced() {
        val request = OneTimeWorkRequestBuilder<LocalBackupWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .addTag(AUTO_BACKUP_TAG)
            .build()
        workManager.enqueueUniqueWork(AUTO_BACKUP_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val AUTO_BACKUP_NAME = "marbledo-auto-backup"
        const val AUTO_BACKUP_TAG = "marbledo-backup"
        const val PERIODIC_BACKUP_NAME = "marbledo-periodic-backup"
    }
}
