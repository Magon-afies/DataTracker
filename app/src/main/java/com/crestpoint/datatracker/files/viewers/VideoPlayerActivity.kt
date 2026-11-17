package com.crestpoint.datatracker.files.viewers

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.audiofx.LoudnessEnhancer
import android.view.PixelCopy
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.ui.sheetPick
import com.crestpoint.datatracker.files.ui.ActionSheet
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.SheetAction
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.math.abs

/** Lays the video surface out as Fit / Fill / Zoom inside the screen. */
class VideoStage(ctx: Context, val surface: SurfaceView) : ViewGroup(ctx) {
    var videoW = 0
    var videoH = 0
    var mode = 0 // 0 fit, 1 stretch, 2 zoom-to-fill
    init { addView(surface) }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(getDefaultSize(0, w), getDefaultSize(0, h)) }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = r - l; val ch = b - t
        var w = cw; var h = ch
        if (videoW > 0 && videoH > 0 && cw > 0 && ch > 0) {
            val va = videoW.toFloat() / videoH; val ca = cw.toFloat() / ch
            when (mode) {
                0 -> if (va > ca) { w = cw; h = (cw / va).toInt() } else { h = ch; w = (ch * va).toInt() }
                2 -> if (va > ca) { h = ch; w = (ch * va).toInt() } else { w = cw; h = (cw / va).toInt() }
            }
        }
        val x = (cw - w) / 2; val y = (ch - h) / 2
        surface.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        surface.layout(x, y, x + w, y + h)
    }
}

