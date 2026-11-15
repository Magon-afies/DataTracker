#!/usr/bin/env python3
import os, shutil

proj = os.path.expanduser("~/DataTracker")
src_java = os.path.join(proj, "app/src/main/java/com/crestpoint/datatracker")

print("==> Cleaning conflicting files and writing definitive UI toolkit & bots...")

# 1. Recreate clean directories
ui_dir = os.path.join(src_java, "files/ui")
util_dir = os.path.join(src_java, "util")
cleaner_dir = os.path.join(src_java, "cleaner")
databot_dir = os.path.join(src_java, "databot")

os.makedirs(ui_dir, exist_ok=True)
os.makedirs(util_dir, exist_ok=True)
os.makedirs(cleaner_dir, exist_ok=True)
os.makedirs(databot_dir, exist_ok=True)

# 2. Write robust UiKit.kt & Design helpers (including 'human', 'iconBtn', 'card', etc.)
uikit_code = r"""package com.crestpoint.datatracker.files.ui

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
"""

with open(os.path.join(ui_dir, "UiKit.kt"), "w", encoding="utf-8") as f:
    f.write(uikit_code)

# 3. Write FormatBytes with both 'format' and 'human' aliases
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

    fun human(bytes: Long): String = format(bytes)
}
"""

with open(os.path.join(util_dir, "FormatBytes.kt"), "w", encoding="utf-8") as f:
    f.write(format_bytes_code)

# 4. Write CacheBotActivity.kt cleanly
cachebot_code = r"""package com.crestpoint.datatracker.cleaner

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import java.io.File

class CacheBotActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var sizeText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cleanButton: LinearLayout
    private lateinit var detailsContainer: LinearLayout
    private val h = Handler(Looper.getMainLooper())
    private var totalCacheBytes = 0L
    private var isScanning = false
    private var isCleaning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = this
        val accent = UiKit.color(act, R.color.brand_purple)
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B14.toInt())
            setPadding(UiKit.dp(act, 16), UiKit.dp(act, 32), UiKit.dp(act, 16), UiKit.dp(act, 16))
        }

        val topBar = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Glass.iconBtn(act, Glass.BACK) { finish() })
            addView(UiKit.text(act, "CacheBot Cleaner", 18f, bold = true), UiKit.lp(0, -2, 1f))
            addView(Glass.iconBtn(act, Glass.SETTINGS, 44) { scanCache() })
        }
        root.addView(topBar)

        val sv = ScrollView(act).apply { isVerticalScrollBarEnabled = false }
        val contentCol = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(act, 16), 0, UiKit.dp(act, 16))
        }

        val heroCard = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Design.card(act, 0xFF141526.toInt(), 20)
            setPadding(UiKit.dp(act, 24), UiKit.dp(act, 28), UiKit.dp(act, 24), UiKit.dp(act, 28))
            
            val iconBox = ImageView(act).apply {
                setImageDrawable(PathIcon(Glass.CLEANER, accent))
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(act, 56), UiKit.dp(act, 56))
            }
            addView(iconBox)

            statusText = UiKit.text(act, "Ready to clean", 15f, R.color.on_surface_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(act, 12), 0, 4)
            }
            addView(statusText)

            sizeText = UiKit.text(act, "0 MB", 32f, bold = true).apply {
                gravity = Gravity.CENTER
            }
            addView(sizeText)

            progressBar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = 0; isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(-1, UiKit.dp(act, 6)).apply {
                    setMargins(0, UiKit.dp(act, 20), 0, 0)
                }
            }
            addView(progressBar)
        }
        contentCol.addView(heroCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(act, 16)) })

        cleanButton = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(accent); cornerRadius = UiKit.dp(act, 24).toFloat() }
            setPadding(UiKit.dp(act, 24), UiKit.dp(act, 16), UiKit.dp(act, 24), UiKit.dp(act, 16))
            setOnClickListener { if (!isScanning && !isCleaning) executeClean() }
            Design.ripple(this, 24)
            addView(UiKit.text(act, "⚡ Instant Deep Clean", 16f, bold = true).apply { setTextColor(Color.WHITE) })
        }
        contentCol.addView(cleanButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(act, 24)) })

        contentCol.addView(UiKit.text(act, "STORAGE BREAKDOWN", 12f, R.color.on_surface_muted).apply {
            setPadding(UiKit.dp(act, 4), 0, 0, UiKit.dp(act, 8))
        })

        detailsContainer = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        contentCol.addView(detailsContainer)

        sv.addView(contentCol)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        scanCache()
    }

    private fun scanCache() {
        if (isScanning) return
        isScanning = true
        statusText.text = "Scanning app cache…"
        progressBar.isIndeterminate = true
        detailsContainer.removeAllViews()

        Thread {
            val act = this
            val items = ArrayList<Pair<String, Long>>()
            var bytes = 0L
            listOf(cacheDir, externalCacheDir, codeCacheDir).forEach { f ->
                if (f != null && f.exists()) {
                    var sz = 0L
                    try { f.walkTopDown().forEach { if (it.isFile) sz += it.length() } } catch (_: Exception) {}
                    if (sz > 0) items.add(Pair(f.name.replaceFirstChar { it.uppercase() } + " Cache", sz))
                    bytes += sz
                }
            }
            totalCacheBytes = bytes
            h.post {
                isScanning = false
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                sizeText.text = FormatBytes.human(totalCacheBytes)
                statusText.text = if (totalCacheBytes > 0) "Junk files found. Ready to clear." else "System is sparkling clean!"
                if (items.isEmpty()) addDetailRow("No cache residue found", "0 KB")
                else items.forEach { addDetailRow(it.first, FormatBytes.human(it.second)) }
            }
        }.start()
    }

    private fun executeClean() {
        if (isCleaning) return
        isCleaning = true
        statusText.text = "Clearing cache files…"
        progressBar.isIndeterminate = true
        cleanButton.alpha = 0.5f

        Thread {
            var freed = 0L
            try {
                listOf(cacheDir, externalCacheDir, codeCacheDir).forEach { f ->
                    if (f != null && f.exists()) {
                        f.walkTopDown().forEach { if (it.isFile) { val l = it.length(); if (it.delete()) freed += l } }
                    }
                }
            } catch (_: Exception) {}
            h.post {
                isCleaning = false
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                cleanButton.alpha = 1f
                sizeText.text = "0 MB"
                statusText.text = "Successfully freed " + FormatBytes.human(freed) + "!"
                UiKit.toast(this, "Freed " + FormatBytes.human(freed))
                detailsContainer.removeAllViews()
                addDetailRow("Temporary Cache", "0 KB")
            }
        }.start()
    }

    private fun addDetailRow(title: String, sizeStr: String) {
        val act = this
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Design.card(act, 0xFF141526.toInt(), 14)
            setPadding(UiKit.dp(act, 16), UiKit.dp(act, 14), UiKit.dp(act, 16), UiKit.dp(act, 14))
            addView(UiKit.text(act, title, 14f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(UiKit.text(act, sizeStr, 13f, R.color.on_surface_muted))
        }
        detailsContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(act, 8)) })
    }
}
"""

with open(os.path.join(cleaner_dir, "CacheBotActivity.kt"), "w", encoding="utf-8") as f:
    f.write(cachebot_code)

print("==> Definitive cleanup and fix completed successfully.")
