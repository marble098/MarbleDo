package com.marbledo.core.data.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupWorkInitializer {
    fun ensureDailyBackup(context: Context) {
        val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(1, TimeUnit.DAYS)
            .addTag(WorkManagerBackupScheduler.AUTO_BACKUP_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WorkManagerBackupScheduler.PERIODIC_BACKUP_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
