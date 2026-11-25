package com.crestpoint.datatracker.files.viewers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import java.io.File

/** The simple, standard player (Android's built-in VideoView) with clean vector controls. */
class ClassicPlayerActivity : AppCompatActivity() {
    companion object {
        fun start(ctx: Context, f: File) { ctx.startActivity(Intent(ctx, ClassicPlayerActivity::class.java).putExtra("path", f.path)) }
    }

    private lateinit var video: VideoView
    private lateinit var seek: SeekBar
    private lateinit var timeT: TextView
    private lateinit var playBtn: ImageView
    private lateinit var bar: LinearLayout
    private lateinit var path: String
    private val handler = Handler(Looper.getMainLooper())
    private var dragging = false
    private val tick = object : Runnable {
        override fun run() {
            if (!dragging && video.duration > 0) {
                seek.max = video.duration; seek.progress = video.currentPosition
                timeT.text = fmt(video.currentPosition) + " / " + fmt(video.duration)
            }
            playBtn.setImageResource(if (video.isPlaying) R.drawable.ic_fx_pause else R.drawable.ic_fx_play)
            handler.postDelayed(this, 300)
        }
    }

    private fun fmt(ms: Int): String { val s = ms / 1000; return "${s / 60}:${"%02d".format(s % 60)}" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra("path") ?: run { finish(); return }
        val prefs = FilesPrefs(this)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        video = VideoView(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(0xAA000000.toInt())
        bar.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 6), UiKit.dp(this, 12), UiKit.dp(this, 14))
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        seek = SeekBar(this)
        seek.progressTintList = android.content.res.ColorStateList.valueOf(UiKit.color(this, R.color.brand_purple))
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(UiKit.color(this, R.color.brand_purple))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) { dragging = false; video.seekTo(seek.progress) }
        })
        timeT = UiKit.text(this, "0:00 / 0:00", 12f)
        row.addView(seek, UiKit.lp(0, -2, 1f)); row.addView(timeT)
        val ctl = LinearLayout(this)
        ctl.gravity = Gravity.CENTER
        ctl.addView(Design.icon(this, R.drawable.ic_fx_replay, 52, Color.WHITE) { video.seekTo((video.currentPosition - 10000).coerceAtLeast(0)) })
        playBtn = Design.icon(this, R.drawable.ic_fx_pause, 64, Color.WHITE) { if (video.isPlaying) video.pause() else video.start() }
        ctl.addView(playBtn)
        ctl.addView(Design.icon(this, R.drawable.ic_fx_forward, 52, Color.WHITE) { video.seekTo(video.currentPosition + 10000) })
        bar.addView(row); bar.addView(ctl)
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(Design.icon(this, R.drawable.ic_fx_close, 44, Color.WHITE) { finish() },
            FrameLayout.LayoutParams(UiKit.dp(this, 44), UiKit.dp(this, 44), Gravity.TOP or Gravity.START).also { it.topMargin = UiKit.dp(this, 30); it.leftMargin = UiKit.dp(this, 8) })
        video.setOnClickListener { bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        setContentView(root)

        video.setVideoPath(path)
        video.setOnPreparedListener { val p = prefs.mediaPos(path); if (p > 2000 && p < video.duration - 3000) video.seekTo(p); video.start() }
        video.setOnErrorListener { _, _, _ -> UiKit.toast(this, "Can't play this file here"); finish(); true }
        handler.post(tick)
    }

    override fun onPause() { super.onPause(); try { FilesPrefs(this).setMediaPos(path, video.currentPosition) } catch (_: Exception) { }; video.pause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
