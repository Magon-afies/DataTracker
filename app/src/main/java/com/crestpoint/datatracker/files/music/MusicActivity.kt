package com.crestpoint.datatracker.files.music

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Now-playing screen. Playback itself lives in MusicService so it keeps going when you leave. */
class MusicActivity : AppCompatActivity() {
    private lateinit var art: ImageView
    private lateinit var artEmoji: TextView
    private lateinit var titleT: TextView
    private lateinit var subT: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeT: TextView
    private lateinit var playBtn: TextView
    private lateinit var shuffleBtn: TextView
    private lateinit var repeatBtn: TextView
    private lateinit var upNext: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var shownPath = ""
    private var dragging = false
    private val tick = object : Runnable {
        override fun run() { sync(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayListExtra("paths")
        if (paths != null && paths.isNotEmpty()) {
            val idx = intent.getIntExtra("index", 0)
            val cur = MusicService.current
            if (cur == null || cur.path != paths.getOrNull(idx) || !MusicService.playing) {
                MusicService.play(this, paths.map { File(it) }, idx)
            }
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        val g = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(UiKit.color(this, R.color.brand_purple_dark), UiKit.color(this, R.color.surface))
        )
        root.background = g
        val pad = UiKit.dp(this, 24)
        root.setPadding(pad, UiKit.dp(this, 44), pad, pad)

        val top = LinearLayout(this)
        top.addView(UiKit.text(this, "Now playing", 14f, R.color.on_surface_muted, bold = true), UiKit.lp(0, -2, 1f))
        top.addView(UiKit.iconBtn(this, "✕") { finish() })
        root.addView(top)

        val frame = android.widget.FrameLayout(this)
        art = ImageView(this)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.visibility = android.view.View.GONE
        artEmoji = UiKit.text(this, "🎵", 90f)
        artEmoji.gravity = Gravity.CENTER
        frame.background = UiKit.rounded(this, R.color.surface_elevated, 32)
        frame.addView(artEmoji, android.widget.FrameLayout.LayoutParams(-1, -1))
        frame.addView(art, android.widget.FrameLayout.LayoutParams(-1, -1))
        Design.roundedOutline(frame, 32)
        frame.elevation = UiKit.dp(this, 16).toFloat()
        val size = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val flp = LinearLayout.LayoutParams(size, size)
        flp.topMargin = UiKit.dp(this, 20); flp.bottomMargin = UiKit.dp(this, 24)
        root.addView(frame, flp)

        titleT = UiKit.text(this, "Nothing playing", 22f, bold = true)
        titleT.gravity = Gravity.CENTER; titleT.maxLines = 2
        subT = UiKit.text(this, "", 14f, R.color.on_surface_muted)
        subT.gravity = Gravity.CENTER
        root.addView(titleT); root.addView(subT)

        seek = SeekBar(this)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) timeT.text = fmt(p) + " / " + fmt(seek.max) }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) { dragging = false; MusicService.command(this@MusicActivity, MusicService.A_SEEK, seek.progress) }
        })
        val slp = LinearLayout.LayoutParams(-1, -2); slp.topMargin = UiKit.dp(this, 22)
        root.addView(seek, slp)
        timeT = UiKit.text(this, "0:00 / 0:00", 12f, R.color.on_surface_muted)
        root.addView(timeT)

        val ctl = LinearLayout(this)
        ctl.gravity = Gravity.CENTER
        shuffleBtn = ctlBtn("🔀", 22f) { MusicService.shuffle = !MusicService.shuffle; sync() }
        val prev = ctlBtn("⏮", 30f) { MusicService.command(this, MusicService.A_PREV) }
        playBtn = ctlBtn("▶", 40f) { MusicService.command(this, MusicService.A_TOGGLE) }
        val next = ctlBtn("⏭", 30f) { MusicService.command(this, MusicService.A_NEXT) }
        repeatBtn = ctlBtn("🔁", 22f) { MusicService.repeat = !MusicService.repeat; sync() }
        listOf(shuffleBtn, prev, playBtn, next, repeatBtn).forEach { ctl.addView(it) }
        val clp = LinearLayout.LayoutParams(-1, -2); clp.topMargin = UiKit.dp(this, 14)
        root.addView(ctl, clp)

        upNext = UiKit.text(this, "", 12f, R.color.on_surface_muted)
        upNext.gravity = Gravity.CENTER
        upNext.setPadding(0, UiKit.dp(this, 18), 0, 0)
        root.addView(upNext)
        setContentView(root)
    }

    private fun ctlBtn(glyph: String, sp: Float, run: () -> Unit): TextView {
        val t = UiKit.text(this, glyph, sp)
        t.gravity = Gravity.CENTER
        val p = UiKit.dp(this, 14)
        t.setPadding(p, p, p, p)
        t.setOnClickListener { run() }
        Design.ripple(t)
        return t
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun fmt(ms: Int): String { val s = ms / 1000; return "${s / 60}:${"%02d".format(s % 60)}" }

    private fun sync() {
        val f = MusicService.current
        val p = MusicService.player
        playBtn.text = if (MusicService.playing) "⏸" else "▶"
        shuffleBtn.alpha = if (MusicService.shuffle) 1f else 0.4f
        repeatBtn.alpha = if (MusicService.repeat) 1f else 0.4f
        if (f == null) { titleT.text = "Nothing playing"; return }
        if (f.path != shownPath) { shownPath = f.path; loadMeta(f) }
        try {
            if (p != null && !dragging) {
                seek.max = p.duration; seek.progress = p.currentPosition
                timeT.text = fmt(p.currentPosition) + " / " + fmt(p.duration)
            }
        } catch (_: Exception) { }
        val q = MusicService.queue
        upNext.text = if (q.size > 1) "Next up: " + q[(MusicService.index + 1) % q.size].nameWithoutExtension + "  ·  ${MusicService.index + 1}/${q.size}" else ""
    }

    private fun loadMeta(f: File) {
        titleT.text = f.nameWithoutExtension; subT.text = ""
        art.visibility = android.view.View.GONE; artEmoji.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val m = MediaMetadataRetriever()
                    m.setDataSource(f.absolutePath)
                    val t = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val a = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val pic = m.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    m.release()
                    Triple(t, a, pic)
                } catch (_: Exception) { null }
            }
            if (r != null && shownPath == f.path) {
                if (!r.first.isNullOrBlank()) titleT.text = r.first
                subT.text = r.second ?: ""
                if (r.third != null) { art.setImageBitmap(r.third); art.visibility = android.view.View.VISIBLE; artEmoji.visibility = android.view.View.GONE }
            }
        }
    }
}
