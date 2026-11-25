package id.jplb.absensi

import android.content.Context

object AppConfig {
    const val DEFAULT_URL =
        "https://script.google.com/macros/s/AKfycbwRz_d-BelbTHUAe_195XoNvUr6YoN4TWMt5f9YJp9I8BHM0Z0frb0ydmSsbvDSeKWF/exec"
    private const val PREF_NAME = "absensi_jplb"
    const val PREF_URL = "app_url"

    fun getUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun saveUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (url == null) prefs.edit().remove(PREF_URL).apply()
        else prefs.edit().putString(PREF_URL, url).apply()
    }
}
