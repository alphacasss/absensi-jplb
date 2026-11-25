package id.jplb.absensi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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

/** Foreground service yang menyuntikkan lokasi uji ke provider GPS sistem. */
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
                startForeground(NOTIFICATION_ID, buildNotification())
                startMock()
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
                addTestProvider()
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                while (running) {
                    val loc = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = lat
                        longitude = lng
                        accuracy = 5f
                        altitude = 12.5
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
                    Thread.sleep(200)
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "Mock location ditolak", se)
                notifyOnMain("Mock location gagal. Pilih aplikasi ini di Developer options → Mock location app.")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e(TAG, "Mock location error", e)
            } finally {
                removeProviderSafe()
                running = false
                isRunning = false
            }
        }.also { it.start() }
    }

    private fun stopMock() {
        running = false
        mockThread?.interrupt()
        mockThread = null
        removeProviderSafe()
        isRunning = false
    }

    @SuppressLint("MissingPermission")
    private fun addTestProvider() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val props = ProviderProperties.Builder()
                .setPowerUsage(ProviderProperties.POWER_USAGE_HIGH)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .setHasNetworkRequirement(true)
                .build()
            locationManager.addTestProvider(LocationManager.GPS_PROVIDER, props)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                true, false, false, false, true, true, true,
                Criteria.POWER_HIGH, Criteria.ACCURACY_FINE
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeProviderSafe() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.d(TAG, "removeTestProvider: ${e.message}")
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
            .setOngoing(true)
            .build()
    }
}
