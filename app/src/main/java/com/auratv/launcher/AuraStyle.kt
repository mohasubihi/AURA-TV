package com.auratv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object AuraStyle {
    const val RED = 0xFFCF3048.toInt()
    const val INK = 0xFF090D16.toInt()
    const val WHITE = 0xFFF5F2F3.toInt()
    const val MUTED = 0xFFB9BBC5.toInt()

    fun dp(c: Context, n: Number) = (n.toFloat() * c.resources.displayMetrics.density + 0.5f).toInt()

    fun shape(color: Int, radius: Float, stroke: Int = 0, border: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (border > 0) setStroke(border, stroke)
        }

    fun text(
        c: Context, value: String, size: Float, color: Int = WHITE,
        bold: Boolean = false, right: Boolean = false
    ) = TextView(c).apply {
        text = value
        setTextColor(color)
        textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        gravity = if (right) Gravity.CENTER_VERTICAL or Gravity.RIGHT else Gravity.CENTER_VERTICAL or Gravity.LEFT
        textDirection = if (right) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
        includeFontPadding = false
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    fun card(c: Context, radiusDp: Int = 17) = FrameLayout(c).apply {
        background = shape(0xEE10141C.toInt(), dp(c, radiusDp).toFloat(), 0x44FFFFFF, dp(c, 1))
        clipToOutline = true
        isFocusable = true
        isClickable = true
        elevation = dp(c, 4).toFloat()
    }

    fun focus(c: Context, view: View, onClick: () -> Unit) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.isClickable = true
        view.setOnClickListener { onClick() }
        view.onFocusChangeListener = View.OnFocusChangeListener { v, focused ->
            v.animate().cancel()
            v.animate().scaleX(if (focused) 1.022f else 1f)
                .scaleY(if (focused) 1.022f else 1f)
                .setDuration(150).start()
            if (v is FrameLayout) {
                val base = if (focused) 0xF21A1E2B.toInt() else 0xE9131620.toInt()
                v.background = shape(base, dp(c, 17).toFloat(),
                    if (focused) 0xFFDD6674.toInt() else 0x35FFFFFF, dp(c, if (focused) 2 else 1))
            }
        }
    }

    fun cover(c: Context) = ImageView(c).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(0xFF252934.toInt())
    }

    fun row(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun column(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    fun grow() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)

    fun full() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}
