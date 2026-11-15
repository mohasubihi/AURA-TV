package com.auratv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/** AURA 3 — real View hierarchy (not a single fixed 1920x1080 Canvas). */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val network = Executors.newFixedThreadPool(2)
    private val images = ImageLoader()
    private val prefs by lazy { getSharedPreferences("aura_v3", MODE_PRIVATE) }
    private lateinit var background: ImageView
    private lateinit var timeLabel: TextView
    private lateinit var dateLabel: TextView
    private lateinit var weatherLabel: TextView
    private lateinit var matchHeader: TextView
    private lateinit var competitionLabel: TextView
    private lateinit var matchTeams: TextView
    private lateinit var matchDetails: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroChannel: TextView
    private lateinit var heroImage: ImageView
    private lateinit var heroCard: FrameLayout
    private lateinit var videoRow: LinearLayout
    private lateinit var videoSectionTitle: TextView
    private lateinit var appRow: LinearLayout
    private lateinit var focusFirst: View
    private var videos: List<VideoItem> = emptyList()
    private var activeHeroIndex = -1
    private var match: MatchItem? = null
    private var weather: WeatherItem? = null
    private var scene = ""
    private var lastWeather = 0L
    private var lastVideos = 0L
    private var lastMatches = 0L

    private fun dp(n: Number) = AuraStyle.dp(this, n)
    private fun text(value: String, size: Float, color: Int = AuraStyle.WHITE,
                     bold: Boolean = false, right: Boolean = false) =
        AuraStyle.text(this, value, size, color, bold, right)

    private val clockTick = object : Runnable {
        override fun run() {
            updateClockAndScene()
            rotateFeature()
            handler.postDelayed(this, 30_000)
        }
    }
    private val dataTick = object : Runnable {
        override fun run() {
            refreshData()
            handler.postDelayed(this, 12 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        buildScreen()
        restoreCachedVideos()
        updateClockAndScene()
        focusFirst.post { focusFirst.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(dataTick)
        handler.post(clockTick)
        handler.post(dataTick)
    }

    override fun onPause() {
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(dataTick)
        super.onPause()
    }

    override fun onDestroy() {
        network.shutdownNow()
        images.shutdown()
        super.onDestroy()
    }

    private fun buildScreen() {
        val metrics = resources.displayMetrics
        val heightDp = metrics.heightPixels / metrics.density
        val safe = (heightDp * .035f).toInt().coerceIn(16, 36)
        val available = (heightDp - 2 * safe).toInt()

        val root = FrameLayout(this).apply { setBackgroundColor(AuraStyle.INK) }
        background = AuraStyle.cover(this)
        root.addView(background, AuraStyle.full())
        // Only photograph + atmosphere here, no UI baked into this bitmap.
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x7A060914, 0x45060810, 0xE8080B14.toInt(), 0xF8080B14.toInt()))
        }, AuraStyle.full())

        val shell = AuraStyle.row(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(safe), dp(safe), dp(safe), dp(safe))
        }
        root.addView(shell, AuraStyle.full())
        val rail = makeRail()
        shell.addView(rail, LinearLayout.LayoutParams(dp(65), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            rightMargin = dp(20)
        })

        val content = AuraStyle.column(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        shell.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        content.addView(makeHeader(), LinearLayout.LayoutParams(-1, dp((available * .21f).toInt())))
        val hero = AuraStyle.row(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, dp((available * .35f).toInt())))
        val matchCard = makeMatchCard()
        hero.addView(matchCard, LinearLayout.LayoutParams(0, -1, .39f).apply { rightMargin = dp(15) })
        hero.addView(makeHeroCard(), LinearLayout.LayoutParams(0, -1, .61f))

        val videoHead = AuraStyle.row(this)
        videoSectionTitle = text("أحدث الفيديوهات · مانشستر يونايتد", 17f, AuraStyle.WHITE, true, true)
        videoHead.addView(videoSectionTitle, LinearLayout.LayoutParams(0, -1, 1f))
        content.addView(videoHead, LinearLayout.LayoutParams(-1, dp((available * .06f).toInt())))

        val videoScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            fillViewport = true
        }
        videoRow = AuraStyle.row(this).apply { clipChildren = false; setPadding(dp(3), dp(3), dp(3), dp(3)) }
        videoScroll.addView(videoRow, ViewGroup.LayoutParams(-2, -1))
        content.addView(videoScroll, LinearLayout.LayoutParams(-1, dp((available * .23f).toInt())))

        val appTitle = text("تطبيقات التلفزيون", 16f, AuraStyle.WHITE, true, true)
        content.addView(appTitle, LinearLayout.LayoutParams(-1, dp((available * .05f).toInt())))
        val appScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            fillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        appRow = AuraStyle.row(this).apply { clipChildren = false; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        appScroll.addView(appRow, ViewGroup.LayoutParams(-2, -1))
        content.addView(appScroll, LinearLayout.LayoutParams(-1, dp((available * .10f).toInt())))
        setContentView(root)
        renderEmptyVideos()
        renderInstalledApps()
    }

    private fun makeRail(): View {
        val rail = AuraStyle.column(this).apply {
            gravity = Gravity.CENTER
            background = AuraStyle.shape(0xAF11151F.toInt(), dp(24).toFloat(), 0x22FFFFFF, dp(1))
        }
        val entries: List<Triple<Int, String, () -> Unit>> = listOf(
            Triple(R.drawable.ic_wifi, "إعدادات الشبكة", {
                try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }),
            Triple(R.drawable.ic_home, "الرئيسية", { focusFirst.requestFocus() }),
            Triple(R.drawable.ic_settings, "إعدادات AURA", { openSettings() })
        )
        entries.forEachIndexed { index, (icon, title, action) ->
            val slot = FrameLayout(this).apply {
                contentDescription = title
                background = AuraStyle.shape(if (index == 1) 0x33FFFFFF else Color.TRANSPARENT, dp(15).toFloat())
            }
            val image = ImageView(this).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            slot.addView(image, FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER))
            AuraStyle.focus(this, slot, action)
            rail.addView(slot, LinearLayout.LayoutParams(dp(55), dp(60)).apply { bottomMargin = dp(12) })
        }
        return rail
    }

    private fun makeHeader(): View {
        val wrap = AuraStyle.row(this).apply { gravity = Gravity.TOP or Gravity.CENTER_VERTICAL }
        val greeting = AuraStyle.column(this)
        greeting.addView(text("A  U  R  A   ·   T V", 17f, AuraStyle.WHITE, true),
            LinearLayout.LayoutParams(-1, dp(29)))
        greeting.addView(text("مساء الخير، محمد", 28f, AuraStyle.WHITE, true),
            LinearLayout.LayoutParams(-1, dp(46)))
        greeting.addView(text("الرياض  ·  كرة قدم  ·  فيديوهات", 13f, AuraStyle.MUTED),
            LinearLayout.LayoutParams(-1, dp(24)))
        wrap.addView(greeting, LinearLayout.LayoutParams(0, -2, 1f))
        val details = AuraStyle.column(this).apply { gravity = Gravity.RIGHT }
        timeLabel = text("", 30f, AuraStyle.WHITE, true, true)
        dateLabel = text("", 12f, AuraStyle.MUTED, right = true)
        weatherLabel = text("الرياض · جاري تحديث الطقس", 14f, AuraStyle.WHITE, right = true)
        details.addView(timeLabel, LinearLayout.LayoutParams(-1, dp(40)))
        details.addView(dateLabel, LinearLayout.LayoutParams(-1, dp(24)))
        details.addView(weatherLabel, LinearLayout.LayoutParams(-1, dp(26)))
        wrap.addView(details, LinearLayout.LayoutParams(dp(215), -2))
        val devil = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                assets.open("red_devil.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) }
            } catch (_: Exception) { }
            contentDescription = "رمز مانشستر يونايتد الأحمر"
        }
        wrap.addView(devil, LinearLayout.LayoutParams(dp(25), dp(35)).apply { leftMargin = dp(14); topMargin = dp(3) })
        return wrap
    }

    private fun makeMatchCard(): FrameLayout {
        val card = AuraStyle.card(this)
        val body = AuraStyle.column(this).apply {
            setPadding(dp(23), dp(19), dp(20), dp(15))
        }
        val top = AuraStyle.row(this)
        competitionLabel = text("MANCHESTER UNITED", 12f, 0xFFDC9DA5.toInt(), true)
        top.addView(competitionLabel, LinearLayout.LayoutParams(0, dp(30), 1f))
        matchHeader = text("مانشستر يونايتد", 17f, AuraStyle.WHITE, true, true)
        top.addView(matchHeader, LinearLayout.LayoutParams(0, dp(30), 1f))
        body.addView(top)
        body.addView(View(this), LinearLayout.LayoutParams(-1, 0, .2f))
        matchTeams = text("المباراة القادمة", 21f, AuraStyle.WHITE, true, true)
        body.addView(matchTeams, LinearLayout.LayoutParams(-1, 0, .5f))
        matchDetails = text("أضف مفتاح المباريات من الإعدادات", 13f, AuraStyle.MUTED, right = true)
        body.addView(matchDetails, LinearLayout.LayoutParams(-1, 0, .32f))
        val bottom = text("جدول الفريق  ←", 13f, 0xFFFFA3AC.toInt(), true, true)
        body.addView(bottom, LinearLayout.LayoutParams(-1, dp(23)))
        card.addView(body, AuraStyle.full())
        AuraStyle.focus(this, card) { openMatch() }
        focusFirst = card
        return card
    }

    private fun makeHeroCard(): FrameLayout {
        heroCard = AuraStyle.card(this)
        heroImage = AuraStyle.cover(this)
        heroCard.addView(heroImage, AuraStyle.full())
        val shade = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xED0B0E18.toInt(), 0x990B0E18.toInt(), 0x190B0E18))
        }
        heroCard.addView(shade, AuraStyle.full())
        val copy = AuraStyle.column(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), dp(16), dp(22), dp(15))
        }
        heroChannel = text("YOUTUBE  ·  محتوى يتحدث تلقائيًا", 13f, 0xFFEE8D9C.toInt(), true)
        copy.addView(heroChannel, LinearLayout.LayoutParams(-1, dp(29)))
        heroTitle = text("جاري تحميل أحدث فيديو", 22f, AuraStyle.WHITE, true)
        heroTitle.maxLines = 3
        copy.addView(heroTitle, LinearLayout.LayoutParams(-1, dp(104)))
        copy.addView(text("شاهد على YouTube  ←", 14f, AuraStyle.WHITE, true),
            LinearLayout.LayoutParams(-1, dp(34)))
        heroCard.addView(copy, FrameLayout.LayoutParams(-1, -1))
        AuraStyle.focus(this, heroCard) { videos.getOrNull(activeHeroIndex)?.let { openVideo(it) } ?: openYouTube() }
        return heroCard
    }

    private fun renderEmptyVideos() {
        videoRow.removeAllViews()
        val note = text("يتم تحميل الفيديوهات الجديدة من قناة مانشستر يونايتد الرسمية…", 16f, AuraStyle.MUTED)
        videoRow.addView(note, LinearLayout.LayoutParams(-2, -1).apply { leftMargin = dp(14) })
    }

    private fun renderVideos(items: List<VideoItem>) {
        videos = items
        videoRow.removeAllViews()
        if (items.isEmpty()) {
            activeHeroIndex = -1
            heroChannel.text = "YOUTUBE"
            heroTitle.text = "لا توجد فيديوهات متاحة الآن"
            heroImage.setImageDrawable(null)
            videoSectionTitle.text = "فيديوهات YouTube · لم تتوفر بيانات جديدة"
            val retry = AuraStyle.card(this)
            retry.addView(text("إعادة المحاولة  ↻", 16f, AuraStyle.WHITE, true).apply { gravity = Gravity.CENTER }, AuraStyle.full())
            AuraStyle.focus(this, retry) { lastVideos = 0L; refreshData() }
            videoRow.addView(retry, LinearLayout.LayoutParams(dp(205), -1))
            return
        }
        activeHeroIndex = -1
        rotateFeature()
        val tileWidth = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * .157f)
            .toInt().coerceIn(135, 265)
        for (video in items.take(12)) {
            val card = AuraStyle.card(this, 13)
            val preview = AuraStyle.cover(this)
            card.addView(preview, AuraStyle.full())
            images.load(preview, video.thumbnail)
            val mask = View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x00000000, 0x99050912.toInt(), 0xF0050912.toInt()))
            }
            card.addView(mask, AuraStyle.full())
            val label = text(video.title, 13f, AuraStyle.WHITE, true)
            label.setPadding(dp(11), dp(3), dp(9), dp(5))
            label.maxLines = 2
            card.addView(label, FrameLayout.LayoutParams(-1, dp(50), Gravity.BOTTOM))
            card.contentDescription = video.title
            AuraStyle.focus(this, card) { openVideo(video) }
            videoRow.addView(card, LinearLayout.LayoutParams(dp(tileWidth), -1).apply { rightMargin = dp(12) })
        }
    }

    /** Rotates the featured *real* video every 45 minutes; never a mock poster. */
    private fun rotateFeature() {
        if (videos.isEmpty() || !::heroImage.isInitialized) return
        val index = ((System.currentTimeMillis() / (45L * 60_000L)) % videos.size).toInt()
        if (index != activeHeroIndex) {
            activeHeroIndex = index
            val item = videos[index]
            heroChannel.text = "YOUTUBE  ·  ${item.channel}"
            heroTitle.text = item.title
            images.load(heroImage, item.thumbnail)
        }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        videoSectionTitle.text = when (hour) {
            in 5..11 -> "صباحك · أحدث فيديوهات ${videos.first().channel}"
            in 12..18 -> "اليوم · جديد ${videos.first().channel}"
            else -> "سهرة محمد · فيديوهات ${videos.first().channel}"
        }
    }

    private fun renderInstalledApps() {
        appRow.removeAllViews()
        val pm = packageManager
        val found = linkedMapOf<String, android.content.pm.ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (entry in activities) {
                val name = entry.activityInfo?.packageName ?: continue
                if (name != packageName) found[name] = entry
            }
        }
        val priority = listOf("youtube", "netflix", "primevideo", "amazonvideo", "shahid", "disney", "spotify", "apple")
        val sorted = found.entries.sortedWith(
            compareBy<Map.Entry<String, android.content.pm.ResolveInfo>> { entry ->
                val name = entry.key.lowercase(Locale.US)
                val score = priority.indexOfFirst { name.contains(it) }
                if (score < 0) 100 else score
            }.thenBy { it.value.loadLabel(pm).toString() }
        )
        val tileWidth = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density) * .137f)
            .toInt().coerceIn(115, 225)
        for ((pack, info) in sorted.take(10)) {
            val label = info.loadLabel(pm).toString()
            val card = AuraStyle.card(this, 13)
            val row = AuraStyle.row(this).apply {
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            val icon = ImageView(this).apply {
                try { setImageDrawable(info.loadIcon(pm)) } catch (_: Exception) { }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(9) })
            val title = text(label, 13f, AuraStyle.WHITE, true)
            title.maxLines = 1
            row.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
            card.addView(row, AuraStyle.full())
            AuraStyle.focus(this, card) { launchApp(pack) }
            appRow.addView(card, LinearLayout.LayoutParams(dp(tileWidth), -1).apply { rightMargin = dp(10) })
        }
        val all = AuraStyle.card(this, 13)
        all.addView(text("كل التطبيقات", 14f, AuraStyle.WHITE, true).apply { gravity = Gravity.CENTER }, AuraStyle.full())
        AuraStyle.focus(this, all) { openAppsDialog() }
        appRow.addView(all, LinearLayout.LayoutParams(dp(132), -1))
    }

    private fun openAppsDialog() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val items = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
        if (items.isEmpty()) { toast("تعذر قراءة التطبيقات"); return }
        AlertDialog.Builder(this).setTitle("التطبيقات المثبتة")
            .setItems(items.map { it.loadLabel(pm).toString() }.toTypedArray()) { _, i ->
                launchApp(items[i].activityInfo.packageName)
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) startActivity(intent) else toast("التطبيق غير قابل للتشغيل")
        } catch (_: Exception) { toast("تعذر فتح التطبيق") }
    }

    private fun openVideo(video: VideoItem) {
        val url = Uri.parse(video.url)
        try {
            val intent = Intent(Intent.ACTION_VIEW, url).apply { setPackage("com.google.android.youtube.tv") }
            startActivity(intent)
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) { toast("تعذر فتح YouTube") }
        }
    }

    private fun openYouTube() {
        for (pack in listOf("com.google.android.youtube.tv", "com.google.android.youtube")) {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(pack)
                ?: packageManager.getLaunchIntentForPackage(pack)
            if (intent != null) { startActivity(intent); return }
        }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/tv"))) }
        catch (_: Exception) { toast("YouTube غير متاح") }
    }

    private fun openMatch() {
        val item = match
        val content = if (item == null) "قم بإدخال مفتاح football-data.org من الإعدادات لعرض المباراة القادمة تلقائيًا."
            else "${item.home} × ${item.away}\n${item.competition}\n${formatMatchTime(item.utc)}"
        AlertDialog.Builder(this).setTitle("مانشستر يونايتد")
            .setMessage(content)
            .setPositiveButton("جدول المباريات") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.manutd.com/en/matches"))) }
                catch (_: Exception) { toast("تعذر فتح جدول المباريات") }
            }
            .setNegativeButton("إغلاق", null).show()
    }

    private fun openSettings() {
        val options = arrayOf("مصادر YouTube", "مفتاح بيانات المباريات", "الخلفية حسب الوقت والطقس", "تحديث المحتوى الآن")
        AlertDialog.Builder(this).setTitle("إعدادات AURA TV")
            .setItems(options) { _, i ->
                when (i) {
                    0 -> editValue("قنوات YouTube", "channels", RemoteData.UNITED_CHANNEL,
                        "ضع Channel ID مفصولًا بفواصل، وتبدأ القناة بـ UC")
                    1 -> editValue("Football API Token", "football_key", "",
                        "من football-data.org — يُحفظ على التلفزيون فقط")
                    2 -> chooseScene()
                    3 -> { lastVideos = 0L; lastWeather = 0L; lastMatches = 0L; refreshData(); toast("يجري تحديث البيانات") }
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun editValue(title: String, key: String, default: String, hint: String) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(prefs.getString(key, default) ?: default)
            setSelectAllOnFocus(true)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(hint)
            .setView(input).setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString(key, input.text.toString().trim()).apply()
                lastVideos = 0L; lastMatches = 0L
                refreshData()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun chooseScene() {
        val choices = arrayOf("تلقائي", "ليل", "غروب", "نهار", "مطر", "غيم")
        val ids = arrayOf("auto", "night", "sunset", "day", "rain", "cloud")
        val current = ids.indexOf(prefs.getString("scene", "auto")).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("أجواء كافد")
            .setSingleChoiceItems(choices, current) { dialog, i ->
                prefs.edit().putString("scene", ids[i]).apply()
                updateClockAndScene()
                dialog.dismiss()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun updateClockAndScene() {
        val locale = Locale("ar", "SA")
        timeLabel.text = SimpleDateFormat("h:mm a", locale).format(Date())
        dateLabel.text = SimpleDateFormat("EEEE، d MMMM yyyy", locale).format(Date())
        val info = weather
        weatherLabel.text = if (info == null) "الرياض  ·  الطقس غير متاح"
            else "الرياض  ·  ${info.temperature}°  ·  ${weatherName(info.code)}"
        val chosen = prefs.getString("scene", "auto") ?: "auto"
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val now = if (chosen != "auto") chosen else when {
            info != null && (info.code in 51..67 || info.code in 80..82 || info.code in 95..99) -> "rain"
            info != null && info.code in 2..3 -> "cloud"
            hour in 6..15 -> "day"
            hour in 16..18 -> "sunset"
            else -> "night"
        }
        if (scene != now) {
            scene = now
            try {
                assets.open("kafd_$now.jpg").use { stream ->
                    background.setImageBitmap(BitmapFactory.decodeStream(stream))
                }
            } catch (_: Exception) { background.setBackgroundColor(AuraStyle.INK) }
        }
    }

    private fun weatherName(code: Int) = when {
        code == 0 -> "صحو"
        code in 1..3 -> "غائم جزئيًا"
        code in 45..48 -> "ضباب"
        code in 51..67 || code in 80..82 -> "أمطار"
        code in 95..99 -> "عواصف"
        else -> "متغير"
    }

    private fun formatMatchTime(utc: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(utc)
            if (date == null) "—" else SimpleDateFormat("EEEE d MMMM · h:mm a", Locale("ar", "SA"))
                .apply { timeZone = TimeZone.getTimeZone("Asia/Riyadh") }.format(date)
        } catch (_: Exception) { "—" }
    }

    private fun refreshData() {
        val now = System.currentTimeMillis()
        if (now - lastWeather > 25 * 60_000) {
            lastWeather = now
            network.execute {
                try {
                    val result = RemoteData.weather()
                    runOnUiThread { if (!isDestroyed) { weather = result; updateClockAndScene() } }
                } catch (_: Exception) { }
            }
        }
        if (now - lastVideos > 18 * 60_000) {
            lastVideos = now
            val channelText = prefs.getString("channels", RemoteData.UNITED_CHANNEL) ?: RemoteData.UNITED_CHANNEL
            val ids = channelText.split(",", "\n", " ").map { it.trim() }.filter { it.isNotBlank() }
            network.execute {
                val data = try { RemoteData.youtube(ids) } catch (_: Exception) { emptyList() }
                if (data.isNotEmpty()) {
                    cacheVideos(data)
                    runOnUiThread { if (!isDestroyed) renderVideos(data) }
                } else if (videos.isEmpty()) runOnUiThread { if (!isDestroyed) renderVideos(emptyList()) }
            }
        }
        if (now - lastMatches > 60 * 60_000) {
            lastMatches = now
            val key = prefs.getString("football_key", "") ?: ""
            if (key.isNotBlank()) network.execute {
                val value = try { RemoteData.nextMatch(key) } catch (_: Exception) { null }
                runOnUiThread {
                    if (!isDestroyed) {
                        match = value
                        matchHeader.text = "المباراة القادمة"
                        competitionLabel.text = value?.competition ?: "MANCHESTER UNITED"
                        matchTeams.text = value?.let { "${it.home}  ×  ${it.away}" } ?: "بيانات المباراة غير متاحة"
                        matchDetails.text = value?.let { formatMatchTime(it.utc) } ?: "تحقق من مفتاح API في الإعدادات"
                    }
                }
            }
        }
    }

    private fun cacheVideos(items: List<VideoItem>) {
        val arr = JSONArray()
        items.forEach { video -> arr.put(JSONObject().put("id", video.id).put("title", video.title)
            .put("channel", video.channel).put("published", video.published)) }
        prefs.edit().putString("videos_cache", arr.toString()).apply()
    }

    private fun restoreCachedVideos() {
        try {
            val raw = prefs.getString("videos_cache", "") ?: ""
            if (raw.isBlank()) return
            val arr = JSONArray(raw)
            val cache = (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { VideoItem(it.getString("id"), it.getString("title"),
                    it.getString("channel"), it.getString("published")) }
            }
            if (cache.isNotEmpty()) renderVideos(cache)
        } catch (_: Exception) { }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
