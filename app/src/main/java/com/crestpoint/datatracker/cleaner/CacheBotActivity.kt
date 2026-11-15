package com.crestpoint.datatracker.cleaner

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
