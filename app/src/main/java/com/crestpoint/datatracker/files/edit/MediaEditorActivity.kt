package com.crestpoint.datatracker.files.edit

import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.RangeSlider
import java.io.File

/** Video editor (trim, crop, rotate, mute) and audio editor (trim, volume). Saves a copy. */
class MediaEditorActivity : AppCompatActivity() {
    private lateinit var src: File
    private var isVideo = true
    private var durMs = 0L
    private var vw = 0
    private var vh = 0
    private lateinit var video: VideoView
    private lateinit var range: RangeSlider
    private lateinit var info: TextView
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private lateinit var exportBtn: MaterialButton
    private lateinit var playBtn: MaterialButton
    private lateinit var cropRow: LinearLayout
    private lateinit var optRow: LinearLayout
    private var cropIdx = 0
    private var rot = 0
    private var mute = false
    private var vol = 100
    private var transformer: Transformer? = null
    private var exporting = false
    private val h = Handler(Looper.getMainLooper())
    private val crops = listOf("Original" to 0f, "1:1" to 1f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "4:3" to 4f / 3f, "3:4" to 3f / 4f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        src = File(intent.getStringExtra("path") ?: run { finish(); return })
        isVideo = com.crestpoint.datatracker.files.FileKinds.of(src) == com.crestpoint.datatracker.files.FileKind.VIDEO

        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(src.path)
            durMs = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            vw = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            vh = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rt = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rt == 90 || rt == 270) { val t = vw; vw = vh; vh = t }
        } catch (_: Exception) {
        } finally {
            try { mr.release() } catch (_: Exception) {}
        }
        if (durMs < 1000L) { UiKit.toast(this, "Cannot read this file"); finish(); return }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 32), UiKit.dp(this, 12), UiKit.dp(this, 16))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val name = UiKit.text(this, (if (isVideo) "Video editor · " else "Audio editor · ") + src.name, 15f, bold = true)
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(name, UiKit.lp(0, -2, 1f))
        top.addView(UiKit.iconBtn(this, "✕") { finish() })
        col.addView(top)

        video = VideoView(this)
        video.setVideoPath(src.path)
        video.setOnPreparedListener { video.seekTo(1) }
        video.setOnErrorListener { _, _, _ -> true }
        col.addView(video, UiKit.lp(-1, UiKit.dp(this, if (isVideo) 240 else 90)))

        playBtn = UiKit.button(this, "Play preview", true) { togglePlay() }
        col.addView(playBtn)

        info = UiKit.text(this, "", 13f, R.color.on_surface_muted)
        col.addView(info)
        range = RangeSlider(this)
        range.valueFrom = 0f
        range.valueTo = durMs / 1000f
        range.values = listOf(0f, durMs / 1000f)
        range.addOnChangeListener { _, _, fromUser ->
            updateInfo()
            if (fromUser) video.seekTo((range.values[0] * 1000).toInt())
        }
        col.addView(range)
        updateInfo()

        if (isVideo) {
            col.addView(UiKit.text(this, "Crop", 13f, R.color.on_surface_muted))
            cropRow = LinearLayout(this)
            val hs = HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            hs.addView(cropRow)
            col.addView(hs)
            optRow = LinearLayout(this)
            col.addView(optRow)
            buildCropRow(); buildOptRow()
        } else {
            val t = UiKit.text(this, "Volume 100%", 13f, R.color.on_surface_muted)
            col.addView(t)
            val sb = SeekBar(this)
            sb.max = 200
            sb.progress = 100
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { vol = p; t.text = "Volume $p%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
        }

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.visibility = View.GONE
        col.addView(bar)
        status = UiKit.text(this, "Drag the handles to choose the part to keep.", 12f, R.color.on_surface_muted)
        col.addView(status)
        exportBtn = UiKit.button(this, "Save edited copy") { export() }
        col.addView(exportBtn)

        val sv = ScrollView(this)
        sv.setBackgroundColor(UiKit.color(this, R.color.surface))
        sv.addView(col)
        setContentView(sv)
        h.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                val end = (range.values[1] * 1000).toInt()
                if (video.isPlaying && video.currentPosition >= end) {
                    video.pause(); video.seekTo((range.values[0] * 1000).toInt()); playBtn.text = "Play preview"
                }
            } catch (_: Exception) {}
            h.postDelayed(this, 200)
        }
    }

    private fun togglePlay() {
        if (video.isPlaying) { video.pause(); playBtn.text = "Play preview"; return }
        val s = (range.values[0] * 1000).toInt()
        val p = video.currentPosition
        if (p < s || p >= (range.values[1] * 1000).toInt() - 100) video.seekTo(s)
        video.start(); playBtn.text = "Pause"
    }

    private fun fmt(sec: Float): String {
        val t = sec.toInt()
        return "%d:%02d".format(t / 60, t % 60)
    }

    private fun updateInfo() {
        val a = range.values[0]; val b = range.values[1]
        info.text = "Start ${fmt(a)}  ·  End ${fmt(b)}  ·  Length ${fmt(b - a)}"
    }

    private fun buildCropRow() {
        cropRow.removeAllViews()
        crops.forEachIndexed { i, (label, _) ->
            cropRow.addView(Design.chip(this, label, i == cropIdx) { cropIdx = i; buildCropRow() })
        }
    }

    private fun buildOptRow() {
        optRow.removeAllViews()
        optRow.addView(Design.chip(this, "⟳ Rotate ${rot}°", rot != 0) { rot = (rot + 90) % 360; buildOptRow() })
        optRow.addView(Design.chip(this, if (mute) "Sound: off" else "Sound: on", mute) { mute = !mute; buildOptRow() })
    }

    private fun export() {
        if (exporting) return
        val s = (range.values[0] * 1000).toLong()
        val e = (range.values[1] * 1000).toLong()
        if (e - s < 500L) { UiKit.toast(this, "Keep at least half a second"); return }
        video.pause(); playBtn.text = "Play preview"

        val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s).setEndPositionMs(e).build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(src)).setClippingConfiguration(clip).build()
        val videoFx = ArrayList<Effect>()
        val audioFx = ArrayList<AudioProcessor>()
        if (isVideo) {
            val ratio = crops[cropIdx].second
            if (ratio > 0f && vw > 0 && vh > 0) {
                val frame = vw.toFloat() / vh.toFloat()
                if (ratio < frame) {
                    val x = ratio / frame
                    videoFx.add(Crop(-x, x, -1f, 1f))
                } else {
                    val y = frame / ratio
                    videoFx.add(Crop(-1f, 1f, -y, y))
                }
            }
            if (rot != 0) videoFx.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(rot.toFloat()).build())
        } else if (vol != 100) {
            val v = vol / 100f
            val p = ChannelMixingAudioProcessor()
            p.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(v))
            p.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(v))
            audioFx.add(p)
        }
        val eb = EditedMediaItem.Builder(item).setEffects(Effects(audioFx, videoFx))
        if (isVideo) eb.setRemoveAudio(mute) else eb.setRemoveVideo(true)

        val out = Editors.outFile(src, if (isVideo) "mp4" else "m4a")
        outPath = out.path
        val tb = Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC)
        if (isVideo) tb.setVideoMimeType(MimeTypes.VIDEO_H264)
        val t = tb.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { finishExport(null) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                finishExport(exportException.message ?: "Export failed")
            }
        }).build()
        transformer = t
        exporting = true
        exportBtn.isEnabled = false
        bar.progress = 0
        bar.visibility = View.VISIBLE
        status.text = "Saving… keep this screen open"
        t.start(eb.build(), out.absolutePath)
        pollProgress()
    }

    private var outPath = ""

    private fun pollProgress() {
        val t = transformer ?: return
        if (!exporting) return
        val holder = ProgressHolder()
        if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) bar.progress = holder.progress
        h.postDelayed({ pollProgress() }, 500)
    }

    private fun finishExport(error: String?) {
        exporting = false
        exportBtn.isEnabled = true
        bar.visibility = View.GONE
        if (error != null) {
            try { File(outPath).delete() } catch (_: Exception) {}
            status.text = "Could not save: $error"
        } else {
            MediaScannerConnection.scanFile(this, arrayOf(outPath), null, null)
            status.text = "Saved: " + File(outPath).name
            UiKit.toast(this, "Saved " + File(outPath).name)
        }
    }

    override fun onPause() { super.onPause(); try { video.pause() } catch (_: Exception) {} }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        if (exporting) { try { transformer?.cancel() } catch (_: Exception) {}; try { File(outPath).delete() } catch (_: Exception) {} }
        super.onDestroy()
    }
}
