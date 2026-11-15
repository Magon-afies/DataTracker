#!/usr/bin/env python3
import os

proj = os.path.expanduser("~/DataTracker")
src_java = os.path.join(proj, "app/src/main/java/com/crestpoint/datatracker")
ui_dir = os.path.join(src_java, "files/ui")
util_dir = os.path.join(src_java, "util")
usage_dir = os.path.join(src_java, "usage")

os.makedirs(ui_dir, exist_ok=True)
os.makedirs(util_dir, exist_ok=True)
os.makedirs(usage_dir, exist_ok=True)

print("==> Writing comprehensive UiKit, Palette, FormatBytes, and UI Helpers...")

# 1. Comprehensive UiKit.kt & Design System
uikit_code = r"""package com.crestpoint.datatracker.files.ui

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
}
"""

with open(os.path.join(ui_dir, "UiKit.kt"), "w", encoding="utf-8") as f:
    f.write(uikit_code)

# 2. FormatBytes Utility
format_bytes_code = r"""package com.crestpoint.datatracker.util

object FormatBytes {
    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
"""

with open(os.path.join(util_dir, "FormatBytes.kt"), "w", encoding="utf-8") as f:
    f.write(format_bytes_code)

# 3. Usage & Repository Stubs to satisfy DataBot / AppManager
usage_repo_code = r"""package com.crestpoint.datatracker.usage

class UsageRepository {
    fun getAppUsageStats(): Map<String, Long> = emptyMap()
}
"""

with open(os.path.join(usage_dir, "UsageRepository.kt"), "w", encoding="utf-8") as f:
    f.write(usage_repo_code)

print("==> All missing utility and UI classes successfully created.")
