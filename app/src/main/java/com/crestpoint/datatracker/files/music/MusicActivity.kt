package com.crestpoint.datatracker.files.music

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.ActionSheet
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.SheetAction
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.files.ui.sheetPick
import com.crestpoint.datatracker.files.viewers.MediaEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Now-playing screen. The player lives in MusicService; buttons act on it directly so they respond instantly. */
@OptIn(UnstableApi::class)
class MusicActivity : AppCompatActivity() {
    private lateinit var art: ImageView
    private lateinit var artEmoji: TextView
    private lateinit var titleT: TextView
    private lateinit var subT: TextView
    private lateinit var seek: SeekBar
    private lateinit var timeT: TextView
    private lateinit var playBtn: ImageView
    private lateinit var shuffleBtn: ImageView
    private lateinit var repeatBtn: ImageView
    private lateinit var upNext: TextView
    private lateinit var speedChip: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var shownPath = ""
    private var dragging = false
    private val tick = object : Runnable { override fun run() { sync(); handler.postDelayed(this, 300) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayListExtra("paths")
        if (paths != null && paths.isNotEmpty()) {
            val idx = intent.getIntExtra("index", 0)
            val same = MusicService.player != null && MusicService.queue.map { it.path } == paths && MusicService.index == idx
            if (!same) MusicService.play(this, paths.map { File(it) }, idx)
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(UiKit.color(this, R.color.brand_purple_dark), UiKit.color(this, R.color.surface))
        )
        val pad = UiKit.dp(this, 24)
        root.setPadding(pad, UiKit.dp(this, 40), pad, pad)

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Design.icon(this, R.drawable.ic_fx_back, 44) { finish() })
        top.addView(UiKit.text(this, "Now playing", 14f, R.color.on_surface_muted, bold = true).also { it.gravity = Gravity.CENTER }, UiKit.lp(0, -2, 1f))
        top.addView(Design.icon(this, R.drawable.ic_fx_cut, 44) { trim() })
        root.addView(top)

        val frame = FrameLayout(this)
        art = ImageView(this)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.visibility = View.GONE
        artEmoji = UiKit.text(this, "🎵", 90f)
        artEmoji.gravity = Gravity.CENTER
        frame.background = UiKit.rounded(this, R.color.surface_elevated, 32)
        frame.addView(artEmoji, FrameLayout.LayoutParams(-1, -1))
        frame.addView(art, FrameLayout.LayoutParams(-1, -1))
        Design.roundedOutline(frame, 32)
        frame.elevation = UiKit.dp(this, 16).toFloat()
        val size = (resources.displayMetrics.widthPixels * 0.66f).toInt()
        val flp = LinearLayout.LayoutParams(size, size)
        flp.topMargin = UiKit.dp(this, 12); flp.bottomMargin = UiKit.dp(this, 18)
        root.addView(frame, flp)

        titleT = UiKit.text(this, "Nothing playing", 22f, bold = true)
        titleT.gravity = Gravity.CENTER; titleT.maxLines = 2
        subT = UiKit.text(this, "", 14f, R.color.on_surface_muted)
        subT.gravity = Gravity.CENTER
        root.addView(titleT); root.addView(subT)

        seek = SeekBar(this)
        val purple = UiKit.color(this, R.color.brand_purple)
        seek.progressTintList = ColorStateList.valueOf(purple)
        seek.thumbTintList = ColorStateList.valueOf(purple)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) timeT.text = fmt(p.toLong()) + " / " + fmt(seek.max.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) { dragging = false; MusicService.player?.seekTo(seek.progress.toLong()) }
        })
        val slp = LinearLayout.LayoutParams(-1, -2); slp.topMargin = UiKit.dp(this, 16)
        root.addView(seek, slp)
        timeT = UiKit.text(this, "0:00 / 0:00", 12f, R.color.on_surface_muted)
        root.addView(timeT)

        val ctl = LinearLayout(this)
        ctl.gravity = Gravity.CENTER
        shuffleBtn = ctlBtn(R.drawable.ic_fx_shuffle, 44) {
            val p = MusicService.player ?: return@ctlBtn
            p.shuffleModeEnabled = !p.shuffleModeEnabled; sync()
        }
        val prev = ctlBtn(R.drawable.ic_fx_prev, 56) { MusicService.prev(); sync() }
        playBtn = ctlBtn(R.drawable.ic_fx_play, 76) {
            MusicService.toggle(this)
            playBtn.setImageResource(if (MusicService.playing) R.drawable.ic_fx_pause else R.drawable.ic_fx_play)
        }
        val pg = android.graphics.drawable.GradientDrawable(); pg.shape = android.graphics.drawable.GradientDrawable.OVAL; pg.setColor(0x33FFFFFF)
        playBtn.background = pg
        val next = ctlBtn(R.drawable.ic_fx_next, 56) { MusicService.next(); sync() }
        repeatBtn = ctlBtn(R.drawable.ic_fx_repeat, 44) {
            val p = MusicService.player ?: return@ctlBtn
            p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
            sync()
        }
        listOf(shuffleBtn, prev, playBtn, next, repeatBtn).forEach { ctl.addView(it) }
        val clp = LinearLayout.LayoutParams(-1, -2); clp.topMargin = UiKit.dp(this, 8)
        root.addView(ctl, clp)

        val extras = LinearLayout(this)
        extras.gravity = Gravity.CENTER
        extras.addView(Design.icon(this, R.drawable.ic_fx_queue, 46) { queueSheet() })
        extras.addView(Design.icon(this, R.drawable.ic_fx_timer, 46) { timerSheet() })
        speedChip = UiKit.text(this, "1×", 15f, bold = true)
        speedChip.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 10), UiKit.dp(this, 16), UiKit.dp(this, 10))
        speedChip.setOnClickListener { speedSheet() }
        extras.addView(speedChip)
        extras.addView(Design.icon(this, R.drawable.ic_fx_equalizer, 46) { equalizer() })
        extras.addView(Design.icon(this, R.drawable.ic_fx_close, 46, UiKit.color(this, R.color.health_bad)) { MusicService.stop(this); finish() })
        root.addView(extras)

        upNext = UiKit.text(this, "", 12f, R.color.on_surface_muted)
        upNext.gravity = Gravity.CENTER
        upNext.setPadding(0, UiKit.dp(this, 10), 0, 0)
        root.addView(upNext)
        setContentView(root)
    }

    private fun ctlBtn(res: Int, size: Int, run: () -> Unit): ImageView {
        val v = Design.icon(this, res, size, UiKit.color(this, R.color.on_surface), run)
        val lp = LinearLayout.LayoutParams(UiKit.dp(this, size), UiKit.dp(this, size))
        lp.setMargins(UiKit.dp(this, 5), 0, UiKit.dp(this, 5), 0)
        v.layoutParams = lp
        return v
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "${s / 60}:${"%02d".format(s % 60)}" }

    private fun sync() {
        val p = MusicService.player
        val f = MusicService.current
        playBtn.setImageResource(if (MusicService.playing) R.drawable.ic_fx_pause else R.drawable.ic_fx_play)
        val on = UiKit.color(this, R.color.on_surface)
        shuffleBtn.imageTintList = ColorStateList.valueOf(if (p?.shuffleModeEnabled == true) UiKit.color(this, R.color.brand_cyan) else 0x66FFFFFF)
        repeatBtn.imageTintList = ColorStateList.valueOf(when (p?.repeatMode) {
            Player.REPEAT_MODE_ALL -> on
            Player.REPEAT_MODE_ONE -> UiKit.color(this, R.color.brand_cyan)
            else -> 0x66FFFFFF
        })
        if (f == null) { titleT.text = "Nothing playing"; return }
        if (f.path != shownPath) { shownPath = f.path; loadMeta(f) }
        if (p != null && !dragging) {
            val d = p.duration
            if (d > 0) { seek.max = d.toInt(); seek.progress = p.currentPosition.toInt(); timeT.text = fmt(p.currentPosition) + " / " + fmt(d) }
            speedChip.text = ("${p.playbackParameters.speed}×").replace(".0×", "×")
        }
        val q = MusicService.queue
        upNext.text = if (q.size > 1) "Track ${MusicService.index + 1} of ${q.size}  ·  Next: " + q[(MusicService.index + 1) % q.size].nameWithoutExtension else ""
    }

    private fun loadMeta(f: File) {
        titleT.text = f.nameWithoutExtension; subT.text = ""
        art.visibility = View.GONE; artEmoji.visibility = View.VISIBLE
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
                if (r.third != null) { art.setImageBitmap(r.third); art.visibility = View.VISIBLE; artEmoji.visibility = View.GONE }
            }
        }
    }

    private fun queueSheet() {
        val p = MusicService.player ?: return
        val q = MusicService.queue
        if (q.isEmpty()) return
        ActionSheet.show(this, "Up next · ${q.size} tracks", q.take(150).mapIndexed { i, f ->
            sheetPick(i == MusicService.index, f.nameWithoutExtension) { p.seekTo(i, 0L); p.play(); sync() }
        })
    }

    private fun timerSheet() {
        val opts = listOf(0, 10, 20, 30, 45, 60)
        ActionSheet.show(this, "Sleep timer", opts.map { m ->
            val active = if (m == 0) MusicService.sleepAt == 0L else false
            sheetPick(active, if (m == 0) "Off" else "Stop after $m minutes") {
                MusicService.sleepAt = if (m == 0) 0L else System.currentTimeMillis() + m * 60_000L
                UiKit.toast(this, if (m == 0) "Sleep timer off" else "Music will stop in $m minutes")
            }
        })
    }

    private fun speedSheet() {
        val p = MusicService.player ?: return
        val cur = p.playbackParameters.speed
        ActionSheet.show(this, "Playback speed", listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { s ->
            sheetPick(s == cur, if (s == 1f) "Normal" else "${s}×") { p.setPlaybackSpeed(s); sync() }
        })
    }

    private fun equalizer() {
        val p = MusicService.player ?: return
        val i = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, p.audioSessionId)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        try { startActivity(i) } catch (_: Exception) { UiKit.toast(this, "No equalizer found on this phone") }
    }

    private fun trim() {
        val p = MusicService.player ?: return
        val f = MusicService.current ?: return
        p.pause()
        MediaEditor.show(this, f, p.duration, p.currentPosition, MediaEditor.Mode.TRIM)
    }
}
