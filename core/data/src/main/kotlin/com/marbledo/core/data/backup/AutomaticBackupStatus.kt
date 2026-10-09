package com.marbledo.core.data.backup

import android.content.Context
import java.io.File

/** Reads the automatic backup folder written by [LocalBackupWorker], so Settings can show when the last backup ran. */
object AutomaticBackupStatus {
    /** Modification time of the newest automatic backup, or null when none exists yet. Performs file I/O. */
    fun latestBackupMillis(context: Context): Long? =
        File(context.filesDir, LocalBackupWorker.BACKUP_DIRECTORY)
            .listFiles { file -> file.name.endsWith(".mdo") }
            ?.maxOfOrNull(File::lastModified)
}
