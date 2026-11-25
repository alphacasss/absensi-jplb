package id.jplb.absensi

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MapPickerActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var etSearch: EditText
    private lateinit var listResults: ListView
    private var marker: Marker? = null
    private var selected: GeoPoint? = null
    private val results = mutableListOf<GeoPoint>()
    private val labels = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val favPrefs by lazy { getSharedPreferences("absensi_jplb", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "AbsensiJPLB/1.1 (Android)"
        setContentView(R.layout.activity_map_picker)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        map = findViewById(R.id.map)
        etSearch = findViewById(R.id.etSearch)
        listResults = findViewById(R.id.listResults)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
        map.controller.setZoom(16.0)

        val compass = CompassOverlay(this, map)
        compass.enableCompass()
        map.overlays.add(compass)

        val scaleBar = ScaleBarOverlay(map).apply {
            setCentred(true)
            setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 24)
        }
        map.overlays.add(scaleBar)
        val start = GeoPoint(
            intent.getDoubleExtra("lat", AppConfig.DEFAULT_MOCK_LAT),
            intent.getDoubleExtra("lng", AppConfig.DEFAULT_MOCK_LNG)
        )
        setPoint(start)

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) setPoint(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) setPoint(p)
                return true
            }
        }))

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        listResults.adapter = adapter
        listResults.setOnItemClickListener { _, _, position, _ ->
            if (position < results.size) {
                setPoint(results[position])
                map.controller.setCenter(results[position])
            }
        }

        findViewById<View>(R.id.btnSearch).setOnClickListener { searchPlace() }
        findViewById<View>(R.id.btnSaveFavorite).setOnClickListener { showSaveFavoriteDialog() }
        renderFavorites()
        findViewById<View>(R.id.btnUseLocation).setOnClickListener {
            val point = selected
            if (point == null) {
                Toast.makeText(this, "Pilih titik di peta dulu", Toast.LENGTH_SHORT).show()
            } else {
                val data = android.content.Intent().apply {
                    putExtra("lat", point.latitude)
                    putExtra("lng", point.longitude)
                }
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    private fun loadFavorites(): JSONArray {
        val raw = favPrefs.getString("map_favorites", null)
            ?: return JSONArray().put(
                JSONObject()
                    .put("name", AppConfig.DEFAULT_FAV_NAME)
                    .put("lat", AppConfig.DEFAULT_MOCK_LAT)
                    .put("lng", AppConfig.DEFAULT_MOCK_LNG)
            )
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun saveFavorites(array: JSONArray) {
        favPrefs.edit().putString("map_favorites", array.toString()).apply()
    }

    private fun renderFavorites() {
        val container = findViewById<LinearLayout>(R.id.favContainer)
        container.removeAllViews()
        val favorites = loadFavorites()
        for (i in 0 until favorites.length()) {
            val item = favorites.getJSONObject(i)
            val button = com.google.android.material.button.MaterialButton(this).apply {
                text = item.optString("name", "Lokasi ${i + 1}")
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 130
                ).apply { marginEnd = 16 }
                setOnClickListener {
                    setPoint(GeoPoint(item.optDouble("lat"), item.optDouble("lng")))
                }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MapPickerActivity)
                        .setTitle(text)
                        .setMessage("Hapus lokasi favorit ini?")
                        .setPositiveButton("Hapus") { _, _ ->
                            val current = loadFavorites()
                            current.remove(i)
                            saveFavorites(current)
                            renderFavorites()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                    true
                }
            }
            container.addView(button)
        }
    }

    private fun showSaveFavoriteDialog() {
        val point = selected
        if (point == null) {
            Toast.makeText(this, "Pilih titik dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.fav_name_hint); setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle(R.string.fav_save_title)
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val favorites = loadFavorites()
                val item = JSONObject().apply {
                    put("name", name)
                    put("lat", point.latitude)
                    put("lng", point.longitude)
                }
                favorites.put(item)
                saveFavorites(favorites)
                renderFavorites()
                Toast.makeText(this, "Tersimpan: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setPoint(point: GeoPoint) {
        selected = point
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = point
            title = getString(R.string.map_selected)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        map.controller.setCenter(point)
        map.invalidate()
    }

    private fun searchPlace() {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) {
            Toast.makeText(this, "Masukkan nama tempat", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try {
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&q=" +
                        URLEncoder.encode(query, "UTF-8")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "AbsensiJPLB/1.1 (Android)")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                val foundLabels = mutableListOf<String>()
                val foundPoints = mutableListOf<GeoPoint>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    foundPoints.add(GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")))
                    foundLabels.add(obj.getString("display_name"))
                }
                runOnUiThread {
                    results.clear(); results.addAll(foundPoints)
                    labels.clear(); labels.addAll(foundLabels)
                    adapter.notifyDataSetChanged()
                    if (foundPoints.isNotEmpty()) {
                        setPoint(foundPoints[0])
                        Toast.makeText(this, "${foundPoints.size} hasil ditemukan", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Tempat tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Pencarian gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }
}
