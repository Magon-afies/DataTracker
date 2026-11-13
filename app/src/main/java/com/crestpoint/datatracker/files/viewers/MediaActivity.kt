package com.crestpoint.datatracker.files.viewers

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.ui.UiKit
import java.io.File

/** Built-in video/audio player (system codecs) with speed control and resume position. */
class MediaActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    private lateinit var path: String
    private lateinit var prefs: FilesPrefs
    private var player: MediaPlayer? = null
    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    private var speedIdx = 0
    private lateinit var speedLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra("path") ?: run { finish(); return }
        prefs = FilesPrefs(this)
        val f = File(path)

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        video = VideoView(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setBackgroundColor(0x99000000.toInt())
        top.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 32), UiKit.dp(this, 8), UiKit.dp(this, 6))
        val name = UiKit.text(this, f.name, 14f)
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(name, UiKit.lp(0, -2, 1f))
        speedLabel = UiKit.text(this, "1x", 16f, bold = true)
        speedLabel.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8), UiKit.dp(this, 12), UiKit.dp(this, 8))
        speedLabel.setOnClickListener { cycleSpeed() }
        top.addView(speedLabel)
        top.addView(UiKit.iconBtn(this, "✕") { finish() })
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)

        val mc = MediaController(this)
        mc.setAnchorView(video)
        video.setMediaController(mc)
        video.setVideoPath(path)
        video.setOnPreparedListener { mp ->
            player = mp
            val pos = prefs.mediaPos(path)
            if (pos > 0 && pos < video.duration - 3000) video.seekTo(pos)
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            UiKit.toast(this, "Cannot play this file here"); finish(); true
        }
        video.setOnCompletionListener { prefs.setMediaPos(path, 0) }
    }

    private fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.size
        val s = speeds[speedIdx]
        speedLabel.text = "${s}x".replace(".0x", "x")
        try {
            val mp = player ?: return
            val wasPlaying = video.isPlaying
            mp.playbackParams = PlaybackParams().setSpeed(s)
            if (!wasPlaying) video.pause()
        } catch (_: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        try { if (video.currentPosition > 0) prefs.setMediaPos(path, video.currentPosition) } catch (_: Exception) { }
        video.pause()
    }
}
