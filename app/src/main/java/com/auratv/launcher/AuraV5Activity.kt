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
 * AURA 5: structured, responsive television layout.
 * No baked-in cards, no absolutely-positioned text over external thumbnails.
 * Full background is art; every clickable item is a native focusable Android view.
 */
class AuraV5Activity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val images = ImageLoader()
    private val prefs by lazy { getSharedPreferences("aura_lounge_v5", MODE_PRIVATE) }
    private val zone = TimeZone.getTimeZone("Asia/Riyadh")
    private var scale = 1f

    private lateinit var backgroundImage: ImageView
    private lateinit var timeLabel: TextView
    private lateinit var dateLabel: TextView
    private lateinit var weatherLabel: TextView
    private lateinit var helloLabel: TextView
    private lateinit var videoHeading: TextView
    private lateinit var videoImage: ImageView
    private lateinit var videoTitle: TextView
    private lateinit var videoSource: TextView
    private lateinit var fixtureHeading: TextView
    private lateinit var fixtureTeams: TextView
    private lateinit var fixtureDetail: TextView
    private lateinit var thumbnails: LinearLayout
    private lateinit var appDock: LinearLayout
    private lateinit var featuredPanel: FrameLayout

    private var weather: WeatherItem? = null
    private var fixture: MatchItem? = null
    private var videos: List<VideoItem> = emptyList()
    private var displayedVideoId = ""
    private var activeScene = ""
    private var nextWeather = 0L
    private var nextYouTube = 0L
    private var nextFixture = 0L

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            updateScene()
            rotateFeature()
            ui.postDelayed(this, 60_000L)
        }
    }
    private val dataTick = object : Runnable {
        override fun run() {
            fetchData()
            ui.postDelayed(this, 10 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val dm = resources.displayMetrics
        scale = min(dm.widthPixels / 1920f, dm.heightPixels / 1080f)
        buildScreen()
        restoreVideos()
        updateClock()
        updateScene()
        featuredPanel.post { featuredPanel.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(clockTick)
        ui.removeCallbacks(dataTick)
        ui.post(clockTick)
        ui.post(dataTick)
    }
    override fun onPause() {
        ui.removeCallbacks(clockTick)
        ui.removeCallbacks(dataTick)
        super.onPause()
    }
    override fun onDestroy() {
        pool.shutdownNow()
        images.shutdown()
        super.onDestroy()
    }

    private fun u(value: Int) = (value * scale).roundToInt().coerceAtLeast(1)
    private fun shape(color: Int, radius: Int = 20, stroke: Int = 0x30FFFFFF, strokeSize: Int = 1) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = u(radius).toFloat()
            if (strokeSize > 0) setStroke(u(strokeSize), stroke)
        }

    private fun gradient(direction: GradientDrawable.Orientation, vararg colors: Int) =
        GradientDrawable(direction, colors)

    private fun text(
        initial: String,
        size: Int,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        gravityValue: Int = Gravity.CENTER_VERTICAL,
        lines: Int = 1
    ) = TextView(this).apply {
        text = initial
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, u(size).toFloat())
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        gravity = gravityValue
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
    }
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
    }
    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, u(height))
    }
    private fun gap(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(u(width), 1)
    }

    private fun panel(): FrameLayout = FrameLayout(this).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = shape(0xEE0D121B.toInt(), 22, 0x33FFFFFF, 1)
        clipToOutline = true
        clipChildren = true
    }

    private fun focusable(target: FrameLayout, click: () -> Unit) {
        target.isFocusable = true
        target.isClickable = true
        target.setOnClickListener { click() }
        val border = View(this).apply {
            background = shape(Color.TRANSPARENT, 22, 0xFFE9E5E2.toInt(), 2)
            isFocusable = false
            isClickable = false
            alpha = 0f
        }
        target.addView(border, FrameLayout.LayoutParams(-1, -1))
        target.setOnFocusChangeListener { _, focused ->
            target.animate().cancel()
            target.animate().scaleX(if (focused) 1.012f else 1f)
                .scaleY(if (focused) 1.012f else 1f).setDuration(150).start()
            border.animate().alpha(if (focused) 0.87f else 0f).setDuration(145).start()
        }
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF080C13.toInt()) }
        backgroundImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(backgroundImage, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = gradient(GradientDrawable.Orientation.TOP_BOTTOM,
                0x66080C14, 0x58080C14, 0xCB070A11.toInt(), 0xF8080A0E.toInt())
        }, FrameLayout.LayoutParams(-1, -1))
        val body = row().apply { setPadding(u(40), u(35), u(64), u(36)) }
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        val rail = column()
        body.addView(rail, LinearLayout.LayoutParams(u(88), -1))
        rail.addView(spacer(286))
        addRailItem(rail, R.drawable.ic_wifi, "الشبكة") {
            try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } catch (_: Exception) { openDeviceSettings() }
        }
        rail.addView(spacer(20))
        addRailItem(rail, R.drawable.ic_home, "الرئيسية") { featuredPanel.requestFocus() }
        rail.addView(spacer(20))
        addRailItem(rail, R.drawable.ic_settings, "الإعدادات") { showSettings() }
        body.addView(gap(36))

        val content = column()
        body.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        makeHeader(content)
        makeWelcome(content)

        val spotlight = row()
        content.addView(spotlight, LinearLayout.LayoutParams(-1, u(286)))
        val match = makeFixture()
        spotlight.addView(match, LinearLayout.LayoutParams(0, -1, 0.40f))
        spotlight.addView(gap(22))
        featuredPanel = makeFeatured()
        spotlight.addView(featuredPanel, LinearLayout.LayoutParams(0, -1, 0.60f))

        content.addView(spacer(19))
        val section = row()
        content.addView(section, LinearLayout.LayoutParams(-1, u(47)))
        videoHeading = text("جديد الليلة", 25, Color.WHITE, true)
        section.addView(videoHeading, LinearLayout.LayoutParams(0, -1, 1f))
        section.addView(text("محتوى فعلي من YouTube", 17, 0xFFB8BFCA.toInt(),
            false, Gravity.RIGHT or Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(u(420), -1))

        thumbnails = row()
        content.addView(thumbnails, LinearLayout.LayoutParams(-1, u(207)))
        drawVideoPlaceholder()
        content.addView(spacer(18))

        appDock = row().apply {
            setPadding(u(10), u(6), u(10), u(6))
            background = shape(0xCB0A1018.toInt(), 18, 0x2EFFFFFF, 1)
        }
        content.addView(appDock, LinearLayout.LayoutParams(-1, u(85)))
        buildApps()
    }

    private fun addRailItem(parent: LinearLayout, icon: Int, description: String, onClick: () -> Unit) {
        val tile = panel().apply { background = shape(0x7A101720, 17, 0x26FFFFFF, 1) }
        parent.addView(tile, LinearLayout.LayoutParams(u(70), u(70)).apply { leftMargin = u(8) })
        val img = ImageView(this).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        tile.addView(img, FrameLayout.LayoutParams(u(27), u(27), Gravity.CENTER))
        tile.contentDescription = description
        focusable(tile, onClick)
    }

    private fun makeHeader(content: LinearLayout) {
        val header = row()
        content.addView(header, LinearLayout.LayoutParams(-1, u(92)))
        val logo = column()
        header.addView(logo, LinearLayout.LayoutParams(0, -1, 1f))
        val wordmark = text("A U R A", 27, Color.WHITE, true).apply { letterSpacing = .18f }
        logo.addView(wordmark, LinearLayout.LayoutParams(-1, u(49)))
        logo.addView(View(this).apply { setBackgroundColor(0xFFCC3544.toInt()) },
            LinearLayout.LayoutParams(u(55), u(3)))

        weatherLabel = text("الرياض", 23, 0xFFE4E6EB.toInt())
        header.addView(weatherLabel, LinearLayout.LayoutParams(u(285), u(62)))
        header.addView(gap(24))
        val clockBlock = column()
        header.addView(clockBlock, LinearLayout.LayoutParams(u(190), -1))
        timeLabel = text("", 48, Color.WHITE, true, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        clockBlock.addView(timeLabel, LinearLayout.LayoutParams(-1, u(58)))
        dateLabel = text("", 18, 0xFFCCD0D8.toInt(), false, Gravity.RIGHT or Gravity.CENTER_VERTICAL)
        clockBlock.addView(dateLabel, LinearLayout.LayoutParams(-1, u(24)))
        header.addView(gap(24))

        val devil = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try { assets.open("red_devil.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) } }
            catch (_: Exception) { }
        }
        header.addView(devil, LinearLayout.LayoutParams(u(32), u(53)))
    }

    private fun makeWelcome(content: LinearLayout) {
        val welcome = column()
        content.addView(welcome, LinearLayout.LayoutParams(-1, u(212)))
        welcome.addView(spacer(50))
        helloLabel = text("مساء الخير، محمد", 59, Color.WHITE, true)
        welcome.addView(helloLabel, LinearLayout.LayoutParams(-1, u(77)))
        welcome.addView(spacer(10))
        welcome.addView(View(this).apply { setBackgroundColor(0xFFCF3748.toInt()) },
            LinearLayout.LayoutParams(u(64), u(4)))
    }

    private fun makeFixture(): FrameLayout {
        val card = panel()
        val wash = View(this).apply {
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT,
                0x8A2B0712.toInt(), 0x6A140B13, 0x1410141A)
        }
        card.addView(wash, FrameLayout.LayoutParams(-1, -1))
        val inner = column().apply { setPadding(u(34), u(25), u(27), u(20)) }
        card.addView(inner, FrameLayout.LayoutParams(-1, -1))
        fixtureHeading = text("MANCHESTER UNITED  /  NEXT MATCH", 19, 0xFFDDBFC2.toInt(), true)
        inner.addView(fixtureHeading, LinearLayout.LayoutParams(-1, u(44)))
        inner.addView(View(this).apply { setBackgroundColor(0xFFCA3040.toInt()) },
            LinearLayout.LayoutParams(u(46), u(3)))
        inner.addView(spacer(20))
        fixtureTeams = text("مباريات مانشستر يونايتد", 36, Color.WHITE, true, Gravity.CENTER_VERTICAL, 2)
        inner.addView(fixtureTeams, LinearLayout.LayoutParams(-1, u(92)))
        inner.addView(spacer(16))
        fixtureDetail = text("اضغط لفتح الجدول الرسمي", 21, 0xFFCCD1D9.toInt(), false, Gravity.CENTER_VERTICAL, 2)
        inner.addView(fixtureDetail, LinearLayout.LayoutParams(-1, u(55)))
        card.contentDescription = "جدول مباريات مانشستر يونايتد"
        focusable(card) { openFixture() }
        return card
    }

    private fun makeFeatured(): FrameLayout {
        val card = panel()
        videoImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF18232E.toInt())
        }
        card.addView(videoImage, FrameLayout.LayoutParams(-1, -1))
        card.addView(View(this).apply {
            background = gradient(GradientDrawable.Orientation.LEFT_RIGHT,
                0xF70B1018.toInt(), 0xE50C131C.toInt(), 0x7D0B1119, 0x210B1119)
        }, FrameLayout.LayoutParams(-1, -1))
        val stack = column().apply { setPadding(u(32), u(22), 0, u(14)) }
        card.addView(stack, FrameLayout.LayoutParams(u(615), -1, Gravity.LEFT or Gravity.TOP))
        val tag = text("YOUTUBE  ·  اكتشف", 19, 0xFFD6A8B0.toInt(), true)
        stack.addView(tag, LinearLayout.LayoutParams(-1, u(43)))
        stack.addView(spacer(12))
        videoTitle = text("أحدث فيديوهاتك في مكان واحد", 36, Color.WHITE, true,
            Gravity.TOP or Gravity.LEFT, 2)
        stack.addView(videoTitle, LinearLayout.LayoutParams(-1, u(112)))
        videoSource = text("محتوى متجدد من القنوات المختارة", 20, 0xFFCDD1D8.toInt())
        stack.addView(videoSource, LinearLayout.LayoutParams(-1, u(42)))
        val watch = text("▶  شاهد على YouTube", 20, 0xFFF0E8E9.toInt(), true)
        stack.addView(watch, LinearLayout.LayoutParams(-1, u(46)))
        card.contentDescription = "الفيديو المميز"
        focusable(card) { currentVideo()?.let { openVideo(it) } ?: openYoutube() }
        return card
    }

    private fun drawVideoPlaceholder() {
        thumbnails.removeAllViews()
        for (i in 0 until 5) {
            val tile = panel().apply { background = shape(0xD40B1018.toInt(), 14, 0x24FFFFFF, 1) }
            thumbnails.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
            if (i < 4) thumbnails.addView(gap(17))
        }
    }

    private fun showVideos(items: List<VideoItem>) {
        videos = items
        thumbnails.removeAllViews()
        if (items.isEmpty()) { drawVideoPlaceholder(); return }
        rotateFeature(force = true)
        for (i in 0 until 5) {
            val item = items.getOrNull(i)
            val tile = panel().apply { background = shape(0xE30C111B.toInt(), 14, 0x22FFFFFF, 1) }
            thumbnails.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
            if (item != null) {
                val block = column()
                tile.addView(block, FrameLayout.LayoutParams(-1, -1))
                val poster = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF101924.toInt())
                }
                block.addView(poster, LinearLayout.LayoutParams(-1, u(140)))
                images.load(poster, item.thumbnail)
                val caption = text(item.title, 18, 0xFFF3F4F7.toInt(), true,
                    Gravity.CENTER_VERTICAL, 2).apply { setPadding(u(12), 0, u(10), 0) }
                block.addView(caption, LinearLayout.LayoutParams(-1, 0, 1f))
                tile.contentDescription = item.title
                focusable(tile) { openVideo(item) }
            }
            if (i < 4) thumbnails.addView(gap(17))
        }
    }

    private fun buildApps() {
        appDock.removeAllViews()
        val pm = packageManager
        val found = linkedMapOf<String, android.content.pm.ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            for (entry in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val pack = entry.activityInfo?.packageName ?: continue
                if (pack != packageName) found.putIfAbsent(pack, entry)
            }
        }
        val priorities = listOf("youtube", "netflix", "shahid", "primevideo", "disney")
        val sorted = found.entries.sortedWith(compareBy<Map.Entry<String, android.content.pm.ResolveInfo>> {
            priorities.indexOfFirst { keyword -> it.key.lowercase(Locale.US).contains(keyword) }
                .let { priority -> if (priority < 0) 99 else priority }
        }.thenBy { it.value.loadLabel(pm).toString() }).take(5)

        for ((pack, entry) in sorted) {
            val tile = panel().apply { background = shape(0x14FFFFFF, 14, Color.TRANSPARENT, 0) }
            appDock.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
            appDock.addView(gap(10))
            val line = row().apply {
                setPadding(u(14), u(8), u(9), u(8))
                gravity = Gravity.CENTER_VERTICAL
            }
            tile.addView(line, FrameLayout.LayoutParams(-1, -1))
            val logo = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                try { setImageDrawable(entry.loadIcon(pm)) } catch (_: Exception) { }
            }
            line.addView(logo, LinearLayout.LayoutParams(u(43), u(43)))
            line.addView(gap(10))
            val name = entry.loadLabel(pm).toString()
            line.addView(text(name, 17, 0xFFEEF0F4.toInt(), true),
                LinearLayout.LayoutParams(0, -1, 1f))
            tile.contentDescription = name
            focusable(tile) { openApp(pack) }
        }
        val tile = panel().apply { background = shape(0x14FFFFFF, 14, Color.TRANSPARENT, 0) }
        appDock.addView(tile, LinearLayout.LayoutParams(0, -1, 1f))
        val label = text("▦  كل التطبيقات", 19, Color.WHITE, true, Gravity.CENTER)
        tile.addView(label, FrameLayout.LayoutParams(-1, -1))
        focusable(tile) { allApps() }
    }

    private fun updateClock() {
        val now = Date()
        val hour = Calendar.getInstance(zone).get(Calendar.HOUR_OF_DAY)
        timeLabel.text = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(now)
        dateLabel.text = SimpleDateFormat("EEEE، d MMMM", Locale("ar", "SA"))
            .apply { timeZone = zone }.format(now)
        helloLabel.text = when (hour) {
            in 5..11 -> "صباح الخير، محمد"
            in 12..18 -> "مساء الخير، محمد"
            else -> "ليلتك جميلة، محمد"
        }
        videoHeading.text = when (hour) {
            in 5..11 -> "جديد هذا الصباح"
            in 12..18 -> "اكتشف اليوم"
            else -> "جديد الليلة"
        }
        weatherLabel.text = weather?.let {
            "الرياض   " + it.temperature + "°  ·  " + weatherText(it.code)
        } ?: "الرياض   —"
    }

    private fun weatherText(code: Int): String = when {
        code == 0 -> "صحو"
        code in 1..3 -> "غائم"
        code in 45..48 -> "ضباب"
        code in 51..67 || code in 80..82 -> "مطر"
        code in 95..99 -> "عواصف"
        else -> "متغير"
    }

    private fun updateScene() {
        val hour = Calendar.getInstance(zone).get(Calendar.HOUR_OF_DAY)
        val c = weather?.code ?: -1
        val sceneSetting = prefs.getString("scene", "auto").orEmpty()
        val scene = if (sceneSetting != "auto") sceneSetting else when {
            c in 51..67 || c in 80..82 || c in 95..99 -> "rain"
            c in 2..3 -> "cloud"
            hour in 6..15 -> "day"
            hour in 16..18 -> "sunset"
            else -> "night"
        }
        if (activeScene == scene) return
        activeScene = scene
        try {
            assets.open("kafd_" + scene + ".jpg").use {
                backgroundImage.setImageBitmap(BitmapFactory.decodeStream(it))
            }
        } catch (_: Exception) { backgroundImage.setBackgroundColor(0xFF0C1522.toInt()) }
    }

    private fun currentVideo(): VideoItem? {
        if (videos.isEmpty()) return null
        val slot = ((System.currentTimeMillis() / 1_800_000L) % videos.size).toInt()
        return videos.getOrNull(slot)
    }

    private fun rotateFeature(force: Boolean = false) {
        val v = currentVideo() ?: return
        if (!force && displayedVideoId == v.id) return
        displayedVideoId = v.id
        videoTitle.text = v.title
        videoSource.text = v.channel
        images.load(videoImage, v.thumbnail)
    }

    private fun fetchData() {
        val now = System.currentTimeMillis()
        if (now >= nextWeather) {
            nextWeather = now + 40 * 60_000L
            pool.execute {
                try {
                    val result = RemoteData.weather()
                    runOnUiThread {
                        if (!isDestroyed) { weather = result; updateClock(); updateScene() }
                    }
                } catch (_: Exception) { }
            }
        }
        if (now >= nextYouTube) {
            nextYouTube = now + 20 * 60_000L
            val custom = prefs.getString("channels", "").orEmpty()
            val channels = if (custom.isBlank()) listOf(
                RemoteData.UNITED_CHANNEL, "UCWOA1ZGywLbqmigxE4Qlvuw"
            ) else custom.split(",", " ", "\n").map { it.trim() }.filter { it.isNotBlank() }.take(4)
            pool.execute {
                try {
                    val items = RemoteData.youtube(channels)
                    if (items.isNotEmpty()) {
                        cacheVideos(items)
                        runOnUiThread { if (!isDestroyed) showVideos(items) }
                    }
                } catch (_: Exception) { }
            }
        }
        if (now >= nextFixture) {
            nextFixture = now + 60 * 60_000L
            val key = prefs.getString("football_key", "").orEmpty()
            if (key.isNotBlank()) pool.execute {
                val found = try { RemoteData.nextMatch(key) } catch (_: Exception) { null }
                runOnUiThread {
                    if (!isDestroyed) {
                        fixture = found
                        fixtureHeading.text = found?.competition ?: "MANCHESTER UNITED  /  NEXT MATCH"
                        fixtureTeams.text = found?.let { it.home + "    ×    " + it.away }
                            ?: "المباراة القادمة غير متاحة"
                        fixtureDetail.text = found?.let { matchDate(it.utc) }
                            ?: "افتح جدول الفريق الرسمي"
                    }
                }
            }
        }
    }

    private fun cacheVideos(items: List<VideoItem>) {
        val arr = JSONArray()
        for (v in items.take(14)) {
            arr.put(JSONObject().put("id", v.id).put("title", v.title)
                .put("channel", v.channel).put("published", v.published))
        }
        prefs.edit().putString("videos", arr.toString()).apply()
    }
    private fun restoreVideos() {
        try {
            val source = prefs.getString("videos", "") ?: return
            val arr = JSONArray(source)
            val values = (0 until arr.length()).map {
                val x = arr.getJSONObject(it)
                VideoItem(x.getString("id"), x.getString("title"),
                    x.getString("channel"), x.getString("published"))
            }
            if (values.isNotEmpty()) showVideos(values)
        } catch (_: Exception) { }
    }

    private fun matchDate(utc: String): String = try {
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parse.timeZone = TimeZone.getTimeZone("UTC")
        val date = parse.parse(utc)
        if (date == null) "" else SimpleDateFormat("EEEE، d MMMM  ·  HH:mm", Locale("ar", "SA"))
            .apply { timeZone = zone }.format(date)
    } catch (_: Exception) { "" }

    private fun openVideo(item: VideoItem) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
        try { startActivity(intent.apply { setPackage("com.google.android.youtube.tv") }) }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
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
    private fun openFixture() {
        val details = fixture?.let {
            it.home + " × " + it.away + "\n" + it.competition + "\n" + matchDate(it.utc)
        } ?: "مواعيد المباريات المباشرة تحتاج مفتاح football-data.org. يمكنك عرض جدول الفريق الرسمي."
        AlertDialog.Builder(this).setTitle("مانشستر يونايتد")
            .setMessage(details)
            .setPositiveButton("جدول الفريق") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.manutd.com/en/matches"))) }
                catch (_: Exception) { toast("تعذر فتح الجدول") }
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun allApps() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val all = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
        AlertDialog.Builder(this).setTitle("تطبيقات التلفزيون")
            .setItems(all.map { it.loadLabel(pm).toString() }.toTypedArray()) { _, index ->
                openApp(all[index].activityInfo.packageName)
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun showSettings() {
        val names = arrayOf("قنوات YouTube", "مفتاح مباريات يونايتد", "خلفية كافد", "تحديث المحتوى")
        AlertDialog.Builder(this).setTitle("إعدادات AURA")
            .setItems(names) { _, index ->
                when (index) {
                    0 -> editSetting("قنوات YouTube", "channels", "Channel IDs مفصولة بفاصلة؛ فارغ = القنوات الافتراضية")
                    1 -> editSetting("مفتاح football-data.org", "football_key", "يُحفظ داخل التلفزيون فقط")
                    2 -> chooseScene()
                    3 -> { nextYouTube = 0; nextWeather = 0; nextFixture = 0; fetchData(); toast("جاري تحديث المحتوى") }
                }
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun editSetting(title: String, key: String, hint: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString(key, "").orEmpty())
            setPadding(u(18), u(14), u(18), u(14))
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(hint).setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString(key, input.text.toString().trim()).apply()
                nextYouTube = 0; nextFixture = 0; fetchData()
            }.setNegativeButton("إلغاء", null).show()
    }
    private fun chooseScene() {
        val names = arrayOf("تلقائي", "ليل", "غروب", "صباح", "مطر", "غيم")
        val keys = arrayOf("auto", "night", "sunset", "day", "rain", "cloud")
        AlertDialog.Builder(this).setTitle("خلفية كافد")
            .setSingleChoiceItems(names, keys.indexOf(prefs.getString("scene", "auto")).coerceAtLeast(0)) { dialog, i ->
                prefs.edit().putString("scene", keys[i]).apply()
                activeScene = ""
                updateScene()
                dialog.dismiss()
            }.setNegativeButton("إغلاق", null).show()
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
