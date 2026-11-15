package com.auratv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Canvas-based 1920x1080 TV home screen: no WebView dependency, no touch required.
 * All buttons and cards are separately drawn, focusable, and actionable.
 */
class AuraHomeView(
    context: Context,
    private val onAction: (String) -> Unit,
    private val weather: () -> Pair<Int?, Int?>
) : View(context) {
    private val white = Color.rgb(251, 248, 249)
    private val muted = Color.rgb(191, 193, 204)
    private val red = Color.rgb(238, 21, 52)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val images = mutableMapOf<String, Bitmap>()
    private var currentScene = ""
    private var selected = "home"
    private val hits = mutableListOf<Hit>()
    private data class Hit(val id: String, val rect: RectF)

    private val arabic = Typeface.create("sans-serif", Typeface.NORMAL)
    private val arabicBold = Typeface.create("sans-serif", Typeface.BOLD)
    private val displayFont = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        listOf("peaky", "stadium", "red_devil").forEach { load(it) }
        (1..6).forEach { load("poster_$it") }
    }

    fun setSelection(id: String) { selected = id; invalidate() }
    fun updateScene(scene: String) {
        if (scene != currentScene) {
            currentScene = scene
            load("kafd_$scene")
            invalidate()
        }
    }

    private fun load(name: String): Bitmap? {
        images[name]?.let { return it }
        val suffix = if (name == "red_devil") "png" else "jpg"
        return try {
            context.assets.open("$name.$suffix").use { BitmapFactory.decodeStream(it) }
                .also { if (it != null) images[name] = it }
        } catch (_: Exception) { null }
    }

    override fun onDraw(target: Canvas) {
        super.onDraw(target)
        val save = target.save()
        target.scale(width / 1920f, height / 1080f)
        target.drawColor(Color.rgb(4, 6, 14))
        image(target, "kafd_${if (currentScene.isEmpty()) "night" else currentScene}", RectF(0f, 0f, 1920f, 1080f), 0f)
        // Deep lower mask makes real card content readable on KAFD panorama.
        gradient(target, RectF(0f, 460f, 1920f, 1080f), intArrayOf(0x10070810, 0xE9070810.toInt()), false)
        hits.clear()
        drawHeader(target)
        drawSidebar(target)
        drawGreeting(target)
        drawMatch(target)
        drawHero(target)
        drawPosters(target)
        drawDock(target)
        target.restoreToCount(save)
    }

    private fun box(c: Canvas, r: RectF, color: Int, rad: Float=21f, stroke: Int?=null, strokeWidth: Float=2f) {
        p.reset(); p.isAntiAlias = true; p.color = color; p.style = Paint.Style.FILL
        c.drawRoundRect(r,rad,rad,p)
        if (stroke != null) {
            p.style = Paint.Style.STROKE; p.color=stroke; p.strokeWidth=strokeWidth
            c.drawRoundRect(r,rad,rad,p); p.style=Paint.Style.FILL
        }
    }

    private fun gradient(c: Canvas, r: RectF, colors: IntArray, leftToRight: Boolean, rad: Float=0f) {
        p.reset(); p.isAntiAlias=true
        p.shader = if (leftToRight) LinearGradient(r.left,r.top,r.right,r.top,colors,null,Shader.TileMode.CLAMP)
            else LinearGradient(r.left,r.top,r.left,r.bottom,colors,null,Shader.TileMode.CLAMP)
        c.drawRoundRect(r,rad,rad,p)
        p.shader=null
    }

    private fun image(c: Canvas, name: String, rect: RectF, radius: Float=17f) {
        val bm = load(name) ?: return
        val s=c.save()
        if (radius>0) { val clip=Path();clip.addRoundRect(rect,radius,radius,Path.Direction.CW); c.clipPath(clip) }
        p.reset(); p.isAntiAlias=true;p.isFilterBitmap=true
        c.drawBitmap(bm,null,rect,p)
        c.restoreToCount(s)
    }

    private fun t(c: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int=white,
                  bold: Boolean=false, align: Paint.Align=Paint.Align.LEFT, family: Typeface?=null) {
        p.reset();p.isAntiAlias=true; p.isSubpixelText=true; p.color=color; p.textSize=size
        p.typeface=family ?: if (bold) arabicBold else arabic
        p.textAlign=align
        c.drawText(text,x,baseline,p)
    }

    private fun focus(c: Canvas, id: String, r: RectF, radius: Float=18f) {
        hits.add(Hit(id,RectF(r)))
        if (id != selected) return
        p.reset();p.isAntiAlias=true;p.color=0x65FF2540;p.style=Paint.Style.STROKE;p.strokeWidth=11f
        p.setShadowLayer(22f,0f,0f,red); c.drawRoundRect(r,radius,radius,p)
        p.clearShadowLayer();p.color=Color.rgb(255,65,82);p.strokeWidth=3f
        c.drawRoundRect(r,radius,radius,p);p.style=Paint.Style.FILL
    }

    private fun drawHeader(c: Canvas) {
        t(c,"▲",96f,89f,58f,red,true)
        t(c,"A  U  R  A    T  V",184f,85f,30f,white,false)
        val now = Date()
        t(c,SimpleDateFormat("HH:mm",Locale.getDefault()).format(now),1755f,85f,49f,white,true,Paint.Align.RIGHT)
        t(c,SimpleDateFormat("EEEE dd MMMM yyyy",Locale("ar")).format(now),1755f,118f,19f,muted,false,Paint.Align.RIGHT)
        val (temp,code)=weather()
        val icon = if (code != null && (code in 51..67 || code in 80..99)) "☂" else if (code!=null && code in 2..3) "☁" else "☾"
        t(c,icon,1502f,81f,39f,white)
        t(c,if(temp==null) "—°" else "$temp°",1462f,81f,30f,white,true,Paint.Align.RIGHT)
        t(c,"الرياض",1330f,81f,27f,white,false,Paint.Align.RIGHT)
        // Small minimal red devil, used only in the far corner.
        image(c,"red_devil",RectF(1818f,35f,1878f,112f),0f)
    }

    private fun drawGreeting(c: Canvas) {
        t(c,"مساء الخير",230f,190f,30f,white,false)
        t(c,"محمد",230f,274f,78f,white,true)
        box(c,RectF(230f,292f,306f,298f),red,4f)
    }

    private fun drawSidebar(c: Canvas) {
        val frame=RectF(48f,356f,170f,699f)
        box(c,frame,0xCE090B14.toInt(),33f,0x887E7B8A.toInt())
        val items=listOf(Triple("wifi","⌁",410f),Triple("home","⌂",510f),Triple("settings","⚙",610f))
        for ((id,label,cy) in items) {
            val r=RectF(69f,cy-40f,149f,cy+40f)
            if (id==selected) box(c,r,0xAB340817.toInt(),21f,red)
            // Wi-Fi uses a hand-drawn universal icon (no emoji rendering differences).
            if(id=="wifi") drawWifi(c,109f,cy)
            else t(c,label,109f,cy+15f,58f,white,id=="home",Paint.Align.CENTER)
            focus(c,id,r,21f)
        }
    }

    private fun drawWifi(c: Canvas,x: Float,y: Float) {
        p.reset();p.isAntiAlias=true;p.color=white;p.style=Paint.Style.STROKE;p.strokeWidth=5.5f;p.strokeCap=Paint.Cap.ROUND
        val r=RectF(x-32,y-24,x+32,y+38)
        c.drawArc(r,215f,110f,false,p)
        c.drawArc(RectF(x-22,y-15,x+22,y+25),216f,108f,false,p)
        p.style=Paint.Style.FILL;c.drawCircle(x,y+18f,5f,p)
    }

    private fun drawMatch(c: Canvas) {
        val r=RectF(210f,379f,916f,666f)
        box(c,r,0xF20E0911.toInt(),25f,0xFF9C2440.toInt())
        image(c,"stadium",RectF(210f,425f,510f,667f),24f)
        gradient(c,r,intArrayOf(0x49190915,0xE70D0711.toInt()),true,25f)
        // Left crimson stadium texture fades into deep charcoal.
        gradient(c,RectF(210f,490f,645f,665f),intArrayOf(0x002E0000,0xDA5B001C.toInt()),false,22f)
        t(c,"Premier League",280f,432f,31f,white,true,Paint.Align.LEFT,displayFont)
        t(c,"♛",246f,431f,38f,white,true)
        t(c,"المباراة القادمة",867f,429f,28f,white,true,Paint.Align.RIGHT)
        // Deliberately marked demo until a verified fixtures provider is connected.
        box(c,RectF(717f,441f,866f,477f),0x8B44152D.toInt(),17f)
        t(c,"بيانات تجريبية",793f,465f,19f,0xFFFAD9DD.toInt(),false,Paint.Align.CENTER)
        // Crests in this edition use tiny vector-like team tokens without a third-party API.
        team(c,450f,522f,"EFC",0xFF1253A0.toInt(),"إيفرتون")
        t(c,"VS",573f,532f,35f,white,true,Paint.Align.CENTER,displayFont)
        team(c,676f,522f,"MU",0xFFB80924.toInt(),"مانشستر يونايتد")
        t(c,"تُحدّث المواعيد بعد الربط",867f,615f,21f,muted,false,Paint.Align.RIGHT)
        focus(c,"match",r,25f)
    }

    private fun team(c: Canvas,x:Float,y:Float,letters:String,color:Int,label:String) {
        box(c,RectF(x-49f,y-47f,x+49f,y+48f),color,46f,0xBFFFE7E7.toInt(),2f)
        t(c,letters,x,y+14f,37f,white,true,Paint.Align.CENTER,displayFont)
        t(c,label,x,y+95f,22f,white,false,Paint.Align.CENTER)
    }

    private fun drawHero(c: Canvas) {
        val r=RectF(954f,379f,1865f,666f)
        image(c,"peaky",r,25f)
        // The hero source image is demo concept art; tapping opens Netflix app, not a fake deep link.
        focus(c,"hero",r,25f)
    }

    private fun drawPosters(c: Canvas) {
        t(c,"أفلام ومسلسلات مختارة لك",228f,719f,28f,white,true)
        val imagesCount=6
        val x0=216f; val spacing=13f; val w=255f; val y=740f; val h=142f
        for (i in 0 until imagesCount) {
            val x=x0+i*(w+spacing)
            val r=RectF(x,y,x+w,y+h)
            image(c,"poster_${i+1}",r,19f)
            box(c,r,Color.TRANSPARENT,19f,0x665E6170)
            focus(c,"poster_$i",r,19f)
        }
    }

    private fun drawDock(c: Canvas) {
        val dock=RectF(337f,905f,1812f,1017f)
        box(c,dock,0xE40D0E16.toInt(),30f,0x70525361)
        val appItems=listOf(
            Triple("all_apps","كل التطبيقات",0xFF303039.toInt()),
            Triple("netflix","NETFLIX",0xFF590714.toInt()),
            Triple("youtube","▶ YouTube",0xFF24212B.toInt()),
            Triple("apple","● tv",0xFF25252D.toInt()),
            Triple("prime","prime video",0xFF1B2836.toInt()),
            Triple("disney","Disney+",0xFF222434.toInt())
        )
        val tileW=227f; val gap=13f
        for (i in appItems.indices) {
            val (id,label,color)=appItems[i]
            val left=352f+i*(tileW+gap)
            val r=RectF(left,922f,left+tileW,1002f)
            box(c,r,color,21f,0x685C5664)
            val fg=if(id=="netflix"||id=="youtube") red else white
            t(c,label,r.centerX(),974f,if(id=="all_apps") 24f else 36f,fg,true,Paint.Align.CENTER,displayFont)
            focus(c,id,r,21f)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when(keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                onAction(selected);return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1,0);return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1,0);return true }
            KeyEvent.KEYCODE_DPAD_UP -> { move(0,-1);return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { move(0,1);return true }
            KeyEvent.KEYCODE_MENU -> { onAction("settings");return true }
        }
        return super.onKeyDown(keyCode,event)
    }

    private fun move(dx:Int,dy:Int) {
        val old=hits.find { it.id==selected } ?: return
        val ox=old.rect.centerX(); val oy=old.rect.centerY()
        var best:Hit?=null; var bestScore=Float.MAX_VALUE
        for (candidate in hits) {
            if(candidate.id==selected) continue
            val xx=candidate.rect.centerX()-ox; val yy=candidate.rect.centerY()-oy
            val primary=xx*dx+yy*dy
            if(primary<=10)continue
            val cross=abs(xx*dy-yy*dx)
            val score=primary+cross*2.5f + if(cross>primary*2.6f) 2500f else 0f
            if(score<bestScore){bestScore=score;best=candidate}
        }
        if(best!=null){selected=best.id;invalidate()}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action==MotionEvent.ACTION_UP) {
            val x=event.x*1920f/width; val y=event.y*1080f/height
            val hit=hits.lastOrNull { it.rect.contains(x,y) }
            if(hit!=null){selected=hit.id;invalidate();onAction(hit.id);return true}
        }
        return true
    }
}
