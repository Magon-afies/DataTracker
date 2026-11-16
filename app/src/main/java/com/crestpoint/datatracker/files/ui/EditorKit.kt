package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.crestpoint.datatracker.R
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import kotlin.math.min

/** Aspect-ratio frame icon (a small outlined box with the given shape). */
class RatioIcon(private val ratio: Float, tint: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        p.strokeWidth = s / 11f
        var w = s * 0.86f
        var h = w
        if (ratio >= 1f) h = w / ratio else w = h * ratio
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, s / 9f, s / 9f, p)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Building blocks that give the photo, video and audio editors one premium, icon-led look. */
object EditorKit {
    private const val FRAME = "M3,3h18v18H3z M5,5v14h14V5z"
    private const val FLIP = "M3,19h7V5z M14,19h7L14,5z M11,3h2v18h-2z"
    private const val RESET = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
    private const val TRASH = "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"
    private const val TEXT = "M2.5,4v3h5v12h3V7h5V4h-13zM21.5,9h-9v3h3v7h3v-7h3V9z"
    private const val VOL_ON = "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
    private const val VOL_OFF = "M3,9v6h4l5,5V4L7,9H3z M15.4,9.6l1.4,-1.4 2.2,2.2 2.2,-2.2 1.4,1.4 -2.2,2.2 2.2,2.2 -1.4,1.4 -2.2,-2.2 -2.2,2.2 -1.4,-1.4 2.2,-2.2z"
    private const val NOTE = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
    private const val IMAGE = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z"
    private const val BRUSH = "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z"
    private const val ERASE = "M5,16l8,-8 5,5 -8,8H8z"
    private const val EYE = "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
    private const val BRIGHT = "M12,7a5,5 0 1,0 0.001,0z M11,1h2v3h-2z M11,20h2v3h-2z M1,11h3v2H1z M20,11h3v2h-3z"
    private const val CONTRAST = "M12,3a9,9 0 0,0 0,18z"
    private const val DROP = "M12,2.69l5.66,5.66a8,8 0 1,1 -11.31,0z"
    private const val WARM = "M12,2a3,3 0 0,0 -3,3v7.2a5,5 0 1,0 6,0V5a3,3 0 0,0 -3,-3z"
    private const val FADE = "M3,4h18v3H3z M3,10h12v3H3z M3,16h6v3H3z"
    private const val VIGNETTE = "M12,3a9,9 0 1,0 0.001,0z M12,7a5,5 0 1,1 -0.001,0z"
    private const val SHARP = "M12,3L2,21h20z"
    private const val GRID4 = "M4,4h6v6H4z M14,4h6v6h-6z M4,14h6v6H4z M14,14h6v6h-6z"
    private const val POS_TOP = "M3,3h18v18H3z M5,5v14h14V5z M7,6h10v2H7z"
    private const val POS_MID = "M3,3h18v18H3z M5,5v14h14V5z M7,11h10v2H7z"
    private const val POS_BOT = "M3,3h18v18H3z M5,5v14h14V5z M7,16h10v2H7z"

    private class Spec(val path: String? = null, val ratio: Float = 0f, val swatch: Int = 0)

    private fun ratioOf(l: String): Float? {
        val i = l.indexOf(':')
        if (i <= 0) return null
        val a = l.substring(0, i).toFloatOrNull() ?: return null
        val b = l.substring(i + 1).toFloatOrNull() ?: return null
        return if (b > 0f) a / b else null
    }

