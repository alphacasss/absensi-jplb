package id.jplb.absensi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar

class MockLocationActivity : AppCompatActivity() {

    private lateinit var etLat: EditText
    private lateinit var etLng: EditText
    private lateinit var tvStatus: TextView
    private val prefs by lazy { getSharedPreferences("absensi_jplb", Context.MODE_PRIVATE) }

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        etLat = findViewById(R.id.etLat)
        etLng = findViewById(R.id.etLng)
        tvStatus = findViewById(R.id.tvMockStatus)

        etLat.setText(prefs.getString("mock_lat", "-8.5500"))
        etLng.setText(prefs.getString("mock_lng", "116.5500"))

        findViewById<View>(R.id.btnStartMock).setOnClickListener { startMock() }
        findViewById<View>(R.id.btnStopMock).setOnClickListener { stopMock() }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        tvStatus.setText(if (MockLocationService.isRunning) R.string.mock_status_on else R.string.mock_status_off)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissions() {
        if (!hasLocationPermission()) {
            requestLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMock() {
        val lat = etLat.text.toString().trim().toDoubleOrNull()
        val lng = etLng.text.toString().trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "Latitude -90..90, longitude -180..180", Toast.LENGTH_LONG).show()
            return
        }
        ensurePermissions()
        prefs.edit().putString("mock_lat", lat.toString()).putString("mock_lng", lng.toString()).apply()
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
        }
        ContextCompat.startForegroundService(this, intent)
        tvStatus.setText(R.string.mock_status_on)
    }

    private fun stopMock() {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        startService(intent)
        tvStatus.setText(R.string.mock_status_off)
    }
}
