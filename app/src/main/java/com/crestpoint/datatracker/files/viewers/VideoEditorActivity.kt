package com.crestpoint.datatracker.files.viewers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Thumbnail filmstrip with draggable trim handles and a playhead (the heart of the editor). */
class TimelineView(ctx: Context) : View(ctx) {
    var durationMs = 1L
    var startMs = 0L
    var endMs = 1L
    var playheadMs = 0L
        set(v) { field = v; invalidate() }
    var thumbs: List<Bitmap?> = emptyList()
    var onTrim: ((Long, Long, Boolean) -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val pad = 26 * d
    private val handleW = 14 * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = ctx.getColor(R.color.brand_purple)
    private var drag = 0

    private fun xFor(ms: Long): Float = pad + (ms.toFloat() / durationMs.coerceAtLeast(1)) * (width - 2 * pad)
    private fun msFor(x: Float): Long = (((x - pad) / (width - 2 * pad)) * durationMs).toLong().coerceIn(0L, durationMs)

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(getDefaultSize(0, w), (84 * d).toInt()) }

    override fun onDraw(c: Canvas) {
        val top = 12 * d; val bottom = height - 12 * d
        val l = pad; val r = width - pad
        paint.style = Paint.Style.FILL
        paint.color = 0xFF191A2D.toInt()
        c.drawRect(l, top, r, bottom, paint)
        if (thumbs.isNotEmpty()) {
            val cell = (r - l) / thumbs.size
            thumbs.forEachIndexed { i, b ->
                if (b != null) c.drawBitmap(b, null, RectF(l + i * cell, top, l + (i + 1) * cell, bottom), paint)
            }
        }
        val sx = xFor(startMs); val ex = xFor(endMs)
        paint.color = 0xAA000000.toInt()
        c.drawRect(l, top, sx, bottom, paint)
        c.drawRect(ex, top, r, bottom, paint)
        // frame between the handles
        paint.color = purple
        c.drawRect(sx, top - 3 * d, ex, top, paint)
        c.drawRect(sx, bottom, ex, bottom + 3 * d, paint)
        // handles
        c.drawRoundRect(RectF(sx - handleW, top - 3 * d, sx, bottom + 3 * d), 6 * d, 6 * d, paint)
        c.drawRoundRect(RectF(ex, top - 3 * d, ex + handleW, bottom + 3 * d), 6 * d, 6 * d, paint)
        paint.color = Color.WHITE
        c.drawRoundRect(RectF(sx - handleW / 2 - d, height / 2f - 9 * d, sx - handleW / 2 + d, height / 2f + 9 * d), d, d, paint)
        c.drawRoundRect(RectF(ex + handleW / 2 - d, height / 2f - 9 * d, ex + handleW / 2 + d, height / 2f + 9 * d), d, d, paint)
        // playhead
        val px = xFor(playheadMs.coerceIn(startMs, endMs))
        c.drawRect(px - 1.5f * d, 2 * d, px + 1.5f * d, height - 2 * d, paint)
        c.drawCircle(px, 4 * d, 4.5f * d, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val sx = xFor(startMs); val ex = xFor(endMs)
                drag = when {
                    abs(e.x - (sx - handleW / 2)) < 26 * d -> 1
                    abs(e.x - (ex + handleW / 2)) < 26 * d -> 2
                    else -> 3
                }
                if (drag == 3) seekTo(e.x)
            }
            MotionEvent.ACTION_MOVE -> when (drag) {
                1 -> { startMs = min(msFor(e.x), endMs - 500); onTrim?.invoke(startMs, endMs, false); invalidate() }
                2 -> { endMs = max(msFor(e.x), startMs + 500); onTrim?.invoke(startMs, endMs, false); invalidate() }
                3 -> seekTo(e.x)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drag == 1 || drag == 2) onTrim?.invoke(startMs, endMs, true)
                drag = 0
            }
        }
        return true
    }

    private fun seekTo(x: Float) {
        val t = msFor(x).coerceIn(startMs, endMs)
        playheadMs = t
        onSeek?.invoke(t)
    }
}

/** Drag-to-crop box drawn over the preview. rect = left, top, right, bottom as fractions of the video frame. */
class CropOverlayView(ctx: Context) : View(ctx) {
    var videoDims: () -> Pair<Int, Int> = { 0 to 0 }
    var rect = floatArrayOf(0.08f, 0.08f, 0.92f, 0.92f)
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drag = -1
    private var lastX = 0f
    private var lastY = 0f

    private fun frame(): RectF {
        val (vw, vh) = videoDims()
        if (vw <= 0 || vh <= 0 || width == 0 || height == 0) return RectF(0f, 0f, width.toFloat(), height.toFloat())
        val va = vw.toFloat() / vh; val ca = width.toFloat() / height
        val w: Float; val h: Float
        if (va > ca) { w = width.toFloat(); h = w / va } else { h = height.toFloat(); w = h * va }
        val x = (width - w) / 2; val y = (height - h) / 2
        return RectF(x, y, x + w, y + h)
    }

