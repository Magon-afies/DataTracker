package com.crestpoint.datatracker.files.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKind
import com.google.android.material.bottomsheet.BottomSheetDialog

object Palette {
    fun kind(k: FileKind): Int = when (k) {
        FileKind.FOLDER -> 0xFFFFB74D.toInt()
        FileKind.IMAGE -> 0xFFF06292.toInt()
        FileKind.VIDEO -> 0xFFBA68C8.toInt()
        FileKind.AUDIO -> 0xFF4DB6AC.toInt()
        FileKind.DOC -> 0xFF64B5F6.toInt()
        FileKind.TEXT -> 0xFF81C784.toInt()
        FileKind.ARCHIVE -> 0xFFFF8A65.toInt()
        FileKind.APK -> 0xFFAED581.toInt()
        FileKind.OTHER -> 0xFF90A4AE.toInt()
    }
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}

object Design {
    fun ripple(v: View) {
        val out = TypedValue()
        v.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        if (out.resourceId != 0) v.foreground = ContextCompat.getDrawable(v.context, out.resourceId)
    }

    fun roundedOutline(v: View, radiusDp: Int) {
        val r = UiKit.dp(v.context, radiusDp).toFloat()
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
    }

    /** A soft coloured square with an emoji inside; the emoji view is returned via tag. */
    fun iconTile(ctx: Context, emoji: String, color: Int, sizeDp: Int, radiusDp: Int = 14, emojiSp: Float = 22f): FrameLayout {
        val f = FrameLayout(ctx)
        val g = GradientDrawable()
        g.setColor(Palette.withAlpha(color, 0x33))
        g.cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
        f.background = g
        val t = TextView(ctx)
        t.text = emoji
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, emojiSp)
        t.gravity = Gravity.CENTER
        f.addView(t, FrameLayout.LayoutParams(-1, -1))
        f.tag = t
        f.layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, sizeDp), UiKit.dp(ctx, sizeDp))
        roundedOutline(f, radiusDp)
        return f
    }

    fun chip(ctx: Context, label: String, filled: Boolean, onClick: (() -> Unit)?): TextView {
        val t = UiKit.text(ctx, label, 13f, if (filled) R.color.on_surface else R.color.on_surface_muted, bold = filled)
        val g = GradientDrawable()
        g.cornerRadius = UiKit.dp(ctx, 20).toFloat()
        g.setColor(if (filled) Palette.withAlpha(UiKit.color(ctx, R.color.brand_purple), 0x66) else UiKit.color(ctx, R.color.surface_elevated))
        t.background = g
        val h = UiKit.dp(ctx, 12); val v = UiKit.dp(ctx, 7)
        t.setPadding(h, v, h, v)
        if (onClick != null) { t.setOnClickListener { onClick() }; ripple(t) }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(UiKit.dp(ctx, 4), 0, UiKit.dp(ctx, 4), 0)
        t.layoutParams = lp
        return t
    }

    fun circleButton(ctx: Context, glyph: String, sizeDp: Int, colorRes: Int, onClick: () -> Unit): TextView {
        val t = UiKit.text(ctx, glyph, 26f, R.color.on_surface, bold = true)
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(UiKit.color(ctx, colorRes))
        t.background = g
        t.elevation = UiKit.dp(ctx, 8).toFloat()
        t.setOnClickListener { onClick() }
        return t
    }
}

class SheetAction(val emoji: String, val label: String, val danger: Boolean = false, val run: () -> Unit)

object ActionSheet {
    fun show(ctx: Context, title: String?, actions: List<SheetAction>) {
        val d = BottomSheetDialog(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.background = UiKit.rounded(ctx, R.color.surface_elevated, 24)
        val p = UiKit.dp(ctx, 12)
        col.setPadding(p, UiKit.dp(ctx, 14), p, UiKit.dp(ctx, 20))
        if (title != null) {
            val t = UiKit.text(ctx, title, 16f, bold = true)
            t.setPadding(UiKit.dp(ctx, 10), 0, 0, UiKit.dp(ctx, 8))
            t.maxLines = 1
            t.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            col.addView(t)
        }
        for (a in actions) {
            val row = LinearLayout(ctx)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 13), UiKit.dp(ctx, 10), UiKit.dp(ctx, 13))
            row.addView(UiKit.text(ctx, a.emoji, 20f).also { it.setPadding(0, 0, UiKit.dp(ctx, 14), 0) })
            row.addView(UiKit.text(ctx, a.label, 15f, if (a.danger) R.color.health_bad else R.color.on_surface))
            Design.ripple(row)
            row.setOnClickListener { d.dismiss(); a.run() }
            col.addView(row)
        }
        d.setContentView(col)
        d.show()
    }
}

/** Animated storage ring with a purple→cyan gradient. */
class RingView(ctx: Context) : View(ctx) {
    var center = ""
    var sub = ""
    private var fraction = 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val c1 = ContextCompat.getColor(ctx, R.color.brand_purple)
    private val c2 = ContextCompat.getColor(ctx, R.color.brand_cyan)

    init {
        track.style = Paint.Style.STROKE; track.strokeCap = Paint.Cap.ROUND
        track.color = 0x22FFFFFF
        arc.style = Paint.Style.STROKE; arc.strokeCap = Paint.Cap.ROUND
        big.color = ContextCompat.getColor(ctx, R.color.on_surface); big.textAlign = Paint.Align.CENTER
        big.typeface = Typeface.DEFAULT_BOLD
        small.color = ContextCompat.getColor(ctx, R.color.on_surface_muted); small.textAlign = Paint.Align.CENTER
    }

    fun animateTo(f: Float) {
        val a = ValueAnimator.ofFloat(fraction, f.coerceIn(0f, 1f))
        a.duration = 900
        a.addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
        a.start()
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.11f
        track.strokeWidth = sw; arc.strokeWidth = sw
        val cx = width / 2f; val cy = height / 2f
        val r = RectF(cx - s / 2 + sw / 2, cy - s / 2 + sw / 2, cx + s / 2 - sw / 2, cy + s / 2 - sw / 2)
        c.save(); c.rotate(-90f, cx, cy)
        c.drawArc(r, 0f, 360f, false, track)
        arc.shader = SweepGradient(cx, cy, intArrayOf(c1, c2, c1), floatArrayOf(0f, 0.7f, 1f))
        if (fraction > 0.003f) c.drawArc(r, 0f, 360f * fraction, false, arc)
        c.restore()
        big.textSize = s * 0.22f; small.textSize = s * 0.11f
        c.drawText(center, cx, cy + big.textSize * 0.1f, big)
        c.drawText(sub, cx, cy + big.textSize * 0.1f + small.textSize * 1.4f, small)
    }
}
