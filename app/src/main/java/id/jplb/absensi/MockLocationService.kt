package id.jplb.absensi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Menyuntikkan lokasi uji ke provider GPS dan Network.
 * Pendekatan mengikuti FakeTraveler / Android-Mock-Location (GPL).
 */
class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "id.jplb.absensi.START_MOCK"
        const val ACTION_STOP = "id.jplb.absensi.STOP_MOCK"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 91
        private const val TAG = "MockLocationService"
        var isRunning = false
            private set
    }

    private lateinit var locationManager: LocationManager
    private var mockThread: Thread? = null
    private var running = false
    private var lat = 0.0
    private var lng = 0.0

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lat = intent.getDoubleExtra(EXTRA_LAT, lat)
                lng = intent.getDoubleExtra(EXTRA_LNG, lng)
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startMock()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Gagal start foreground", e)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopMock()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMock()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startMock() {
        if (running) return
        running = true
        isRunning = true
        mockThread = Thread {
            try {
                addTestProvider(LocationManager.GPS_PROVIDER, fine = true)
                addTestProvider(LocationManager.NETWORK_PROVIDER, fine = false)
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

                while (running) {
                    val now = System.currentTimeMillis()
                    val elapsed = SystemClock.elapsedRealtimeNanos()
                    val gpsLoc = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 5f
                        altitude = 12.5
                        time = now
                        elapsedRealtimeNanos = elapsed
                    }
                    val netLoc = Location(LocationManager.NETWORK_PROVIDER).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 5f
                        time = now
                        elapsedRealtimeNanos = elapsed
                    }
                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLoc)
                    try {
                        locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, netLoc)
                    } catch (e: Exception) {
                        Log.d(TAG, "network provider: ${e.message}")
                    }
                    Thread.sleep(500)
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "Mock location ditolak", se)
                notifyOnMain("Lokasi virtual gagal. Pilih aplikasi ini di Developer options → Mock location app.")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e(TAG, "Mock location error", e)
            } finally {
                removeProviderSafe(LocationManager.GPS_PROVIDER)
                removeProviderSafe(LocationManager.NETWORK_PROVIDER)
                running = false
                isRunning = false
                MockTileService.requestUpdate(this@MockLocationService)
            }
        }.also { it.start() }
    }

    private fun stopMock() {
        running = false
        mockThread?.interrupt()
        mockThread = null
        removeProviderSafe(LocationManager.GPS_PROVIDER)
        removeProviderSafe(LocationManager.NETWORK_PROVIDER)
        isRunning = false
        MockTileService.requestUpdate(this)
    }

    @SuppressLint("MissingPermission")
    private fun addTestProvider(provider: String, fine: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val props = ProviderProperties.Builder()
                .setPowerUsage(if (fine) ProviderProperties.POWER_USAGE_HIGH else ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(if (fine) ProviderProperties.ACCURACY_FINE else ProviderProperties.ACCURACY_COARSE)
                .setHasNetworkRequirement(true)
                .build()
            locationManager.addTestProvider(provider, props)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                provider,
                true, false, false, false, fine, fine, fine,
                if (fine) Criteria.POWER_HIGH else Criteria.POWER_LOW,
                if (fine) Criteria.ACCURACY_FINE else Criteria.ACCURACY_COARSE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeProviderSafe(provider: String) {
        try {
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            Log.d(TAG, "removeTestProvider($provider): ${e.message}")
        }
    }

    private fun notifyOnMain(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Mock Location", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MockLocationActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_home)
            .setContentTitle(getString(R.string.mock_notif_title))
            .setContentText("$lat, $lng")
            .setContentIntent(pi)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.notif_stop),
                PendingIntent.getService(
                    this, 2,
                    Intent(this, MockLocationService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .build()
    }
}
