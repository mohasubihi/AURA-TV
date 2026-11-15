package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * AURA NOVA
 * Real installed app inventory + original WebView scene.
 * WebView only loads the packaged local HTML. No external script is allowed.
 */
class AuraNovaActivity : Activity() {

    private var browser: WebView? = null
    private val recent by lazy { getSharedPreferences("aura_nova", MODE_PRIVATE) }
    private val repo = LinkedHashMap<String, ResolveInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        collectApps()
        try {
            val w = WebView(this)
            browser = w
            w.setBackgroundColor(Color.rgb(4, 10, 25))
            w.isFocusable = true
            w.isFocusableInTouchMode = true
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.allowFileAccess = true
            w.settings.allowContentAccess = false
            w.settings.cacheMode = WebSettings.LOAD_DEFAULT
            w.settings.useWideViewPort = true
            w.settings.loadWithOverviewMode = true
            w.settings.builtInZoomControls = false
            w.settings.displayZoomControls = false
            @Suppress("DEPRECATION")
            w.settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            w.settings.allowUniversalAccessFromFileURLs = false
            w.addJavascriptInterface(Bridge(), "Aura")
            w.webChromeClient = WebChromeClient()
            w.webViewClient = object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url?.startsWith("file:///android_asset/nova/") != true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url?.startsWith("file:///android_asset/nova/") == true) {
                        view?.evaluateJavascript("window.setApps(window.Aura.getApps())", null)
                        view?.evaluateJavascript("window.setNetwork(${isConnected()})", null)
                    }
                }
            }
            setContentView(w)
            w.loadUrl("file:///android_asset/nova/index.html")
        } catch (_: Throwable) {
            // Some Android TV builds do not ship a functional WebView provider.
            // Fall back to the previous, working native launcher rather than crash.
            startActivity(Intent(this, AuraFlowActivity::class.java))
            finish()
        }
    }

    private fun isConnected(): Boolean = try {
        val manager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) { false }

    private fun collectApps() {
        repo.clear()
        val pm = packageManager
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val request = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val apps = pm.queryIntentActivities(request, 0)
            for (app in apps) {
                val pkg = app.activityInfo?.packageName ?: continue
                if (pkg != packageName && !repo.containsKey(pkg)) repo[pkg] = app
            }
        }
    }

    private fun visibleApps(): List<Pair<String, ResolveInfo>> {
        val favorites = listOf("netflix", "youtube.tv", "disney", "primevideo", "shahid", "youtube", "spotify")
        val last = recent.getString("last", "").orEmpty()
        return repo.entries.sortedWith(
            compareBy<Map.Entry<String, ResolveInfo>> {
                if (it.key == last) -1
                else favorites.indexOfFirst { name -> it.key.lowercase(Locale.US).contains(name) }
                    .let { index -> if (index < 0) 100 else index }
            }.thenBy { it.value.loadLabel(packageManager).toString() }
        ).take(10).map { it.key to it.value }
    }

    private fun iconUri(drawable: Drawable): String? = try {
        val bmp = Bitmap.createBitmap(132, 132, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        drawable.setBounds(4, 4, 128, 128)
        drawable.draw(cv)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
        bmp.recycle()
        "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }

    private fun jsonApps(): String {
        val colors = arrayOf(
            "#e9a2ad" to "233,162,173", "#ff8588" to "255,133,136",
            "#8bd2ff" to "139,210,255", "#a6b3f8" to "166,179,248",
            "#ae9ef0" to "174,158,240", "#7ed9c8" to "126,217,200"
        )
        val array = JSONArray()
        for ((index, item) in visibleApps().withIndex()) {
            val (pkg, res) = item
            val color = colors[index % colors.size]
            val objectJson = JSONObject()
            objectJson.put("id", pkg)
            objectJson.put("name", res.loadLabel(packageManager).toString())
            objectJson.put("icon", iconUri(res.loadIcon(packageManager)) ?: JSONObject.NULL)
            objectJson.put("accent", color.first)
            objectJson.put("rgb", color.second)
            array.put(objectJson)
        }
        array.put(JSONObject()
            .put("id", "__all__")
            .put("name", "كل التطبيقات")
            .put("accent", "#a9b5ee")
            .put("rgb", "169,181,238"))
        return array.toString()
    }

    inner class Bridge {
        @JavascriptInterface
        fun getApps(): String = try {
            jsonApps()
        } catch (_: Exception) { "[]" }

        @JavascriptInterface
        fun launch(packageId: String) {
            runOnUiThread {
                if (packageId == "__all__") {
                    showAllApps()
                } else {
                    openApp(packageId)
                }
            }
        }

        @JavascriptInterface
        fun openNetwork() {
            runOnUiThread {
                try { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
                catch (_: Exception) { try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
            }
        }

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread {
                try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                catch (_: Exception) {}
            }
        }
    }

    private fun openApp(packageId: String) {
        if (!repo.containsKey(packageId)) return
        try {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(packageId)
                ?: packageManager.getLaunchIntentForPackage(packageId)
            if (intent != null) {
                recent.edit().putString("last", packageId).apply()
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun showAllApps() {
        val pm = packageManager
        val apps = repo.entries.sortedBy { it.value.loadLabel(pm).toString() }
        AlertDialog.Builder(this)
            .setTitle("التطبيقات المثبتة")
            .setItems(apps.map { it.value.loadLabel(pm).toString() }.toTypedArray()) { _, which ->
                openApp(apps[which].key)
            }.setNegativeButton("إغلاق", null).show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && browser != null) {
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
                KeyEvent.KEYCODE_DPAD_UP -> "up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                KeyEvent.KEYCODE_BACK -> "back"
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> "enter"
                else -> null
            }
            if (action != null) {
                browser?.evaluateJavascript("window.onRemote('$action')", null)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        browser?.onResume()
        browser?.evaluateJavascript("if(window.setNetwork)window.setNetwork(${isConnected()})", null)
    }
    override fun onPause() {
        browser?.onPause()
        super.onPause()
    }
    override fun onDestroy() {
        browser?.removeJavascriptInterface("Aura")
        browser?.destroy()
        browser = null
        super.onDestroy()
    }
}
