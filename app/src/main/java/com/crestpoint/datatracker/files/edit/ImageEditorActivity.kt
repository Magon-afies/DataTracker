package com.crestpoint.datatracker.files.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Image with a draggable crop box. Crop box lives in bitmap coordinates. */
class CropView(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null
        private set
    var aspect = 0f
        private set
    var filter: ColorFilter? = null
        set(v) { field = v; invalidate() }

    private val crop = RectF()
    private val dst = RectF()
    private var scale = 1f
    private val dens = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hit = 44f * dens
    private var mode = 0
    private var corner = 0
    private var lx = 0f
    private var ly = 0f

    fun setBitmap(b: Bitmap) {
        bmp = b
        crop.set(0f, 0f, b.width.toFloat(), b.height.toFloat())
        if (aspect > 0f) applyAspect()
        layoutDst(); invalidate()
    }

    fun setAspectRatio(a: Float) { aspect = a; if (a > 0f) applyAspect() else resetCrop(); invalidate() }

    private fun resetCrop() { bmp?.let { crop.set(0f, 0f, it.width.toFloat(), it.height.toFloat()) } }

    private fun applyAspect() {
        val b = bmp ?: return
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        var w = bw; var h = w / aspect
        if (h > bh) { h = bh; w = h * aspect }
        crop.set((bw - w) / 2f, (bh - h) / 2f, (bw + w) / 2f, (bh + h) / 2f)
    }

    fun cropRect(): Rect {
        val b = bmp ?: return Rect(0, 0, 1, 1)
        val l = crop.left.roundToInt().coerceIn(0, b.width - 1)
        val t = crop.top.roundToInt().coerceIn(0, b.height - 1)
        val r = crop.right.roundToInt().coerceIn(l + 1, b.width)
        val bt = crop.bottom.roundToInt().coerceIn(t + 1, b.height)
        return Rect(l, t, r, bt)
    }

    private fun layoutDst() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val pad = 16f * dens
        scale = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
        val w = b.width * scale; val h = b.height * scale
        dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); layoutDst() }

    private fun vx(x: Float) = dst.left + x * scale
    private fun vy(y: Float) = dst.top + y * scale

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        paint.colorFilter = filter
        c.drawBitmap(b, null, dst, paint)
        val r = RectF(vx(crop.left), vy(crop.top), vx(crop.right), vy(crop.bottom))
        c.drawRect(dst.left, dst.top, dst.right, r.top, dim)
        c.drawRect(dst.left, r.bottom, dst.right, dst.bottom, dim)
        c.drawRect(dst.left, r.top, r.left, r.bottom, dim)
        c.drawRect(r.right, r.top, dst.right, r.bottom, dim)
        c.drawRect(r, line)
        for (i in 1..2) {
            val x = r.left + r.width() * i / 3f
            val y = r.top + r.height() * i / 3f
            c.drawLine(x, r.top, x, r.bottom, grid)
            c.drawLine(r.left, y, r.right, y, grid)
        }
        val rad = 9f * dens
        c.drawCircle(r.left, r.top, rad, dot); c.drawCircle(r.right, r.top, rad, dot)
        c.drawCircle(r.left, r.bottom, rad, dot); c.drawCircle(r.right, r.bottom, rad, dot)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bmp ?: return false
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lx = e.x; ly = e.y
                val px = floatArrayOf(vx(crop.left), vx(crop.right), vx(crop.left), vx(crop.right))
                val py = floatArrayOf(vy(crop.top), vy(crop.top), vy(crop.bottom), vy(crop.bottom))
                var best = -1; var bd = hit
                for (i in 0..3) {
                    val d = hypot(e.x - px[i], e.y - py[i])
                    if (d < bd) { bd = d; best = i }
                }
                val inside = RectF(vx(crop.left), vy(crop.top), vx(crop.right), vy(crop.bottom)).contains(e.x, e.y)
                mode = if (best >= 0) { corner = best; 2 } else if (inside) 1 else 0
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == 1) {
                    val dx = ((e.x - lx) / scale).coerceIn(-crop.left, bw - crop.right)
                    val dy = ((e.y - ly) / scale).coerceIn(-crop.top, bh - crop.bottom)
                    crop.offset(dx, dy)
                } else if (mode == 2) {
                    val ax = if (corner == 0 || corner == 2) crop.right else crop.left
                    val ay = if (corner < 2) crop.bottom else crop.top
                    val px = ((e.x - dst.left) / scale).coerceIn(0f, bw)
                    val py = ((e.y - dst.top) / scale).coerceIn(0f, bh)
                    val minS = 48f
                    val sx = if (px >= ax) 1f else -1f
                    val sy = if (py >= ay) 1f else -1f
                    var w = abs(px - ax); var h = abs(py - ay)
                    if (aspect > 0f) {
                        w = max(w, h * aspect).coerceAtLeast(minS); h = w / aspect
                        val maxW = if (sx > 0) bw - ax else ax
                        val maxH = if (sy > 0) bh - ay else ay
                        if (w > maxW) { w = maxW; h = w / aspect }
                        if (h > maxH) { h = maxH; w = h * aspect }
                    } else {
                        w = w.coerceAtLeast(minS); h = h.coerceAtLeast(minS)
                    }
                    val x2 = (ax + sx * w).coerceIn(0f, bw)
                    val y2 = (ay + sy * h).coerceIn(0f, bh)
                    crop.set(min(ax, x2), min(ay, y2), max(ax, x2), max(ay, y2))
                }
                lx = e.x; ly = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }
}

