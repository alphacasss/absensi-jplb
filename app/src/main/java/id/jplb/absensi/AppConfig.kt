package id.jplb.absensi

import android.content.Context

object AppConfig {
    const val DEFAULT_URL =
        "https://script.google.com/macros/s/AKfycbwRz_d-BelbTHUAe_195XoNvUr6YoN4TWMt5f9YJp9I8BHM0Z0frb0ydmSsbvDSeKWF/exec"
    private const val PREF_NAME = "absensi_jplb"
    const val PREF_URL = "app_url"

    const val DEFAULT_MOCK_LAT = -8.633967
    const val DEFAULT_MOCK_LNG = 116.440739
    const val DEFAULT_FAV_NAME = "Lokasi Default"

    fun getUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    /** Migrasi sekali: pasang koordinat default dan favorit bawaan. */
    fun ensureMockDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt("mock_defaults_version", 0) >= 1) return

        val favorites = try {
            org.json.JSONArray(prefs.getString("map_favorites", "[]"))
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        var exists = false
        for (i in 0 until favorites.length()) {
            if (favorites.getJSONObject(i).optString("name") == DEFAULT_FAV_NAME) {
                exists = true
                break
            }
        }
        if (!exists) {
            favorites.put(
                org.json.JSONObject()
                    .put("name", DEFAULT_FAV_NAME)
                    .put("lat", DEFAULT_MOCK_LAT)
                    .put("lng", DEFAULT_MOCK_LNG)
            )
        }
        prefs.edit()
            .putString("mock_lat", DEFAULT_MOCK_LAT.toString())
            .putString("mock_lng", DEFAULT_MOCK_LNG.toString())
            .putString("map_favorites", favorites.toString())
            .putInt("mock_defaults_version", 1)
            .apply()
    }

    fun saveUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (url == null) prefs.edit().remove(PREF_URL).apply()
        else prefs.edit().putString(PREF_URL, url).apply()
    }
}
