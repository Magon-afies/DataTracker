package com.crestpoint.datatracker.files.edit

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.EditorKit
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.Tokens
import com.crestpoint.datatracker.files.ui.UiKit
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Waveform with two trim handles and a playhead. Positions are fractions 0..1. */
class WaveView(ctx: android.content.Context) : View(ctx) {
    var peaks: FloatArray? = null
        set(v) { field = v; invalidate() }
    var start = 0f
    var end = 1f
    var head = 0f
        set(v) { field = v; invalidate() }
    var minGap = 0.02f
    var onTrim: (() -> Unit)? = null
    var onSeek: ((Float) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val hw = 16f * d
    private val accent = ContextCompat.getColor(ctx, R.color.brand_purple)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9A9FF.toInt(); strokeCap = Paint.Cap.ROUND }
    private val barOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FFFFFF; strokeCap = Paint.Cap.ROUND }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND }
    private val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var drag = 0

    private fun tx(f: Float) = hw + f * (width - 2 * hw)
    private fun fx(x: Float) = ((x - hw) / (width - 2 * hw)).coerceIn(0f, 1f)

    override fun onDraw(c: Canvas) {
        val hgt = height.toFloat()
        val l = hw
        val r = width - hw
        val p = peaks
        val n = p?.size ?: 80
        val slot = (r - l) / n
        bar.strokeWidth = max(2f * d, slot * 0.55f)
        barOff.strokeWidth = bar.strokeWidth
        val xs = tx(start)
        val xe = tx(end)
        for (i in 0 until n) {
            val v = if (p != null) p[i] else 0.15f
            val h = max(4f * d, v * (hgt - 16f * d))
            val x = l + slot * (i + 0.5f)
            val paint = if (x in xs..xe) bar else barOff
            c.drawLine(x, hgt / 2f - h / 2f, x, hgt / 2f + h / 2f, paint)
        }
        c.drawRoundRect(RectF(xs, 1.5f * d, xe, hgt - 1.5f * d), 8f * d, 8f * d, frame)
        c.drawRoundRect(RectF(xs - hw, 0f, xs, hgt), 8f * d, 8f * d, handle)
        c.drawRoundRect(RectF(xe, 0f, xe + hw, hgt), 8f * d, 8f * d, handle)
        val gy = hgt / 2f
        c.drawLine(xs - hw / 2f, gy - 8f * d, xs - hw / 2f, gy + 8f * d, grip)
        c.drawLine(xe + hw / 2f, gy - 8f * d, xe + hw / 2f, gy + 8f * d, grip)
        val hx = tx(head.coerceIn(start, end))
        c.drawRoundRect(RectF(hx - 1.5f * d, 0f, hx + 1.5f * d, hgt), 2f * d, 2f * d, headP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val grab = 30f * d
                drag = if (abs(e.x - (tx(start) - hw / 2f)) < grab) 1 else if (abs(e.x - (tx(end) + hw / 2f)) < grab) 2 else 3
                parent?.requestDisallowInterceptTouchEvent(true)
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = 0
        }
        return true
    }

    private fun move(x: Float) {
        when (drag) {
            1 -> { start = min(fx(x + hw / 2f), end - minGap).coerceAtLeast(0f); head = start; onTrim?.invoke(); onSeek?.invoke(start) }
            2 -> { end = max(fx(x - hw / 2f), start + minGap).coerceAtMost(1f); head = end; onTrim?.invoke(); onSeek?.invoke(end) }
            3 -> { head = fx(x).coerceIn(start, end); onSeek?.invoke(head) }
        }
        invalidate()
    }
}

