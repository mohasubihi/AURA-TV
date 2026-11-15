package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AURA 4 — Lounge
 *
 * Native Android Views rendered at the TV's actual pixel density.
 * Wallpaper imagery is independent of the interface. All titles, cards,
 * launchers, and focus targets are live Views, not a baked mockup.
 */
class AuraV4Activity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(3)
    private val pictures = ImageLoader()
    private val preferences by lazy { getSharedPreferences("aura_lounge", MODE_PRIVATE) }
    private lateinit var canvas: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var weatherView: TextView
    private lateinit var greetingView: TextView
    private lateinit var headingView: TextView
    private lateinit var featureCover: ImageView
    private lateinit var featureTitle: TextView
    private lateinit var featureSubtitle: TextView
    private lateinit var matchTitle: TextView
    private lateinit var matchSub: TextView
    private lateinit var videosContainer: FrameLayout
    private lateinit var appsContainer: FrameLayout
    private lateinit var firstFocusable: View
    private var sizing = 1f
    private var movies: List<VideoItem> = emptyList()
    private var currentHero = -1
    private var weather: WeatherItem? = null
    private var nextMatch: MatchItem? = null
    private var sceneName = ""
    private var nextWeatherRefresh = 0L
    private var nextVideoRefresh = 0L
    private var nextMatchRefresh = 0L
    private val riyadh = TimeZone.getTimeZone("Asia/Riyadh")

    private val heartbeat = object : Runnable {
        override fun run() {
            updateClock()
            changeAtmosphere()
            changeFeature()
            ui.postDelayed(this, 30_000L)
        }
    }
    private val refresh = object : Runnable {
        override fun run() {
            fetchContent()
            ui.postDelayed(this, 20 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val dm = resources.displayMetrics
        sizing = min(dm.widthPixels / 1920f, dm.heightPixels / 1080f)
        construct()
        recoverFeed()
        updateClock()
        changeAtmosphere()
        firstFocusable.post { firstFocusable.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(heartbeat)
        ui.removeCallbacks(refresh)
        ui.post(heartbeat)
        ui.post(refresh)
    }
    override fun onPause() {
        ui.removeCallbacks(heartbeat)
        ui.removeCallbacks(refresh)
        super.onPause()
    }
    override fun onDestroy() {
        worker.shutdownNow()
        pictures.shutdown()
        super.onDestroy()
    }

    private fun px(value: Int) = (value * sizing).roundToInt().coerceAtLeast(1)
    private fun area(x: Int, y: Int, w: Int, h: Int) =
        FrameLayout.LayoutParams(px(w), px(h)).apply {
            leftMargin = px(x); topMargin = px(y)
        }

    private fun rounded(color: Int, radius: Int, border: Int = 0, stroke: Int = 0) =
        GradientDrawable().apply {
            setColor(color); cornerRadius = px(radius).toFloat()
            if (stroke > 0) setStroke(px(stroke), border)
        }

    private fun gradient(vararg shades: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, shades)

    private fun label(
        value: String,
        size: Int,
        tint: Int = 0xFFF7F7F7.toInt(),
        weight: Boolean = false,
        align: Int = Gravity.CENTER_VERTICAL
    ) = TextView(this).apply {
        text = value
        setTextColor(tint)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size).toFloat())
        typeface = Typeface.create(if (weight) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        gravity = align
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun put(parent: FrameLayout, child: View, x: Int, y: Int, w: Int, h: Int) {
        parent.addView(child, area(x, y, w, h))
    }

    private fun frame(x: Int, y: Int, w: Int, h: Int, tone: Int = 0xEE0D121B.toInt()): FrameLayout =
        FrameLayout(this).also {
            it.background = rounded(tone, 24, 0x36FFFFFF, 1)
            it.clipToOutline = true
            it.elevation = px(8).toFloat()
            put(canvas, it, x, y, w, h)
        }

    private fun interactive(target: FrameLayout, run: () -> Unit) {
        target.isFocusable = true
        target.isClickable = true
        target.setOnClickListener { run() }
        val outline = View(this).apply {
            background = rounded(Color.TRANSPARENT, 23, 0xFFECEFF1.toInt(), 2)
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        target.addView(outline, FrameLayout.LayoutParams(-1, -1))
        target.setOnFocusChangeListener { _, focused ->
            target.animate().cancel()
            target.animate().scaleX(if (focused) 1.018f else 1f)
                .scaleY(if (focused) 1.018f else 1f)
                .setDuration(165).start()
            outline.animate().alpha(if (focused) 1f else 0f).setDuration(145).start()
            target.elevation = px(if (focused) 20 else 8).toFloat()
        }
    }

    private fun construct() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF080C13.toInt()) }
        canvas = FrameLayout(this).apply {
            clipChildren = false; clipToPadding = false
        }
        root.addView(canvas, FrameLayout.LayoutParams(px(1920), px(1080), Gravity.CENTER))
        setContentView(root)

        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        put(canvas, wallpaper, 0, 0, 1920, 1080)
        put(canvas, View(this).apply {
            background = gradient(0x77060A11, 0x2804080D, 0xD9060A10.toInt(), 0xFA060A10.toInt())
        }, 0, 0, 1920, 1080)
        put(canvas, View(this).apply { setBackgroundColor(0x88080B12.toInt()) }, 0, 330, 1920, 750)

        // Top line: typography is native and scales to the actual screen pixels.
        put(canvas, label("A U R A", 30, 0xFFFFFFFF.toInt(), true), 175, 65, 370, 56)
        put(canvas, View(this).apply { setBackgroundColor(0xFFC82A41.toInt()) }, 175, 126, 66, 4)
        clockView = label("", 52, Color.WHITE, true, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        dateView = label("", 20, 0xFFBCC3CB.toInt(), false, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        weatherView = label("الرياض  ·  —", 25, 0xFFE1E4E9.toInt(), false, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        put(canvas, clockView, 1580, 61, 255, 65)
        put(canvas, dateView, 1570, 123, 265, 32)
        put(canvas, weatherView, 1280, 74, 270, 52)
        val devil = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try { assets.open("red_devil.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) } }
            catch (_: Exception) { }
        }
        put(canvas, devil, 1840, 75, 35, 49)

        greetingView = label("مساء الخير، محمد", 60, Color.WHITE, true)
        put(canvas, greetingView, 176, 209, 950, 85)
        put(canvas, label("الرياض  /  يونايتد  /  الليلة", 22, 0xFFC7CFD8.toInt()), 178, 290, 880, 38)

        nav()

        // Dominant feature panel. Real YouTube thumbnails and text, never a fake poster.
        val hero = frame(174, 376, 1022, 300, 0xF0111720.toInt())
        featureCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF17212D.toInt())
        }
        hero.addView(featureCover, FrameLayout.LayoutParams(-1, -1))
        hero.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                0xFA090D15.toInt(), 0xE60B111D.toInt(), 0xA50B111C.toInt(), 0x210B111C
            ))
        }, FrameLayout.LayoutParams(-1, -1))
        put(hero, label("YOUTUBE  /  اكتشف", 20, 0xFFF58D97.toInt(), true), 38, 33, 585, 36)
        featureTitle = label("محتوى جديد لكل ليلة", 43, Color.WHITE, true, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { maxLines = 3 }
        put(hero, featureTitle, 39, 81, 660, 145)
        featureSubtitle = label("أحدث الفيديوهات من قنواتك المختارة", 22, 0xFFCCD4DD.toInt())
        put(hero, featureSubtitle, 39, 236, 730, 33)
        interactive(hero) { movies.getOrNull(currentHero)?.let { openVideo(it) } ?: openYoutube() }
        firstFocusable = hero

        val matchCard = frame(1220, 376, 630, 300, 0xF2181017.toInt())
        matchCard.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                0x572F0711, 0x14120F19, 0x1012141B
            ))
        }, FrameLayout.LayoutParams(-1, -1))
        put(matchCard, label("MANCHESTER UNITED", 21, 0xFFFFB9C2.toInt(), true), 34, 27, 500, 38)
        put(matchCard, View(this).apply { setBackgroundColor(0xFFD43147.toInt()) }, 34, 72, 44, 4)
        matchTitle = label("جدول مباريات يونايتد", 39, Color.WHITE, true)
        matchTitle.maxLines = 3
        put(matchCard, matchTitle, 34, 92, 558, 120)
        matchSub = label("شاهد المباراة القادمة من المصدر الرسمي", 20, 0xFFBBC3CD.toInt())
        matchSub.maxLines = 2
        put(matchCard, matchSub, 34, 228, 552, 47)
        interactive(matchCard) { openMatch() }

        headingView = label("مختارة لهذه الليلة", 28, Color.WHITE, true)
        put(canvas, headingView, 178, 704, 740, 44)
        put(canvas, label("مقاطع حقيقية تتحدث من الإنترنت", 20, 0xFFAAB1BA.toInt(),
            false, Gravity.RIGHT or Gravity.CENTER_VERTICAL), 1260, 709, 560, 36)

        videosContainer = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        put(canvas, videosContainer, 174, 764, 1675, 222)

        appsContainer = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        put(canvas, appsContainer, 174, 1001, 1675, 62)
        renderVideoPlaceholders()
        renderApps()
    }

    private fun nav() {
        val items = listOf(
            Triple(R.drawable.ic_wifi, "الشبكة", { try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { openDeviceSettings() } }),
            Triple(R.drawable.ic_home, "الرئيسية", { firstFocusable.requestFocus() }),
            Triple(R.drawable.ic_settings, "الإعدادات", { showSettings() })
        )
        for ((index, entry) in items.withIndex()) {
            val tile = frame(48, 434 + index * 91, 74, 73, 0xB5101720.toInt())
            val img = ImageView(this).apply { setImageResource(entry.first); scaleType = ImageView.ScaleType.FIT_CENTER }
            put(tile, img, 23, 21, 28, 30)
            tile.contentDescription = entry.second
            interactive(tile, entry.third)
        }
    }

    private fun renderVideoPlaceholders() {
        videosContainer.removeAllViews()
        val empty = FrameLayout(this).apply {
            background = rounded(0xD40F151E.toInt(), 18, 0x22FFFFFF, 1)
        }
        put(videosContainer, empty, 0, 0, 1670, 206)
        put(empty, label("تظهر هنا أحدث الفيديوهات عند اكتمال الاتصال.", 26, 0xFFC7CCD4.toInt()), 32, 36, 1250, 52)
        put(empty, label("يمكنك فتح YouTube الآن", 22, 0xFFE3A8AF.toInt()), 32, 104, 1050, 43)
        interactive(empty) { openYoutube() }
    }

    private fun showVideos(items: List<VideoItem>) {
        movies = items
        videosContainer.removeAllViews()
        if (items.isEmpty()) { renderVideoPlaceholders(); return }
        changeFeature(force = true)
        for ((i, item) in items.take(4).withIndex()) {
            val block = FrameLayout(this).apply {
                background = rounded(0xF0121720.toInt(), 18, 0x30FFFFFF, 1)
                clipToOutline = true
            }
            put(videosContainer, block, i * 420, 0, 400, 222)
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF151B24.toInt())
            }
            put(block, image, 0, 0, 400, 167)
            pictures.load(image, item.thumbnail)
            val title = label(item.title, 20, Color.WHITE, true).apply { maxLines = 2 }
            put(block, title, 17, 168, 367, 50)
            interactive(block) { openVideo(item) }
            block.contentDescription = item.title
        }
    }

    private fun changeFeature(force: Boolean = false) {
        if (!::featureTitle.isInitialized || movies.isEmpty()) return
        val slot = ((System.currentTimeMillis() / (30L * 60_000L)) % movies.size).toInt()
        if (!force && slot == currentHero) return
        currentHero = slot
        val item = movies[slot]
        featureTitle.text = item.title
        featureSubtitle.text = item.channel + "  ·  شاهد على YouTube"
        pictures.load(featureCover, item.thumbnail)
    }

    private fun renderApps() {
        appsContainer.removeAllViews()
        val pm = packageManager
        val results = linkedMapOf<String, android.content.pm.ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            for (entry in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val pack = entry.activityInfo?.packageName ?: continue
                if (pack != packageName) results[pack] = entry
            }
        }
        val first = listOf("youtube", "netflix", "shahid", "primevideo", "disney", "spotify", "apple")
        val sorted = results.entries.sortedWith(compareBy<Map.Entry<String, android.content.pm.ResolveInfo>> {
            val p = it.key.lowercase(Locale.US)
            first.indexOfFirst { word -> p.contains(word) }.let { order -> if (order < 0) 100 else order }
        }.thenBy { it.value.loadLabel(pm).toString() })
        var x = 0
        sorted.take(7).forEach { (pack, entry) ->
            val tile = FrameLayout(this).apply {
                background = rounded(0xE019202A.toInt(), 15, 0x2BFFFFFF, 1)
            }
            put(appsContainer, tile, x, 0, 201, 62)
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                try { setImageDrawable(entry.loadIcon(pm)) } catch (_: Exception) { }
            }
            put(tile, icon, 13, 11, 40, 40)
            val title = label(entry.loadLabel(pm).toString(), 20, Color.WHITE, true)
            title.maxLines = 1
            put(tile, title, 62, 5, 132, 51)
            interactive(tile) { openApp(pack) }
            x += 216
        }
        val other = FrameLayout(this).apply { background = rounded(0xE019202A.toInt(), 15, 0x2BFFFFFF, 1) }
        put(appsContainer, other, x.coerceAtMost(1530), 0, 145, 62)
        put(other, label("التطبيقات  +", 19, Color.WHITE, true, Gravity.CENTER), 0, 0, 145, 62)
        interactive(other) { showAllApps() }
    }

    private fun updateClock() {
        val now = Date()
        val hour = Calendar.getInstance(riyadh).get(Calendar.HOUR_OF_DAY)
        val ar = Locale("ar", "SA")
        clockView.text = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = riyadh }.format(now)
        dateView.text = SimpleDateFormat("EEEE، d MMMM", ar).apply { timeZone = riyadh }.format(now)
        greetingView.text = when (hour) {
            in 5..11 -> "صباح الخير، محمد"
            in 12..17 -> "مساء الخير، محمد"
            else -> "ليلتك جميلة، محمد"
        }
        headingView.text = when (hour) {
            in 5..11 -> "صباحك يبدأ هنا"
            in 12..18 -> "جديد اليوم"
            else -> "مختارة لهذه الليلة"
        }
        weatherView.text = weather?.let { "الرياض  ·  " + it.temperature + "°  " + weatherWord(it.code) }
            ?: "الرياض  ·  الطقس غير متاح"
    }

    private fun weatherWord(c: Int): String = when {
        c == 0 -> "صحو"
        c in 1..3 -> "غائم جزئيًا"
        c in 45..48 -> "ضباب"
        c in 51..67 || c in 80..82 -> "مطر"
        c in 95..99 -> "عواصف"
        else -> "متغير"
    }

    private fun changeAtmosphere() {
        val cal = Calendar.getInstance(riyadh)
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val condition = weather?.code ?: -1
        val override = preferences.getString("wallpaper", "auto") ?: "auto"
        val selected = if (override != "auto") override else when {
            condition in 51..67 || condition in 80..82 || condition in 95..99 -> "rain"
            condition in 2..3 -> "cloud"
            h in 6..15 -> "day"
            h in 16..18 -> "sunset"
            else -> "night"
        }
        if (selected == sceneName) return
        sceneName = selected
        try {
            assets.open("kafd_" + selected + ".jpg").use {
                val options = BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                wallpaper.setImageBitmap(BitmapFactory.decodeStream(it, null, options))
            }
        } catch (_: Exception) { wallpaper.setBackgroundColor(0xFF111823.toInt()) }
    }

    private fun fetchContent() {
        val now = System.currentTimeMillis()
        if (now >= nextWeatherRefresh) {
            nextWeatherRefresh = now + 30 * 60_000L
            worker.execute {
                try {
                    val info = RemoteData.weather()
                    runOnUiThread { if (!isDestroyed) { weather = info; updateClock(); changeAtmosphere() } }
                } catch (_: Exception) { }
            }
        }
        if (now >= nextVideoRefresh) {
            nextVideoRefresh = now + 20 * 60_000L
            val custom = preferences.getString("channels", "")?.trim().orEmpty()
            val channels = if (custom.isBlank()) listOf(
                RemoteData.UNITED_CHANNEL, "UCWOA1ZGywLbqmigxE4Qlvuw", "UCi8e0iOVk1fEOogdfu4YgfA"
            ) else custom.split(",", " ", "\n").map { it.trim() }.filter { it.isNotBlank() }.take(4)
            worker.execute {
                try {
                    val items = RemoteData.youtube(channels)
                    if (items.isNotEmpty()) {
                        cache(items)
                        runOnUiThread { if (!isDestroyed) showVideos(items) }
                    }
                } catch (_: Exception) { }
            }
        }
        if (now >= nextMatchRefresh) {
            nextMatchRefresh = now + 60 * 60_000L
            val key = preferences.getString("football_key", "").orEmpty()
            if (key.isNotEmpty()) worker.execute {
                val found = try { RemoteData.nextMatch(key) } catch (_: Exception) { null }
                runOnUiThread {
                    if (!isDestroyed) {
                        nextMatch = found
                        matchTitle.text = found?.let { it.home + "  ×  " + it.away } ?: "المباراة القادمة غير متاحة"
                        matchSub.text = found?.let { it.competition + " · " + formatMatch(it.utc) }
                            ?: "افتح جدول يونايتد الرسمي"
                    }
                }
            }
        }
    }

    private fun cache(items: List<VideoItem>) {
        val arr = JSONArray()
        for (v in items.take(12)) {
            arr.put(JSONObject().put("id", v.id).put("title", v.title)
                .put("channel", v.channel).put("published", v.published))
        }
        preferences.edit().putString("recent_feed", arr.toString()).apply()
    }
    private fun recoverFeed() {
        try {
            val raw = preferences.getString("recent_feed", "") ?: return
            val arr = JSONArray(raw)
            val restored = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VideoItem(o.getString("id"), o.getString("title"), o.getString("channel"), o.getString("published"))
            }
            if (restored.isNotEmpty()) showVideos(restored)
        } catch (_: Exception) { }
    }

    private fun openVideo(v: VideoItem) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url)).apply { setPackage("com.google.android.youtube.tv") }) }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url))) }
            catch (_: Exception) { toast("تعذر فتح الفيديو") }
        }
    }
    private fun openYoutube() = openApp("com.google.android.youtube.tv")
    private fun openApp(pack: String) {
        try {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(pack)
                ?: packageManager.getLaunchIntentForPackage(pack)
            if (intent == null) toast("التطبيق غير متوفر") else startActivity(intent)
        } catch (_: Exception) { toast("تعذر فتح التطبيق") }
    }
    private fun openDeviceSettings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
    }
    private fun openMatch() {
        val m = nextMatch
        val value = m?.let { it.home + " × " + it.away + "\n" + it.competition + "\n" + formatMatch(it.utc) }
            ?: "لإظهار المباراة المقبلة مباشرة، أضف مفتاح football-data.org من الإعدادات."
        AlertDialog.Builder(this).setTitle("مانشستر يونايتد").setMessage(value)
            .setPositiveButton("جدول الفريق") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.manutd.com/en/matches"))) }
                catch (_: Exception) { toast("تعذر فتح الجدول") }
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun formatMatch(utc: String): String = try {
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parse.timeZone = TimeZone.getTimeZone("UTC")
        val date = parse.parse(utc)
        if (date == null) "" else SimpleDateFormat("d MMMM · h:mm a", Locale("ar", "SA"))
            .apply { timeZone = riyadh }.format(date)
    } catch (_: Exception) { "" }

    private fun showAllApps() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("تطبيقات التلفزيون")
            .setItems(found.map { it.loadLabel(pm).toString() }.toTypedArray()) { _, i ->
                openApp(found[i].activityInfo.packageName)
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun showSettings() {
        val choices = arrayOf("تخصيص قنوات YouTube", "مفتاح مباريات يونايتد", "أجواء كافد", "تحديث المحتوى")
        AlertDialog.Builder(this).setTitle("إعدادات AURA")
            .setItems(choices) { _, index ->
                when (index) {
                    0 -> edit("قنوات YouTube", "channels", "اكتب Channel IDs مفصولة بفاصلة. اتركها فارغة للقنوات الافتراضية")
                    1 -> edit("مفتاح football-data.org", "football_key", "المفتاح يُحفظ على التلفزيون، وليس على GitHub")
                    2 -> pickScene()
                    else -> { nextVideoRefresh = 0; nextMatchRefresh = 0; nextWeatherRefresh = 0; fetchContent(); toast("تم طلب تحديث المحتوى") }
                }
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun edit(title: String, key: String, message: String) {
        val input = EditText(this).apply {
            setSingleLine(true); setText(preferences.getString(key, "").orEmpty())
            setPadding(px(20), px(18), px(20), px(18))
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                preferences.edit().putString(key, input.text.toString().trim()).apply()
                nextVideoRefresh = 0; nextMatchRefresh = 0; fetchContent()
            }.setNegativeButton("إلغاء", null).show()
    }
    private fun pickScene() {
        val names = arrayOf("تلقائي", "ليل", "غروب", "صباح", "مطر", "غيم")
        val keys = arrayOf("auto", "night", "sunset", "day", "rain", "cloud")
        AlertDialog.Builder(this).setTitle("الخلفية")
            .setSingleChoiceItems(names, keys.indexOf(preferences.getString("wallpaper", "auto")).coerceAtLeast(0)) { dialog, which ->
                preferences.edit().putString("wallpaper", keys[which]).apply()
                sceneName = ""; changeAtmosphere(); dialog.dismiss()
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
}
