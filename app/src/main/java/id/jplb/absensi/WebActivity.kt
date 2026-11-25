package id.jplb.absensi

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar

class WebActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_URL =
            "https://script.google.com/macros/s/AKfycbwRz_d-BelbTHUAe_195XoNvUr6YoN4TWMt5f9YJp9I8BHM0Z0frb0ydmSsbvDSeKWF/exec"
        private const val PREF_NAME = "absensi_jplb"
        private const val PREF_URL = "app_url"
    }

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private lateinit var errorView: View

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var lastBackPress = 0L

    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private fun currentUrl(): String = prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val origin = pendingGeoOrigin
            if (origin != null) pendingGeoCallback?.invoke(origin, granted, false)
            pendingGeoOrigin = null
            pendingGeoCallback = null
            if (!granted) {
                toast("Izin lokasi ditolak. Absensi GPS tidak akan bekerja.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_web)

        webView = findViewById(R.id.webView)
        swipe = findViewById(R.id.swipeRefresh)
        progress = findViewById(R.id.progressBar)
        toolbar = findViewById(R.id.toolbar)
        errorView = findViewById(R.id.errorView)

        setupToolbar()
        setupWebView()
        setupBackHandler()

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            updateTitle(currentUrl())
        } else {
            loadApp()
        }
    }

    private fun setupToolbar() {
        toolbar.title = getString(R.string.app_name)
        toolbar.setNavigationOnClickListener { loadApp() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reload -> {
                    webView.reload()
                    true
                }

                R.id.action_browser -> {
                    openInBrowser(currentUrl())
                    true
                }

                R.id.action_change_url -> {
                    showUrlDialog()
                    true
                }

                R.id.action_share -> {
                    shareUrl()
                    true
                }

                R.id.action_about -> {
                    showAbout()
                    true
                }

                else -> false
            }
        }

        findViewById<View>(R.id.btnRetry).setOnClickListener { loadApp() }
        findViewById<View>(R.id.navBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else toast("Sudah di halaman awal")
        }
        findViewById<View>(R.id.navForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward() else toast("Tidak ada halaman depan")
        }
        findViewById<View>(R.id.navHome).setOnClickListener { loadApp() }
        findViewById<View>(R.id.navReload).setOnClickListener { webView.reload() }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString AbsensiJPLB/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleExternalUrl(url)
            }

            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleExternalUrl(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                errorView.visibility = View.GONE
                progress.visibility = View.VISIBLE
                progress.progress = 0
                updateTitle(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                swipe.isRefreshing = false
                updateTitle(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showError(error?.description?.toString() ?: "Halaman tidak dapat dimuat")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100) {
                    progress.visibility = View.GONE
                    swipe.isRefreshing = false
                } else {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) toolbar.subtitle = title
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                if (ContextCompat.checkSelfPermission(
                        this@WebActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@WebActivity)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@WebActivity)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@WebActivity).apply { setText(defaultValue ?: "") }
                AlertDialog.Builder(this@WebActivity)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Batal") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = webView
                resultMsg.sendToTarget()
                return true
            }
        }

        swipe.setOnRefreshListener { webView.reload() }
        swipe.setColorSchemeResources(R.color.primary, R.color.accent)
    }

    private fun handleExternalUrl(url: String): Boolean {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka tautan ini")
            true
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    isEnabled = false
                    finish()
                } else {
                    lastBackPress = now
                    toast("Tekan sekali lagi untuk keluar")
                }
            }
        })
    }

    private fun loadApp() {
        errorView.visibility = View.GONE
        val url = currentUrl()
        updateTitle(url)
        webView.loadUrl(url)
    }

    private fun updateTitle(url: String?) {
        try {
            val host = Uri.parse(url ?: currentUrl()).host
            if (!host.isNullOrBlank()) toolbar.subtitle = host
        } catch (_: Exception) {
        }
    }

    private fun showError(detail: String) {
        errorView.visibility = View.VISIBLE
        progress.visibility = View.GONE
        swipe.isRefreshing = false
        findViewById<android.widget.TextView>(R.id.errorDetail).text = detail
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            setText(currentUrl())
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("URL Web App")
            .setMessage("Ubah alamat jika URL deployment Google Apps Script berubah.")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val value = input.text.toString().trim()
                if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                    prefs.edit().putString(PREF_URL, value).apply()
                    webView.clearCache(true)
                    loadApp()
                } else {
                    toast("URL harus diawali http:// atau https://")
                }
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.edit().remove(PREF_URL).apply()
                webView.clearCache(true)
                loadApp()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("Tidak ada browser terpasang")
        }
    }

    private fun shareUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl())
        }
        startActivity(Intent.createChooser(intent, "Bagikan tautan absensi"))
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Aplikasi absensi berbasis WebView (mesin Chrome) untuk Sistem Absensi Gudang JPLB. Lokasi yang dikirim adalah GPS asli perangkat.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