    private fun box(v: RectF) = RectF(v.left + rect[0] * v.width(), v.top + rect[1] * v.height(), v.left + rect[2] * v.width(), v.top + rect[3] * v.height())

    override fun onDraw(c: Canvas) {
        val v = frame(); val b = box(v)
        paint.style = Paint.Style.FILL
        paint.color = 0x99000000.toInt()
        c.drawRect(v.left, v.top, v.right, b.top, paint)
        c.drawRect(v.left, b.bottom, v.right, v.bottom, paint)
        c.drawRect(v.left, b.top, b.left, b.bottom, paint)
        c.drawRect(b.right, b.top, v.right, b.bottom, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2 * d
        c.drawRect(b, paint)
        paint.strokeWidth = d
        paint.alpha = 110
        for (i in 1..2) {
            c.drawLine(b.left + b.width() * i / 3, b.top, b.left + b.width() * i / 3, b.bottom, paint)
            c.drawLine(b.left, b.top + b.height() * i / 3, b.right, b.top + b.height() * i / 3, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        val r = 7 * d
        for ((x, y) in listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom)) c.drawCircle(x, y, r, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val v = frame(); val b = box(v); val t = 30 * d
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x; lastY = e.y
                fun near(x: Float, y: Float) = abs(e.x - x) < t && abs(e.y - y) < t
                drag = when {
                    near(b.left, b.top) -> 1
                    near(b.right, b.top) -> 2
                    near(b.left, b.bottom) -> 3
                    near(b.right, b.bottom) -> 4
                    b.contains(e.x, e.y) -> 0
                    else -> -1
                }
            }
            MotionEvent.ACTION_MOVE -> if (drag >= 0 && v.width() > 0 && v.height() > 0) {
                val dx = (e.x - lastX) / v.width(); val dy = (e.y - lastY) / v.height()
                lastX = e.x; lastY = e.y
                val m = 0.1f
                var l = rect[0]; var tp = rect[1]; var rt = rect[2]; var bt = rect[3]
                when (drag) {
                    0 -> {
                        val w = rt - l; val h = bt - tp
                        l = (l + dx).coerceIn(0f, 1f - w); tp = (tp + dy).coerceIn(0f, 1f - h); rt = l + w; bt = tp + h
                    }
                    1 -> { l = (l + dx).coerceIn(0f, rt - m); tp = (tp + dy).coerceIn(0f, bt - m) }
                    2 -> { rt = (rt + dx).coerceIn(l + m, 1f); tp = (tp + dy).coerceIn(0f, bt - m) }
                    3 -> { l = (l + dx).coerceIn(0f, rt - m); bt = (bt + dy).coerceIn(tp + m, 1f) }
                    4 -> { rt = (rt + dx).coerceIn(l + m, 1f); bt = (bt + dy).coerceIn(tp + m, 1f) }
                }
                rect = floatArrayOf(l, tp, rt, bt)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = -1
        }
        return true
    }
}

/**
 * Offline video editor: filmstrip timeline, trim / split / merge clips, rotate & flip, crop, filters,
 * colour adjust, speed, volume (to 200%), background music, text, quality and frame capture.
 * Live preview uses ExoPlayer effects; export uses Media3 Transformer — all on the phone.
 */
@OptIn(UnstableApi::class)
class VideoEditorActivity : AppCompatActivity() {
    companion object {
        fun start(ctx: Context, f: File, posMs: Long = 0L) {
            ctx.startActivity(Intent(ctx, VideoEditorActivity::class.java).putExtra("path", f.path).putExtra("pos", posMs))
        }
    }

    class Clip(val file: File, val durationMs: Long, val w: Int, val h: Int) {
        var startMs = 0L
        var endMs = durationMs
        var thumbs: List<Bitmap?> = emptyList()
        fun copy(): Clip { val c = Clip(file, durationMs, w, h); c.startMs = startMs; c.endMs = endMs; c.thumbs = thumbs; return c }
    }

    private val clips = ArrayList<Clip>()
    private var sel = 0

    // edit state
    private var rotation = 0
    private var flipH = false
    private var flipV = false
    private var cropMode = 0
    private var filter = 0
    private var brightness = 0f
    private var contrast = 0f
    private var saturation = 0f
    private var speed = 1f
    private var volume = 1f
    private var audioMode = 0 // 0 keep, 1 mute, 2 audio only
    private var resH = 0
    private var musicUri: Uri? = null
    private var musicName = ""
    private var text = ""
    private var textPos = 2
    private var textPct = 7
    private var textColor = Color.WHITE
    private var cropEditing = false
    private var cropRect = floatArrayOf(0.08f, 0.08f, 0.92f, 0.92f)
    private lateinit var cropOverlay: CropOverlayView

    private lateinit var player: ExoPlayer
    private lateinit var stage: VideoStage
    private lateinit var timeline: TimelineView
    private lateinit var playBtn: ImageView
    private lateinit var timeT: TextView
    private lateinit var totalT: TextView
    private lateinit var clipsRow: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var toolStrip: LinearLayout
    private var activeTool = 0
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val c = clips.getOrNull(sel)
            if (c != null) {
                val pos = player.currentPosition
                timeline.playheadMs = c.startMs + pos
                timeT.text = fmt(pos)
                if (player.isPlaying && pos >= c.endMs - c.startMs - 40) { player.pause(); player.seekTo(0) }
            }
            handler.postDelayed(this, 80)
        }
    }

