package com.crestpoint.datatracker.files.edit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Canvas for the editor: shows the photo, live colour filter, vignette, crop box, brush mask and draggable text. */
class SnapView(ctx: Context) : View(ctx) {
    enum class Mode { VIEW, CROP, TEXT, BRUSH }

    var mode = Mode.VIEW
    var bmp: Bitmap? = null
    var origBmp: Bitmap? = null
    var compare = false
        set(v) { field = v; invalidate() }
    var filter: ColorFilter? = null
    var vig = 0f
    var mask: Bitmap? = null
    var brushFilter: ColorFilter? = null
    var showMask = false
    var text = ""
    var textFx = 0.5f
    var textFy = 0.5f
    var textSize = 0.07f
    var textColor = Color.WHITE
    var aspect = 0f
    val crop = RectF()
    var onSwipe: ((Float, Float) -> Unit)? = null
    var onStroke: ((Float, Float, Boolean) -> Unit)? = null

    private val dst = RectF()
    private var scale = 1f
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dstIn = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val redMask = Paint().apply { color = 0x88FF2D55.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; setShadowLayer(6f, 0f, 2f, 0xAA000000.toInt()) }
    private val hit = 44f * d
    private var touchMode = 0
    private var corner = 0
    private var lx = 0f
    private var ly = 0f

    fun setBitmap(b: Bitmap) {
        bmp = b
        crop.set(0f, 0f, b.width.toFloat(), b.height.toFloat())
        if (aspect > 0f) applyAspect()
        layoutDst()
        invalidate()
    }

    fun resetState() {
        mode = Mode.VIEW; filter = null; vig = 0f; mask = null; brushFilter = null
        showMask = false; text = ""; aspect = 0f
        bmp?.let { crop.set(0f, 0f, it.width.toFloat(), it.height.toFloat()) }
        invalidate()
    }