private fun afmt(ms: Long): String {
    val s = max(0L, ms) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Audio editor: waveform trim, volume, instant preview, export as .m4a. */
class MediaEditorActivity : AppCompatActivity() {
    private lateinit var src: File
    private var dur = 0L
    private var player: ExoPlayer? = null
    private lateinit var wave: WaveView
    private lateinit var tStart: TextView
    private lateinit var tHead: TextView
    private lateinit var tEnd: TextView
    private lateinit var playBtn: ImageView
    private lateinit var overlay: FrameLayout
    private lateinit var overlayText: TextView
    private lateinit var overlayBar: ProgressBar
    private var vol = 100
    private var transformer: Transformer? = null
    private var exporting = false
    private var outPath = ""
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { onTick(); h.postDelayed(this, 100) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        src = File(intent.getStringExtra("path") ?: run { finish(); return })
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(src.path)
            dur = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try { mr.release() } catch (_: Exception) {}
        }
        if (dur < 1000L) { UiKit.toast(this, "Cannot read this audio"); finish(); return }

        val accent = UiKit.color(this, R.color.brand_purple)
        val outer = FrameLayout(this)
        outer.setBackgroundColor(0xFF0B0B14.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 26), UiKit.dp(this, 12), UiKit.dp(this, 12))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Glass.iconBtn(this, Glass.CLOSE) { finish() })
        top.addView(UiKit.text(this, "Audio editor", 16f, bold = true), UiKit.lp(0, -2, 1f))
        val ex = UiKit.button(this, "Export") { export() }
        ex.backgroundTintList = ColorStateList.valueOf(accent)
        top.addView(ex)
        col.addView(top)

        // hero card: file + waveform + transport
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.background = Tokens.cardBg(this, 22)
        hero.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 14), UiKit.dp(this, 12), UiKit.dp(this, 14))
        val info = LinearLayout(this)
        info.gravity = Gravity.CENTER_VERTICAL
        val badge = FrameLayout(this)
        badge.background = Glass.pill(0x337657F6, 16, this)
        val note = ImageView(this)
        note.setImageDrawable(PathIcon("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z", accent))
        badge.addView(note, FrameLayout.LayoutParams(UiKit.dp(this, 26), UiKit.dp(this, 26), Gravity.CENTER))
        info.addView(badge, LinearLayout.LayoutParams(UiKit.dp(this, 52), UiKit.dp(this, 52)))
        val names = LinearLayout(this)
        names.orientation = LinearLayout.VERTICAL
        names.setPadding(UiKit.dp(this, 12), 0, 0, 0)
        val nm = UiKit.text(this, src.nameWithoutExtension, 15f, bold = true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.END
        names.addView(nm)
        names.addView(UiKit.text(this, src.extension.uppercase() + "  ·  " + afmt(dur), 12f, R.color.on_surface_muted))
        info.addView(names, UiKit.lp(0, -2, 1f))
        hero.addView(info)

        wave = WaveView(this)
        wave.minGap = max(0.01f, 500f / dur)
        wave.onTrim = { updateLabels() }
        wave.onSeek = { f -> player?.seekTo((f * dur).toLong()) }
        val wl = LinearLayout.LayoutParams(-1, UiKit.dp(this, 120))
        wl.setMargins(0, UiKit.dp(this, 14), 0, UiKit.dp(this, 6))
        hero.addView(wave, wl)

        val times = LinearLayout(this)
        tStart = UiKit.text(this, "0:00", 12f, R.color.on_surface_muted)
        tHead = UiKit.text(this, "0:00", 12f, bold = true)
        tEnd = UiKit.text(this, afmt(dur), 12f, R.color.on_surface_muted)
        tHead.gravity = Gravity.CENTER
        tEnd.gravity = Gravity.END
        times.addView(tStart, UiKit.lp(0, -2, 1f))
        times.addView(tHead, UiKit.lp(0, -2, 1f))
        times.addView(tEnd, UiKit.lp(0, -2, 1f))
        hero.addView(times)

        val transport = LinearLayout(this)
        transport.gravity = Gravity.CENTER
        playBtn = ImageView(this)
        val pp = UiKit.dp(this, 16)
        playBtn.setPadding(pp, pp, pp, pp)
        playBtn.background = Glass.circle(accent)
        playBtn.setOnClickListener { togglePlay() }
        val tl = LinearLayout.LayoutParams(UiKit.dp(this, 64), UiKit.dp(this, 64))
        tl.topMargin = UiKit.dp(this, 10)
        transport.addView(playBtn, tl)
        hero.addView(transport)
        col.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@MediaEditorActivity, 10) })

        // controls card
        val ctl = LinearLayout(this)
        ctl.orientation = LinearLayout.VERTICAL
        ctl.background = Tokens.cardBg(this, 22)
        ctl.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12), UiKit.dp(this, 14), UiKit.dp(this, 12))
        ctl.addView(EditorKit.slider(this, "Volume %", 0, 200, 100) { vol = it; player?.volume = min(1f, vol / 100f) })
        ctl.addView(UiKit.text(this, "Drag the purple handles to keep only the part you want. Saved as a new .m4a file next to the original.", 12f, R.color.on_surface_muted)
            .also { it.setPadding(0, UiKit.dp(this, 8), 0, 0) })
        col.addView(ctl, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@MediaEditorActivity, 10) })

        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col)
        outer.addView(sv, FrameLayout.LayoutParams(-1, -1))

        overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xE6000000.toInt())
        overlay.visibility = View.GONE
        overlay.isClickable = true
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24))
        overlayText = UiKit.text(this, "Exporting…", 16f, bold = true)
        overlayBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        overlayBar.max = 100
        card.addView(overlayText)
        card.addView(overlayBar, UiKit.lp(UiKit.dp(this, 240), -2))
        card.addView(UiKit.button(this, "Cancel", true) { cancelExport() })
        overlay.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        outer.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)

        val p = ExoPlayer.Builder(this).build()
        player = p
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(src)))
        p.prepare()
        p.playWhenReady = false
        updatePlayIcon()
        updateLabels()
        Thread {
            val peaks = computePeaks(src.path, 80)
            runOnUiThread { if (!isDestroyed) wave.peaks = peaks }
        }.start()
        h.post(tick)
    }

    private fun onTick() {
        val p = player ?: return
        val pos = p.currentPosition
        val s = (wave.start * dur).toLong()
        val e = (wave.end * dur).toLong()
        if (p.isPlaying) {
            if (pos >= e) { p.pause(); p.seekTo(s) }
            else if (pos < s - 300) p.seekTo(s)
        }
        wave.head = pos.toFloat() / dur
        tHead.text = afmt(pos)
        updatePlayIcon()
    }

    private fun updateLabels() {
        tStart.text = afmt((wave.start * dur).toLong())
        tEnd.text = afmt((wave.end * dur).toLong()) + "  (" + afmt(((wave.end - wave.start) * dur).toLong()) + ")"
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); return }
        val s = (wave.start * dur).toLong()
        val e = (wave.end * dur).toLong()
        if (p.currentPosition < s || p.currentPosition >= e - 100) p.seekTo(s)
        p.play()
    }

    private fun updatePlayIcon() {
        val playing = player?.isPlaying == true
        playBtn.setImageDrawable(PathIcon(if (playing) Glass.PAUSE else Glass.PLAY, Color.WHITE))
    }

    /** Decodes the audio and keeps the loudest value for each of [buckets] slices. Returns null if it cannot decode. */
    private fun computePeaks(path: String, buckets: Int): FloatArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(path)
            var track = -1
            for (i in 0 until ex.trackCount) {
                val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { track = i; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else dur * 1000L
            if (durUs <= 0L) return null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val peaks = FloatArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && guard < 400000 && !isDestroyed) {
                guard++
                if (!inputDone) {
                    val ib = codec.dequeueInputBuffer(5000)
                    if (ib >= 0) {
                        val buf = codec.getInputBuffer(ib)
                        val n = if (buf != null) ex.readSampleData(buf, 0) else -1
                        if (n < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ib, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 5000)
                if (ob >= 0) {
                    if (info.size > 0) {
                        val out = codec.getOutputBuffer(ob)
                        if (out != null) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val sb = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val bucket = ((info.presentationTimeUs.toDouble() / durUs) * buckets).toInt().coerceIn(0, buckets - 1)
                            var m = 0
                            var i = 0
                            while (i < sb.limit()) {
                                val v = abs(sb.get(i).toInt())
                                if (v > m) m = v
                                i += 8
                            }
                            val f = m / 32768f
                            if (f > peaks[bucket]) peaks[bucket] = f
                        }
                    }
                    codec.releaseOutputBuffer(ob, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                }
            }
            codec.stop()
            codec.release()
            val top = peaks.max()
            if (top <= 0f) return null
            for (i in peaks.indices) peaks[i] = (peaks[i] / top).coerceIn(0.05f, 1f)
            return peaks
        } catch (_: Throwable) {
            return null
        } finally {
            try { ex.release() } catch (_: Exception) {}
        }
    }

    // ---------- export
    private fun export() {
        if (exporting) return
        val s = (wave.start * dur).toLong()
        val e = (wave.end * dur).toLong()
        if (e - s < 500L) { UiKit.toast(this, "Keep at least half a second"); return }
        player?.pause()
        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s).setEndPositionMs(e).build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(src)).setClippingConfiguration(clip).build()
        val audioFx = ArrayList<AudioProcessor>()
        if (vol != 100) {
            val v = vol / 100f
            val ap = ChannelMixingAudioProcessor()
            ap.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(v))
            ap.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(v))
            audioFx.add(ap)
        }
        val eb = EditedMediaItem.Builder(item).setEffects(Effects(audioFx, emptyList()))
        eb.setRemoveVideo(true)
        val out = Editors.outFile(src, "m4a")
        outPath = out.path
        val t = Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { finishExport(null) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finishExport(exportException.message ?: "Export failed")
                }
            }).build()
        transformer = t
        exporting = true
        overlay.visibility = View.VISIBLE
        overlayBar.progress = 0
        overlayText.text = "Exporting…"
        t.start(eb.build(), out.absolutePath)
        pollProgress()
    }

    private fun pollProgress() {
        val t = transformer ?: return
        if (!exporting) return
        val holder = ProgressHolder()
        if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            overlayBar.progress = holder.progress
            overlayText.text = "Exporting… ${holder.progress}%"
        }
        h.postDelayed({ pollProgress() }, 400)
    }

    private fun cancelExport() {
        try { transformer?.cancel() } catch (_: Exception) {}
        exporting = false
        overlay.visibility = View.GONE
        try { File(outPath).delete() } catch (_: Exception) {}
    }

    private fun finishExport(error: String?) {
        if (!exporting) return
        exporting = false
        overlay.visibility = View.GONE
        if (error != null) {
            try { File(outPath).delete() } catch (_: Exception) {}
            UiKit.toast(this, "Could not export: $error")
        } else {
            MediaScannerConnection.scanFile(this, arrayOf(outPath), null, null)
            UiKit.toast(this, "Saved " + File(outPath).name)
            finish()
        }
    }

    override fun onStop() { super.onStop(); try { player?.pause() } catch (_: Exception) {} }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        if (exporting) { try { transformer?.cancel() } catch (_: Exception) {}; try { File(outPath).delete() } catch (_: Exception) {} }
        player?.release()
        player = null
        super.onDestroy()
    }
}