/**
 * Video player on Media3 ExoPlayer: hardware decoding, gestures (swipe for volume / brightness / seek,
 * double-tap to skip, hold for 2x), subtitles, audio tracks, speed, lock, PiP, resume, playlist.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var prefs: FilesPrefs
    private lateinit var root: FrameLayout
    private lateinit var stage: VideoStage
    private lateinit var controls: FrameLayout
    private lateinit var titleT: TextView
    private lateinit var seek: SeekBar
    private lateinit var posT: TextView
    private lateinit var durT: TextView
    private lateinit var playBtn: ImageView
    private lateinit var lockBtn: ImageView
    private lateinit var unlockBtn: ImageView
    private lateinit var speedChip: TextView
    private lateinit var hud: TextView
    private lateinit var subText: TextView
    private lateinit var audio: AudioManager

    private var paths: List<String> = emptyList()
    private var index = 0
    private var locked = false
    private var controlsOn = true
    private var dragging = false
    private var firstSize = true
    private var textOn = true
    private var enhancer: LoudnessEnhancer? = null
    private var boostLevel = 0
    private var sleepRunnable: Runnable? = null
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speed = 1f
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { if (player.isPlaying && !dragging) setControls(false) }
    private val hideHud = Runnable { hud.visibility = View.GONE }
    private var lastSave = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (!dragging) {
                val d = player.duration
                if (d > 0) { seek.max = d.toInt(); durT.text = fmt(d) }
                seek.progress = player.currentPosition.toInt()
                seek.secondaryProgress = player.bufferedPosition.toInt()
                posT.text = fmt(player.currentPosition)
            }
            val now = System.currentTimeMillis()
            if (now - lastSave > 5000) { lastSave = now; savePos() }
            handler.postDelayed(this, 400)
        }
    }

    // gesture state
    private enum class G { NONE, SEEK, BRIGHT, VOL }
    private var gmode = G.NONE
    private var startPos = 0L
    private var pendingSeek = 0L
    private var startVol = 0
    private var startBright = 0.5f
    private var longSpeed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = intent.getStringArrayListExtra("paths") ?: listOfNotNull(intent.getStringExtra("path"))
        index = intent.getIntExtra("index", 0).coerceIn(0, (paths.size - 1).coerceAtLeast(0))
        if (paths.isEmpty()) { finish(); return }
        prefs = FilesPrefs(this)
        audio = getSystemService(AudioManager::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build()
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)

        buildUi()
        player.setVideoSurfaceView(stage.surface)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(vs: VideoSize) {
                stage.videoW = (vs.width * vs.pixelWidthHeightRatio).toInt(); stage.videoH = vs.height
                stage.requestLayout()
                if (firstSize && vs.width > 0) {
                    firstSize = false
                    requestedOrientation = if (vs.width > vs.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.setImageResource(if (isPlaying) R.drawable.ic_fx_pause else R.drawable.ic_fx_play)
                if (isPlaying) scheduleHide() else handler.removeCallbacks(hideControls)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    prefs.setMediaPos(paths[index], 0)
                    if (index < paths.size - 1) go(1) else setControls(true)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                UiKit.toast(this@VideoPlayerActivity, "Can't play this video here (${error.errorCodeName})")
                MaterialAlertDialogBuilder(this@VideoPlayerActivity).setTitle("Playback problem")
                    .setMessage("Try another app for this file?")
                    .setPositiveButton("Open with…") { _, _ -> FileOpener.external(this@VideoPlayerActivity, File(paths[index])); finish() }
                    .setNegativeButton("Close", null).show()
            }
            override fun onCues(cueGroup: CueGroup) {
                val t = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
                subText.text = t
                subText.visibility = if (t.isBlank() || !textOn) View.GONE else View.VISIBLE
            }
        })
        load(index)
        handler.post(ticker)
    }

    // ------------------------------------------------------------------ UI

    private fun white(res: Int, size: Int, onClick: () -> Unit): ImageView =
        Design.icon(this, res, size, Color.WHITE, onClick)

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        stage = VideoStage(this, SurfaceView(this))
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))

        subText = TextView(this)
        subText.setTextColor(Color.WHITE)
        subText.textSize = 17f
        subText.gravity = Gravity.CENTER
        subText.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        subText.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 4), UiKit.dp(this, 16), UiKit.dp(this, 4))
        subText.visibility = View.GONE
        val slp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        slp.bottomMargin = UiKit.dp(this, 96)
        root.addView(subText, slp)

        hud = TextView(this)
        hud.setTextColor(Color.WHITE)
        hud.textSize = 18f
        hud.gravity = Gravity.CENTER
        val hg = GradientDrawable(); hg.cornerRadius = UiKit.dp(this, 14).toFloat(); hg.setColor(0xAA000000.toInt())
        hud.background = hg
        hud.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 10), UiKit.dp(this, 18), UiKit.dp(this, 10))
        hud.visibility = View.GONE
        root.addView(hud, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).also { it.topMargin = UiKit.dp(this, 70) })

        controls = FrameLayout(this)
        // top bar
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
        top.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 22), UiKit.dp(this, 8), UiKit.dp(this, 18))
        top.addView(white(R.drawable.ic_fx_back, 44) { finish() })
        titleT = TextView(this)
        titleT.setTextColor(Color.WHITE); titleT.textSize = 16f; titleT.maxLines = 1
        titleT.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(titleT, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(white(R.drawable.ic_fx_pip, 44) { enterPip() })
        top.addView(white(R.drawable.ic_fx_more, 44) { moreSheet() })
        controls.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // centre controls
        val mid = LinearLayout(this)
        mid.gravity = Gravity.CENTER
        mid.addView(white(R.drawable.ic_fx_prev, 52) { go(-1) })
        mid.addView(white(R.drawable.ic_fx_replay, 56) { seekBy(-10_000) })
        playBtn = white(R.drawable.ic_fx_pause, 76) { togglePlay() }
        val pg = GradientDrawable(); pg.shape = GradientDrawable.OVAL; pg.setColor(0x66FFFFFF)
        playBtn.background = pg
        val plp = LinearLayout.LayoutParams(UiKit.dp(this, 76), UiKit.dp(this, 76))
        plp.setMargins(UiKit.dp(this, 14), 0, UiKit.dp(this, 14), 0)
        mid.addView(playBtn, plp)
        mid.addView(white(R.drawable.ic_fx_forward, 56) { seekBy(10_000) })
        mid.addView(white(R.drawable.ic_fx_next, 52) { go(1) })
        controls.addView(mid, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // bottom
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), 0x00000000))
        bottom.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 20), UiKit.dp(this, 16), UiKit.dp(this, 10))
        val sRow = LinearLayout(this)
        sRow.gravity = Gravity.CENTER_VERTICAL
        posT = TextView(this); posT.setTextColor(Color.WHITE); posT.textSize = 12f; posT.text = "0:00"
        durT = TextView(this); durT.setTextColor(Color.WHITE); durT.textSize = 12f; durT.text = "0:00"
        seek = SeekBar(this)
        val purple = UiKit.color(this, R.color.brand_purple)
        seek.progressTintList = ColorStateList.valueOf(purple)
        seek.thumbTintList = ColorStateList.valueOf(purple)
        seek.secondaryProgressTintList = ColorStateList.valueOf(0x66FFFFFF)
        seek.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) posT.text = fmt(p.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true; handler.removeCallbacks(hideControls) }
            override fun onStopTrackingTouch(s: SeekBar?) { dragging = false; player.seekTo(seek.progress.toLong()); scheduleHide() }
        })
        sRow.addView(posT)
        sRow.addView(seek, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sRow.addView(durT)
        bottom.addView(sRow)

        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        lockBtn = white(R.drawable.ic_fx_lock, 44) { setLocked(true) }
        speedChip = TextView(this)
        speedChip.setTextColor(Color.WHITE); speedChip.textSize = 14f; speedChip.text = "1×"
        speedChip.typeface = android.graphics.Typeface.DEFAULT_BOLD
        speedChip.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 8), UiKit.dp(this, 14), UiKit.dp(this, 8))
        speedChip.setOnClickListener { speedSheet() }
        row.addView(lockBtn, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.height = UiKit.dp(this, 44) })
        row.addView(speedChip)
        row.addView(white(R.drawable.ic_fx_aspect, 44) { cycleAspect() }, UiKit.lp(0, UiKit.dp(this, 44), 1f))
        row.addView(white(R.drawable.ic_fx_rotate, 44) { toggleOrientation() }, UiKit.lp(0, UiKit.dp(this, 44), 1f))
        row.addView(white(R.drawable.ic_fx_subtitles, 44) { trackSheet(C.TRACK_TYPE_TEXT, "Subtitles") }, UiKit.lp(0, UiKit.dp(this, 44), 1f))
        row.addView(white(R.drawable.ic_fx_audiotrack, 44) { trackSheet(C.TRACK_TYPE_AUDIO, "Audio track") }, UiKit.lp(0, UiKit.dp(this, 44), 1f))
        bottom.addView(row)
        controls.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(controls, FrameLayout.LayoutParams(-1, -1))

        unlockBtn = white(R.drawable.ic_fx_unlock, 56) { setLocked(false) }
        val ug = GradientDrawable(); ug.shape = GradientDrawable.OVAL; ug.setColor(0x88000000.toInt())
        unlockBtn.background = ug
        unlockBtn.visibility = View.GONE
        root.addView(unlockBtn, FrameLayout.LayoutParams(UiKit.dp(this, 56), UiKit.dp(this, 56), Gravity.CENTER_VERTICAL or Gravity.START).also { it.leftMargin = UiKit.dp(this, 24) })

        root.setOnTouchListener { _, ev -> onRootTouch(ev) }
        setContentView(root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val wic = WindowCompat.getInsetsController(window, root)
        wic.hide(WindowInsetsCompat.Type.systemBars())
        wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // ------------------------------------------------------------- gestures

    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) { unlockBtn.visibility = View.VISIBLE; handler.postDelayed({ unlockBtn.visibility = View.GONE }, 2200) }
                else setControls(!controlsOn)
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val w = root.width
                when {
                    e.x < w * 0.4f -> { seekBy(-10_000); hud("⏪  10 s") }
                    e.x > w * 0.6f -> { seekBy(10_000); hud("10 s  ⏩") }
                    else -> togglePlay()
                }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (locked) return
                longSpeed = true; player.setPlaybackSpeed(2f); hud("2× speed")
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || e1 == null || longSpeed) return false
                if (gmode == G.NONE) {
                    gmode = if (abs(e2.x - e1.x) > abs(e2.y - e1.y)) G.SEEK else if (e1.x < root.width / 2f) G.BRIGHT else G.VOL
                }
                when (gmode) {
                    G.SEEK -> {
                        val d = player.duration.coerceAtLeast(0L)
                        pendingSeek = (startPos + ((e2.x - e1.x) / root.width * 120_000f).toLong()).coerceIn(0L, d)
                        val diff = (pendingSeek - startPos) / 1000
                        hud("${fmt(pendingSeek)}   (${if (diff >= 0) "+" else ""}${diff}s)")
                    }
                    G.BRIGHT -> {
                        val v = (startBright + (e1.y - e2.y) / root.height * 1.3f).coerceIn(0.02f, 1f)
                        window.attributes = window.attributes.also { it.screenBrightness = v }
                        hud("Brightness  ${(v * 100).toInt()}%")
                    }
                    G.VOL -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val v = (startVol + (e1.y - e2.y) / root.height * max * 1.3f).toInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        hud("Volume  ${v * 100 / max}%")
                    }
                    else -> {}
                }
                return true
            }
        })
    }

    private fun onRootTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gmode = G.NONE
                startPos = player.currentPosition
                pendingSeek = startPos
                startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val b = window.attributes.screenBrightness
                startBright = if (b >= 0f) b else 0.5f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gmode == G.SEEK) player.seekTo(pendingSeek)
                if (longSpeed) { longSpeed = false; player.setPlaybackSpeed(speed) }
                gmode = G.NONE
            }
        }
        detector.onTouchEvent(ev)
        return true
    }

    // -------------------------------------------------------------- actions

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun hud(msg: String) {
        hud.text = msg; hud.visibility = View.VISIBLE
        handler.removeCallbacks(hideHud); handler.postDelayed(hideHud, 800)
    }

    private fun setControls(on: Boolean) {
        controlsOn = on
        controls.animate().alpha(if (on) 1f else 0f).setDuration(180).withEndAction { controls.visibility = if (on) View.VISIBLE else View.GONE }.start()
        if (on) { controls.visibility = View.VISIBLE; scheduleHide() }
    }

    private fun scheduleHide() { handler.removeCallbacks(hideControls); if (player.isPlaying) handler.postDelayed(hideControls, 3500) }

    private fun setLocked(on: Boolean) {
        locked = on
        if (on) { setControls(false); hud("Screen locked") } else { unlockBtn.visibility = View.GONE; setControls(true) }
    }

    private fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }

    private fun seekBy(delta: Long) {
        val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + delta).coerceIn(0L, d))
    }

    private fun buildItem(f: File): MediaItem {
        val subs = ArrayList<MediaItem.SubtitleConfiguration>()
        val base = f.nameWithoutExtension
        f.parentFile?.listFiles()?.filter {
            it.isFile && it.nameWithoutExtension.startsWith(base) && it.extension.lowercase() in setOf("srt", "vtt", "ass", "ssa")
        }?.forEach { s ->
            val mime = when (s.extension.lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                else -> MimeTypes.TEXT_SSA
            }
            subs.add(
                MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(s)).setMimeType(mime).setLanguage("und")
                    .setLabel(s.name).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            )
        }
        return MediaItem.Builder().setUri(Uri.fromFile(f)).setSubtitleConfigurations(subs).build()
    }

    private fun load(i: Int) {
        index = i
        val f = File(paths[i])
        titleT.text = f.name
        val resume = prefs.mediaPos(f.path).toLong()
        player.setMediaItem(buildItem(f), if (resume > 3000) resume else 0L)
        player.prepare()
        player.playWhenReady = true
    }

    private fun go(dir: Int) {
        if (paths.size < 2) { hud(if (dir > 0) "Last video" else "First video"); return }
        savePos()
        load((index + dir + paths.size) % paths.size)
    }

    private fun savePos() {
        val d = player.duration
        val p = player.currentPosition
        prefs.setMediaPos(paths[index], if (d > 0 && d - p < 5000) 0 else p.toInt())
    }

    private fun cycleAspect() {
        stage.mode = (stage.mode + 1) % 3
        stage.requestLayout()
        hud(listOf("Fit", "Stretch", "Zoom to fill")[stage.mode])
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun speedSheet() {
        ActionSheet.show(this, "Playback speed", speeds.map { sp ->
            sheetPick(sp == speed, if (sp == 1f) "Normal" else "${sp}×") {
                speed = sp; player.setPlaybackSpeed(sp); speedChip.text = "${sp}×".replace(".0×", "×")
            }
        })
    }

    private fun trackSheet(type: Int, title: String) {
        val acts = ArrayList<SheetAction>()
        if (type == C.TRACK_TYPE_TEXT) {
            acts.add(sheetPick(!textOn, "Off") {
                textOn = false; subText.visibility = View.GONE
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            })
        }
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (ti in 0 until g.length) {
                val f = g.getTrackFormat(ti)
                val name = listOfNotNull(f.label, f.language?.takeIf { it != "und" }, f.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { "${it}ch" })
                    .ifEmpty { listOf("Track ${acts.size + 1}") }.joinToString(" · ")
                acts.add(sheetPick(g.isTrackSelected(ti) && (type != C.TRACK_TYPE_TEXT || textOn), name) {
                    if (type == C.TRACK_TYPE_TEXT) textOn = true
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, listOf(ti))).build()
                })
            }
        }
        if (acts.isEmpty() || (type == C.TRACK_TYPE_TEXT && acts.size == 1)) { UiKit.toast(this, "No ${title.lowercase()} in this video"); return }
        ActionSheet.show(this, title, acts)
    }

    private fun moreSheet() {
        val f = File(paths[index])
        val boostLabel = if (boostLevel == 0) "Volume boost: off" else "Volume boost: +${boostLevel * 3} dB"
        ActionSheet.show(this, f.name, listOf(
            SheetAction("", "Video info", iconRes = R.drawable.ic_fx_info) { info(f) },
            SheetAction("", "Save screenshot", iconRes = R.drawable.ic_fx_image) { screenshot(f) },
            SheetAction("", "Trim / cut video", iconRes = R.drawable.ic_fx_cut) { editor(f, MediaEditor.Mode.TRIM) },
            SheetAction("", "Extract audio (M4A)", iconRes = R.drawable.ic_fx_audiotrack) { editor(f, MediaEditor.Mode.EXTRACT_AUDIO) },
            SheetAction("", "Save a muted copy", iconRes = R.drawable.ic_fx_volume_off) { editor(f, MediaEditor.Mode.MUTE) },
            SheetAction("", boostLabel, iconRes = R.drawable.ic_fx_volume) { cycleBoost() },
            SheetAction("", "Sleep timer", iconRes = R.drawable.ic_fx_timer) { sleepSheet() },
            SheetAction("", "Share", iconRes = R.drawable.ic_fx_share) { FileOpener.share(this, listOf(f)) },
            SheetAction("", "Open with another app", iconRes = R.drawable.ic_fx_file) { FileOpener.external(this, f); finish() }
        ))
    }

    private fun editor(f: File, mode: MediaEditor.Mode) {
        player.pause()
        MediaEditor.show(this, f, player.duration, player.currentPosition, mode)
    }

    private fun cycleBoost() {
        boostLevel = (boostLevel + 1) % 4
        try {
            val e = enhancer ?: LoudnessEnhancer(player.audioSessionId).also { enhancer = it }
            e.setTargetGain(boostLevel * 300)
            e.enabled = boostLevel > 0
            hud(if (boostLevel == 0) "Volume boost off" else "Volume boost +${boostLevel * 3} dB")
        } catch (e: Exception) { UiKit.toast(this, "Volume boost isn't available on this phone") }
    }

    private fun sleepSheet() {
        val opts = listOf(0, 15, 30, 45, 60, 90)
        ActionSheet.show(this, "Sleep timer", opts.map { m ->
            sheetPick(false, if (m == 0) "Off" else "Pause after $m minutes") {
                sleepRunnable?.let { handler.removeCallbacks(it) }
                sleepRunnable = null
                if (m > 0) {
                    val r = Runnable { player.pause(); hud("Sleep timer: paused") }
                    sleepRunnable = r
                    handler.postDelayed(r, m * 60_000L)
                    hud("Pausing in $m min")
                } else hud("Sleep timer off")
            }
        })
    }

    private fun screenshot(f: File) {
        val sv = stage.surface
        if (sv.width == 0 || sv.height == 0) return
        val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(sv, bmp, { res ->
                if (res == PixelCopy.SUCCESS) {
                    Thread {
                        val dir = File(FilePaths.internalRoot(), "Pictures/DataTracker")
                        dir.mkdirs()
                        val out = FileOps.uniqueFile(dir, "${f.nameWithoutExtension}_${fmt(player.currentPosition).replace(':', '-')}.png")
                        try { out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { }
                        runOnUiThread { UiKit.toast(this, "Saved: ${out.name}") }
                    }.start()
                } else UiKit.toast(this, "Couldn't capture this frame")
            }, handler)
        } catch (e: Exception) { UiKit.toast(this, "Screenshot isn't available") }
    }

    private fun info(f: File) {
        val v = player.videoFormat
        val a = player.audioFormat
        val msg = buildString {
            append("File: ${f.name}\nSize: ${FormatBytes.human(f.length())}\nDuration: ${fmt(player.duration)}\n")
            if (v != null) append("Video: ${v.width}×${v.height}  ${v.sampleMimeType}  ${if (v.frameRate > 0) "%.0f fps".format(v.frameRate) else ""}  ${if (v.bitrate > 0) "${v.bitrate / 1000} kbps" else ""}\n")
            if (a != null) append("Audio: ${a.sampleMimeType}  ${a.channelCount}ch  ${if (a.sampleRate > 0) "${a.sampleRate} Hz" else ""}\n")
            append("Path: ${f.absolutePath}")
        }
        MaterialAlertDialogBuilder(this).setTitle("Video info").setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun enterPip() {
        try {
            val vs = player.videoSize
            var r = if (vs.width > 0 && vs.height > 0) vs.width.toFloat() / vs.height else 16f / 9f
            r = r.coerceIn(0.45f, 2.3f)
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational((r * 1000).toInt(), 1000)).build())
        } catch (e: Exception) { UiKit.toast(this, "Picture-in-picture isn't available") }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying && !locked) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        controls.visibility = if (isInPictureInPictureMode) View.GONE else if (controlsOn) View.VISIBLE else View.GONE
        if (isInPictureInPictureMode) { hud.visibility = View.GONE; unlockBtn.visibility = View.GONE }
    }

    override fun onPause() {
        super.onPause()
        savePos()
        if (!isInPictureInPictureMode) player.pause()
    }

    override fun onStop() { super.onStop(); player.pause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { enhancer?.release() } catch (_: Exception) { }
        player.release()
        super.onDestroy()
    }
}
