#!/usr/bin/env python3
# DataTracker Ultimate Master Overhaul: Modern UI, SVG Icons, Editors, Fast CacheBot & Background Data Restrictor Bot
import os, sys

proj = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/DataTracker")
SRC = os.path.join(proj, "app/src/main")
J = os.path.join(SRC, "java/com/crestpoint/datatracker/files")
GRADLE = os.path.join(proj, "app/build.gradle.kts")
MAN = os.path.join(SRC, "AndroidManifest.xml")

print("==> Deploying DataTracker Ultimate Master Package...")

# 1. Gradle Dependencies
if os.path.exists(GRADLE):
    g = open(GRADLE, encoding="utf-8").read()
    if "media3-transformer" not in g:
        g = g.replace("dependencies {\n", 
            'dependencies {\n    implementation("androidx.media3:media3-exoplayer:1.4.1")\n'
            '    implementation("androidx.media3:media3-ui:1.4.1")\n'
            '    implementation("androidx.media3:media3-transformer:1.4.1")\n'
            '    implementation("androidx.media3:media3-effect:1.4.1")\n'
            '    implementation("androidx.media3:media3-common:1.4.1")\n')
        open(GRADLE, "w", encoding="utf-8").write(g)

# 2. Manifest Activities & OpenWith Intent Filters
if os.path.exists(MAN):
    m = open(MAN, encoding="utf-8").read()
    if "OpenWithActivity" not in m:
        m = m.replace("    </application>", '''
        <activity android:name=".files.viewers.MediaActivity" android:exported="false" android:supportsPictureInPicture="true" android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
        <activity android:name=".files.edit.VideoEditorActivity" android:exported="false" android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
        <activity android:name=".files.edit.SnapEditorActivity" android:exported="false" />
        <activity android:name=".cleaner.CacheBotActivity" android:exported="false" />
        <activity android:name=".databot.DataBotActivity" android:exported="false" />
        <activity android:name=".files.OpenWithActivity" android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
                <data android:mimeType="audio/*" />
                <data android:mimeType="image/*" />
                <data android:mimeType="application/pdf" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.EDIT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
                <data android:mimeType="audio/*" />
                <data android:mimeType="image/*" />
            </intent-filter>
        </activity>
    </application>''')
        open(MAN, "w", encoding="utf-8").write(m)

os.makedirs(os.path.join(J, "edit"), exist_ok=True)
os.makedirs(os.path.join(J, "viewers"), exist_ok=True)
os.makedirs(os.path.join(J, "ui"), exist_ok=True)
os.makedirs(os.path.join(SRC, "java/com/crestpoint/datatracker/cleaner"), exist_ok=True)
os.makedirs(os.path.join(SRC, "java/com/crestpoint/datatracker/databot"), exist_ok=True)

# 3. Modern Design System & SVG Icons (Design.kt & Glass.kt)
print("==> Writing Modern UI Design System...")
with open(os.path.join(J, "ui/Design.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.crestpoint.datatracker.R

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

    fun chip(ctx: Context, label: String, active: Boolean, onClick: () -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(if (active) UiKit.color(ctx, R.color.brand_purple) else 0xFF1E1F38.toInt())
                cornerRadius = UiKit.dp(ctx, 20).toFloat()
                if (!active) setStroke(UiKit.dp(ctx, 1), 0x33FFFFFF)
            }
            val padH = UiKit.dp(ctx, 16); val padV = UiKit.dp(ctx, 10)
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick() }
            ripple(this, 20)
        }
        val t = UiKit.text(ctx, label, 13f, if (active) android.R.color.white else R.color.on_surface_muted)
        t.gravity = Gravity.CENTER
        box.addView(t)
        box.layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            setMargins(UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 6), 0)
        }
        return box
    }
}
""")

with open(os.path.join(J, "ui/Glass.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.files.ui

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
""")

