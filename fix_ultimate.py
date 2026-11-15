#!/usr/bin/env python3
# DataTracker Ultimate Master Script: Applies all features & fixes in one pass.
import os, sys

proj = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else "~/DataTracker")
SRC = os.path.join(proj, "app/src/main")
J = os.path.join(SRC, "java/com/crestpoint/datatracker/files")
GRADLE = os.path.join(proj, "app/build.gradle.kts")
MAN = os.path.join(SRC, "AndroidManifest.xml")

print("==> Applying DataTracker Ultimate Feature & Fix Pack...")

# 1. Gradle Dependencies
if os.path.exists(GRADLE):
    g = open(GRADLE, encoding="utf-8").read()
    if "media3-transformer" not in g:
        g = g.replace("dependencies {\n", 
            'dependencies {\n    implementation("androidx.media3:media3-exoplayer:1.4.1")\n'
            '    implementation("androidx.media3:media3-ui:1.4.1")\n'
            '    implementation("androidx.media3:media3-transformer:1.4.1")\n'
            '    implementation("androidx.media3:media3-effect:1.4.1")\n'
            '    implementation("androidx.media3:media3-common:1.4.1")\n')
        open(GRADLE, "w", encoding="utf-8").write(g)

# 2. Manifest Activities & OpenWith Intent Filters
if os.path.exists(MAN):
    m = open(MAN, encoding="utf-8").read()
    if "OpenWithActivity" not in m:
        m = m.replace("    </application>", '''
        <activity android:name=".files.viewers.MediaActivity" android:exported="false" android:supportsPictureInPicture="true" android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
        <activity android:name=".files.edit.VideoEditorActivity" android:exported="false" android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
        <activity android:name=".files.edit.SnapEditorActivity" android:exported="false" />
        <activity android:name=".files.OpenWithActivity" android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
                <data android:mimeType="audio/*" />
                <data android:mimeType="image/*" />
                <data android:mimeType="application/pdf" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.EDIT" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
                <data android:mimeType="audio/*" />
                <data android:mimeType="image/*" />
            </intent-filter>
        </activity>
    </application>''')
        open(MAN, "w", encoding="utf-8").write(m)

os.makedirs(os.path.join(J, "edit"), exist_ok=True)
os.makedirs(os.path.join(J, "viewers"), exist_ok=True)
os.makedirs(os.path.join(J, "ui"), exist_ok=True)

print("==> Writing core editor dispatcher...")
open(os.path.join(J, "edit/Editors.kt"), "w", encoding="utf-8").write(r'''package com.crestpoint.datatracker.files.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import java.io.File

object Editors {
    fun canEdit(f: File): Boolean {
        if (!f.isFile) return false
        val k = FileKinds.of(f)
        return (k == FileKind.IMAGE && f.extension.lowercase() != "svg") || k == FileKind.VIDEO || k == FileKind.AUDIO
    }

    fun open(ctx: Context, f: File) {
        if (!canEdit(f)) return
        val k = FileKinds.of(f)
        val cls = when (k) {
            FileKind.IMAGE -> SnapEditorActivity::class.java
            FileKind.VIDEO -> VideoEditorActivity::class.java
            else -> MediaEditorActivity::class.java
        }
        val i = Intent(ctx, cls).putExtra("path", f.path)
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun outFile(src: File, ext: String): File {
        val dir = if (src.path.contains("/open_with/"))
            File(Environment.getExternalStorageDirectory(), "Download").also { it.mkdirs() }
        else (src.parentFile ?: src.absoluteFile.parentFile)
        var n = File(dir, "${src.nameWithoutExtension}_edited.$ext")
        var i = 2
        while (n.exists()) { n = File(dir, "${src.nameWithoutExtension}_edited$i.$ext"); i++ }
        return n
    }
}
''')

