package id.jplb.absensi

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusText: TextView
    private lateinit var browserText: TextView
    private lateinit var authDescText: TextView

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

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_change_url -> { showUrlDialog(); true }
                R.id.action_about -> { showAbout(); true }
                else -> false
            }
        }

        updateStatus()

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