# 4. Ultra-Fast CacheBot Cleaner
print("==> Writing Lightning-Fast CacheBot...")
with open(os.path.join(SRC, "java/com/crestpoint/datatracker/cleaner/CacheBotActivity.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.cleaner

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
        val accent = UiKit.color(this, R.color.brand_purple)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B14.toInt())
            setPadding(UiKit.dp(this, 16), UiKit.dp(this, 32), UiKit.dp(this, 16), UiKit.dp(this, 16))
        }

        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Glass.iconBtn(this, Glass.BACK) { finish() })
            addView(UiKit.text(this, "CacheBot Cleaner", 18f, bold = true), UiKit.lp(0, -2, 1f))
            addView(Glass.iconBtn(this, Glass.SETTINGS, 44) { scanCache() })
        }
        root.addView(topBar)

        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16))
        }

        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Design.card(this@CacheBotActivity, 0xFF141526.toInt(), 20)
            setPadding(UiKit.dp(this, 24), UiKit.dp(this, 28), UiKit.dp(this, 24), UiKit.dp(this, 28))
            
            val iconBox = ImageView(this@CacheBotActivity).apply {
                setImageDrawable(PathIcon(Glass.CLEANER, accent))
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@CacheBotActivity, 56), UiKit.dp(this@CacheBotActivity, 56))
            }
            addView(iconBox)

            statusText = UiKit.text(this@CacheBotActivity, "Ready to clean", 15f, R.color.on_surface_muted).apply {
                gravity = Gravity.CENTER
                setPadding(0, UiKit.dp(this@CacheBotActivity, 12), 0, 4)
            }
            addView(statusText)

            sizeText = UiKit.text(this@CacheBotActivity, "0 MB", 32f, bold = true).apply {
                gravity = Gravity.CENTER
            }
            addView(sizeText)

            progressBar = ProgressBar(this@CacheBotActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progress = 0; isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(-1, UiKit.dp(this@CacheBotActivity, 6)).apply {
                    setMargins(0, UiKit.dp(this@CacheBotActivity, 20), 0, 0)
                }
            }
            addView(progressBar)
        }
        contentCol.addView(heroCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@CacheBotActivity, 16)) })

        cleanButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(accent); cornerRadius = UiKit.dp(this@CacheBotActivity, 24).toFloat() }
            setPadding(UiKit.dp(this, 24), UiKit.dp(this, 16), UiKit.dp(this, 24), UiKit.dp(this, 16))
            setOnClickListener { if (!isScanning && !isCleaning) executeClean() }
            Design.ripple(this, 24)
            addView(UiKit.text(this@CacheBotActivity, "⚡ Instant Deep Clean", 16f, bold = true).apply { setTextColor(Color.WHITE) })
        }
        contentCol.addView(cleanButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@CacheBotActivity, 24)) })

        contentCol.addView(UiKit.text(this, "STORAGE BREAKDOWN", 12f, R.color.on_surface_muted).apply {
            setPadding(UiKit.dp(this, 4), 0, 0, UiKit.dp(this, 8))
        })

        detailsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
                sizeText.text = formatSize(totalCacheBytes)
                statusText.text = if (totalCacheBytes > 0) "Junk files found. Ready to clear." else "System is sparkling clean!"
                if (items.isEmpty()) addDetailRow("No cache residue found", "0 KB")
                else items.forEach { addDetailRow(it.first, formatSize(it.second)) }
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
                statusText.text = "Successfully freed " + formatSize(freed) + "!"
                UiKit.toast(this@CacheBotActivity, "Freed " + formatSize(freed))
                detailsContainer.removeAllViews()
                addDetailRow("Temporary Cache", "0 KB")
            }
        }.start()
    }

    private fun addDetailRow(title: String, sizeStr: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Design.card(this@CacheBotActivity, 0xFF141526.toInt(), 14)
            setPadding(UiKit.dp(this@CacheBotActivity, 16), UiKit.dp(this@CacheBotActivity, 14), UiKit.dp(this@CacheBotActivity, 16), UiKit.dp(this@CacheBotActivity, 14))
            addView(UiKit.text(this@CacheBotActivity, title, 14f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(UiKit.text(this@CacheBotActivity, sizeStr, 13f, R.color.on_surface_muted))
        }
        detailsContainer.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this, 8)) })
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
""")

# 5. New Background App & Data Restrictor Bot (DataBotActivity.kt)
print("==> Writing Background App & Data Restrictor Bot...")
with open(os.path.join(SRC, "java/com/crestpoint/datatracker/databot/DataBotActivity.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.databot

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit

class DataBotActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = UiKit.color(this, R.color.brand_purple)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B14.toInt())
            setPadding(UiKit.dp(this, 16), UiKit.dp(this, 32), UiKit.dp(this, 16), UiKit.dp(this, 16))
        }

        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Glass.iconBtn(this, Glass.BACK) { finish() })
            addView(UiKit.text(this, "Background Data Bot", 18f, bold = true), UiKit.lp(0, -2, 1f))
        }
        root.addView(topBar)

        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16))
        }

        // Hero Card
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = Design.card(this@DataBotActivity, 0xFF141526.toInt(), 20)
            setPadding(UiKit.dp(this, 24), UiKit.dp(this, 28), UiKit.dp(this, 24), UiKit.dp(this, 28))
            
            val icon = ImageView(this@DataBotActivity).apply {
                setImageDrawable(PathIcon(Glass.SETTINGS, accent))
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@DataBotActivity, 56), UiKit.dp(this@DataBotActivity, 56))
            }
            addView(icon)
            
            addView(UiKit.text(this@DataBotActivity, "Background App Guard", 18f, bold = true).apply {
                setPadding(0, UiKit.dp(this@DataBotActivity, 12), 0, 4); gravity = Gravity.CENTER
            })
            addView(UiKit.text(this@DataBotActivity, "Easily manage and restrict background data consumption app-by-app to stop hidden data drains.", 13f, R.color.on_surface_muted).apply {
                gravity = Gravity.CENTER
            })
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@DataBotActivity, 24)) })

        // Action Buttons for Background Restriction Guidance
        fun addActionCard(title: String, desc: String, onClick: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Design.card(this@DataBotActivity, 0xFF141526.toInt(), 16)
                setPadding(UiKit.dp(this@DataBotActivity, 18), UiKit.dp(this@DataBotActivity, 16), UiKit.dp(this@DataBotActivity, 16), UiKit.dp(this@DataBotActivity, 16))
                setOnClickListener { onClick() }
                Design.ripple(this, 16)
                
                addView(UiKit.text(this@DataBotActivity, title, 15f, bold = true))
                addView(UiKit.text(this@DataBotActivity, desc, 12f, R.color.on_surface_muted).apply {
                    setPadding(0, 4, 0, 0)
                })
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@DataBotActivity, 12)) })
        }

        addActionCard("🛡️ Restrict Background Data Settings", "Open system data usage settings to toggle background data off for heavy apps one after the other.") {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }

        addActionCard("🔋 Battery & Background Restrictions", "Open battery usage optimization to stop background CPU and data drain.") {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }

        sv.addView(content)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
""")

print("==> Master Package script complete!")
