package com.crestpoint.datatracker.files.viewers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.edit.Editors
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import java.io.File
import kotlin.math.abs
import kotlin.math.max

private fun fmtTime(ms: Long): String {
    val s = max(0L, ms) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Full-screen video player: gestures, speed, playlist of the folder, resume, edit shortcut. */
class MediaActivity : AppCompatActivity() {
    private lateinit var path: String
    private lateinit var prefs: FilesPrefs
    private lateinit var root: FrameLayout
    private lateinit var pv: PlayerView
    private lateinit var title: TextView
    private lateinit var playBtn: ImageView
    private lateinit var slider: Slider
    private lateinit var tCur: TextView
    private lateinit var tDur: TextView
    private lateinit var speedChip: TextView
    private lateinit var hud: TextView
    private lateinit var topBar: View
    private lateinit var centerRow: View
    private lateinit var bottomBar: View
    private var player: ExoPlayer? = null
    private var list: List<File> = emptyList()
    private var index = 0
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIdx = 2
    private var zoom = false
    private var dragging = false
    private var shown = true
    private var volAcc = -1f
    private var pendingSeek = 0L
    private val h = Handler(Looper.getMainLooper())
    private val hideRun = Runnable { if (player?.isPlaying == true) setControls(false) }
    private val hudHide = Runnable { hud.visibility = View.GONE }
    private val tick = object : Runnable {
        override fun run() { updateProgress(); h.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra("path") ?: run { finish(); return }
        prefs = FilesPrefs(this)
        val cur = File(path)
        var vids = cur.parentFile?.listFiles()?.filter { FileKinds.of(it) == FileKind.VIDEO }?.sortedBy { it.name.lowercase() } ?: emptyList()
        if (vids.none { it.path == path }) vids = vids + cur
        list = vids
        index = list.indexOfFirst { it.path == path }.coerceAtLeast(0)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val ic = WindowCompat.getInsetsController(window, window.decorView)
        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ic.hide(WindowInsetsCompat.Type.systemBars())

        val accent = UiKit.color(this, R.color.brand_purple)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        pv = PlayerView(this)
        pv.useController = false
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))

        // top bar
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.background = Glass.gradient(true)
        top.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 26), UiKit.dp(this, 8), UiKit.dp(this, 22))
        top.addView(Glass.iconBtn(this, Glass.BACK) { finish() })
        title = UiKit.text(this, "", 15f, bold = true)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(title, UiKit.lp(0, -2, 1f))
        top.addView(Glass.iconBtn(this, Glass.EDIT) { player?.pause(); Editors.open(this, File(list[index].path)) })
        top.addView(Glass.iconBtn(this, Glass.ROTATE) { toggleOrientation() })
        top.addView(Glass.iconBtn(this, Glass.ASPECT) {
            zoom = !zoom
            pv.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            hudText(if (zoom) "Fill screen" else "Fit")
        })
        topBar = top
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // centre controls
        val mid = LinearLayout(this)
        mid.gravity = Gravity.CENTER
        mid.addView(Glass.iconBtn(this, Glass.PREV, 52) { go(-1) })
        mid.addView(Glass.iconBtn(this, Glass.REW, 52) { seekBy(-10_000L) })
        playBtn = ImageView(this)
        playBtn.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 20), UiKit.dp(this, 20), UiKit.dp(this, 20))
        playBtn.background = Glass.circle(accent)
        playBtn.setOnClickListener { togglePlay() }
        val pl = LinearLayout.LayoutParams(UiKit.dp(this, 76), UiKit.dp(this, 76))
        pl.setMargins(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), 0)
        mid.addView(playBtn, pl)
        mid.addView(Glass.iconBtn(this, Glass.FF, 52) { seekBy(10_000L) })
        mid.addView(Glass.iconBtn(this, Glass.NEXT, 52) { go(1) })
        centerRow = mid
        root.addView(mid, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // bottom bar
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.background = Glass.gradient(false)
        bottom.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 28), UiKit.dp(this, 16), UiKit.dp(this, 18))
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        tCur = UiKit.text(this, "0:00", 12f)
        tDur = UiKit.text(this, "0:00", 12f)
        slider = Slider(this)
        slider.valueFrom = 0f
        slider.valueTo = 1f
        slider.value = 0f
        slider.trackHeight = UiKit.dp(this, 4)
        slider.thumbRadius = UiKit.dp(this, 7)
        slider.labelBehavior = LabelFormatter.LABEL_GONE
        slider.trackActiveTintList = ColorStateList.valueOf(accent)
        slider.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        slider.trackInactiveTintList = ColorStateList.valueOf(0x44FFFFFF)
        slider.addOnChangeListener { _, v, fromUser -> if (fromUser) tCur.text = fmtTime((v * 1000).toLong()) }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(s: Slider) { dragging = true }
            override fun onStopTrackingTouch(s: Slider) { dragging = false; player?.seekTo((s.value * 1000).toLong()) }
        })
        row.addView(tCur)
        row.addView(slider, UiKit.lp(0, -2, 1f))
        row.addView(tDur)
        bottom.addView(row)
        val row2 = LinearLayout(this)
        row2.gravity = Gravity.END
        speedChip = UiKit.text(this, "1x", 13f, bold = true)
        speedChip.background = Glass.pill(0x55FFFFFF, 16, this)
        speedChip.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 6), UiKit.dp(this, 14), UiKit.dp(this, 6))
        speedChip.setOnClickListener { cycleSpeed() }
        row2.addView(speedChip)
        bottom.addView(row2)
        bottomBar = bottom
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        hud = UiKit.text(this, "", 16f, bold = true)
        hud.background = Glass.pill(0xAA000000.toInt(), 14, this)
        hud.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 10), UiKit.dp(this, 18), UiKit.dp(this, 10))
        hud.visibility = View.GONE
        val hl = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hl.topMargin = UiKit.dp(this, 90)
        root.addView(hud, hl)

        root.setOnTouchListener { _, e -> gd.onTouchEvent(e); true }
        setContentView(root)

        val p = ExoPlayer.Builder(this).build()
        player = p
        pv.player = p
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayIcon(); scheduleHide() }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val d = p.duration
                    if (d > 0) { slider.valueTo = max(1f, d / 1000f); tDur.text = fmtTime(d) }
                    if (pendingSeek > 0L) {
                        if (d > 0 && pendingSeek < d - 3000L) p.seekTo(pendingSeek)
                        pendingSeek = 0L
                    }
                }
                if (state == Player.STATE_ENDED) {
                    prefs.setMediaPos(list[index].path, 0)
                    if (index < list.size - 1) go(1) else setControls(true)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                UiKit.toast(this@MediaActivity, "Cannot play this file here")
                finish()
            }
        })
        load(index)
        h.post(tick)
    }

    private val gd by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean { volAcc = -1f; return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { setControls(!shown); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val left = e.x < root.width / 2f
                seekBy(if (left) -10_000L else 10_000L)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null || abs(dy) < abs(dx)) return false
                val d = dy / root.height * 1.4f
                if (e1.x < root.width / 2f) adjustBrightness(d) else adjustVolume(d)
                return true
            }
        })
    }

    private fun load(i: Int) {
        val p = player ?: return
        val f = list[i]
        path = f.path
        title.text = f.name
        pendingSeek = prefs.mediaPos(path).toLong()
        slider.value = 0f
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(f)))
        p.prepare()
        p.playWhenReady = true
        p.setPlaybackSpeed(speeds[speedIdx])
        updatePlayIcon()
    }

    private fun go(dir: Int) {
        if (list.size < 2) return
        player?.let { if (it.currentPosition > 0) prefs.setMediaPos(path, it.currentPosition.toInt()) }
        index = (index + dir + list.size) % list.size
        load(index)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) { p.seekTo(0); p.play() }
        else if (p.isPlaying) p.pause() else p.play()
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        val t = (p.currentPosition + ms).coerceIn(0L, max(0L, p.duration))
        p.seekTo(t)
        hudText(if (ms < 0) "−10s" else "+10s")
    }

    private fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.size
        val s = speeds[speedIdx]
        player?.setPlaybackSpeed(s)
        speedChip.text = "${s}x".replace(".0x", "x")
        scheduleHide()
    }

    private fun toggleOrientation() {
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun updatePlayIcon() {
        val playing = player?.isPlaying == true
        playBtn.setImageDrawable(PathIcon(if (playing) Glass.PAUSE else Glass.PLAY, Color.WHITE))
    }

    private fun updateProgress() {
        val p = player ?: return
        if (dragging || p.duration <= 0L) return
        slider.value = (p.currentPosition / 1000f).coerceIn(0f, slider.valueTo)
        tCur.text = fmtTime(p.currentPosition)
    }

    private fun setControls(show: Boolean) {
        shown = show
        val v = if (show) View.VISIBLE else View.GONE
        topBar.visibility = v; centerRow.visibility = v; bottomBar.visibility = v
        if (show) scheduleHide()
    }

    private fun scheduleHide() {
        h.removeCallbacks(hideRun)
        if (shown && player?.isPlaying == true) h.postDelayed(hideRun, 3500)
    }

    private fun hudText(s: String) {
        hud.text = s
        hud.visibility = View.VISIBLE
        h.removeCallbacks(hudHide)
        h.postDelayed(hudHide, 800)
    }

    private fun adjustBrightness(d: Float) {
        val lp = window.attributes
        val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        lp.screenBrightness = (cur + d).coerceIn(0.02f, 1f)
        window.attributes = lp
        hudText("Brightness ${(lp.screenBrightness * 100).toInt()}%")
    }

    private fun adjustVolume(d: Float) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volAcc < 0f) volAcc = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        volAcc = (volAcc + d * mx).coerceIn(0f, mx.toFloat())
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volAcc.toInt(), 0)
        hudText("Volume ${(volAcc * 100 / mx).toInt()}%")
    }

    override fun onPause() {
        super.onPause()
        player?.let { if (it.currentPosition > 0) prefs.setMediaPos(path, it.currentPosition.toInt()); it.pause() }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
