package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.ImageView
import kotlin.math.max

/** Horizontal bar split into coloured parts (for example storage by file type). */
class SegmentBar(ctx: Context) : View(ctx) {
    private var values: List<Float> = emptyList()
    private var colors: List<Int> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }

    fun setSegments(v: List<Float>, c: List<Int>) { values = v; colors = c; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(MeasureSpec.getSize(w), (12 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        c.drawRoundRect(box, r, r, track)
        val total = values.sum()
        if (total <= 0f) return
        val clip = Path()
        clip.addRoundRect(box, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        var x = 0f
        for (i in values.indices) {
            val w = width * values[i] / total
            paint.color = colors.getOrElse(i) { Color.GRAY }
            c.drawRect(x, 0f, x + w - 2f, height.toFloat(), paint)
            x += w
        }
        c.restore()
    }
}

/** Small line chart with a soft fill, for usage over time. */
class Sparkline(ctx: Context) : View(ctx) {
    private var pts: List<Float> = emptyList()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7657F6.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x337657F6; style = Paint.Style.FILL }

    fun setPoints(p: List<Float>) { pts = p; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(MeasureSpec.getSize(w), (56 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        if (pts.size < 2) return
        val mx = max(pts.max(), 0.0001f)
        val step = width.toFloat() / (pts.size - 1)
        val path = Path()
        val area = Path()
        for (i in pts.indices) {
            val x = i * step
            val y = height - (pts[i] / mx) * (height - 8f) - 4f
            if (i == 0) { path.moveTo(x, y); area.moveTo(x, height.toFloat()); area.lineTo(x, y) }
            else { path.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(width.toFloat(), height.toFloat())
        area.close()
        c.drawPath(area, fill)
        c.drawPath(path, line)
    }
}

/** An icon drawn from a 24x24 path (see PathIcon) that can be used straight in a layout. */
class SvgIcon(ctx: Context) : ImageView(ctx) {
    fun setPath(data: String, color: Int = Color.WHITE) { setImageDrawable(PathIcon(data, color)) }
}
