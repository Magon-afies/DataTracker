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
    const val HOME = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"
    const val FOLDER = "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"
    const val SEARCH = "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    const val SETTINGS = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94l-0.36,-2.54c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41l-0.36,2.54c-0.59,0.24 -1.13,0.57 -1.62,0.94l-2.39,-0.96c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87c-0.12,0.21 -0.08,0.47 0.12,0.61l2.03,1.58c-0.05,0.3 -0.09,0.63 -0.09,0.94s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61l-2.01,-1.58zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6 -1.62,3.6 -3.6,3.6z"
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
    const val MORE = "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    const val LOCK = "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
    const val CLEANER = "M19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4zM6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12z"

    fun iconBtn(ctx: Context, data: String, sizeDp: Int = 44, tint: Int = Color.WHITE, onClick: () -> Unit): ImageView {
        val v = ImageView(ctx)
        val p = UiKit.dp(ctx, 10)
        v.setPadding(p, p, p, p)
        v.setImageDrawable(PathIcon(data, tint))
        v.setOnClickListener { onClick() }
        Design.ripple(v, sizeDp / 2)
        v.layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, sizeDp), UiKit.dp(ctx, sizeDp))
        return v
    }

    fun gradient(fromTop: Boolean): GradientDrawable = GradientDrawable(
        if (fromTop) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(0xDD0B0B14.toInt(), 0x00000000)
    )

    fun circle(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun pill(color: Int, radiusDp: Int, ctx: Context): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = UiKit.dp(ctx, radiusDp).toFloat() }
}
