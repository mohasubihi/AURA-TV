package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/** AURA TV 2.0 — native Android TV experience, D-pad first. */
class MainActivity : Activity() {
    private lateinit var home: AuraHomeView
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("aura_v2", MODE_PRIVATE) }
    private var temperature: Int? = null
    private var weatherCode: Int? = null
    private var tickCounter = 0

    private val ticker = object : Runnable {
        override fun run() {
            home.invalidate()
            home.updateScene(sceneName())
            tickCounter++
            if (tickCounter % 30 == 0) loadWeather()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        home = AuraHomeView(this,
            onAction = { action -> handleAction(action) },
            weather = { temperature to weatherCode })
        setContentView(home)
        home.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        loadWeather()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun sceneName(): String {
        val chosen = prefs.getString("scene", "auto") ?: "auto"
        if (chosen != "auto") return chosen
        val code = weatherCode
        if (code != null && (code in 51..67 || code in 80..82 || code in 95..99)) return "rain"
        if (code != null && code in 2..3) return "cloud"
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..15 -> "day"
            in 16..18 -> "sunset"
            else -> "night"
        }
    }

    private fun loadWeather() {
        worker.execute {
            // No API key, no location permission, fixed public location: Riyadh.
            // Weather uses actual data only. If unavailable, show no invented temperature.
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=24.7667&longitude=46.6383&current=temperature_2m,weather_code&timezone=Asia%2FRiyadh")
                val c = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                try {
                    if (c.responseCode == 200) {
                        val obj = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
                        val temp = obj.getDouble("temperature_2m").toInt()
                        val code = obj.getInt("weather_code")
                        runOnUiThread {
                            temperature = temp
                            weatherCode = code
                            home.updateScene(sceneName())
                            home.invalidate()
                        }
                    }
                } finally { c.disconnect() }
            } catch (_: Exception) { /* Keep the UI usable offline. */ }
        }
    }

    private fun handleAction(action: String) {
        when {
            action == "wifi" -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            action == "home" -> home.setSelection("home")
            action == "settings" -> showSettings()
            action == "match" -> AlertDialog.Builder(this)
                .setTitle("مباراة مانشستر يونايتد")
                .setMessage("البطاقة حاليًا نموذج بصري كما طلبت. ربط مواعيد المباريات الحقيقية بمصدر بيانات سيكون في المرحلة التالية، لذلك لن نعرض موعدًا غير موثوق.")
                .setPositiveButton("حسنًا", null).show()
            action == "hero" || action.startsWith("poster_") -> launchOneOf(listOf("com.netflix.ninja", "com.netflix.mediaclient"), "Netflix")
            action == "netflix" -> launchOneOf(listOf("com.netflix.ninja", "com.netflix.mediaclient"), "Netflix")
            action == "youtube" -> launchOneOf(listOf("com.google.android.youtube.tv", "com.google.android.youtube"), "YouTube")
            action == "prime" -> launchOneOf(listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), "Prime Video")
            action == "apple" -> launchOneOf(listOf("com.apple.atve.androidtv.appletv"), "Apple TV")
            action == "disney" -> launchOneOf(listOf("com.disney.disneyplus"), "Disney+")
            action == "all_apps" -> showApps()
        }
    }

    private fun launchOneOf(packageNames: List<String>, label: String) {
        for (name in packageNames) {
            try {
                val intent = packageManager.getLeanbackLaunchIntentForPackage(name)
                    ?: packageManager.getLaunchIntentForPackage(name)
                if (intent != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) { }
        }
        Toast.makeText(this, "$label غير متوفر ضمن تطبيقات هذا التلفزيون", Toast.LENGTH_LONG).show()
    }

    private fun showApps() {
        val pm = packageManager
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        val found = linkedMapOf<String, String>()
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            for (info in pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
                val pack = info.activityInfo?.packageName ?: continue
                if (pack != packageName) found[pack] = info.loadLabel(pm).toString()
            }
        }
        if (found.isEmpty()) {
            Toast.makeText(this, "لم يتم العثور على تطبيقات قابلة للتشغيل", Toast.LENGTH_SHORT).show()
            return
        }
        val apps = found.toList().sortedBy { it.second.lowercase(Locale.getDefault()) }
        AlertDialog.Builder(this)
            .setTitle("كل التطبيقات")
            .setItems(apps.map { it.second }.toTypedArray()) { _, i -> launchOneOf(listOf(apps[i].first), apps[i].second) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf("تلقائي حسب الوقت والطقس", "كافد — ليل", "كافد — صباح", "كافد — غروب", "كافد — مطر", "كافد — غيم")
        val keys = arrayOf("auto", "night", "day", "sunset", "rain", "cloud")
        val selected = keys.indexOf(prefs.getString("scene", "auto")).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("خلفية كافد")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                prefs.edit().putString("scene", keys[which]).apply()
                home.updateScene(sceneName())
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
