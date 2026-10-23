package com.mycompany.datatracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.mycompany.datatracker.util.FormatBytes

/** Tiny stacked bar chart: series A (bottom, e.g. mobile) + series B (top, e.g. Wi-Fi). */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var a = LongArray(0)
    private var b = LongArray(0)

    private val density = resources.displayMetrics.density
    private val paintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6750A4.toInt() }
    private val paintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7DB7E8.toInt() }
    private val textLeft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textCenter = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val c = ta.getColor(0, Color.GRAY)
        ta.recycle()
        textLeft.color = c
        textLeft.textSize = 11f * density
        textCenter.color = c
        textCenter.textSize = 10f * density
        textCenter.textAlign = Paint.Align.CENTER
    }

    fun setData(labels: List<String>, a: LongArray, b: LongArray = LongArray(a.size)) {
        this.labels = labels
        this.a = a
        this.b = b
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = a.size
        if (n == 0 || width == 0) return

        val labelH = 16f * density
        val top = 14f * density
        val chartH = height - labelH - top
        if (chartH <= 0f) return

        var max = 1L
        for (i in 0 until n) max = maxOf(max, a[i] + b[i])

        canvas.drawText(FormatBytes.human(max), 0f, 10f * density, textLeft)

        val slot = width.toFloat() / n
        val barW = slot * 0.65f
        val base = top + chartH
        val step = maxOf(1, Math.ceil(n / 8.0).toInt())

        for (i in 0 until n) {
            val left = i * slot + (slot - barW) / 2f
            val hA = chartH * (a[i].toFloat() / max)
            val hB = chartH * (b[i].toFloat() / max)
            canvas.drawRect(left, base - hA, left + barW, base, paintA)
            canvas.drawRect(left, base - hA - hB, left + barW, base - hA, paintB)
            if (i % step == 0 && i < labels.size) {
                canvas.drawText(labels[i], left + barW / 2f, height - 3f * density, textCenter)
            }
        }
    }
}