/** Crop, rotate, flip, brightness, contrast, saturation and sharpen. Saves a copy next to the original. */
class ImageEditorActivity : AppCompatActivity() {
    private lateinit var src: File
    private lateinit var cropView: CropView
    private lateinit var status: TextView
    private lateinit var saveBtn: View
    private lateinit var aspectRow: LinearLayout
    private var aspectIdx = 0
    private var bri = 0
    private var con = 100
    private var sat = 100
    private var shp = 0
    private var filterIdx = 0
    private var vig = 0
    private var sizePct = 100
    private var quality = 95
    private lateinit var filterRow: LinearLayout
    private val filterNames = listOf("None", "B&W", "Sepia", "Vivid", "Cool", "Warm", "Fade", "Invert")
    private val aspects = listOf("Free" to 0f, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        src = File(intent.getStringExtra("path") ?: run { finish(); return })
        val bmp = load(src)
        if (bmp == null) { UiKit.toast(this, "Cannot open this image"); finish(); return }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        root.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 32), UiKit.dp(this, 8), UiKit.dp(this, 8))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val name = UiKit.text(this, "Edit · " + src.name, 15f, bold = true)
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(name, UiKit.lp(0, -2, 1f))
        saveBtn = UiKit.button(this, "Save copy") { save() }
        top.addView(saveBtn)
        top.addView(UiKit.iconBtn(this, "✕") { finish() })
        root.addView(top)

        cropView = CropView(this)
        root.addView(cropView, UiKit.lp(-1, 0, 1f))
        cropView.setBitmap(bmp)

        status = UiKit.text(this, "Drag the corners to crop. Edits are saved as a new file.", 12f, R.color.on_surface_muted)
        root.addView(status)

        val tools = LinearLayout(this)
        tools.gravity = Gravity.CENTER_VERTICAL
        aspectRow = LinearLayout(this)
        buildAspects()
        tools.addView(Design.chip(this, "⟳ Rotate", false) { transform(Matrix().apply { postRotate(90f) }) })
        tools.addView(Design.chip(this, "⇋ Flip", false) { transform(Matrix().apply { postScale(-1f, 1f) }) })
        tools.addView(Design.chip(this, "⇵ Flip V", false) { transform(Matrix().apply { postScale(1f, -1f) }) })
        tools.addView(aspectRow)
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(tools)
        root.addView(hs)

        filterRow = LinearLayout(this)
        buildFilters()
        val fhs = HorizontalScrollView(this)
        fhs.isHorizontalScrollBarEnabled = false
        fhs.addView(filterRow)
        root.addView(fhs)

        val sliders = LinearLayout(this)
        sliders.orientation = LinearLayout.VERTICAL
        sliders.addView(slider("Brightness", -100, 100, 0) { bri = it; refreshFilter() })
        sliders.addView(slider("Contrast", 50, 150, 100) { con = it; refreshFilter() })
        sliders.addView(slider("Saturation", 0, 200, 100) { sat = it; refreshFilter() })
        sliders.addView(slider("Sharpen", 0, 100, 0) { shp = it })
        sliders.addView(slider("Vignette", 0, 100, 0) { vig = it })
        sliders.addView(slider("Size %", 10, 100, 100) { sizePct = it })
        sliders.addView(slider("Quality", 50, 100, 95) { quality = it })
        val sv = ScrollView(this)
        sv.addView(sliders)
        root.addView(sv, UiKit.lp(-1, UiKit.dp(this, 210)))
        setContentView(root)
    }

    private fun buildAspects() {
        aspectRow.removeAllViews()
        aspects.forEachIndexed { i, (label, ratio) ->
            aspectRow.addView(Design.chip(this, label, i == aspectIdx) {
                aspectIdx = i; cropView.setAspectRatio(ratio); buildAspects()
            })
        }
    }

    private fun transform(m: Matrix) {
        val b = cropView.bmp ?: return
        cropView.setBitmap(Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true))
    }

    private fun slider(label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val t = UiKit.text(this, "$label $init", 13f, R.color.on_surface_muted)
        row.addView(t, UiKit.lp(UiKit.dp(this, 130), -2))
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = init - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { t.text = "$label ${p + min}"; onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, UiKit.lp(0, -2, 1f))
        return row
    }

    private fun buildFilters() {
        filterRow.removeAllViews()
        filterNames.forEachIndexed { i, n ->
            filterRow.addView(Design.chip(this, n, i == filterIdx) { filterIdx = i; buildFilters(); refreshFilter() })
        }
    }

    private fun filterMatrix(i: Int): ColorMatrix = when (i) {
        1 -> ColorMatrix().apply { setSaturation(0f) }
        2 -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        3 -> ColorMatrix().apply { setSaturation(1.6f) }
        4 -> ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.15f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        5 -> ColorMatrix(floatArrayOf(1.15f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.85f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        6 -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, 25f, 0f, 0.85f, 0f, 0f, 25f, 0f, 0f, 0.85f, 0f, 25f, 0f, 0f, 0f, 1f, 0f))
        7 -> ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
        else -> ColorMatrix()
    }

    private fun vignette(b: Bitmap, k: Float): Bitmap {
        val o = b.copy(Bitmap.Config.ARGB_8888, true)
        val r = hypot(o.width / 2f, o.height / 2f)
        val p = Paint()
        p.shader = android.graphics.RadialGradient(o.width / 2f, o.height / 2f, r,
            intArrayOf(0x00000000, 0x00000000, (k * 200).toInt() shl 24), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        Canvas(o).drawRect(0f, 0f, o.width.toFloat(), o.height.toFloat(), p)
        return o
    }

    private fun matrixFor(b: Int, c: Int, s: Int, f: Int = 0): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(s / 100f)
        val k = c / 100f
        val t = (1f - k) * 128f + b * 1.5f
        m.postConcat(ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, t,
            0f, k, 0f, 0f, t,
            0f, 0f, k, 0f, t,
            0f, 0f, 0f, 1f, 0f)))
        if (f > 0) m.postConcat(filterMatrix(f))
        return m
    }

    private fun refreshFilter() { cropView.filter = ColorMatrixColorFilter(matrixFor(bri, con, sat, filterIdx)) }

    private fun load(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > 3000) s *= 2
        var b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val ori = try { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
        val m = Matrix()
        when (ori) {
            6 -> m.postRotate(90f)
            3 -> m.postRotate(180f)
            8 -> m.postRotate(270f)
            2 -> m.postScale(-1f, 1f)
            4 -> m.postScale(1f, -1f)
        }
        if (!m.isIdentity) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        return b
    }

    private fun ch(c: Int, l: Int, r: Int, u: Int, d: Int, sh: Int, a: Float): Int {
        val v = ((c shr sh) and 255) * (1f + 4f * a) -
            a * (((l shr sh) and 255) + ((r shr sh) and 255) + ((u shr sh) and 255) + ((d shr sh) and 255))
        return v.toInt().coerceIn(0, 255)
    }

    private fun sharpen(src: Bitmap, a: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val c = px[i]; val l = px[i - 1]; val r = px[i + 1]; val u = px[i - w]; val d = px[i + w]
                out[i] = (c and 0xFF000000.toInt()) or (ch(c, l, r, u, d, 16, a) shl 16) or
                    (ch(c, l, r, u, d, 8, a) shl 8) or ch(c, l, r, u, d, 0, a)
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun save() {
        val b = cropView.bmp ?: return
        val r = cropView.cropRect()
        val b0 = bri; val c0 = con; val s0 = sat; val sh0 = shp
        val f0 = filterIdx; val v0 = vig; val z0 = sizePct; val q0 = quality
        val ext0 = src.extension.lowercase()
        val ext = if (ext0 == "png") "png" else if (ext0 == "jpg" || ext0 == "jpeg") ext0 else "jpg"
        saveBtn.isEnabled = false
        status.text = "Saving…"
        lifecycleScope.launch {
            val path: String? = withContext(Dispatchers.Default) {
                try {
                    val cropped = Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
                    val adj = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG)
                    p.colorFilter = ColorMatrixColorFilter(matrixFor(b0, c0, s0, f0))
                    Canvas(adj).drawBitmap(cropped, 0f, 0f, p)
                    var fin = if (sh0 > 0) sharpen(adj, sh0 / 100f * 1.2f) else adj
                    if (v0 > 0) fin = vignette(fin, v0 / 100f)
                    if (z0 < 100) fin = Bitmap.createScaledBitmap(fin, max(1, fin.width * z0 / 100), max(1, fin.height * z0 / 100), true)
                    val out = Editors.outFile(src, ext)
                    FileOutputStream(out).use {
                        fin.compress(if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, q0, it)
                    }
                    out.path
                } catch (t: Throwable) { null }
            }
            saveBtn.isEnabled = true
            if (path == null) {
                status.text = "Could not save (not enough memory or storage)"
            } else {
                MediaScannerConnection.scanFile(this@ImageEditorActivity, arrayOf(path), null, null)
                UiKit.toast(this@ImageEditorActivity, "Saved " + File(path).name)
                finish()
            }
        }
    }
}
