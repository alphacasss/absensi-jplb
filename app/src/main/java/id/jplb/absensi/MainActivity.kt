package id.jplb.absensi

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusText: TextView
    private lateinit var browserText: TextView
    private lateinit var authDescText: TextView
    private lateinit var tvUpdateStatus: TextView
    private var pendingApk: File? = null

    private val installSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingApk?.let { file -> installApk(file) }
    }

    private val chromePackages = listOf(
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.android.chrome.beta",
        "com.android.chrome.dev"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        statusText = findViewById(R.id.statusText)
        browserText = findViewById(R.id.browserText)
        authDescText = findViewById(R.id.authDesc)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_check_update -> { checkForUpdate(false); true }
                R.id.action_change_url -> { showUrlDialog(); true }
                R.id.action_about -> { showAbout(); true }
                else -> false
            }
        }

        updateStatus()

        tvUpdateStatus.setOnClickListener { checkForUpdate(false) }
        // Tersembunyi untuk pengguna biasa; tahan lama teks versi untuk membuka.
        tvUpdateStatus.setOnLongClickListener {
            val prefs = getSharedPreferences("absensi_jplb", MODE_PRIVATE)
            val enabled = !prefs.getBoolean("dev_mode", false)
            prefs.edit().putBoolean("dev_mode", enabled).apply()
            applyDevMode()
            toast(if (enabled) "Mode pengembang aktif" else "Mode pengembang nonaktif")
            true
        }
        applyDevMode()
        checkForUpdate(true)

        findViewById<View>(R.id.btnOpen).setOnClickListener { openAbsensi() }
        findViewById<View>(R.id.btnBrowser).setOnClickListener {
            openExternal(AppConfig.getUrl(this))
        }
        findViewById<View>(R.id.btnWebView).setOnClickListener {
            startActivity(Intent(this, WebActivity::class.java))
        }
        findViewById<View>(R.id.btnMock).setOnClickListener {
            startActivity(Intent(this, MockLocationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        applyDevMode()
    }

    private fun applyDevMode() {
        val dev = getSharedPreferences("absensi_jplb", MODE_PRIVATE)
            .getBoolean("dev_mode", false)
        findViewById<View>(R.id.btnMock).visibility = if (dev) View.VISIBLE else View.GONE
        val tile = ComponentName(this, MockTileService::class.java)
        val state = if (dev)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(
            tile, state, PackageManager.DONT_KILL_APP
        )
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Chrome didahulukan agar otorisasi/localStorage Chrome tetap terpakai. */
    private fun customTabPackage(): String? {
        chromePackages.forEach { if (isInstalled(it)) return it }
        return CustomTabsClient.getPackageName(this, null)
    }

    private fun defaultBrowserPackage(): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        return try {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun isChrome(packageName: String?): Boolean {
        if (packageName == null) return false
        return chromePackages.contains(packageName)
    }

    private fun browserLabel(packageName: String?): String {
        if (packageName == null) return getString(R.string.browser_webview)
        if (isChrome(packageName)) return getString(R.string.browser_chrome)
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun updateStatus() {
        val pkg = customTabPackage() ?: defaultBrowserPackage()
        when {
            isChrome(pkg) -> {
                statusText.setText(R.string.status_connected)
                authDescText.setText(R.string.auth_desc_chrome)
            }
            pkg != null -> {
                statusText.setText(R.string.status_other_browser)
                authDescText.setText(R.string.auth_desc_other)
            }
            else -> {
                statusText.setText(R.string.status_no_browser)
                authDescText.setText(R.string.auth_desc_webview)
            }
        }
        browserText.text = browserLabel(pkg)
    }

    private fun openAbsensi() {
        val url = AppConfig.getUrl(this)
        val pkg = customTabPackage()

        if (pkg != null) {
            val drawable = AppCompatResources.getDrawable(this, R.drawable.ic_close)
            val closeIcon = if (drawable != null) {
                val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, bmp.width, bmp.height)
                drawable.draw(canvas)
                bmp
            } else null

            val builder = CustomTabsIntent.Builder()
                .setToolbarColor(ContextCompat.getColor(this, R.color.primary))
                .setSecondaryToolbarColor(ContextCompat.getColor(this, R.color.primary_dark))
                .setNavigationBarColor(ContextCompat.getColor(this, R.color.surface))
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_LIGHT)
                .setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left)
                .setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right)
            if (closeIcon != null) builder.setCloseButtonIcon(closeIcon)

            val tabs = builder.build()
            tabs.intent.setPackage(pkg)
            tabs.launchUrl(this, Uri.parse(url))
            return
        }

        // Tidak ada penyedia Custom Tabs: pakai browser standar apa pun.
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            startActivity(fallback)
        } catch (e: Exception) {
            startActivity(Intent(this, WebActivity::class.java))
        }
    }

    private fun openExternal(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        customTabPackage()?.let { intent.setPackage(it) }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Browser tidak tersedia")
        }
    }

    private fun checkForUpdate(auto: Boolean) {
        tvUpdateStatus.setText(R.string.update_checking)
        Thread {
            try {
                val info = UpdateChecker.fetchLatest()
                val newer = UpdateChecker.isNewer(info.tag, BuildConfig.VERSION_NAME)
                runOnUiThread {
                    if (newer) {
                        tvUpdateStatus.text = getString(R.string.update_available, info.tag)
                        if (!auto) showUpdateDialog(info)
                    } else {
                        tvUpdateStatus.text = getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                        if (!auto) toast("Sudah versi terbaru")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvUpdateStatus.setText(R.string.update_failed)
                    if (!auto) toast("Gagal mengecek update: ${e.message}")
                }
            }
        }.start()
    }

    private fun showUpdateDialog(info: UpdateChecker.ReleaseInfo) {
        val sizeMb = if (info.size > 0) "%.1f MB".format(info.size / 1024f / 1024f) else "-"
        val message = buildString {
            append(info.notes.ifBlank { "Pembaruan tersedia dari GitHub Release." })
            append("\n\nUkuran: ").append(sizeMb)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_title) + " " + info.tag)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ -> downloadUpdate(info) }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun downloadUpdate(info: UpdateChecker.ReleaseInfo) {
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            toast(getString(R.string.update_no_apk))
            return
        }
        toast(getString(R.string.update_downloading))
        Thread {
            try {
                val dir = File(getExternalFilesDir("apk"), "update").apply { mkdirs() }
                val file = File(dir, "app-release-${info.tag}.apk")
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                runOnUiThread {
                    pendingApk = file
                    startInstall(file)
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Gagal mengunduh: ${e.message}") }
            }
        }.start()
    }

    private fun startInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            toast(getString(R.string.update_install_permission))
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            installSettings.launch(intent)
            return
        }
        installApk(file)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Gagal membuka installer: ${e.message}")
        }
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply { setText(AppConfig.getUrl(this@MainActivity)); setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("URL Web App")
            .setMessage("Ubah alamat jika URL deployment Google Apps Script berubah.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val value = input.text.toString().trim()
                if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                    AppConfig.saveUrl(this, value)
                    toast("URL diperbarui")
                } else toast("URL harus diawali http:// atau https://")
            }
            .setNeutralButton("Reset") { _, _ -> AppConfig.saveUrl(this, null); toast("URL kembali ke bawaan") }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Aplikasi absensi Gudang JPLB. Jika Chrome tersedia, ID dan otorisasi perangkat memakai profil Chrome yang sama. Jika tidak, aplikasi memakai browser lain atau mode WebView.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
