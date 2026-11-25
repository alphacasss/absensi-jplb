package id.jplb.absensi

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val API_URL =
        "https://api.github.com/repos/alphacasss/absensi-jplb/releases/latest"

    data class ReleaseInfo(
        val tag: String,
        val notes: String,
        val apkUrl: String?,
        val size: Long
    )

    fun fetchLatest(): ReleaseInfo {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "AbsensiJPLB-OTA")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(body)
        var apkUrl: String? = null
        var size = 0L
        obj.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name", "").endsWith(".apk", true)) {
                    apkUrl = asset.optString("browser_download_url")
                    size = asset.optLong("size")
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = obj.optString("tag_name", ""),
            notes = obj.optString("body", ""),
            apkUrl = apkUrl,
            size = size
        )
    }

    fun isNewer(release: String, current: String): Boolean {
        val r = numbers(release)
        val c = numbers(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun numbers(value: String): List<Int> =
        Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()
}
