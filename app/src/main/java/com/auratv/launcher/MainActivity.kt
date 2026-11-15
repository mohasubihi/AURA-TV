package com.auratv.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AURA TV: native, dependency-free Android TV launcher prototype.
 * Remote navigation uses Android's native focus system.
 */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("aura_tv", MODE_PRIVATE) }
    private val clockHandler = Handler(Looper.getMainLooper())
    private var timeView: TextView? = null
    private var dateView: TextView? = null
    private var overlay: FrameLayout? = null
    private lateinit var canvasRoot: FrameLayout
    private val gold = Color.rgb(218, 187, 140)
    private val pale = Color.rgb(235, 241, 250)
    private val muted = Color.rgb(168, 183, 199)
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000)
        }
    }

    data class TvApp(val packageId: String, val label: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildHome()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        super.onPause()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 20, border: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != Color.TRANSPARENT) setStroke(dp(1), border)
        }

    private fun gradient(colors: IntArray, radius: Int = 0): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun font(size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        textSize = size
        setTextColor(color)
        typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun accent(): Int = when (prefs.getString("accent", "gold")) {
        "blue" -> Color.rgb(166, 215, 246)
        "violet" -> Color.rgb(203, 179, 251)
        else -> gold
    }

    private fun currentWallpaper(): String {
        val mode = prefs.getString("wallpaper", "auto") ?: "auto"
        if (mode != "auto") return mode
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 6..10 -> "desert"
            in 11..16 -> "aurora"
            in 17..19 -> "sunset"
            else -> "night"
        }
    }

    private fun buildHome() {
        canvasRoot = FrameLayout(this)
        canvasRoot.layoutDirection = View.LAYOUT_DIRECTION_RTL
        canvasRoot.setBackgroundColor(Color.rgb(6, 14, 26))
        setContentView(canvasRoot)

        canvasRoot.addView(SkylineView(currentWallpaper()), FrameLayout.LayoutParams(-1, -1))
        if (prefs.getBoolean("custom_wallpaper", false)) {
            val source = prefs.getString("wallpaper_uri", null)
            if (!source.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(source)
                    val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    if (bitmap != null) {
                        canvasRoot.addView(ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(bitmap)
                        }, FrameLayout.LayoutParams(-1, -1))
                    }
                } catch (_: Exception) { /* Use built-in backdrop if picked photo is unavailable. */ }
            }
        }
        canvasRoot.addView(View(this).apply {
            background = gradient(intArrayOf(0x81040C17.toInt(), 0xC8081323.toInt(), 0xF807101D.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(66), dp(42), dp(66), dp(41))
        }
        canvasRoot.addView(content, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
        }
        content.addView(header, LinearLayout.LayoutParams(-1, dp(82)))

        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(brand, LinearLayout.LayoutParams(-2, -2))
        brand.addView(font(24f, accent(), true).apply { text = "A U R A" })
        brand.addView(font(11f, muted).apply {
            text = "تلفزيونك، بأسلوبك"
            setPadding(0, dp(4), 0, 0)
        })

        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val dateBlock = LinearLayout(this).apply {
            gravity = Gravity.END
            orientation = LinearLayout.VERTICAL
        }
        header.addView(dateBlock, LinearLayout.LayoutParams(-2, -2))
        timeView = font(35f, pale).apply { gravity = Gravity.END }
        dateBlock.addView(timeView)
        dateView = font(12f, muted).apply { gravity = Gravity.END }
        dateBlock.addView(dateView)
        updateClock()

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

        val tag = font(13f, accent(), true).apply {
            text = "✦   AURA EXPERIENCE"
            letterSpacing = 0.08f
        }
        hero.addView(tag, LinearLayout.LayoutParams(-2, -2))
        hero.addView(font(52f, pale, true).apply {
            text = "كل شيء، على ذوقك."
            setPadding(0, dp(15), 0, dp(9))
        })
        hero.addView(font(18f, muted).apply {
            text = "مساحتك الخاصة للترفيه — بساطة، هدوء، وتحكم كامل."
        })
        val sub = font(12f, pale).apply {
            text = "  ◉  ${prefs.getString("city", "الرياض")}       •       ${wallpaperLabel()}       •       بدون إعلانات داخل الواجهة  "
            background = rounded(0x56304B5B, 22, 0x337F9FAD)
            setPadding(dp(19), dp(12), dp(19), dp(12))
        }
        hero.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(27) })

        val appBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(appBar, LinearLayout.LayoutParams(-1, dp(220)))
        val appHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        appBar.addView(appHeader, LinearLayout.LayoutParams(-1, dp(42)))
        appHeader.addView(font(19f, pale, true).apply { text = "تطبيقاتك" })
        appHeader.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        appHeader.addView(font(12f, muted).apply { text = "OK للتشغيل  •  Menu لخيارات التطبيق" })

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        appBar.addView(horizontal, LinearLayout.LayoutParams(-1, -1))
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        horizontal.addView(rail)
        for (app in apps(includeHidden = false)) {
            rail.addView(appCard(app), LinearLayout.LayoutParams(dp(142), dp(148)).apply {
                marginEnd = dp(16)
            })
        }
        val settings = specialCard("⚙", "التخصيص") { openSettings() }
        rail.addView(settings, LinearLayout.LayoutParams(dp(142), dp(148)).apply { marginEnd = dp(16) })
        if (rail.childCount > 0) rail.getChildAt(0).requestFocus()
    }

    private fun wallpaperLabel(): String = when (currentWallpaper()) {
        "sunset" -> "أجواء الغروب"
        "desert" -> "أجواء الصباح"
        "aurora" -> "أجواء النهار"
        else -> "أجواء الليل"
    }

    private fun updateClock() {
        val date = Date()
        val ar = Locale("ar", "SA")
        timeView?.text = SimpleDateFormat("hh:mm a", ar).format(date)
        val gregorian = SimpleDateFormat("EEEE، d MMMM yyyy", ar).format(date)
        val hijri = try {
            android.icu.text.SimpleDateFormat("d MMMM y G", android.icu.util.ULocale("ar_SA@calendar=islamic-umalqura")).format(date)
        } catch (_: Exception) { "" }
        dateView?.text = if (hijri.isEmpty()) gregorian else "$gregorian  •  $hijri"
    }

    private fun apps(includeHidden: Boolean): List<TvApp> {
        val pm = packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        val found = linkedMapOf<String, TvApp>()
        for (intent in intents) {
            @Suppress("DEPRECATION")
            val resolved = pm.queryIntentActivities(intent, 0)
            for (info in resolved) {
                val id = info.activityInfo?.packageName ?: continue
                if (id == packageName || found.containsKey(id)) continue
                try {
                    found[id] = TvApp(id, info.loadLabel(pm).toString(), pm.getApplicationIcon(id))
                } catch (_: PackageManager.NameNotFoundException) { }
            }
        }
        val order = prefs.getString("order", "")!!.split("|").filter { it.isNotBlank() }
        val hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        return found.values
            .filter { includeHidden || it.packageId !in hidden }
            .sortedWith(compareBy<TvApp> {
                val index = order.indexOf(it.packageId)
                if (index >= 0) index else 10000
            }.thenBy { it.label.lowercase(Locale.ROOT) })
    }

    private fun appCard(item: TvApp): View {
        val bg = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            background = rounded(0xB51A293B.toInt(), 20, 0x4D8CA2B4)
            stateListAnimator = null
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        bg.addView(inner, FrameLayout.LayoutParams(-1, -1))
        inner.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(item.icon)
        }, LinearLayout.LayoutParams(dp(58), dp(62)))
        inner.addView(font(13f, pale, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = item.label
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(6) })
        setupFocus(bg)
        bg.setOnClickListener { launchApp(item.packageId) }
        bg.setOnLongClickListener { manageApp(item); true }
        bg.setOnKeyListener { _, key, event ->
            if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_MENU) {
                manageApp(item)
                true
            } else false
        }
        return bg
    }

    private fun specialCard(icon: String, title: String, onClick: () -> Unit): View {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = rounded(0xA31A293B.toInt(), 20, 0x6D899AAF)
        }
        holder.addView(font(33f, accent()).apply {
            text = icon
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(76)))
        holder.addView(font(13f, pale, true).apply {
            text = title
            gravity = Gravity.CENTER
        })
        setupFocus(holder)
        holder.setOnClickListener { onClick() }
        return holder
    }

    private fun setupFocus(view: View) {
        view.setOnFocusChangeListener { v, focused ->
            v.animate().scaleX(if (focused) 1.075f else 1f)
                .scaleY(if (focused) 1.075f else 1f)
                .setDuration(170).start()
            v.elevation = if (focused) dp(14).toFloat() else dp(0).toFloat()
            v.background = rounded(
                if (focused) 0xDD293F52.toInt() else 0xB51A293B.toInt(),
                20,
                if (focused) accent() else 0x4D8CA2B4
            )
        }
    }

    private fun launchApp(id: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(id)
            ?: packageManager.getLaunchIntentForPackage(id)
        if (intent == null) {
            Toast.makeText(this, "تعذّر فتح هذا التطبيق", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(intent) }
        catch (_: Exception) { Toast.makeText(this, "تعذّر فتح هذا التطبيق", Toast.LENGTH_SHORT).show() }
    }

    private fun openSettings() {
        showPanel("تخصيص AURA TV") { body ->
            addSetting(body, "الخلفيات", "تلقائي / ليلي / نهاري / غروب / صباح") {
                val choices = listOf("auto", "night", "aurora", "sunset", "desert")
                val current = prefs.getString("wallpaper", "auto") ?: "auto"
                val next = choices[(choices.indexOf(current) + 1) % choices.size]
                prefs.edit().putString("wallpaper", next).putBoolean("custom_wallpaper", false).apply()
                buildHome(); openSettings()
            }
            addSetting(body, "صورتك الخاصة", "اختيار خلفية من ملفات التلفزيون") {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                try { startActivityForResult(intent, 10) }
                catch (_: Exception) { Toast.makeText(this, "لا يوجد متصفح ملفات متاح", Toast.LENGTH_LONG).show() }
            }
            addSetting(body, "لون التركيز", "ذهبي / أزرق ثلجي / بنفسجي") {
                val choices = listOf("gold", "blue", "violet")
                val current = prefs.getString("accent", "gold") ?: "gold"
                prefs.edit().putString("accent", choices[(choices.indexOf(current) + 1) % choices.size]).apply()
                buildHome(); openSettings()
            }
            addSetting(body, "المدينة", "الرياض / جدة / الدمام") {
                val choices = listOf("الرياض", "جدة", "الدمام")
                val current = prefs.getString("city", "الرياض") ?: "الرياض"
                prefs.edit().putString("city", choices[(choices.indexOf(current) + 1) % choices.size]).apply()
                buildHome(); openSettings()
            }
            addSetting(body, "التطبيقات المخفية", "استعادة أو إدارة التطبيقات") { showHiddenApps() }
            addSetting(body, "إعدادات Android", "فتح إعدادات جهاز TCL") {
                try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                catch (_: Exception) { Toast.makeText(this, "تعذّر فتح الإعدادات", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showHiddenApps() {
        val hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        showPanel("التطبيقات المخفية") { body ->
            val entries = apps(includeHidden = true).filter { it.packageId in hidden }
            if (entries.isEmpty()) {
                body.addView(font(16f, muted).apply { text = "ما فيه تطبيقات مخفية حاليًا" })
            }
            for (item in entries) {
                addSetting(body, item.label, "إظهار التطبيق على الشاشة الرئيسية") {
                    setHidden(item.packageId, false)
                    buildHome(); showHiddenApps()
                }
            }
        }
    }

    private fun manageApp(item: TvApp) {
        showPanel(item.label) { body ->
            addSetting(body, "تشغيل التطبيق", "فتح ${item.label}") {
                closePanel(); launchApp(item.packageId)
            }
            addSetting(body, "تحريكه للأمام", "تقديم الأيقونة داخل الصف") {
                moveApp(item.packageId, -1); buildHome()
            }
            addSetting(body, "تحريكه للخلف", "تأخير الأيقونة داخل الصف") {
                moveApp(item.packageId, 1); buildHome()
            }
            addSetting(body, "إخفاء التطبيق", "يمكن استعادته من التخصيص لاحقًا") {
                setHidden(item.packageId, true); buildHome()
            }
        }
    }

    private fun moveApp(packageId: String, direction: Int) {
        val ids = apps(includeHidden = true).map { it.packageId }.toMutableList()
        val index = ids.indexOf(packageId)
        if (index < 0) return
        val target = (index + direction).coerceIn(0, ids.lastIndex)
        if (index == target) return
        ids.removeAt(index)
        ids.add(target, packageId)
        prefs.edit().putString("order", ids.joinToString("|")).apply()
    }

    private fun setHidden(packageId: String, hide: Boolean) {
        val set = (prefs.getStringSet("hidden", emptySet()) ?: emptySet()).toMutableSet()
        if (hide) set.add(packageId) else set.remove(packageId)
        prefs.edit().putStringSet("hidden", set).apply()
    }

    private fun addSetting(body: LinearLayout, name: String, description: String, onClick: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(25), dp(15), dp(25), dp(15))
            isFocusable = true
            isClickable = true
            background = rounded(0xAA243347.toInt(), 14, 0x447E91A3)
        }
        body.addView(item, LinearLayout.LayoutParams(-1, dp(82)).apply { bottomMargin = dp(10) })
        item.addView(font(17f, pale, true).apply { text = name })
        item.addView(font(12f, muted).apply {
            text = description
            setPadding(0, dp(7), 0, 0)
        })
        setupFocus(item)
        item.setOnClickListener { onClick() }
    }

    private fun showPanel(title: String, populate: (LinearLayout) -> Unit) {
        closePanel()
        val dim = FrameLayout(this).apply {
            setBackgroundColor(0xD9060E19.toInt())
            isFocusableInTouchMode = true
        }
        canvasRoot.addView(dim, FrameLayout.LayoutParams(-1, -1))
        overlay = dim
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(27), dp(25), dp(27), dp(25))
            background = rounded(0xFF101F2E.toInt(), 25, 0x887C96A9.toInt())
        }
        dim.addView(panel, FrameLayout.LayoutParams(dp(570), -2, Gravity.CENTER))
        panel.addView(font(27f, accent(), true).apply {
            text = title
        }, LinearLayout.LayoutParams(-1, dp(55)))
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(450)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        populate(body)
        val closeButton = font(15f, pale, true).apply {
            text = "رجوع إلى الشاشة الرئيسية"
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = rounded(0x88566A7D.toInt(), 13)
        }
        panel.addView(closeButton, LinearLayout.LayoutParams(-1, dp(49)).apply { topMargin = dp(13) })
        setupFocus(closeButton)
        closeButton.setOnClickListener { closePanel() }
        if (body.childCount > 0) body.getChildAt(0).requestFocus()
        else closeButton.requestFocus()
    }

    private fun closePanel() {
        overlay?.let { parent -> (parent.parent as? ViewGroup)?.removeView(parent) }
        overlay = null
    }

    @Deprecated("Used to support Android TV system photo picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) { }
            prefs.edit().putString("wallpaper_uri", uri.toString())
                .putBoolean("custom_wallpaper", true).apply()
            buildHome()
        }
    }

    @Deprecated("Back is used to dismiss in-app settings panels")
    override fun onBackPressed() {
        if (overlay != null) closePanel() else super.onBackPressed()
    }

    /** Local, offline skyline illustration. Uses no third-party stock photographs. */
    private inner class SkylineView(private val mode: String) : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val seed = floatArrayOf(.03f, .11f, .20f, .27f, .35f, .43f, .50f, .59f, .66f, .74f, .82f, .90f, .97f)
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w == 0f || h == 0f) return
            val palette = when (mode) {
                "desert" -> intArrayOf(0xFF587B8F.toInt(), 0xFFD6BBA5.toInt(), 0xFF324D5B.toInt())
                "sunset" -> intArrayOf(0xFF251D42.toInt(), 0xFFA85B6E.toInt(), 0xFF17243E.toInt())
                "aurora" -> intArrayOf(0xFF1B4C61.toInt(), 0xFF456F7F.toInt(), 0xFF142B3C.toInt())
                else -> intArrayOf(0xFF071329.toInt(), 0xFF254056.toInt(), 0xFF07101F.toInt())
            }
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, palette, null,
                android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            // Horizon glow.
            paint.color = if (mode == "sunset") 0x28F9C4AC else 0x1C9DC3C7
            canvas.drawOval(RectF(w * .07f, h * .54f, w * 1.04f, h * 1.03f), paint)
            // Small distant buildings.
            for (i in 0..31) {
                val x = (i / 32f) * w
                val bWidth = w * (.019f + (i % 4) * .006f)
                val bHeight = h * (.12f + ((i * 17) % 18) / 100f)
                paint.color = if (i % 2 == 0) 0xFF12283B.toInt() else 0xFF172F42.toInt()
                canvas.drawRect(x, h * .78f - bHeight, x + bWidth, h * .91f, paint)
            }
            // Hero skyscrapers.
            val heights = floatArrayOf(.31f, .37f, .28f, .42f, .53f, .39f, .33f, .46f, .29f, .37f, .31f, .35f, .27f)
            for (i in seed.indices) {
                val x = seed[i] * w
                val towerWidth = w * (if (i % 3 == 0) .048f else .035f)
                val top = h * (1f - heights[i])
                paint.color = if (i % 3 == 0) 0xFF0B2131.toInt() else 0xFF102B3D.toInt()
                canvas.drawRect(x, top, x + towerWidth, h, paint)
                paint.color = if (mode == "night" || mode == "sunset") 0x5578C9CF else 0x338DC2C1
                for (j in 0..14) {
                    val y = top + h * .024f + j * h * .017f
                    if (y > h * .87f) break
                    if ((j + i) % 3 != 0) {
                        canvas.drawRect(x + towerWidth * .20f, y,
                            x + towerWidth * .27f, y + h * .003f, paint)
                        canvas.drawRect(x + towerWidth * .61f, y,
                            x + towerWidth * .69f, y + h * .003f, paint)
                    }
                }
            }
            paint.color = 0xFF07121F.toInt()
            canvas.drawRect(0f, h * .88f, w, h, paint)
            if (mode == "night") {
                paint.color = 0x99D2E7F1.toInt()
                for (i in 1..25) {
                    val x = ((i * 157) % 997) / 997f * w
                    val y = ((i * 71) % 293) / 550f * h
                    canvas.drawCircle(x, y, if (i % 5 == 0) 1.6f else 0.8f, paint)
                }
            }
        }
    }
}