    private fun specFor(label: String): Spec {
        val l = label.lowercase().replace("•", "").trim()
        ratioOf(l)?.let { return Spec(ratio = it) }
        return when {
            l == "free" || l == "original" -> Spec(path = FRAME)
            l.startsWith("rotate") -> Spec(path = Glass.ROTATE)
            l.startsWith("flip v") -> Spec(path = FLIP)
            l.startsWith("flip") -> Spec(path = FLIP)
            l == "reset" || l == "clear" -> Spec(path = RESET)
            l == "remove" -> Spec(path = TRASH)
            l == "add text" || l == "edit text" -> Spec(path = TEXT)
            l == "top" -> Spec(path = POS_TOP)
            l == "middle" -> Spec(path = POS_MID)
            l == "bottom" -> Spec(path = POS_BOT)
            l.startsWith("sound on") -> Spec(path = VOL_ON)
            l.startsWith("sound off") -> Spec(path = VOL_OFF)
            l == "audio only" -> Spec(path = NOTE)
            l.startsWith("save this frame") -> Spec(path = IMAGE)
            l == "paint" -> Spec(path = BRUSH)
            l == "erase" -> Spec(path = ERASE)
            l == "show mask" -> Spec(path = EYE)
            l == "brightness" -> Spec(path = BRIGHT)
            l == "contrast" -> Spec(path = CONTRAST)
            l == "saturation" -> Spec(path = DROP)
            l == "warmth" -> Spec(path = WARM)
            l == "fade" -> Spec(path = FADE)
            l == "vignette" -> Spec(path = VIGNETTE)
            l == "sharpen" -> Spec(path = SHARP)
            l == "structure" -> Spec(path = GRID4)
            l == "none" -> Spec(swatch = 0xFF9E9E9E.toInt())
            l == "b&w" -> Spec(swatch = 0xFFE0E0E0.toInt())
            l == "warm" -> Spec(swatch = 0xFFFF9800.toInt())
            l == "cool" -> Spec(swatch = 0xFF29B6F6.toInt())
            l == "invert" -> Spec(swatch = 0xFF7E57C2.toInt())
            l == "white" -> Spec(swatch = Color.WHITE)
            l == "black" -> Spec(swatch = 0xFF212121.toInt())
            l == "yellow" -> Spec(swatch = 0xFFFFD60A.toInt())
            l == "red" -> Spec(swatch = 0xFFFF453A.toInt())
            else -> Spec()
        }
    }

    /** A rounded option tile: icon on top, label below. Selected tiles get a purple outline. */
    fun opt(ctx: Context, label: String, filled: Boolean, onClick: (() -> Unit)?): View {
        val accent = UiKit.color(ctx, R.color.brand_purple)
        val spec = specFor(label)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.cornerRadius = UiKit.dp(ctx, 14).toFloat()
        bg.setColor(if (filled) 0x337657F6 else 0xFF23243B.toInt())
        if (filled) bg.setStroke(UiKit.dp(ctx, 2), accent)
        box.background = bg
        val pad = UiKit.dp(ctx, 8)
        box.setPadding(pad, pad, pad, pad)
        box.minimumWidth = UiKit.dp(ctx, 62)
        box.minimumHeight = UiKit.dp(ctx, 58)
        val tint = if (filled) accent else Color.WHITE
        val hasIcon = spec.path != null || spec.ratio > 0f || spec.swatch != 0
        if (hasIcon) {
            val iv = ImageView(ctx)
            if (spec.path != null) iv.setImageDrawable(PathIcon(spec.path, tint))
            else if (spec.ratio > 0f) iv.setImageDrawable(RatioIcon(spec.ratio, tint))
            else iv.setImageDrawable(Glass.circle(spec.swatch))
            box.addView(iv, LinearLayout.LayoutParams(UiKit.dp(ctx, 26), UiKit.dp(ctx, 26)))
        }
        val t = UiKit.text(ctx, label.replace("•", "").trim(), if (hasIcon) 11f else 13f,
            if (filled) R.color.on_surface else R.color.on_surface_muted, bold = filled)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        if (hasIcon) t.setPadding(0, UiKit.dp(ctx, 4), 0, 0)
        box.addView(t)
        if (onClick != null) { box.setOnClickListener { onClick() }; Design.ripple(box) }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4))
        box.layoutParams = lp
        return box
    }

    /** Modern slider with a purple track and white thumb. */
    fun styledSlider(ctx: Context, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): Slider {
        val s = Slider(ctx)
        s.valueFrom = min.toFloat()
        s.valueTo = max.toFloat()
        s.stepSize = 1f
        s.value = init.coerceIn(min, max).toFloat()
        s.trackHeight = UiKit.dp(ctx, 4)
        s.thumbRadius = UiKit.dp(ctx, 8)
        s.labelBehavior = LabelFormatter.LABEL_GONE
        s.trackActiveTintList = ColorStateList.valueOf(UiKit.color(ctx, R.color.brand_purple))
        s.trackInactiveTintList = ColorStateList.valueOf(0x33FFFFFF)
        s.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        s.addOnChangeListener { _, v, _ -> onChange(v.toInt()) }
        return s
    }

    /** Label + live value + slider on one line. */
    fun slider(ctx: Context, label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        val t = UiKit.text(ctx, "$label $init", 12f, R.color.on_surface_muted)
        row.addView(t, UiKit.lp(UiKit.dp(ctx, 118), -2))
        val s = styledSlider(ctx, min, max, init) { v -> t.text = "$label $v"; onChange(v) }
        row.addView(s, UiKit.lp(0, -2, 1f))
        return row
    }
}
