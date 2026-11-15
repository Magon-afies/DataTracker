package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
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

object Design {
    fun ripple(v: View, radiusDp: Int = 12) {
        val ctx = v.context
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
            setColor(Color.WHITE)
        }
        v.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), v.background, mask)
    }

    fun card(ctx: Context, bgCol: Int = 0xFF141526.toInt(), radiusDp: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgCol)
            cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
            setStroke(UiKit.dp(ctx, 1), 0x22FFFFFF)
        }
    }

    fun rounded(ctx: Context, col: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(col)
            cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
        }
    }
}

object Palette {
    val PRIMARY = 0xFF6200EE.toInt()
    val SURFACE = 0xFF141526.toInt()
    val BACKGROUND = 0xFF0B0B14.toInt()
    val ACCENT = 0xFF8A2BE2.toInt()
    val TEXT_MUTED = 0xFF888899.toInt()

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}

class ActionSheet(private val ctx: Context) {
    var title: String = ""
    private val actions = mutableListOf<SheetAction>()

    fun addAction(label: String, onClick: () -> Unit) {
        actions.add(SheetAction(label, onClick))
    }

    fun addAction(action: SheetAction) {
        actions.add(action)
    }

    fun show() {
        val items = actions.map { it.label }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(title.ifEmpty { "Options" })
            .setItems(items) { _, which ->
                actions.getOrNull(which)?.onClick?.invoke()
            }
            .show()
    }
}

data class SheetAction(val label: String, val onClick: () -> Unit)

class RingView(ctx: Context) : View(ctx) {
    var progress: Float = 0f
        set(value) { field = value; invalidate() }
}

class DonutView(ctx: Context) : View(ctx) {
    var progress: Float = 0f
        set(value) { field = value; invalidate() }
    fun setData(vals: FloatArray, colors: IntArray) { invalidate() }
}
