package com.crestpoint.datatracker.files.viewers

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.RangeSlider
import java.io.File

/** Cut, extract audio or mute — done on-device with Media3 Transformer. Results go to Download/DataTracker/Edits. */
@OptIn(UnstableApi::class)
object MediaEditor {
    enum class Mode(val title: String, val suffix: String) {
        TRIM("Trim", "trim"), EXTRACT_AUDIO("Extract audio", "audio"), MUTE("Mute video", "muted")
    }

    private fun fmt(sec: Float): String {
        val s = sec.toInt(); val m = s / 60
        return "%d:%02d".format(m, s % 60)
    }

    fun show(act: AppCompatActivity, file: File, durationMs: Long, currentMs: Long, mode: Mode) {
        if (durationMs < 1500) { UiKit.toast(act, "This file is too short or still loading"); return }
        val total = durationMs / 1000f
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val pad = UiKit.dp(act, 20)
        box.setPadding(pad, UiKit.dp(act, 10), pad, 0)
        val info = UiKit.text(act, "", 14f, bold = true)
        val slider = RangeSlider(act)
        slider.valueFrom = 0f
        slider.valueTo = total
        slider.values = listOf(0f, total)
        slider.minSeparation = 0.5f
        fun refresh() { val v = slider.values; info.text = "From ${fmt(v[0])}  to  ${fmt(v[1])}   (${fmt(v[1] - v[0])})" }
        slider.addOnChangeListener { _, _, _ -> refresh() }
        refresh()
        box.addView(info)
        box.addView(slider, LinearLayout.LayoutParams(-1, -2))
        val row = LinearLayout(act)
        row.addView(Design.chip(act, "Start = now", false) {
            val v = slider.values; val c = (currentMs / 1000f).coerceIn(0f, v[1] - 0.5f); slider.values = listOf(c, v[1]); refresh()
        })
        row.addView(Design.chip(act, "End = now", false) {
            val v = slider.values; val c = (currentMs / 1000f).coerceIn(v[0] + 0.5f, total); slider.values = listOf(v[0], c); refresh()
        })
        box.addView(row)
        box.addView(UiKit.text(act, "Saved to Download/DataTracker/Edits. Your original is never changed.", 11f, R.color.on_surface_muted)
            .also { it.setPadding(0, UiKit.dp(act, 10), 0, 0) })
        MaterialAlertDialogBuilder(act).setTitle(mode.title + " · " + file.name).setView(box)
            .setPositiveButton("Export") { _, _ ->
                val v = slider.values
                export(act, file, (v[0] * 1000).toLong(), (v[1] * 1000).toLong(), durationMs, mode)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun export(act: AppCompatActivity, file: File, startMs: Long, endMs: Long, durationMs: Long, mode: Mode) {
        val isAudio = FileKinds.of(file) == FileKind.AUDIO
        val ext = if (isAudio || mode == Mode.EXTRACT_AUDIO) "m4a" else "mp4"
        val dir = File(FilePaths.internalRoot(), "Download/DataTracker/Edits")
        dir.mkdirs()
        val out = FileOps.uniqueFile(dir, "${file.nameWithoutExtension}_${mode.suffix}.$ext")

        val clipBuilder = MediaItem.ClippingConfiguration.Builder()
        if (startMs > 0) clipBuilder.setStartPositionMs(startMs)
        if (endMs < durationMs - 300) clipBuilder.setEndPositionMs(endMs)
        val item = MediaItem.Builder().setUri(Uri.fromFile(file)).setClippingConfiguration(clipBuilder.build()).build()
        val edited = EditedMediaItem.Builder(item)
            .setRemoveVideo(mode == Mode.EXTRACT_AUDIO)
            .setRemoveAudio(mode == Mode.MUTE)
            .build()

        val pb = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        val label = UiKit.text(act, "Exporting… 0%", 14f)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        val p = UiKit.dp(act, 22)
        col.setPadding(p, p, p, p)
        col.addView(label); col.addView(pb, LinearLayout.LayoutParams(-1, -2))

        val handler = Handler(Looper.getMainLooper())
        lateinit var transformer: Transformer
        var finished = false
        val dialog = MaterialAlertDialogBuilder(act).setTitle("Please wait").setView(col).setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> finished = true; try { transformer.cancel() } catch (_: Exception) { }; out.delete() }
            .create()

        transformer = Transformer.Builder(act).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                finished = true; dialog.dismiss()
                MaterialAlertDialogBuilder(act).setTitle("Done ✓").setMessage("Saved:\n${out.absolutePath}")
                    .setPositiveButton("Open") { _, _ -> FileOpener.open(act, out) }
                    .setNegativeButton("Close", null).show()
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                finished = true; dialog.dismiss(); out.delete()
                UiKit.toast(act, "Export failed: ${exportException.errorCodeName}")
            }
        }).build()

        dialog.show()
        try { transformer.start(edited, out.absolutePath) } catch (e: Exception) {
            finished = true; dialog.dismiss(); UiKit.toast(act, "Could not start export: ${e.message}"); return
        }
        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                val st = transformer.getProgress(holder)
                if (st != Transformer.PROGRESS_STATE_NOT_STARTED) { pb.progress = holder.progress; label.text = "Exporting… ${holder.progress}%" }
                handler.postDelayed(this, 300)
            }
        }
        handler.postDelayed(poll, 300)
    }
}