    private val musicPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            musicUri = uri
            musicName = try {
                contentResolver.query(uri, null, null, null, null)?.use { q ->
                    val i = q.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && q.moveToFirst()) q.getString(i) else null
                }
            } catch (_: Exception) { null } ?: "music"
            if (activeTool == 9) showTool(9)
            UiKit.toast(this, "Music added. It is mixed in when you export.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = File(intent.getStringExtra("path") ?: run { finish(); return })
        val first = probe(f)
        if (first == null) { UiKit.toast(this, "Can't read this video"); finish(); return }
        clips.add(first)
        player = ExoPlayer.Builder(this).build()
        buildUi()
        player.setVideoSurfaceView(stage.surface)
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(vs: VideoSize) {
                stage.videoW = (vs.width * vs.pixelWidthHeightRatio).toInt(); stage.videoH = vs.height; stage.requestLayout()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.setImageResource(if (isPlaying) R.drawable.ic_fx_pause else R.drawable.ic_fx_play)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { player.pause(); player.seekTo(0) }
            }
        })
        loadClip()
        loadThumbs(first)
        showTool(0)
        handler.post(ticker)
    }

    // ----------------------------------------------------------- helpers

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0); val cs = ((ms % 1000) / 100)
        return "%d:%02d.%d".format(s / 60, s % 60, cs)
    }

    private fun probe(f: File): Clip? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            if (dur == null || dur < 500 || w <= 0 || h <= 0) null else Clip(f, dur, w, h)
        } catch (_: Exception) { null } finally { try { r.release() } catch (_: Exception) { } }
    }

    private fun loadThumbs(c: Clip) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val out = ArrayList<Bitmap?>()
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(c.file.absolutePath)
                    val n = 10
                    val th = (200 * c.h.toFloat() / c.w).toInt().coerceIn(60, 360)
                    for (i in 0 until n) {
                        val t = (c.durationMs * (i + 0.5) / n * 1000).toLong()
                        out.add(r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 200, th))
                    }
                } catch (_: Exception) { } finally { try { r.release() } catch (_: Exception) { } }
                out
            }
            c.thumbs = list
            if (clips.getOrNull(sel) === c) { timeline.thumbs = list; timeline.invalidate() }
        }
    }

    private val current: Clip get() = clips[sel.coerceIn(0, clips.size - 1)]

    private fun totalMs(): Long = clips.sumOf { ((it.endMs - it.startMs) / speed).toLong() }

    private fun refreshTotals() { totalT.text = "Total ${fmt(totalMs())}" }

    private fun loadClip() {
        val c = current
        val cfg = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(c.file)).setClippingConfiguration(cfg).build()
        applyPreview()
        player.setMediaItem(item)
        player.setPlaybackSpeed(speed)
        player.volume = min(volume, 1f)
        player.prepare()
        player.pause()
        timeline.durationMs = c.durationMs; timeline.startMs = c.startMs; timeline.endMs = c.endMs; timeline.playheadMs = c.startMs
        timeline.thumbs = c.thumbs
        timeline.invalidate()
        refreshClipChips(); refreshTotals()
    }

    private fun applyPreview() {
        try { player.setVideoEffects(buildEffects(false, current)) } catch (e: Throwable) {
            UiKit.toast(this, "Live preview isn't available for this combination (export will still work)")
        }
    }

    // ------------------------------------------------------------ effects

    private fun cropEffect(c: Clip): Effect? {
        if (cropMode == 0) return null
        if (cropMode == 7) {
            val r = cropRect
            return Crop(r[0] * 2f - 1f, r[2] * 2f - 1f, 1f - r[3] * 2f, 1f - r[1] * 2f)
        }
        val target = listOf(0f, 1f, 16f / 9f, 9f / 16f, 4f / 3f, 3f / 4f, 21f / 9f)[cropMode]
        val rotated = rotation == 90 || rotation == 270
        val sw = if (rotated) c.h else c.w
        val sh = if (rotated) c.w else c.h
        val src = sw.toFloat() / sh
        return if (src > target) { val f = (sh * target) / sw; Crop(-f, f, -1f, 1f) } else { val f = (sw / target) / sh; Crop(-1f, 1f, -f, f) }
    }

    private fun buildEffects(export: Boolean, c: Clip): List<Effect> {
        val l = ArrayList<Effect>()
        if (export && speed != 1f) l.add(SpeedChangeEffect(speed))
        if (rotation != 0 || flipH || flipV) {
            l.add(ScaleAndRotateTransformation.Builder()
                .setScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
                .setRotationDegrees(rotation.toFloat()).build())
        }
        if (export || !cropEditing) cropEffect(c)?.let { l.add(it) }
        when (filter) {
            1 -> l.add(RgbFilter.createGrayscaleFilter())
            2 -> l.add(RgbFilter.createInvertedFilter())
            3 -> l.add(RgbAdjustment.Builder().setRedScale(1.14f).setBlueScale(0.86f).build())
            4 -> l.add(RgbAdjustment.Builder().setRedScale(0.88f).setBlueScale(1.14f).build())
            5 -> { l.add(HslAdjustment.Builder().adjustSaturation(40f).build()); l.add(Contrast(0.12f)) }
            6 -> { l.add(Contrast(-0.22f)); l.add(Brightness(0.08f)) }
            7 -> { l.add(RgbFilter.createGrayscaleFilter()); l.add(Contrast(0.32f)) }
            8 -> { l.add(RgbAdjustment.Builder().setRedScale(1.08f).setGreenScale(1.0f).setBlueScale(0.8f).build()); l.add(Contrast(0.1f)) }
        }
        if (brightness != 0f) l.add(Brightness(brightness))
        if (contrast != 0f) l.add(Contrast(contrast))
        if (saturation != 0f) l.add(HslAdjustment.Builder().adjustSaturation(saturation).build())
        if (text.isNotBlank()) {
            val span = SpannableString(text)
            val px = (c.h * textPct / 100f).toInt().coerceAtLeast(12)
            span.setSpan(AbsoluteSizeSpan(px), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(textColor), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val y = listOf(0.78f, 0f, -0.78f)[textPos]
            val settings = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).setOverlayFrameAnchor(0f, 0f).build()
            l.add(OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(span, settings))))
        }
        if (export) {
            if (clips.size > 1) {
                val ref = clips[0]
                val h = if (resH > 0) resH else ref.h
                val w = (h * (ref.w.toFloat() / ref.h)).toInt() / 2 * 2
                l.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
            } else if (resH > 0 && c.h > resH) {
                l.add(Presentation.createForHeight(resH))
            }
        }
        return l
    }

    // ------------------------------------------------------------------ UI

    private fun ic(res: Int, size: Int = 44, tint: Int = Color.WHITE, run: () -> Unit): ImageView = Design.icon(this, res, size, tint, run)

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0F1020.toInt())
        val dm = resources.displayMetrics

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 30), UiKit.dp(this, 12), UiKit.dp(this, 4))
        top.addView(ic(R.drawable.ic_fx_close) { confirmExit() })
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(UiKit.text(this, "Edit video", 17f, bold = true))
        totalT = UiKit.text(this, "", 11f, R.color.on_surface_muted)
        titles.addView(totalT)
        top.addView(titles, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Design.chip(this, "Export", true) { export() })
        root.addView(top)

        stage = VideoStage(this, SurfaceView(this))
        stage.setBackgroundColor(Color.BLACK)
        val host = FrameLayout(this)
        host.addView(stage, FrameLayout.LayoutParams(-1, -1))
        cropOverlay = CropOverlayView(this)
        cropOverlay.visibility = View.GONE
        cropOverlay.videoDims = { stage.videoW to stage.videoH }
        host.addView(cropOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(host, LinearLayout.LayoutParams(-1, (dm.heightPixels * 0.33f).toInt()))

        val tr = LinearLayout(this)
        tr.gravity = Gravity.CENTER_VERTICAL
        tr.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 2), UiKit.dp(this, 10), 0)
        playBtn = ic(R.drawable.ic_fx_play, 48) { if (player.isPlaying) player.pause() else { if (player.currentPosition >= player.duration - 60 && player.duration > 0) player.seekTo(0); player.play() } }
        timeT = UiKit.text(this, "0:00.0", 12f, R.color.on_surface_muted)
        tr.addView(playBtn)
        tr.addView(timeT)
        tr.addView(View(this), UiKit.lp(0, 1, 1f))
        tr.addView(ic(R.drawable.ic_fx_rew, 40) { player.seekTo((player.currentPosition - 1000).coerceAtLeast(0)) })
        tr.addView(ic(R.drawable.ic_fx_ffwd, 40) { player.seekTo(player.currentPosition + 1000) })
        root.addView(tr)

        timeline = TimelineView(this)
        timeline.onSeek = { src -> player.seekTo((src - current.startMs).coerceAtLeast(0)) }
        timeline.onTrim = { s, e, final ->
            current.startMs = s; current.endMs = e
            refreshTotals()
            if (final) { loadClip(); showTool(activeTool) }
        }
        root.addView(timeline, LinearLayout.LayoutParams(-1, -2))

        val cs = HorizontalScrollView(this)
        cs.isHorizontalScrollBarEnabled = false
        clipsRow = LinearLayout(this)
        clipsRow.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 12), 0)
        cs.addView(clipsRow)
        root.addView(cs)

        val panelScroll = android.widget.ScrollView(this)
        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 6), UiKit.dp(this, 14), UiKit.dp(this, 6))
        panelScroll.addView(panel)
        root.addView(panelScroll, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.setBackgroundColor(0xFF191A2D.toInt())
        toolStrip = LinearLayout(this)
        toolStrip.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 6), UiKit.dp(this, 6), UiKit.dp(this, 10))
        hs.addView(toolStrip)
        root.addView(hs)
        buildToolStrip()
        setContentView(root)
    }

    private val tools = listOf(
        R.drawable.ic_fx_trim to "Trim", R.drawable.ic_fx_cut to "Split", R.drawable.ic_fx_clips to "Clips",
        R.drawable.ic_fx_rotate to "Rotate", R.drawable.ic_fx_crop to "Crop", R.drawable.ic_fx_palette to "Filters",
        R.drawable.ic_fx_tune to "Adjust", R.drawable.ic_fx_speed to "Speed", R.drawable.ic_fx_volume to "Volume",
        R.drawable.ic_fx_audiotrack to "Music", R.drawable.ic_fx_text to "Text", R.drawable.ic_fx_hd to "Quality",
        R.drawable.ic_fx_image to "Frame", R.drawable.ic_fx_refresh to "Reset"
    )

    private fun buildToolStrip() {
        toolStrip.removeAllViews()
        tools.forEachIndexed { i, (icon, label) ->
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 4), UiKit.dp(this, 12), UiKit.dp(this, 4))
            val on = i == activeTool
            val v = Design.icon(this, icon, 34, if (on) UiKit.color(this, R.color.brand_cyan) else Color.WHITE)
            v.background = null
            col.addView(v)
            col.addView(UiKit.text(this, label, 10f, if (on) R.color.brand_cyan else R.color.on_surface_muted))
            col.setOnClickListener { showTool(i) }
            Design.ripple(col)
            toolStrip.addView(col)
        }
    }

    private fun refreshClipChips() {
        clipsRow.removeAllViews()
        if (clips.size < 2) return
        clips.forEachIndexed { i, c ->
            clipsRow.addView(Design.chip(this, "${i + 1} · ${fmt(c.endMs - c.startMs).substringBefore('.')}", i == sel) {
                if (i != sel) { sel = i; loadClip(); showTool(activeTool) }
            })
        }
    }

    private fun showTool(id: Int) {
        if (id != 4 && cropEditing) cancelFreeCrop(false)
        activeTool = id
        buildToolStrip()
        panel.removeAllViews()
        when (id) {
            0 -> toolTrim()
            1 -> toolSplit()
            2 -> toolClips()
            3 -> toolRotate()
            4 -> toolCrop()
            5 -> toolFilters()
            6 -> toolAdjust()
            7 -> toolSpeed()
            8 -> toolVolume()
            9 -> toolMusic()
            10 -> toolText()
            11 -> toolQuality()
            12 -> toolFrame()
            13 -> toolReset()
        }
    }

    private fun opt(label: String, on: Boolean = false, run: () -> Unit): Triple<String, Boolean, () -> Unit> = Triple(label, on, run)

    private fun hint(s: String) { panel.addView(UiKit.text(this, s, 12f, R.color.on_surface_muted).also { it.setPadding(0, 0, 0, UiKit.dp(this, 8)) }) }

    private fun chipRow(items: List<Triple<String, Boolean, () -> Unit>>) {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        items.forEach { (label, on, run) -> row.addView(Design.chip(this, label, on) { run() }.also { (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = UiKit.dp(this, 6) }) }
        hs.addView(row)
        panel.addView(hs)
    }

    private fun toolTrim() {
        hint("Drag the purple handles on the timeline to keep only the part you want. Tap the timeline to move the playhead.")
        chipRow(listOf(
            opt("Start = playhead", false) { current.startMs = (current.startMs + player.currentPosition).coerceAtMost(current.endMs - 500); loadClip(); showTool(0) },
            opt("End = playhead", false) { current.endMs = (current.startMs + player.currentPosition).coerceAtLeast(current.startMs + 500); loadClip(); showTool(0) },
            opt("Reset trim", false) { current.startMs = 0; current.endMs = current.durationMs; loadClip(); showTool(0) }
        ))
        panel.addView(UiKit.text(this, "Selected part: ${fmt(current.endMs - current.startMs)}", 13f, R.color.brand_cyan))
    }

    private fun toolSplit() {
        hint("Cut the clip into two at the playhead. Then trim or delete either part to remove the middle.")
        chipRow(listOf(
            opt("Split at playhead", true) {
                val at = current.startMs + player.currentPosition
                if (at <= current.startMs + 400 || at >= current.endMs - 400) UiKit.toast(this, "Move the playhead into the middle of the clip first")
                else {
                    val right = current.copy()
                    right.startMs = at
                    current.endMs = at
                    clips.add(sel + 1, right)
                    loadClip(); showTool(1)
                    UiKit.toast(this, "Split. Part ${sel + 1} and ${sel + 2} are now separate.")
                }
            },
            opt("Delete this part", false) {
                if (clips.size < 2) UiKit.toast(this, "A video needs at least one part")
                else { clips.removeAt(sel); sel = sel.coerceAtMost(clips.size - 1); loadClip(); showTool(1) }
            }
        ))
    }

    private fun toolClips() {
        hint("Join more videos from this folder, reorder parts, or remove one. All parts are joined in this order.")
        chipRow(listOf(
            opt("＋ Add videos", true) { addVideos() },
            opt("◀ Move left", false) { if (sel > 0) { clips.add(sel - 1, clips.removeAt(sel)); sel--; loadClip(); showTool(2) } },
            opt("Move right ▶", false) { if (sel < clips.size - 1) { clips.add(sel + 1, clips.removeAt(sel)); sel++; loadClip(); showTool(2) } },
            opt("Duplicate", false) { clips.add(sel + 1, current.copy()); sel++; loadClip(); showTool(2) },
            opt("Delete", false) { if (clips.size > 1) { clips.removeAt(sel); sel = sel.coerceAtMost(clips.size - 1); loadClip(); showTool(2) } else UiKit.toast(this, "Need at least one part") }
        ))
        panel.addView(UiKit.text(this, "${clips.size} part(s) · total ${fmt(totalMs())}", 13f, R.color.brand_cyan))
    }

    private fun addVideos() {
        val dir = clips[0].file.parentFile ?: return
        val used = clips.map { it.file.path }.toSet()
        val vids = (dir.listFiles() ?: emptyArray()).filter { it.isFile && FileKinds.of(it) == FileKind.VIDEO && it.path !in used }.sortedBy { it.name.lowercase() }
        if (vids.isEmpty()) { UiKit.toast(this, "No other videos in this folder"); return }
        val checked = BooleanArray(vids.size)
        MaterialAlertDialogBuilder(this).setTitle("Add videos from ${dir.name}")
            .setMultiChoiceItems(vids.map { it.name }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Add") { _, _ ->
                var added = 0
                vids.forEachIndexed { i, f ->
                    if (checked[i]) probe(f)?.let { c -> clips.add(c); loadThumbs(c); added++ }
                }
                if (added > 0) { UiKit.toast(this, "Added $added video(s). Videos are scaled to the first one's size."); loadClip(); showTool(2) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun toolRotate() {
        hint("Turn or mirror the video. The preview updates instantly.")
        chipRow(listOf(
            opt("↺ Left 90°", false) { rotation = (rotation + 270) % 360; cropMode = 0; applyPreview(); updateAspect() },
            opt("↻ Right 90°", false) { rotation = (rotation + 90) % 360; cropMode = 0; applyPreview(); updateAspect() },
            opt("Flip ↔", flipH) { flipH = !flipH; cropMode = 0; applyPreview(); showTool(3) },
            opt("Flip ↕", flipV) { flipV = !flipV; cropMode = 0; applyPreview(); showTool(3) },
            opt("Reset", false) { rotation = 0; flipH = false; flipV = false; applyPreview(); showTool(3) }
        ))
        panel.addView(UiKit.text(this, "Rotation: $rotation°", 13f, R.color.brand_cyan))
    }

    private fun updateAspect() { showTool(3) }

    private fun toolCrop() {
        hint("Pick a shape, or choose Free crop and drag the box (and its corners) over the video.")
        val names = listOf("Original", "1:1 Square", "16:9 Wide", "9:16 Story", "4:3", "3:4", "21:9 Cinema")
        val items = ArrayList(names.mapIndexed { i, n ->
            opt(n, cropMode == i && !cropEditing) { cancelFreeCrop(false); cropMode = i; applyPreview(); showTool(4) }
        })
        items.add(1, opt("Free crop", cropMode == 7 || cropEditing) { startFreeCrop() })
        chipRow(items)
        if (cropEditing) {
            chipRow(listOf(opt("✓ Apply crop", true) { applyFreeCrop() }, opt("Cancel", false) { cancelFreeCrop(true) }))
        }
    }

    private fun startFreeCrop() {
        cropEditing = true
        if (cropMode == 7) cropOverlay.rect = cropRect.copyOf() else cropOverlay.rect = floatArrayOf(0.08f, 0.08f, 0.92f, 0.92f)
        cropOverlay.visibility = View.VISIBLE
        cropOverlay.invalidate()
        applyPreview()
        showTool(4)
    }

    private fun applyFreeCrop() {
        cropRect = cropOverlay.rect.copyOf()
        cropMode = 7
        cropEditing = false
        cropOverlay.visibility = View.GONE
        applyPreview()
        showTool(4)
        UiKit.toast(this, "Crop applied")
    }

    private fun cancelFreeCrop(redraw: Boolean) {
        if (!cropEditing) return
        cropEditing = false
        cropOverlay.visibility = View.GONE
        applyPreview()
        if (redraw) showTool(4)
    }

    private fun toolFilters() {
        hint("One-tap looks. Fine-tune them in Adjust.")
        val names = listOf("None", "Black & white", "Invert", "Warm", "Cool", "Vivid", "Faded", "Noir", "Golden")
        chipRow(names.mapIndexed { i, n -> opt(n, filter == i) { filter = i; applyPreview(); showTool(5) } })
    }

    private fun slider(title: String, from: Float, to: Float, value: Float, fmtV: (Float) -> String, onChange: (Float) -> Unit) {
        val lab = UiKit.text(this, "$title: ${fmtV(value)}", 13f)
        val s = Slider(this)
        s.valueFrom = from; s.valueTo = to; s.value = value.coerceIn(from, to)
        s.addOnChangeListener { _, v, fromUser -> if (fromUser) { lab.text = "$title: ${fmtV(v)}"; onChange(v) } }
        s.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { applyPreview() }
        })
        panel.addView(lab); panel.addView(s, LinearLayout.LayoutParams(-1, -2))
    }

    private fun toolAdjust() {
        hint("Fine-tune the picture.")
        slider("Brightness", -1f, 1f, brightness, { "%+d".format((it * 100).toInt()) }) { brightness = it }
        slider("Contrast", -1f, 1f, contrast, { "%+d".format((it * 100).toInt()) }) { contrast = it }
        slider("Saturation", -100f, 100f, saturation, { "%+d".format(it.toInt()) }) { saturation = it }
    }

    private fun toolSpeed() {
        hint("Slow motion or fast forward. Sound keeps its normal pitch.")
        chipRow(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f).map { s ->
            opt(if (s == 1f) "Normal" else "${s}×", speed == s) { speed = s; player.setPlaybackSpeed(s); refreshTotals(); showTool(7) }
        })
        panel.addView(UiKit.text(this, "Final length: ${fmt(totalMs())}", 13f, R.color.brand_cyan))
    }

    private fun toolVolume() {
        hint("Make the video louder (up to 200%), quieter, or remove the sound. The preview plays up to 100%.")
        slider("Volume", 0f, 2f, volume, { "${(it * 100).toInt()}%" }) { volume = it; player.volume = min(it, 1f) }
        chipRow(listOf(
            opt("Keep sound", audioMode == 0) { audioMode = 0; showTool(8) },
            opt("Mute", audioMode == 1) { audioMode = 1; showTool(8) },
            opt("Audio only (M4A)", audioMode == 2) { audioMode = 2; showTool(8) },
            opt("100%", false) { volume = 1f; player.volume = 1f; showTool(8) }
        ))
    }

    private fun toolMusic() {
        hint("Add background music from your phone. It plays under your video and repeats until the video ends.")
        chipRow(listOf(
            opt("＋ Choose music", true) { musicPicker.launch(arrayOf("audio/*")) },
            opt("Remove music", false) { musicUri = null; musicName = ""; showTool(9) }
        ))
        panel.addView(UiKit.text(this, if (musicUri == null) "No music added" else "🎵 $musicName", 13f, R.color.brand_cyan))
        panel.addView(UiKit.text(this, "Tip: choose Volume → Mute to replace the original sound with the music.", 11f, R.color.on_surface_muted)
            .also { it.setPadding(0, UiKit.dp(this, 6), 0, 0) })
    }

    private fun toolText() {
        hint("Add a title or caption on the video.")
        val et = EditText(this)
        et.hint = "Type your text"
        et.setText(text)
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(0x88FFFFFF.toInt())
        et.setSingleLine(true)
        panel.addView(et)
        chipRow(listOf(
            opt("Top", textPos == 0) { textPos = 0; applyText(et); showTool(10) },
            opt("Middle", textPos == 1) { textPos = 1; applyText(et); showTool(10) },
            opt("Bottom", textPos == 2) { textPos = 2; applyText(et); showTool(10) }
        ))
        chipRow(listOf(4, 7, 11, 15).zip(listOf("Small", "Medium", "Large", "Huge")).map { (v, n) -> opt(n, textPct == v) { textPct = v; applyText(et); showTool(10) } })
        chipRow(listOf("White" to Color.WHITE, "Yellow" to 0xFFFFE066.toInt(), "Red" to 0xFFFF5A5F.toInt(), "Cyan" to 0xFF26C6DA.toInt(), "Black" to Color.BLACK).map { (n, c) ->
            opt(n, textColor == c) { textColor = c; applyText(et); showTool(10) }
        })
        chipRow(listOf(
            opt("Apply text", true) { applyText(et); UiKit.toast(this, "Text applied") },
            opt("Remove text", false) { text = ""; applyPreview(); showTool(10) }
        ))
    }

    private fun applyText(et: EditText) { text = et.text.toString(); applyPreview() }

    private fun toolQuality() {
        hint("Smaller size for sharing (WhatsApp) or keep the original quality.")
        val h = current.h
        chipRow(listOf(0 to "Original (${h}p)", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p").map { (v, n) ->
            opt(n, resH == v) { resH = v; showTool(11) }
        })
    }

    private fun toolFrame() {
        hint("Save the current frame as a picture (Pictures/DataTracker).")
        chipRow(listOf(opt("Save this frame", true) { saveFrame() }))
    }

    private fun saveFrame() {
        val sv = stage.surface
        if (sv.width == 0) return
        val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(sv, bmp, { res ->
                if (res == PixelCopy.SUCCESS) Thread {
                    val dir = File(FilePaths.internalRoot(), "Pictures/DataTracker"); dir.mkdirs()
                    val out = FileOps.uniqueFile(dir, "${current.file.nameWithoutExtension}_frame.png")
                    try { out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) { }
                    runOnUiThread { UiKit.toast(this, "Saved ${out.name}") }
                }.start() else UiKit.toast(this, "Couldn't capture this frame")
            }, handler)
        } catch (_: Exception) { UiKit.toast(this, "Frame capture isn't available") }
    }

    private fun toolReset() {
        hint("Undo every edit and start again from the original video.")
        chipRow(listOf(opt("Reset everything", true) {
            UiKit.confirm(this, "Reset all edits?", "Trims, effects, text and music will be cleared.", "Reset") { resetAll() }
        }))
    }

    private fun resetAll() {
        val f = clips[0].file
        val c = probe(f) ?: return
        clips.clear(); clips.add(c); sel = 0
        rotation = 0; flipH = false; flipV = false; cropMode = 0; filter = 0
        cropEditing = false; cropOverlay.visibility = View.GONE
        brightness = 0f; contrast = 0f; saturation = 0f; speed = 1f; volume = 1f; audioMode = 0; resH = 0
        musicUri = null; musicName = ""; text = ""
        loadClip(); loadThumbs(c); showTool(0)
    }

    // -------------------------------------------------------------- export

    private fun export() {
        player.pause()
        val audioOnly = audioMode == 2
        val dir = File(FilePaths.internalRoot(), "Download/DataTracker/Edits")
        dir.mkdirs()
        val out = FileOps.uniqueFile(dir, clips[0].file.nameWithoutExtension + "_edit." + (if (audioOnly) "m4a" else "mp4"))
        val items = clips.map { c ->
            val cfg = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
            val mi = MediaItem.Builder().setUri(Uri.fromFile(c.file)).setClippingConfiguration(cfg).build()
            val audioProcs = ArrayList<AudioProcessor>()
            if (speed != 1f) audioProcs.add(SonicAudioProcessor().also { it.setSpeed(speed); it.setPitch(1f) })
            if (abs(volume - 1f) > 0.01f) audioProcs.add(ChannelMixingAudioProcessor().also {
                it.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
                it.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
            })
            EditedMediaItem.Builder(mi)
                .setRemoveAudio(audioMode == 1)
                .setRemoveVideo(audioOnly)
                .setEffects(Effects(audioProcs, if (audioOnly) emptyList() else buildEffects(true, c)))
                .build()
        }
        val seqs = ArrayList<EditedMediaItemSequence>()
        seqs.add(EditedMediaItemSequence(items))
        musicUri?.let { uri ->
            val cfg = MediaItem.ClippingConfiguration.Builder().setEndPositionMs(totalMs().coerceAtLeast(1000)).build()
            val mi = MediaItem.Builder().setUri(uri).setClippingConfiguration(cfg).build()
            seqs.add(EditedMediaItemSequence(listOf(EditedMediaItem.Builder(mi).build()), true))
        }
        MediaEditor.run(this, Composition.Builder(seqs).build(), out)
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this).setTitle("Leave the editor?").setMessage("Edits that are not exported will be lost.")
            .setPositiveButton("Leave") { _, _ -> finish() }.setNegativeButton("Keep editing", null).show()
    }

    override fun onPause() { super.onPause(); player.pause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