    fun setAspectRatio(a: Float) {
        aspect = a
        if (a > 0f) applyAspect() else bmp?.let { crop.set(0f, 0f, it.width.toFloat(), it.height.toFloat()) }
        invalidate()
    }

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
        return Rect(l, t, crop.right.roundToInt().coerceIn(l + 1, b.width), crop.bottom.roundToInt().coerceIn(t + 1, b.height))
    }

    private fun layoutDst() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val pad = 10f * d
        scale = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
        val w = b.width * scale; val h = b.height * scale
        dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); layoutDst() }

    private fun vx(x: Float) = dst.left + x * scale
    private fun vy(y: Float) = dst.top + y * scale

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        if (compare) { origBmp?.let { c.drawBitmap(it, null, dst, paint) }; return }
        paint.colorFilter = filter
        c.drawBitmap(b, null, dst, paint)
        paint.colorFilter = null
        val m = mask
        if (mode == Mode.BRUSH && m != null) {
            val sc = c.saveLayer(dst, null)
            paint.colorFilter = brushFilter
            c.drawBitmap(b, null, dst, paint)
            paint.colorFilter = null
            c.drawBitmap(m, null, dst, dstIn)
            c.restoreToCount(sc)
            if (showMask) c.drawBitmap(m, null, dst, redMask)
        }
        if (vig > 0f) {
            val p = Paint()
            val r = hypot(dst.width() / 2f, dst.height() / 2f)
            p.shader = RadialGradient(dst.centerX(), dst.centerY(), r,
                intArrayOf(0x00000000, 0x00000000, ((vig * 230).toInt().coerceIn(0, 255) shl 24)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(dst, p)
        }
        if (mode == Mode.TEXT && text.isNotEmpty()) {
            textPaint.color = textColor
            textPaint.textSize = textSize * dst.height()
            val y = dst.top + textFy * dst.height() - (textPaint.ascent() + textPaint.descent()) / 2f
            c.drawText(text, dst.left + textFx * dst.width(), y, textPaint)
        }
        if (mode == Mode.CROP) {
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
            val rad = 9f * d
            c.drawCircle(r.left, r.top, rad, dot); c.drawCircle(r.right, r.top, rad, dot)
            c.drawCircle(r.left, r.bottom, rad, dot); c.drawCircle(r.right, r.bottom, rad, dot)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val b = bmp ?: return false
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        parent?.requestDisallowInterceptTouchEvent(true)
        when (mode) {
            Mode.VIEW -> {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y }
                    MotionEvent.ACTION_MOVE -> { onSwipe?.invoke(e.x - lx, e.y - ly); lx = e.x; ly = e.y }
                }
                return true
            }
            Mode.TEXT -> {
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
                    textFx = ((e.x - dst.left) / dst.width()).coerceIn(0f, 1f)
                    textFy = ((e.y - dst.top) / dst.height()).coerceIn(0f, 1f)
                    invalidate()
                }
                return true
            }
            Mode.BRUSH -> {
                val bx = ((e.x - dst.left) / scale).coerceIn(0f, bw)
                val by = ((e.y - dst.top) / scale).coerceIn(0f, bh)
                if (e.actionMasked == MotionEvent.ACTION_DOWN) onStroke?.invoke(bx, by, true)
                else if (e.actionMasked == MotionEvent.ACTION_MOVE) onStroke?.invoke(bx, by, false)
                invalidate()
                return true
            }
            Mode.CROP -> {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lx = e.x; ly = e.y
                        val px = floatArrayOf(vx(crop.left), vx(crop.right), vx(crop.left), vx(crop.right))
                        val py = floatArrayOf(vy(crop.top), vy(crop.top), vy(crop.bottom), vy(crop.bottom))
                        var best = -1; var bd = hit
                        for (i in 0..3) {
                            val dd = hypot(e.x - px[i], e.y - py[i])
                            if (dd < bd) { bd = dd; best = i }
                        }
                        val inside = RectF(vx(crop.left), vy(crop.top), vx(crop.right), vy(crop.bottom)).contains(e.x, e.y)
                        touchMode = if (best >= 0) { corner = best; 2 } else if (inside) 1 else 0
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (touchMode == 1) {
                            val dx = ((e.x - lx) / scale).coerceIn(-crop.left, bw - crop.right)
                            val dy = ((e.y - ly) / scale).coerceIn(-crop.top, bh - crop.bottom)
                            crop.offset(dx, dy)
                        } else if (touchMode == 2) {
                            val ax = if (corner == 0 || corner == 2) crop.right else crop.left
                            val ay = if (corner < 2) crop.bottom else crop.top
                            val px = ((e.x - dst.left) / scale).coerceIn(0f, bw)
                            val py = ((e.y - dst.top) / scale).coerceIn(0f, bh)
                            val sx = if (px >= ax) 1f else -1f
                            val sy = if (py >= ay) 1f else -1f
                            var w = abs(px - ax); var h = abs(py - ay)
                            if (aspect > 0f) {
                                w = max(w, h * aspect).coerceAtLeast(48f); h = w / aspect
                                val maxW = if (sx > 0) bw - ax else ax
                                val maxH = if (sy > 0) bh - ay else ay
                                if (w > maxW) { w = maxW; h = w / aspect }
                                if (h > maxH) { h = maxH; w = h * aspect }
                            } else { w = w.coerceAtLeast(48f); h = h.coerceAtLeast(48f) }
                            val x2 = (ax + sx * w).coerceIn(0f, bw)
                            val y2 = (ay + sy * h).coerceIn(0f, bh)
                            crop.set(min(ax, x2), min(ay, y2), max(ax, x2), max(ay, y2))
                        }
                        lx = e.x; ly = e.y
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchMode = 0
                }
                return true
            }
        }
        return true
    }
}

/** Snapseed-style editor: tools you apply one at a time, undo/redo, compare, swipe-to-tune. */
class SnapEditorActivity : AppCompatActivity() {
    private enum class Tool { NONE, TUNE, DETAILS, CROP, LOOKS, BRUSH, TEXT }

    private lateinit var src: File
    private lateinit var view: SnapView
    private lateinit var area: FrameLayout
    private lateinit var actionBar: LinearLayout
    private lateinit var actionTitle: TextView
    private lateinit var hud: TextView
    private lateinit var busy: FrameLayout
    private lateinit var undoBtn: ImageView
    private lateinit var redoBtn: ImageView
    private lateinit var orig: Bitmap
    private val hist = ArrayList<Bitmap>()
    private val redo = ArrayList<Bitmap>()
    private var tool = Tool.NONE
    private val h = Handler(Looper.getMainLooper())
    private var gen = 0

    // tool parameters
    private val names = listOf("Brightness", "Contrast", "Saturation", "Warmth", "Fade", "Vignette", "Sharpen", "Structure")
    private val p = IntArray(8)
    private var sel = 0
    private var accX = 0f
    private var accY = 0f
    private var seekRef: SeekBar? = null
    private var lookIdx = 0
    private var lookK = 100
    private var aspectIdx = 0
    private var brushExp = 30
    private var brushSat = 0
    private var brushSize = 8
    private var erase = false
    private var textColorIdx = 0
    private var maskCanvas: Canvas? = null
    private var lastBx = 0f
    private var lastBy = 0f
    private val aspects = listOf("Free" to 0f, "1:1" to 1f, "4:5" to 4f / 5f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)
    private val looks = listOf("Original", "Vivid", "Warm", "Cool", "B&W", "Noir", "Sepia", "Fade", "Vintage", "Dramatic", "Matte")
    private val textColors = listOf("White" to Color.WHITE, "Black" to Color.BLACK, "Yellow" to 0xFFFFD60A.toInt(), "Red" to 0xFFFF453A.toInt())
    private val renderRun = Runnable { renderDetailsPreview() }