print("==> Writing VideoEditorActivity with fixed ImmutableList...")
open(os.path.join(J, "edit/VideoEditorActivity.kt"), "w", encoding="utf-8").write(r'''package com.crestpoint.datatracker.files.edit

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TimelineView(ctx: android.content.Context) : View(ctx) {
    var frames: List<Bitmap?> = emptyList()
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
    private val bg = Paint().apply { color = 0xFF1A1B2E.toInt() }
    private val dim = Paint().apply { color = 0xB3000000.toInt() }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 3f * d }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND }
    private val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var drag = 0

    private fun tx(f: Float) = hw + f * (width - 2 * hw)
    private fun fx(x: Float) = ((x - hw) / (width - 2 * hw)).coerceIn(0f, 1f)

    override fun onDraw(c: Canvas) {
        val hgt = height.toFloat()
        val l = hw; val r = width - hw
        val clip = Path()
        clip.addRoundRect(RectF(l, 0f, r, hgt), 10f * d, 10f * d, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawRect(l, 0f, r, hgt, bg)
        val n = frames.size
        for (i in 0 until n) {
            val b = frames[i] ?: continue
            val dl = l + (r - l) * i / n
            val dr = l + (r - l) * (i + 1) / n
            c.drawBitmap(b, null, RectF(dl, 0f, dr, hgt), null)
        }
        c.drawRect(l, 0f, tx(start), hgt, dim)
        c.drawRect(tx(end), 0f, r, hgt, dim)
        c.restore()
        val xs = tx(start); val xe = tx(end)
        c.drawRect(xs, 1.5f * d, xe, hgt - 1.5f * d, frame)
        c.drawRoundRect(RectF(xs - hw, 0f, xs, hgt), 8f * d, 8f * d, handle)
        c.drawRoundRect(RectF(xe, 0f, xe + hw, hgt), 8f * d, 8f * d, handle)
        val gy = hgt / 2f
        c.drawLine(xs - hw / 2f, gy - 8f * d, xs - hw / 2f, gy + 8f * d, grip)
        c.drawLine(xe + hw / 2f, gy - 8f * d, xe + hw / 2f, gy + 8f * d, grip)
        val hx = tx(head.coerceIn(start, end))
        c.drawRoundRect(RectF(hx - 1.5f * d, 0f, hx + 1.5f * d, hgt), 2f * d, 2f * d, headP)
        c.drawCircle(hx, 3f * d, 4f * d, headP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val xs = tx(start); val xe = tx(end); val grab = 30f * d
                drag = if (abs(e.x - (xs - hw / 2f)) < grab) 1 else if (abs(e.x - (xe + hw / 2f)) < grab) 2 else 3
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

private fun tfmt(ms: Long): String {
    val s = max(0L, ms) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var src: File
    private var dur = 0L; private var vw = 0; private var vh = 0
    private var player: ExoPlayer? = null
    private lateinit var tl: TimelineView
    private lateinit var tStart: TextView; private lateinit var tHead: TextView; private lateinit var tEnd: TextView
    private lateinit var panel: FrameLayout; private lateinit var tabRow: LinearLayout
    private lateinit var overlay: FrameLayout; private lateinit var overlayText: TextView; private lateinit var overlayBar: ProgressBar
    private lateinit var playIcon: ImageView
    private var tab = 0; private var cropIdx = 0; private var rot = 0; private var flip = false
    private var bright = 0; private var contrast = 0; private var filterIdx = 0
    private var textStr = ""; private var textPos = 2; private var mute = false; private var vol = 100
    private var sizeIdx = 0; private var audioOnly = false
    private var transformer: Transformer? = null; private var exporting = false; private var outPath = ""
    private val h = Handler(Looper.getMainLooper())
    private val previewRun = Runnable { applyPreview() }
    private val crops = listOf("Original" to 0f, "1:1" to 1f, "16:9" to 16f / 9f, "9:16" to 9f / 16f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "21:9" to 21f / 9f)
    private val filters = listOf("None", "B&W", "Warm", "Cool", "Invert")
    private val sizes = listOf("Original" to 0, "1080p" to 1080, "720p" to 720, "480p" to 480)
    private val tabs = listOf("Crop", "Adjust", "Filters", "Text", "Audio", "Output")
    private val tick = object : Runnable { override fun run() { onTick(); h.postDelayed(this, 100) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        src = File(intent.getStringExtra("path") ?: run { finish(); return })
        val mr = MediaMetadataRetriever()
        try {
            mr.setDataSource(src.path)
            dur = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            vw = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            vh = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rt = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rt == 90 || rt == 270) { val t = vw; vw = vh; vh = t }
        } catch (_: Exception) {} finally { try { mr.release() } catch (_: Exception) {} }
        if (dur < 1000L) { UiKit.toast(this, "Cannot read this video"); finish(); return }

        val accent = UiKit.color(this, R.color.brand_purple)
        val outer = FrameLayout(this); outer.setBackgroundColor(0xFF0B0B14.toInt())
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 26), UiKit.dp(this, 10), UiKit.dp(this, 10))

        val top = LinearLayout(this); top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Glass.iconBtn(this, Glass.CLOSE) { finish() })
        top.addView(UiKit.text(this, "Video editor", 16f, bold = true), UiKit.lp(0, -2, 1f))
        val ex = UiKit.button(this, "Export") { export() }
        ex.backgroundTintList = ColorStateList.valueOf(accent)
        top.addView(ex); col.addView(top)

        val stage = FrameLayout(this); stage.setBackgroundColor(Color.BLACK)
        val pv = PlayerView(this); pv.useController = false; stage.addView(pv, FrameLayout.LayoutParams(-1, -1))
        playIcon = ImageView(this)
        val pp = UiKit.dp(this, 16); playIcon.setPadding(pp, pp, pp, pp)
        playIcon.background = Glass.circle(0x99000000.toInt())
        stage.addView(playIcon, FrameLayout.LayoutParams(UiKit.dp(this, 64), UiKit.dp(this, 64), Gravity.CENTER))
        stage.setOnClickListener { togglePlay() }; playIcon.setOnClickListener { togglePlay() }
        val sl = LinearLayout.LayoutParams(-1, 0, 1f); sl.setMargins(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6))
        col.addView(stage, sl)

        tl = TimelineView(this)
        tl.minGap = max(0.01f, 500f / dur)
        tl.onTrim = { updateLabels() }
        tl.onSeek = { f -> player?.seekTo((f * dur).toLong()) }
        col.addView(tl, UiKit.lp(-1, UiKit.dp(this, 58)))

        val times = LinearLayout(this)
        tStart = UiKit.text(this, "0:00", 12f, R.color.on_surface_muted)
        tHead = UiKit.text(this, "0:00", 12f, bold = true)
        tEnd = UiKit.text(this, tfmt(dur), 12f, R.color.on_surface_muted)
        tHead.gravity = Gravity.CENTER
        times.addView(tStart, UiKit.lp(0, -2, 1f))
        times.addView(tHead, UiKit.lp(0, -2, 1f))
        tEnd.gravity = Gravity.END
        times.addView(tEnd, UiKit.lp(0, -2, 1f))
        col.addView(times)

        tabRow = LinearLayout(this)
        val tabScroll = HorizontalScrollView(this); tabScroll.isHorizontalScrollBarEnabled = false; tabScroll.addView(tabRow)
        col.addView(tabScroll)
        panel = FrameLayout(this); col.addView(panel, UiKit.lp(-1, UiKit.dp(this, 132)))
        outer.addView(col, FrameLayout.LayoutParams(-1, -1))

        overlay = FrameLayout(this); overlay.setBackgroundColor(0xE6000000.toInt()); overlay.visibility = View.GONE; overlay.isClickable = true
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.gravity = Gravity.CENTER
        card.setPadding(UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24), UiKit.dp(this, 24))
        overlayText = UiKit.text(this, "Exporting…", 16f, bold = true)
        overlayBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); overlayBar.max = 100
        card.addView(overlayText); card.addView(overlayBar, UiKit.lp(UiKit.dp(this, 240), -2))
        card.addView(UiKit.text(this, "Keep this screen open", 12f, R.color.on_surface_muted))
        card.addView(UiKit.button(this, "Cancel", true) { cancelExport() })
        overlay.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        outer.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)

        val p = ExoPlayer.Builder(this).build(); player = p; pv.player = p
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(src))); p.prepare(); p.playWhenReady = false
        updatePlayIcon(); buildTabs(); buildPanel(); loadThumbs(); h.post(tick)
    }

    private fun effects(forExport: Boolean): ArrayList<Effect> {
        val fx = ArrayList<Effect>()
        val ratio = crops[cropIdx].second
        if (ratio > 0f && vw > 0 && vh > 0) {
            val frame = vw.toFloat() / vh.toFloat()
            if (ratio < frame) { val x = ratio / frame; fx.add(Crop(-x, x, -1f, 1f)) }
            else { val y = frame / ratio; fx.add(Crop(-1f, 1f, -y, y)) }
        }
        if (rot != 0 || flip) {
            fx.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(rot.toFloat()).setScale(if (flip) -1f else 1f, 1f).build())
        }
        if (bright != 0) fx.add(Brightness(bright / 100f))
        if (contrast != 0) fx.add(Contrast(contrast / 100f))
        when (filterIdx) {
            1 -> fx.add(RgbFilter.createGrayscaleFilter())
            2 -> fx.add(RgbAdjustment.Builder().setRedScale(1.15f).setGreenScale(1f).setBlueScale(0.85f).build())
            3 -> fx.add(RgbAdjustment.Builder().setRedScale(0.9f).setGreenScale(1f).setBlueScale(1.15f).build())
            4 -> fx.add(RgbFilter.createInvertedFilter())
        }
        if (forExport && sizes[sizeIdx].second > 0) fx.add(Presentation.createForHeight(sizes[sizeIdx].second))
        if (textStr.isNotEmpty()) {
            val sp = SpannableString(textStr)
            val px = max(24, (max(vh, 480) * 0.06f).toInt())
            sp.setSpan(ForegroundColorSpan(Color.WHITE), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(AbsoluteSizeSpan(px), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(BackgroundColorSpan(0x66000000), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val y = when (textPos) { 0 -> 0.8f; 1 -> 0f; else -> -0.8f }
            val st = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, y).build()
            // Using ImmutableList to resolve the compile-time type mismatch correctly
            fx.add(OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(sp, st))))
        }
        return fx
    }

    private fun applyPreview() {
        val p = player ?: return
        try { p.setVideoEffects(effects(false)) } catch (_: Exception) {}
        p.volume = if (mute) 0f else min(1f, vol / 100f)
    }

    private fun schedulePreview() { h.removeCallbacks(previewRun); h.postDelayed(previewRun, 150) }

    private fun onTick() {
        val p = player ?: return
        val pos = p.currentPosition
        val s = (tl.start * dur).toLong(); val e = (tl.end * dur).toLong()
        if (p.isPlaying) {
            if (pos >= e) { p.pause(); p.seekTo(s) }
            else if (pos < s - 300) p.seekTo(s)
        }
        tl.head = pos.toFloat() / dur
        tHead.text = tfmt(pos)
        updatePlayIcon()
    }

    private fun updateLabels() {
        tStart.text = tfmt((tl.start * dur).toLong())
        tEnd.text = tfmt((tl.end * dur).toLong()) + "  (" + tfmt(((tl.end - tl.start) * dur).toLong()) + ")"
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); return }
        val s = (tl.start * dur).toLong(); val e = (tl.end * dur).toLong()
        if (p.currentPosition < s || p.currentPosition >= e - 100) p.seekTo(s)
        p.play()
    }

    private fun updatePlayIcon() {
        val playing = player?.isPlaying == true
        playIcon.setImageDrawable(PathIcon(if (playing) Glass.PAUSE else Glass.PLAY, Color.WHITE))
        playIcon.alpha = if (playing) 0.0f else 1f
    }

    private fun loadThumbs() {
        val w = 160
        val hh = if (vw > 0 && vh > 0) max(60, w * vh / vw) else 90
        Thread {
            val mr = MediaMetadataRetriever()
            try {
                mr.setDataSource(src.path)
                val n = 10; val got = ArrayList<Bitmap?>()
                for (i in 0 until n) {
                    if (isDestroyed) break
                    val t = min(dur * 1000L * i / n + 100_000L, dur * 1000L - 1)
                    got.add(mr.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, hh))
                    val snap = ArrayList(got)
                    while (snap.size < n) snap.add(null)
                    runOnUiThread { tl.frames = snap }
                }
            } catch (_: Throwable) {} finally { try { mr.release() } catch (_: Exception) {} }
        }.start()
    }

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabs.forEachIndexed { i, n -> tabRow.addView(Design.chip(this, n, i == tab) { tab = i; buildTabs(); buildPanel() }) }
    }

    private fun chipRow(vararg chips: View): View {
        val r = LinearLayout(this); chips.forEach { r.addView(it) }
        val hs = HorizontalScrollView(this); hs.isHorizontalScrollBarEnabled = false; hs.addView(r)
        return hs
    }

    private fun seek(label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this); row.gravity = Gravity.CENTER_VERTICAL
        val t = UiKit.text(this, "$label $init", 13f, R.color.on_surface_muted)
        row.addView(t, UiKit.lp(UiKit.dp(this, 120), -2))
        val sb = SeekBar(this); sb.max = max - min; sb.progress = init - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { t.text = "$label ${p + min}"; onChange(p + min); schedulePreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { applyPreview() }
        })
        row.addView(sb, UiKit.lp(0, -2, 1f))
        return row
    }

    private fun buildPanel() {
        panel.removeAllViews()
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL
        when (tab) {
            0 -> {
                val rs = crops.mapIndexed { i, c -> Design.chip(this, c.first, i == cropIdx) { cropIdx = i; applyPreview(); buildPanel() } }
                box.addView(chipRow(*rs.toTypedArray()))
                box.addView(chipRow(
                    Design.chip(this, "Rotate ${rot}°", rot != 0) { rot = (rot + 90) % 360; applyPreview(); buildPanel() },
                    Design.chip(this, "Flip", flip) { flip = !flip; applyPreview(); buildPanel() }
                ))
            }
            1 -> {
                box.addView(seek("Brightness", -50, 50, bright) { bright = it })
                box.addView(seek("Contrast", -50, 50, contrast) { contrast = it })
                box.addView(chipRow(Design.chip(this, "Reset", false) { bright = 0; contrast = 0; applyPreview(); buildPanel() }))
            }
            2 -> {
                val fs = filters.mapIndexed { i, n -> Design.chip(this, n, i == filterIdx) { filterIdx = i; applyPreview(); buildPanel() } }
                box.addView(chipRow(*fs.toTypedArray()))
            }
            3 -> {
                box.addView(chipRow(
                    Design.chip(this, if (textStr.isEmpty()) "Add text" else "Edit text", textStr.isNotEmpty()) { askText() },
                    Design.chip(this, "Top", textPos == 0) { textPos = 0; applyPreview(); buildPanel() },
                    Design.chip(this, "Middle", textPos == 1) { textPos = 1; applyPreview(); buildPanel() },
                    Design.chip(this, "Bottom", textPos == 2) { textPos = 2; applyPreview(); buildPanel() },
                    Design.chip(this, "Remove", false) { textStr = ""; applyPreview(); buildPanel() }
                ))
                if (textStr.isNotEmpty()) box.addView(UiKit.text(this, "\"$textStr\"", 13f, R.color.on_surface_muted))
            }
            4 -> {
                box.addView(chipRow(Design.chip(this, if (mute) "Sound off" else "Sound on", mute) { mute = !mute; applyPreview(); buildPanel() }))
                box.addView(seek("Volume %", 0, 200, vol) { vol = it })
            }
            5 -> {
                val ss = sizes.mapIndexed { i, s -> Design.chip(this, s.first, i == sizeIdx) { sizeIdx = i; buildPanel() } }
                box.addView(chipRow(*ss.toTypedArray()))
                box.addView(chipRow(
                    Design.chip(this, "Audio only", audioOnly) { audioOnly = !audioOnly; buildPanel() },
                    Design.chip(this, "Save this frame", false) { saveFrame() }
                ))
            }
        }
        val sv = ScrollView(this); sv.addView(box); panel.addView(sv)
    }

    private fun askText() {
        val et = EditText(this); et.setText(textStr); et.hint = "Caption"
        MaterialAlertDialogBuilder(this).setTitle("Text on video").setView(et)
            .setPositiveButton("OK") { _, _ -> textStr = et.text.toString().trim(); applyPreview(); buildPanel() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun saveFrame() {
        val pos = (player?.currentPosition ?: 0L) * 1000L
        Thread {
            val mr = MediaMetadataRetriever()
            try {
                mr.setDataSource(src.path)
                val b = mr.getFrameAtTime(pos, MediaMetadataRetriever.OPTION_CLOSEST)
                if (b != null) {
                    val out = Editors.outFile(src, "jpg")
                    FileOutputStream(out).use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    MediaScannerConnection.scanFile(this, arrayOf(out.path), null, null)
                    runOnUiThread { UiKit.toast(this, "Saved " + out.name) }
                }
            } catch (_: Throwable) {} finally { try { mr.release() } catch (_: Exception) {} }
        }.start()
    }

    private fun export() {
        if (exporting) return
        val s = (tl.start * dur).toLong(); val e = (tl.end * dur).toLong()
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
        val videoFx: List<Effect> = if (audioOnly) emptyList() else effects(true)
        val eb = EditedMediaItem.Builder(item).setEffects(Effects(audioFx, videoFx))
        if (audioOnly) eb.setRemoveVideo(true) else eb.setRemoveAudio(mute)
        val out = Editors.outFile(src, if (audioOnly) "m4a" else "mp4")
        outPath = out.path
        val tb = Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC)
        if (!audioOnly) tb.setVideoMimeType(MimeTypes.VIDEO_H264)
        val t = tb.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { finishExport(null) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                finishExport(exportException.message ?: "Export failed")
            }
        }).build()
        transformer = t; exporting = true
        overlay.visibility = View.VISIBLE; overlayBar.progress = 0; overlayText.text = "Exporting…"
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
        exporting = false; overlay.visibility = View.GONE
        try { File(outPath).delete() } catch (_: Exception) {}
    }

    private fun finishExport(error: String?) {
        if (!exporting) return
        exporting = false; overlay.visibility = View.GONE
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
        player?.release(); player = null
        super.onDestroy()
    }
}
''')

print("==> Master script setup completed successfully!")
