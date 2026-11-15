package com.auratv.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** AURA FLOW: functional cinematic app switcher for Android TV.
 * Native views and proportional rows, no embedded mockups or external APIs. */
class AuraFlowActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("aura_flow", MODE_PRIVATE) }
    private var unit = 1f
    private val tick = object : Runnable {
        override fun run() { updateTime(); handler.postDelayed(this, 30_000) }
    }
    data class Entry(val title: String, val pkg: String?, val logo: Drawable?, val type: Int)
    private val entries = mutableListOf<Entry>()
    private var selected = 0
    private var accent = 0xFF8FBBD4.toInt()
    private var accentAnimator: ValueAnimator? = null
    private lateinit var glow: Backdrop
    private lateinit var logo: ImageView
    private lateinit var title: TextView
    private lateinit var note: TextView
    private lateinit var actionLabel: TextView
    private lateinit var button: FrameLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var network: TextView
    private lateinit var greeting: TextView
    private lateinit var shelf: LinearLayout
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val dm = resources.displayMetrics
        unit = min(dm.widthPixels / 1920f, dm.heightPixels / 1080f).coerceAtLeast(.35f)
        makeScreen()
        refreshApps()
    }
    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
    override fun onPause() {
        handler.removeCallbacks(tick)
        accentAnimator?.cancel()
        super.onPause()
    }
    private fun dp(v: Int) = (v * unit).roundToInt().coerceAtLeast(1)

    private fun bg(color: Int, r: Int, border: Int = 0x295C728A, width: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(r).toFloat()
            if (width > 0) setStroke(dp(width), border)
        }
    private fun label(value: String, size: Int, color: Int = Color.WHITE,
                      bold: Boolean = false, gravityPos: Int = Gravity.CENTER_VERTICAL, lines: Int = 1) =
        TextView(this).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(size).toFloat())
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            includeFontPadding = false
            gravity = gravityPos
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            isFocusable = false
        }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
    }
    private fun col() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
    }
    private fun spaceH(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(v), 1) }
    private fun spaceV(v: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(v)) }
    private fun card(color: Int = 0xF0162232.toInt(), r: Int = 25) =
        FrameLayout(this).apply { background = bg(color, r); layoutDirection = View.LAYOUT_DIRECTION_LTR }

    private fun makeScreen() {
        val root = FrameLayout(this)
        glow = Backdrop(this)
        root.addView(glow, FrameLayout.LayoutParams(-1, -1))
        val layout = col().apply { setPadding(dp(78), dp(46), dp(78), dp(38)) }
        root.addView(layout, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        layout.addView(header, LinearLayout.LayoutParams(-1, dp(90)))
        val brand = col()
        header.addView(brand, LinearLayout.LayoutParams(0, -1, 1f))
        brand.addView(label("A U R A", 32, 0xFFF2F6FA.toInt(), true),
            LinearLayout.LayoutParams(-1, dp(50)))
        brand.addView(label("F L O W     /     YOUR TV", 13, 0xFF8094AD.toInt()),
            LinearLayout.LayoutParams(-1, dp(25)))
        network = label("●  الشبكة", 20, 0xFFAFBDCE.toInt())
        header.addView(network, LinearLayout.LayoutParams(dp(187), -1))
        header.addView(iconAction(R.drawable.ic_wifi, "الشبكة") { openWifi() },
            LinearLayout.LayoutParams(dp(60), dp(60)))
        header.addView(spaceH(14))
        header.addView(iconAction(R.drawable.ic_settings, "الإعدادات") { openSettings() },
            LinearLayout.LayoutParams(dp(60), dp(60)))
        header.addView(spaceH(40))
        val timeBox = col()
        header.addView(timeBox, LinearLayout.LayoutParams(dp(230), -1))
        clockText = label("", 47, Color.WHITE, true, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        dateText = label("", 19, 0xFFB2BFD0.toInt(), false, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        timeBox.addView(clockText, LinearLayout.LayoutParams(-1, dp(56)))
        timeBox.addView(dateText, LinearLayout.LayoutParams(-1, dp(29)))

        layout.addView(spaceV(48))
        val intro = row().apply { gravity = Gravity.CENTER_VERTICAL }
        layout.addView(intro, LinearLayout.LayoutParams(-1, dp(104)))
        intro.addView(label("اختر تطبيقك، وكل شيء آخر يختفي", 23, 0xFFB7C4D4.toInt()),
            LinearLayout.LayoutParams(0, -1, 1f))
        greeting = label("مساء الخير", 53, 0xFFF6F8FC.toInt(), true,
            Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        intro.addView(greeting, LinearLayout.LayoutParams(dp(680), -1))
        layout.addView(spaceV(34))

        val stage = card(0xE90C1728.toInt(), 32).apply {
            background = bg(0xE80D1A2B.toInt(), 32, 0x356C849B, 1)
            clipToOutline = true
        }
        layout.addView(stage, LinearLayout.LayoutParams(-1, dp(369)))
        val stageRow = row().apply { setPadding(dp(52), dp(26), dp(62), dp(26)); gravity = Gravity.CENTER_VERTICAL }
        stage.addView(stageRow, FrameLayout.LayoutParams(-1, -1))
        val art = FrameLayout(this)
        stageRow.addView(art, LinearLayout.LayoutParams(0, -1, .46f))
        art.addView(Orbit(this), FrameLayout.LayoutParams(-1, -1))
        val mark = card(0xC3243649.toInt(), 38)
        art.addView(mark, FrameLayout.LayoutParams(dp(163), dp(163), Gravity.CENTER))
        logo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        mark.addView(logo, FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER))
        val details = col().apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        stageRow.addView(details, LinearLayout.LayoutParams(0, -1, .54f))
        details.addView(label("تطبيقك المحدد", 20, 0xFFB5C6D7.toInt(), true,
            Gravity.RIGHT or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(-1, dp(52)))
        title = label("تطبيقاتك", 63, Color.WHITE, true, Gravity.RIGHT or Gravity.CENTER_VERTICAL, 2)
        details.addView(title, LinearLayout.LayoutParams(-1, dp(100)))
        note = label("اختيارك يحدد التجربة", 26, 0xFFB4C4D4.toInt(),
            false, Gravity.RIGHT or Gravity.CENTER_VERTICAL, 2)
        details.addView(note, LinearLayout.LayoutParams(-1, dp(69)))
        details.addView(spaceV(14))
        val cta = row().apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        details.addView(cta, LinearLayout.LayoutParams(-1, dp(69)))
        button = card(0xFFBEDBE5.toInt(), 18).apply {
            isFocusable = true
            isClickable = true
            contentDescription = "فتح التطبيق المحدد"
            setOnClickListener { launch() }
            setOnFocusChangeListener { _, focus ->
                background = bg(if (focus) Color.WHITE else 0xFFBEDBE5.toInt(), 18, Color.TRANSPARENT, 0)
                animate().scaleX(if (focus) 1.04f else 1f).scaleY(if (focus) 1.04f else 1f)
                    .setDuration(140).start()
            }
        }
        cta.addView(button, LinearLayout.LayoutParams(dp(270), dp(61)))
        actionLabel = label("فتح التطبيق   ←", 23, 0xFF0C1A27.toInt(), true, Gravity.CENTER)
        button.addView(actionLabel, FrameLayout.LayoutParams(-1, -1))

        layout.addView(spaceV(37))
        val section = row()
        layout.addView(section, LinearLayout.LayoutParams(-1, dp(48)))
        count = label("", 19, 0xFF9DB0C4.toInt())
        section.addView(count, LinearLayout.LayoutParams(0, -1, 1f))
        section.addView(label("تطبيقاتك", 28, Color.WHITE, true,
            Gravity.RIGHT or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(dp(370), -1))
        layout.addView(spaceV(20))
        shelf = row()
        layout.addView(shelf, LinearLayout.LayoutParams(-1, dp(186)))
        layout.addView(spaceV(20))
        val footer = row()
        layout.addView(footer, LinearLayout.LayoutParams(-1, dp(29)))
        footer.addView(label("AURA FLOW   ·   MADE FOR THE BIG SCREEN", 14, 0xFF62758D.toInt()),
            LinearLayout.LayoutParams(0, -1, 1f))
        footer.addView(label("الأسهم للتنقل    •    OK للتشغيل", 17,
            0xFF8398AE.toInt(), false, Gravity.RIGHT or Gravity.CENTER_VERTICAL),
            LinearLayout.LayoutParams(dp(460), -1))
    }

    private fun iconAction(res: Int, hint: String, act: () -> Unit): FrameLayout {
        val box = card(0x88213243.toInt(), 17)
        val icon = ImageView(this).apply { setImageResource(res); scaleType = ImageView.ScaleType.FIT_CENTER }
        box.addView(icon, FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER))
        box.contentDescription = hint
        box.isFocusable = true
        box.isClickable = true
        box.setOnClickListener { act() }
        box.setOnFocusChangeListener { _, f ->
            box.background = bg(if (f) 0xFF3C556A.toInt() else 0x88213243.toInt(),
                17, if (f) 0xFFD6E9F5.toInt() else 0x295C728A, if (f) 2 else 1)
        }
        return box
    }

    private fun refreshApps() {
        val pm = packageManager
        val found = linkedMapOf<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            for (app in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val p = app.activityInfo?.packageName ?: continue
                if (p != packageName) found.putIfAbsent(p, app)
            }
        }
        val preferred = listOf("youtube", "netflix", "shahid", "primevideo", "disney", "spotify")
        val recent = prefs.getString("recent", "").orEmpty()
        val sorted = found.entries.sortedWith(compareBy<Map.Entry<String, ResolveInfo>> {
            if (it.key == recent) -1 else preferred.indexOfFirst { word ->
                it.key.lowercase(Locale.US).contains(word)
            }.let { if (it < 0) 99 else it }
        }.thenBy { it.value.loadLabel(pm).toString() })
        entries.clear()
        entries.addAll(sorted.take(5).map { Entry(it.value.loadLabel(pm).toString(),
            it.key, it.value.loadIcon(pm), 0) })
        entries.add(Entry("كل التطبيقات", null, getDrawable(R.drawable.ic_apps), 1))
        if (entries.size < 5) {
            entries.add(Entry("الإعدادات", null, getDrawable(R.drawable.ic_settings), 2))
            entries.add(Entry("الشبكة", null, getDrawable(R.drawable.ic_wifi), 3))
        }
        showShelf()
    }

    private fun showShelf() {
        shelf.removeAllViews()
        count.text = entries.count { it.type == 0 }.toString() + " تطبيقات مثبتة"
        for ((index, entry) in entries.withIndex()) {
            val item = card(0xE21C293B.toInt(), 22).apply {
                isFocusable = true
                isClickable = true
                contentDescription = entry.title
            }
            shelf.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
            if (index < entries.lastIndex) shelf.addView(spaceH(20))
            val contents = col().apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            item.addView(contents, FrameLayout.LayoutParams(-1, -1))
            val holder = card(0x35FFFFFF, 19)
            contents.addView(holder, LinearLayout.LayoutParams(dp(82), dp(82)))
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(entry.logo)
            }
            holder.addView(image, FrameLayout.LayoutParams(dp(55), dp(55), Gravity.CENTER))
            contents.addView(spaceV(12))
            contents.addView(label(entry.title, 22, 0xFFE8EEF6.toInt(), true, Gravity.CENTER),
                LinearLayout.LayoutParams(-1, dp(39)))
            item.setOnFocusChangeListener { _, focus ->
                item.background = bg(if (focus) 0xF0394D60.toInt() else 0xE21C293B.toInt(),
                    22, if (focus) 0xFFE3F3FF.toInt() else 0x295C728A, if (focus) 2 else 1)
                item.animate().cancel()
                item.animate().scaleX(if (focus) 1.035f else 1f)
                    .scaleY(if (focus) 1.035f else 1f)
                    .translationY(if (focus) -dp(5).toFloat() else 0f)
                    .setDuration(150).start()
                if (focus) select(index)
            }
            item.setOnClickListener { select(index); launch() }
            if (index == 0) item.post { item.requestFocus() }
        }
        select(0)
    }

    private fun select(index: Int) {
        val e = entries.getOrNull(index) ?: return
        selected = index
        title.text = e.title
        note.text = when (e.type) {
            0 -> "جاهز للتشغيل على تلفزيونك"
            1 -> "تصفح جميع التطبيقات المثبتة"
            2 -> "إعدادات العرض والاتصال"
            else -> "إعداد شبكة التلفزيون"
        }
        logo.setImageDrawable(e.logo)
        button.contentDescription = "فتح " + e.title
        actionLabel.text = if (e.type == 0) "افتح التطبيق   ←" else "فتح   ←"
        val next = when {
            e.pkg?.contains("youtube", true) == true -> 0xFFE99B90.toInt()
            e.pkg?.contains("netflix", true) == true -> 0xFFDC8497.toInt()
            e.pkg?.contains("shahid", true) == true -> 0xFFB7A1E8.toInt()
            e.pkg?.contains("disney", true) == true -> 0xFF9EB5F2.toInt()
            e.pkg?.contains("primevideo", true) == true -> 0xFF83C9E7.toInt()
            else -> 0xFF8ABDD4.toInt()
        }
        accentAnimator?.cancel()
        val old = accent
        accent = next
        accentAnimator = ValueAnimator.ofObject(ArgbEvaluator(), old, next).apply {
            duration = 350
            addUpdateListener { glow.tint = it.animatedValue as Int; glow.invalidate() }
            start()
        }
    }

    private fun launch() {
        val e = entries.getOrNull(selected) ?: return
        when (e.type) {
            1 -> showAllApps()
            2 -> openSettings()
            3 -> openWifi()
            else -> {
                val pkg = e.pkg ?: return
                try {
                    val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                        ?: packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent == null) Toast.makeText(this, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show()
                    else {
                        prefs.edit().putString("recent", pkg).apply()
                        startActivity(launchIntent)
                    }
                } catch (_: Exception) { Toast.makeText(this, "تعذر فتح التطبيق", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    private fun showAllApps() {
        val pm = packageManager
        val found = linkedMapOf<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            for (r in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (r.activityInfo.packageName != packageName) found.putIfAbsent(r.activityInfo.packageName, r)
            }
        }
        val all = found.values.sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("جميع التطبيقات")
            .setItems(all.map { it.loadLabel(pm).toString() }.toTypedArray()) { _, i ->
                try {
                    val pkg = all[i].activityInfo.packageName
                    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) startActivity(intent)
                } catch (_: Exception) { }
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun openWifi() {
        try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        catch (_: Exception) { openSettings() }
    }
    private fun openSettings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        catch (_: Exception) { }
    }

    private fun updateTime() {
        val now = Date()
        clockText.text = SimpleDateFormat("HH:mm", Locale.US).format(now)
        dateText.text = SimpleDateFormat("EEEE، d MMMM", Locale("ar", "SA")).format(now)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting.text = when (hour) {
            in 5..11 -> "صباح الخير"
            in 12..18 -> "مساء الخير"
            else -> "ليلة هادئة"
        }
        network.text = try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                "●  متصل" else "○  غير متصل"
        } catch (_: Exception) { "●  الشبكة" }
        glow.night = hour >= 18 || hour < 6
        glow.invalidate()
    }

    private inner class Orbit(ctx: Context): View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = min(w, h) * .22f
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2).toFloat()
            for (i in 0..3) {
                p.color = Color.argb(30 - 6 * i, Color.red(accent), Color.green(accent), Color.blue(accent))
                c.drawCircle(w * .5f, h * .5f, radius * (1.15f + i * .43f), p)
            }
            p.style = Paint.Style.FILL
        }
    }
    private class Backdrop(ctx: Context): View(ctx) {
        var tint = 0xFF8ABDD4.toInt()
        var night = true
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun c(alpha: Int) = Color.argb(alpha, Color.red(tint), Color.green(tint), Color.blue(tint))
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, 0f, w, h,
                if (night) 0xFF070B13.toInt() else 0xFF0B1725.toInt(),
                0xFF141E2B.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = RadialGradient(w * .25f, h * .18f, w * .65f,
                intArrayOf(c(76), c(23), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(w * .25f, h * .18f, w * .65f, p)
            p.shader = RadialGradient(w * .82f, h * .76f, w * .55f,
                intArrayOf(c(43), c(13), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(w * .82f, h * .76f, w * .55f, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.3f * w / 1920f
            for (i in 0..4) {
                p.color = Color.argb(18 - i * 3, 174, 200, 227)
                val path = Path()
                path.moveTo(-w * .1f, h * (.44f + .05f * i))
                path.cubicTo(w * .24f, h * (.29f + .055f * i),
                    w * .53f, h * (.70f + .02f * i), w * 1.05f, h * (.27f + .067f * i))
                canvas.drawPath(path, p)
            }
            p.style = Paint.Style.FILL
        }
    }
}
