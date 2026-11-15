package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.PathParser
import kotlin.math.min

/** A 24x24 vector path drawn crisply at any size (no resource files needed). */
class PathIcon(data: String, tint: Int) : Drawable() {
    private val path: Path = try { PathParser.createPathFromPathData(data) } catch (_: Exception) { Path() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }

    override fun draw(c: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        c.scale(s, s)
        c.drawPath(path, paint)
        c.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object Glass {
    const val PLAY = "M8,5v14l11,-7z"
    const val PAUSE = "M6,19h4V5H6v14zM14,5v14h4V5h-4z"
    const val PREV = "M6,6h2v12H6zM9.5,12l8.5,6V6z"
    const val NEXT = "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"
    const val REW = "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z"
    const val FF = "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z"
    const val BACK = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
    const val CLOSE = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    const val EDIT = "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"
    const val ROTATE = "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z"
    const val ASPECT = "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zM21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM21,19.01H3V4.99h18v14.02z"

    fun iconBtn(ctx: Context, data: String, sizeDp: Int = 44, tint: Int = Color.WHITE, onClick: () -> Unit): ImageView {
        val v = ImageView(ctx)
        val p = UiKit.dp(ctx, 10)
        v.setPadding(p, p, p, p)
        v.setImageDrawable(PathIcon(data, tint))
        v.setOnClickListener { onClick() }
        Design.ripple(v)
        v.layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, sizeDp), UiKit.dp(ctx, sizeDp))
        return v
    }

    fun gradient(fromTop: Boolean): GradientDrawable = GradientDrawable(
        if (fromTop) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(0xCC000000.toInt(), 0x00000000)
    )

    fun circle(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun pill(color: Int, radiusDp: Int, ctx: Context): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = UiKit.dp(ctx, radiusDp).toFloat() }
}
