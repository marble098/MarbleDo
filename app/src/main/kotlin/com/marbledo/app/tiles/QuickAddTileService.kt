package com.marble098.marbledo.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.SuppressLint
import com.marble098.marbledo.MainActivity
import com.marble098.marbledo.R

private const val REQUEST_CODE_QUICK_ADD = 1

class QuickAddTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.tile_quick_add)
            icon = Icon.createWithResource(this@QuickAddTileService, R.drawable.ic_stat_marble)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launch = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_ADD, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // TileService.startActivityAndCollapse(Intent) is deprecated in favour of the
        // PendingIntent overload, which only exists from API 34 on.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingLaunch = PendingIntent.getActivity(
                this,
                REQUEST_CODE_QUICK_ADD,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingLaunch)
        } else {
            collapseWithIntent(launch)
        }
    }

    // The Intent overload of startActivityAndCollapse is the only one available below API 34,
    // where the PendingIntent overload recommended by lint does not exist yet.
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun collapseWithIntent(launch: Intent) = startActivityAndCollapse(launch)
}
