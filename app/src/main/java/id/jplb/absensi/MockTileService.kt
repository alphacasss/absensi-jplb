package id.jplb.absensi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/** Quick Settings tile untuk menyalakan/mematikan lokasi virtual. */
class MockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (MockLocationService.isRunning) {
            stopService(Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_STOP
            })
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                updateTile(unavailable = true)
                return
            }
            val prefs = getSharedPreferences("absensi_jplb", Context.MODE_PRIVATE)
            val intent = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START
                putExtra(
                    MockLocationService.EXTRA_LAT,
                    prefs.getString("mock_lat", "-8.55")?.toDoubleOrNull() ?: -8.55
                )
                putExtra(
                    MockLocationService.EXTRA_LNG,
                    prefs.getString("mock_lng", "116.55")?.toDoubleOrNull() ?: 116.55
                )
            }
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                updateTile(unavailable = true)
                return
            }
        }
        updateTile()
    }

    private fun updateTile(unavailable: Boolean = false) {
        val tile = qsTile ?: return
        when {
            unavailable -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.tile_permission)
            }
            MockLocationService.isRunning -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_on)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_off)
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(
                    context,
                    ComponentName(context, MockTileService::class.java)
                )
            }
        }
    }
}