    private val cur: Bitmap get() = hist.last()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        src = File(intent.getStringExtra("path") ?: run { finish(); return })
        val b = load(src)
        if (b == null) { UiKit.toast(this, "Cannot open this image"); finish(); return }
        orig = b
        hist.add(b)

        val accent = UiKit.color(this, R.color.brand_purple)
        val rootF = FrameLayout(this)
        rootF.setBackgroundColor(0xFF0B0B14.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 26), UiKit.dp(this, 8), UiKit.dp(this, 8))

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Glass.iconBtn(this, Glass.CLOSE) { handleBack() })
        val title = UiKit.text(this, src.name, 14f, R.color.on_surface_muted)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(title, UiKit.lp(0, -2, 1f))
        undoBtn = Glass.iconBtn(this, UNDO) { undo() }
        redoBtn = Glass.iconBtn(this, REDO) { redoStep() }
        top.addView(undoBtn)
        top.addView(redoBtn)
        val cmp = Glass.iconBtn(this, COMPARE) {}
        cmp.setOnClickListener(null)
        cmp.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) view.compare = true
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) view.compare = false
            true
        }
        top.addView(cmp)
        val save = UiKit.button(this, "Save") { save() }
        save.backgroundTintList = ColorStateList.valueOf(accent)
        top.addView(save)
        col.addView(top)

        view = SnapView(this)
        view.origBmp = orig
        view.onSwipe = { dx, dy -> onSwipe(dx, dy) }
        view.onStroke = { x, y, first -> stroke(x, y, first) }
        col.addView(view, UiKit.lp(-1, 0, 1f))
        view.setBitmap(orig)

        area = FrameLayout(this)
        col.addView(area, UiKit.lp(-1, UiKit.dp(this, 170)))

        actionBar = LinearLayout(this)
        actionBar.gravity = Gravity.CENTER_VERTICAL
        actionBar.addView(Glass.iconBtn(this, Glass.CLOSE) { cancelTool() })
        actionTitle = UiKit.text(this, "", 15f, bold = true)
        actionTitle.gravity = Gravity.CENTER
        actionBar.addView(actionTitle, UiKit.lp(0, -2, 1f))
        actionBar.addView(Glass.iconBtn(this, CHECK, 44, accent) { applyTool() })
        actionBar.visibility = View.GONE
        col.addView(actionBar)
        rootF.addView(col, FrameLayout.LayoutParams(-1, -1))

        hud = UiKit.text(this, "", 16f, bold = true)
        hud.background = Glass.pill(0xAA000000.toInt(), 14, this)
        hud.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8), UiKit.dp(this, 16), UiKit.dp(this, 8))
        hud.visibility = View.GONE
        val hl = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hl.topMargin = UiKit.dp(this, 110)
        rootF.addView(hud, hl)

        busy = FrameLayout(this)
        busy.setBackgroundColor(0x99000000.toInt())
        busy.isClickable = true
        busy.visibility = View.GONE
        busy.addView(UiKit.text(this, "Working…", 16f, bold = true), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        rootF.addView(busy, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootF)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        showStrip()
        refreshHistoryButtons()
    }

    // ------------------------------------------------------------ loading and saving
    private fun load(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > 2560) s *= 2
        var b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val ori = try { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
        val m = Matrix()
        when (ori) { 6 -> m.postRotate(90f); 3 -> m.postRotate(180f); 8 -> m.postRotate(270f); 2 -> m.postScale(-1f, 1f); 4 -> m.postScale(1f, -1f) }
        if (!m.isIdentity) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        return b
    }

    private fun save() {
        if (tool != Tool.NONE) { UiKit.toast(this, "Tap ✓ or ✕ on the tool first"); return }
        val b = cur
        val e0 = src.extension.lowercase()
        val ext = if (e0 == "png") "png" else if (e0 == "jpg" || e0 == "jpeg") e0 else "jpg"
        busy.visibility = View.VISIBLE
        Thread {
            val out = Editors.outFile(src, ext)
            val ok = try {
                FileOutputStream(out).use { b.compress(if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
                true
            } catch (_: Throwable) { false }
            runOnUiThread {
                busy.visibility = View.GONE
                if (ok) {
                    MediaScannerConnection.scanFile(this, arrayOf(out.path), null, null)
                    UiKit.toast(this, "Saved " + out.name)
                    finish()
                } else UiKit.toast(this, "Could not save")
            }
        }.start()
    }

    private fun handleBack() {
        if (tool != Tool.NONE) { cancelTool(); return }
        if (hist.size > 1) {
            MaterialAlertDialogBuilder(this).setTitle("Discard edits?")
                .setPositiveButton("Discard") { _, _ -> finish() }
                .setNegativeButton("Keep editing", null).show()
        } else finish()
    }

    // ------------------------------------------------------------ history
    private fun push(b: Bitmap) {
        hist.add(b)
        redo.clear()
        while (hist.size > 6) hist.removeAt(0)
        view.setBitmap(cur)
        refreshHistoryButtons()
    }

    private fun undo() {
        if (tool != Tool.NONE || hist.size < 2) return
        redo.add(hist.removeAt(hist.size - 1))
        view.setBitmap(cur)
        refreshHistoryButtons()
    }

    private fun redoStep() {
        if (tool != Tool.NONE || redo.isEmpty()) return
        hist.add(redo.removeAt(redo.size - 1))
        view.setBitmap(cur)
        refreshHistoryButtons()
    }

    private fun refreshHistoryButtons() {
        undoBtn.alpha = if (hist.size > 1) 1f else 0.3f
        redoBtn.alpha = if (redo.isNotEmpty()) 1f else 0.3f
    }

    // ------------------------------------------------------------ tool strip
    private fun showStrip() {
        tool = Tool.NONE
        actionBar.visibility = View.GONE
        area.removeAllViews()
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4), 0)
        row.addView(tile("Tune", TUNE) { startTool(Tool.TUNE) })
        row.addView(tile("Details", DETAILS) { startTool(Tool.DETAILS) })
        row.addView(tile("Crop", CROP) { startTool(Tool.CROP) })
        row.addView(tile("Looks", LOOKS) { startTool(Tool.LOOKS) })
        row.addView(tile("Brush", BRUSH) { startTool(Tool.BRUSH) })
        row.addView(tile("Text", TEXTI) { startTool(Tool.TEXT) })
        row.addView(tile("Rotate", Glass.ROTATE) { quickRotate() })
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        area.addView(hs)
    }

    private fun tile(label: String, icon: String, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.background = Glass.pill(0xFF1A1B2E.toInt(), 16, this)
        val pad = UiKit.dp(this, 12)
        box.setPadding(pad, pad, pad, pad)
        val iv = ImageView(this)
        iv.setImageDrawable(PathIcon(icon, Color.WHITE))
        box.addView(iv, LinearLayout.LayoutParams(UiKit.dp(this, 30), UiKit.dp(this, 30)))
        val t = UiKit.text(this, label, 12f, R.color.on_surface_muted)
        t.gravity = Gravity.CENTER
        box.addView(t)
        box.setOnClickListener { onClick() }
        Design.ripple(box)
        val lp = LinearLayout.LayoutParams(UiKit.dp(this, 78), UiKit.dp(this, 84))
        lp.setMargins(UiKit.dp(this, 5), 0, UiKit.dp(this, 5), 0)
        box.layoutParams = lp
        return box
    }

    private fun quickRotate() {
        val m = Matrix().apply { postRotate(90f) }
        push(Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, m, true))
    }

    // ------------------------------------------------------------ tools
    private fun startTool(t: Tool) {
        tool = t
        java.util.Arrays.fill(p, 0)
        lookIdx = 0; lookK = 100; aspectIdx = 0
        sel = if (t == Tool.DETAILS) 6 else 0
        view.resetState()
        view.setBitmap(cur)
        view.mode = when (t) {
            Tool.CROP -> SnapView.Mode.CROP
            Tool.TEXT -> SnapView.Mode.TEXT
            Tool.BRUSH -> SnapView.Mode.BRUSH
            else -> SnapView.Mode.VIEW
        }
        if (t == Tool.BRUSH) {
            val m = Bitmap.createBitmap(cur.width, cur.height, Bitmap.Config.ALPHA_8)
            view.mask = m
            maskCanvas = Canvas(m)
            updateBrushFilter()
        }
        if (t == Tool.TEXT) { view.text = ""; view.textColor = textColors[textColorIdx].second }
        actionTitle.text = when (t) {
            Tool.TUNE -> "Tune image"; Tool.DETAILS -> "Details"; Tool.CROP -> "Crop"; Tool.LOOKS -> "Looks"
            Tool.BRUSH -> "Brush"; Tool.TEXT -> "Text"; else -> ""
        }
        actionBar.visibility = View.VISIBLE
        buildPanel()
    }

    private fun cancelTool() {
        h.removeCallbacks(renderRun)
        gen++
        view.resetState()
        view.setBitmap(cur)
        showStrip()
    }

    private fun buildPanel() {
        area.removeAllViews()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 4), UiKit.dp(this, 6), 0)
        when (tool) {
            Tool.TUNE -> paramPanel(box, listOf(0, 1, 2, 3, 4, 5))
            Tool.DETAILS -> paramPanel(box, listOf(6, 7))
            Tool.CROP -> {
                val rs = aspects.mapIndexed { i, a -> Design.chip(this, a.first, i == aspectIdx) { aspectIdx = i; view.setAspectRatio(a.second); buildPanel() } }
                box.addView(chipRow(*rs.toTypedArray()))
                box.addView(chipRow(
                    Design.chip(this, "Rotate", false) { transformBase(Matrix().apply { postRotate(90f) }) },
                    Design.chip(this, "Flip H", false) { transformBase(Matrix().apply { postScale(-1f, 1f) }) },
                    Design.chip(this, "Flip V", false) { transformBase(Matrix().apply { postScale(1f, -1f) }) }
                ))
                box.addView(hint("Drag the corners or the box"))
            }
            Tool.LOOKS -> looksPanel(box)
            Tool.BRUSH -> brushPanel(box)
            Tool.TEXT -> textPanel(box)
            else -> {}
        }
        area.addView(box)
    }

    private fun hint(s: String): TextView = UiKit.text(this, s, 12f, R.color.on_surface_muted)

    private fun chipRow(vararg chips: View): View {
        val r = LinearLayout(this)
        chips.forEach { r.addView(it) }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(r)
        return hs
    }

    private fun seekRow(label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val t = UiKit.text(this, "$label $init", 13f, R.color.on_surface_muted)
        row.addView(t, UiKit.lp(UiKit.dp(this, 118), -2))
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = init - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) { t.text = "$label ${pr + min}"; onChange(pr + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, UiKit.lp(0, -2, 1f))
        return row
    }

    // ---- Tune / Details: parameter chips + slider + swipe gestures
    private fun paramPanel(box: LinearLayout, ids: List<Int>) {
        val chips = ids.map { id -> Design.chip(this, names[id] + if (p[id] != 0) " •" else "", id == sel) { sel = id; buildPanel() } }
        box.addView(chipRow(*chips.toTypedArray()))
        val lo = if (sel == 5 || sel == 6 || sel == 7) 0 else -100
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val t = UiKit.text(this, "${names[sel]} ${p[sel]}", 13f, R.color.on_surface_muted)
        row.addView(t, UiKit.lp(UiKit.dp(this, 130), -2))
        val sb = SeekBar(this)
        sb.max = 100 - lo
        sb.progress = p[sel] - lo
        seekRef = sb
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, u: Boolean) {
                p[sel] = pr + lo
                t.text = "${names[sel]} ${p[sel]}"
                livePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, UiKit.lp(0, -2, 1f))
        box.addView(row)
        box.addView(hint("Tip: swipe up/down on the photo to pick a setting, left/right to change it"))
    }

    private fun onSwipe(dx: Float, dy: Float) {
        if (tool != Tool.TUNE && tool != Tool.DETAILS) return
        val ids = if (tool == Tool.TUNE) listOf(0, 1, 2, 3, 4, 5) else listOf(6, 7)
        if (abs(dy) > abs(dx) * 1.2f) {
            accY += dy
            if (abs(accY) > 70f * resources.displayMetrics.density) {
                val i = ids.indexOf(sel).coerceAtLeast(0)
                sel = ids[(i + (if (accY > 0) 1 else -1) + ids.size) % ids.size]
                accY = 0f
                buildPanel()
                showHud("${names[sel]} ${p[sel]}")
            }
        } else {
            accX += dx
            val step = (accX / (view.width / 120f)).toInt()
            if (step != 0) {
                accX -= step * (view.width / 120f)
                seekRef?.let { sb -> sb.progress = (sb.progress + step).coerceIn(0, sb.max) }
                showHud("${names[sel]} ${p[sel]}")
            }
        }
    }

    private fun showHud(s: String) {
        hud.text = s
        hud.visibility = View.VISIBLE
        h.removeCallbacks(hideHud)
        h.postDelayed(hideHud, 700)
    }

    private val hideHud = Runnable { hud.visibility = View.GONE }

    private fun tuneMatrix(): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(1f + p[2] / 100f)
        val k = 1f + p[1] / 100f
        val fadeK = 1f - p[4] / 260f
        val kk = k * fadeK
        val off = (1f - k) * 128f * fadeK + p[0] * 1.5f + p[4] * 0.55f
        m.postConcat(ColorMatrix(floatArrayOf(kk, 0f, 0f, 0f, off, 0f, kk, 0f, 0f, off, 0f, 0f, kk, 0f, off, 0f, 0f, 0f, 1f, 0f)))
        val w = p[3] / 300f
        m.postConcat(ColorMatrix(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        return m
    }

    private fun livePreview() {
        if (tool == Tool.TUNE) {
            view.filter = ColorMatrixColorFilter(tuneMatrix())
            view.vig = p[5] / 100f
            view.invalidate()
        } else if (tool == Tool.DETAILS) {
            h.removeCallbacks(renderRun)
            h.postDelayed(renderRun, 300)
        }
    }

    private fun renderDetailsPreview() {
        if (tool != Tool.DETAILS) return
        val my = ++gen
        val sh = p[6]; val st = p[7]
        if (sh == 0 && st == 0) { view.setBitmap(cur); return }
        val base = cur
        Thread {
            val r = try { detail(base, sh / 100f, st / 100f * 1.5f) } catch (_: Throwable) { null }
            runOnUiThread { if (r != null && my == gen && tool == Tool.DETAILS) view.setBitmap(r) }
        }.start()
    }

    // ---- Looks
    private fun lookMatrix(i: Int): ColorMatrix {
        val m = ColorMatrix()
        fun con(k: Float, off: Float) = ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, off, 0f, k, 0f, 0f, off, 0f, 0f, k, 0f, off, 0f, 0f, 0f, 1f, 0f))
        val sepia = ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        when (i) {
            1 -> { m.setSaturation(1.6f); m.postConcat(con(1.1f, -6f)) }
            2 -> m.set(floatArrayOf(1.15f, 0f, 0f, 0f, 5f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.82f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            3 -> m.set(floatArrayOf(0.88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.18f, 0f, 5f, 0f, 0f, 0f, 1f, 0f))
            4 -> m.setSaturation(0f)
            5 -> { m.setSaturation(0f); m.postConcat(con(1.45f, -40f)) }
            6 -> m.set(sepia)
            7 -> m.set(con(0.85f, 28f))
            8 -> { m.set(sepia); m.postConcat(con(0.9f, 14f)) }
            9 -> { m.setSaturation(0.75f); m.postConcat(con(1.4f, -45f)) }
            10 -> { m.setSaturation(0.8f); m.postConcat(con(0.88f, 18f)) }
        }
        return m
    }

    private fun lerpMatrix(m: ColorMatrix, k: Float): ColorMatrix {
        val id = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        val a = m.array
        return ColorMatrix(FloatArray(20) { id[it] + (a[it] - id[it]) * k })
    }

    private fun looksPanel(box: LinearLayout) {
        val small = Bitmap.createScaledBitmap(cur, 96, max(48, 96 * cur.height / cur.width), true)
        val row = LinearLayout(this)
        looks.forEachIndexed { i, n ->
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            if (i == lookIdx) tile.background = Glass.pill(UiKit.color(this, R.color.brand_purple), 10, this)
            val iv = ImageView(this)
            iv.setImageBitmap(small)
            iv.colorFilter = ColorMatrixColorFilter(lookMatrix(i))
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            tile.addView(iv, LinearLayout.LayoutParams(UiKit.dp(this, 70), UiKit.dp(this, 70)))
            val t = UiKit.text(this, n, 11f)
            t.gravity = Gravity.CENTER
            tile.addView(t)
            tile.setPadding(UiKit.dp(this, 3), UiKit.dp(this, 3), UiKit.dp(this, 3), UiKit.dp(this, 3))
            tile.setOnClickListener { lookIdx = i; applyLookPreview(); buildPanel() }
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.setMargins(UiKit.dp(this, 4), 0, UiKit.dp(this, 4), 0)
            row.addView(tile, lp)
        }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        box.addView(hs)
        box.addView(seekRow("Strength", 0, 100, lookK) { lookK = it; applyLookPreview() })
    }

    private fun applyLookPreview() {
        view.filter = ColorMatrixColorFilter(lerpMatrix(lookMatrix(lookIdx), lookK / 100f))
        view.invalidate()
    }

    // ---- Crop helpers
    private fun transformBase(m: Matrix) {
        val nb = Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, m, true)
        hist[hist.size - 1] = nb
        view.setBitmap(nb)
    }

    // ---- Brush
    private fun updateBrushFilter() {
        val m = ColorMatrix()
        m.setSaturation(1f + brushSat / 100f)
        val o = brushExp * 1.6f
        m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, o, 0f, 1f, 0f, 0f, o, 0f, 0f, 1f, 0f, o, 0f, 0f, 0f, 1f, 0f)))
        view.brushFilter = ColorMatrixColorFilter(m)
        view.invalidate()
    }

    private fun brushPanel(box: LinearLayout) {
        box.addView(chipRow(
            Design.chip(this, "Paint", !erase) { erase = false; buildPanel() },
            Design.chip(this, "Erase", erase) { erase = true; buildPanel() },
            Design.chip(this, "Show mask", view.showMask) { view.showMask = !view.showMask; view.invalidate(); buildPanel() },
            Design.chip(this, "Clear", false) { maskCanvas?.drawColor(0, PorterDuff.Mode.CLEAR); view.invalidate() }
        ))
        box.addView(seekRow("Exposure", -100, 100, brushExp) { brushExp = it; updateBrushFilter() })
        box.addView(seekRow("Saturation", -100, 100, brushSat) { brushSat = it; updateBrushFilter() })
        box.addView(seekRow("Brush size", 1, 30, brushSize) { brushSize = it })
    }

    private fun stroke(x: Float, y: Float, first: Boolean) {
        val c = maskCanvas ?: return
        val r = max(4f, brushSize / 100f * min(cur.width, cur.height))
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        pt.color = Color.BLACK
        pt.maskFilter = BlurMaskFilter(r * 0.45f, BlurMaskFilter.Blur.NORMAL)
        if (erase) pt.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        if (first) {
            c.drawCircle(x, y, r, pt)
        } else {
            pt.style = Paint.Style.STROKE
            pt.strokeWidth = r * 2f
            pt.strokeCap = Paint.Cap.ROUND
            c.drawLine(lastBx, lastBy, x, y, pt)
        }
        lastBx = x; lastBy = y
    }

    // ---- Text
    private fun textPanel(box: LinearLayout) {
        val cs = textColors.mapIndexed { i, c -> Design.chip(this, c.first, i == textColorIdx) { textColorIdx = i; view.textColor = c.second; view.invalidate(); buildPanel() } }
        box.addView(chipRow(Design.chip(this, if (view.text.isEmpty()) "Add text" else "Edit text", view.text.isNotEmpty()) { askText() }, *cs.toTypedArray()))
        box.addView(seekRow("Size", 2, 20, (view.textSize * 100).toInt()) { view.textSize = it / 100f; view.invalidate() })
        box.addView(hint("Drag the text on the photo to place it"))
    }

    private fun askText() {
        val et = EditText(this)
        et.setText(view.text)
        et.hint = "Your text"
        MaterialAlertDialogBuilder(this).setTitle("Text")
            .setView(et)
            .setPositiveButton("OK") { _, _ -> view.text = et.text.toString(); view.invalidate(); buildPanel() }
            .setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------------ commit
    private fun applyTool() {
        val t = tool
        val base = cur
        val snapshot = IntArray(8) { p[it] }
        val lm = lerpMatrix(lookMatrix(lookIdx), lookK / 100f)
        val cr = view.cropRect()
        val txt = view.text
        val tfx = view.textFx; val tfy = view.textFy; val tsz = view.textSize; val tcol = view.textColor
        val bf = view.brushFilter
        val mask = view.mask
        if (t == Tool.DETAILS) { h.removeCallbacks(renderRun); gen++ }
        busy.visibility = View.VISIBLE
        Thread {
            val res: Bitmap? = try {
                when (t) {
                    Tool.TUNE -> {
                        var o = drawWith(base, ColorMatrixColorFilter(tuneMatrix()))
                        if (snapshot[5] > 0) vignette(o, snapshot[5] / 100f)
                        o
                    }
                    Tool.DETAILS -> if (snapshot[6] == 0 && snapshot[7] == 0) null else detail(base, snapshot[6] / 100f, snapshot[7] / 100f * 1.5f)
                    Tool.LOOKS -> if (lookIdx == 0) null else drawWith(base, ColorMatrixColorFilter(lm))
                    Tool.CROP -> if (cr.width() >= base.width && cr.height() >= base.height) null
                        else Bitmap.createBitmap(base, cr.left, cr.top, cr.width(), cr.height())
                    Tool.BRUSH -> if (mask == null) null else {
                        val o = base.copy(Bitmap.Config.ARGB_8888, true)
                        val c = Canvas(o)
                        val sc = c.saveLayer(0f, 0f, o.width.toFloat(), o.height.toFloat(), null)
                        val pt = Paint(Paint.FILTER_BITMAP_FLAG)
                        pt.colorFilter = bf
                        c.drawBitmap(base, 0f, 0f, pt)
                        val di = Paint()
                        di.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                        c.drawBitmap(mask, 0f, 0f, di)
                        c.restoreToCount(sc)
                        o
                    }
                    Tool.TEXT -> if (txt.isEmpty()) null else {
                        val o = base.copy(Bitmap.Config.ARGB_8888, true)
                        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
                        pt.color = tcol
                        pt.textAlign = Paint.Align.CENTER
                        pt.textSize = tsz * o.height
                        pt.setShadowLayer(pt.textSize / 10f, 0f, pt.textSize / 20f, 0xAA000000.toInt())
                        Canvas(o).drawText(txt, tfx * o.width, tfy * o.height - (pt.ascent() + pt.descent()) / 2f, pt)
                        o
                    }
                    else -> null
                }
            } catch (_: Throwable) { null }
            runOnUiThread {
                busy.visibility = View.GONE
                view.resetState()
                if (res != null) push(res) else view.setBitmap(cur)
                showStrip()
            }
        }.start()
    }

    private fun drawWith(src: Bitmap, f: ColorFilter): Bitmap {
        val o = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val pt = Paint(Paint.FILTER_BITMAP_FLAG)
        pt.colorFilter = f
        Canvas(o).drawBitmap(src, 0f, 0f, pt)
        return o
    }

    private fun vignette(o: Bitmap, k: Float) {
        val pt = Paint()
        val r = hypot(o.width / 2f, o.height / 2f)
        pt.shader = RadialGradient(o.width / 2f, o.height / 2f, r,
            intArrayOf(0x00000000, 0x00000000, ((k * 230).toInt().coerceIn(0, 255) shl 24)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        Canvas(o).drawRect(0f, 0f, o.width.toFloat(), o.height.toFloat(), pt)
    }

    private fun shrink(src: Bitmap, factor: Int): Bitmap {
        var t = src
        val target = max(8, src.width / factor)
        while (t.width / 2 >= target) t = Bitmap.createScaledBitmap(t, t.width / 2, max(1, t.height / 2), true)
        return t
    }

    /** Sharpen (edge boost) and structure (local contrast: photo minus a heavily blurred copy). */
    private fun detail(src: Bitmap, sharp: Float, struct: Float): Bitmap {
        val w = src.width; val hh = src.height
        val px = IntArray(w * hh)
        src.getPixels(px, 0, w, 0, 0, w, hh)
        var blur: IntArray? = null
        if (struct > 0f) {
            val big = Bitmap.createScaledBitmap(shrink(src, 12), w, hh, true)
            blur = IntArray(w * hh)
            big.getPixels(blur, 0, w, 0, 0, w, hh)
        }
        val out = IntArray(w * hh)
        for (y in 0 until hh) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                var r = (c shr 16) and 255
                var g = (c shr 8) and 255
                var b = c and 255
                val r0 = r; val g0 = g; val b0 = b
                if (blur != null) {
                    val bl = blur[i]
                    r += ((r0 - ((bl shr 16) and 255)) * struct).toInt()
                    g += ((g0 - ((bl shr 8) and 255)) * struct).toInt()
                    b += ((b0 - (bl and 255)) * struct).toInt()
                }
                if (sharp > 0f && x > 0 && y > 0 && x < w - 1 && y < hh - 1) {
                    val l = px[i - 1]; val rt = px[i + 1]; val u = px[i - w]; val d = px[i + w]
                    r += ((4 * r0 - ((l shr 16) and 255) - ((rt shr 16) and 255) - ((u shr 16) and 255) - ((d shr 16) and 255)) * sharp).toInt()
                    g += ((4 * g0 - ((l shr 8) and 255) - ((rt shr 8) and 255) - ((u shr 8) and 255) - ((d shr 8) and 255)) * sharp).toInt()
                    b += ((4 * b0 - (l and 255) - (rt and 255) - (u and 255) - (d and 255)) * sharp).toInt()
                }
                out[i] = (c and 0xFF000000.toInt()) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
            }
        }
        val res = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, hh)
        return res
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val UNDO = "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z"
        const val REDO = "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z"
        const val COMPARE = "M10,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h5v2h2V1h-2v2zM10,18H5l5,-6v6zM19,3h-5v2h5v13l-5,-6v9h5c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z"
        const val CHECK = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
        const val TUNE = "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
        const val DETAILS = "M12,2L1,21h22L12,2zM12,6l7.53,13H4.47L12,6z"
        const val CROP = "M17,15h2V7c0,-1.1 -0.9,-2 -2,-2H9v2h8v8zM7,17V1H5v4H1v2h4v10c0,1.1 0.9,2 2,2h10v4h2v-4h4v-2H7z"
        const val LOOKS = "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15zM11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5zM9.99,12.99L9,15.17l-0.99,-2.18L5.83,12l2.18,-0.99L9,8.83l0.99,2.18L12.17,12l-2.18,0.99z"
        const val BRUSH = "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z"
        const val TEXTI = "M2.5,4v3h5v12h3V7h5V4h-13zM21.5,9h-9v3h3v7h3v-7h3V9z"
    }
}
