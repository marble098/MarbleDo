package com.marble098.marbledo.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R
import com.marbledo.core.data.db.MarbleDatabase
import com.marbledo.core.data.repository.RoomTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REQUEST_CODE_NEXT_TASK = 2

/**
 * A Quick Settings tile that opens the single most urgent open task: the soonest due one that
 * has not been completed or archived. The lookup happens on a background thread because the
 * Room database refuses to be queried from the main thread.
 */
class NextTaskTileService : TileService() {
    // The tile lives and dies with the Quick Settings panel, so the short-lived MainScope is
    // the right owner for the single lookup it performs.
    private val scope = MainScope()

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val taskId = nextTaskId()
            if (taskId > 0L) {
                launchAppFor(taskId)
            }
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        scope.launch {
            val hasOpenTask = nextTaskId() > 0L
            tile.state = if (hasOpenTask) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_next_task)
            tile.icon = Icon.createWithResource(this@NextTaskTileService, R.drawable.ic_stat_calendar_tasks)
            tile.updateTile()
        }
    }

    private suspend fun nextTaskId(): Long = withContext(Dispatchers.IO) {
        runCatching {
            MarbleDatabase.create(applicationContext).let { db ->
                try {
                    val now = System.currentTimeMillis()
                    RoomTaskRepository(db.taskDao())
                        .snapshot()
                        .filter { task ->
                            !task.isCompleted &&
                                !task.isArchived &&
                                (task.dueAtEpochMillis ?: Long.MIN_VALUE) >= now
                        }
                        .minByOrNull { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                        ?.id
                        ?: 0L
                } finally {
                    db.close()
                }
            }
        }.getOrDefault(0L)
    }

    private fun launchAppFor(taskId: Long) {
        val launch = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // TileService.startActivityAndCollapse(Intent) is deprecated in favour of the
        // PendingIntent overload, which only exists from API 34 on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingLaunch = PendingIntent.getActivity(
                this,
                REQUEST_CODE_NEXT_TASK,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingLaunch)
        } else {
            collapseWithIntent(launch)
        }
    }

    // The Intent overload of startActivityAndCollapse is the only one available below API 34,
    // where the PendingIntent overload preferred by lint does not exist yet. The corresponding
    // lint check is downgraded to a warning in app/lint.xml for that reason.
    @Suppress("DEPRECATION")
    private fun collapseWithIntent(launch: Intent) = startActivityAndCollapse(launch)
}
