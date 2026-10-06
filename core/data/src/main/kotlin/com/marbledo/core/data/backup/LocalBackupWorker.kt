package com.marbledo.core.data.backup

import android.content.Context
import android.util.AtomicFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import com.marbledo.core.data.settings.SettingsRepository
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocalBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val db = MarbleDatabase.create(applicationContext)
            try {
                val tasks = RoomTaskRepository(db.taskDao()).snapshot()
                val settings = SettingsRepository(applicationContext).settings.first()
                val bytes = BackupCodec.encode(tasks, settings)
                val targetDir = File(applicationContext.filesDir, BACKUP_DIRECTORY)
                    .apply { if (!exists() && !mkdirs()) error("Cannot create backup folder") }
                val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC).format(Instant.now())
                val target = File(targetDir, "marbledo-$timestamp.mdo")
                val atomic = AtomicFile(target)
                var output: java.io.FileOutputStream? = null
                try {
                    output = atomic.startWrite()
                    output.write(bytes)
                    atomic.finishWrite(output)
                    output = null
                } catch (exception: Exception) {
                    output?.let(atomic::failWrite)
                    throw exception
                }
                targetDir.listFiles()?.filter { it.name.endsWith(".mdo") }
                    ?.sortedByDescending(File::lastModified)
                    ?.drop(MAX_BACKUPS)
                    ?.forEach(File::delete)
            } finally {
                db.close()
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val BACKUP_DIRECTORY = "automatic-backups"
        const val MAX_BACKUPS = 7
    }
}
