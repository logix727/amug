package dev.logix.amug

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class MugTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val snapshot = MugSnapshotStore(this).read()
        qsTile?.apply {
            state = when {
                snapshot.address == null -> Tile.STATE_UNAVAILABLE
                snapshot.connected -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            label = snapshot.name ?: "AMUG"
            subtitle = when (state) {
                Tile.STATE_ACTIVE -> snapshot.currentC?.let { "${snapshot.unit.display(it).toInt()}${snapshot.unit.symbol}" } ?: "Connected"
                Tile.STATE_INACTIVE -> "Tap to connect"
                else -> "No remembered mug"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val snapshot = MugSnapshotStore(this).read()
        if (snapshot.address == null) return openApp()
        unlockAndRun {
            val intent = Intent(this, MugConnectionService::class.java).setAction(
                if (snapshot.connected) MugConnectionService.ACTION_DISCONNECT else MugConnectionService.ACTION_CONNECT_LAST,
            )
            try {
                if (snapshot.connected) startService(intent) else ContextCompat.startForegroundService(this, intent)
            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                openApp()
            } catch (_: SecurityException) {
                openApp()
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else openAppLegacy(intent)
    }

    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    private fun openAppLegacy(intent: Intent) = startActivityAndCollapse(intent)
}
