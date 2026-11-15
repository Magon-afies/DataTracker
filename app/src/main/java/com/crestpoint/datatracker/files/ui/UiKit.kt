package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.crestpoint.datatracker.R

object UiKit {
    fun dp(ctx: Context, n: Int): Int = (n * ctx.resources.displayMetrics.density).toInt()
    
    fun color(ctx: Context, resId: Int): Int = try {
        ctx.resources.getColor(resId, ctx.theme)
    } catch (_: Exception) {
        try { ctx.resources.getColor(resId) } catch (_: Exception) { 0xFF6200EE.toInt() }
    }

    fun text(ctx: Context, str: String, sz: Float, colRes: Int = android.R.color.white, bold: Boolean = false): TextView {
        return TextView(ctx).apply {
            text = str
            textSize = sz
            setTextColor(if (colRes == android.R.color.white || colRes == R.color.white) Color.WHITE else color(ctx, colRes))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    fun button(ctx: Context, label: String, secondary: Boolean = false, onClick: () -> Unit): Button {
        return Button(ctx).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h, weight)
    }
}

// UI helper stubs for fragments and analyzers
object Palette {
    val PRIMARY = 0xFF6200EE.toInt()
    val SURFACE = 0xFF141526.toInt()
    val BACKGROUND = 0xFF0B0B14.toInt()
    val ACCENT = 0xFF8A2BE2.toInt()
}

class ActionSheet(ctx: Context) {
    var title: String = ""
    fun addAction(action: SheetAction) {}
    fun show() {}
}

data class SheetAction(val label: String, val onClick: () -> Unit)
