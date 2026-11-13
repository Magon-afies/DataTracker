package com.crestpoint.datatracker.files.viewers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class ZoomImageView(ctx: Context) : AppCompatImageView(ctx) {
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    private val m = Matrix()
    private val vals = FloatArray(9)
    private var baseScale = 1f

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = currentScale()
            val target = (cur * d.scaleFactor).coerceIn(baseScale, baseScale * 6f)
            val real = target / cur
            m.postScale(real, real, d.focusX, d.focusY)
            fix(); imageMatrix = m
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale() > baseScale * 1.05f) fit() else {
                val real = 2.5f
                m.postScale(real, real, e.x, e.y); fix(); imageMatrix = m
            }
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (currentScale() > baseScale * 1.05f) { m.postTranslate(-dx, -dy); fix(); imageMatrix = m }
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (currentScale() <= baseScale * 1.05f && abs(vx) > 1200 && abs(vx) > abs(vy)) {
                onSwipe?.invoke(if (vx < 0) 1 else -1); return true
            }
            return false
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        if (!scaleDetector.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); fit() }

    fun show(b: Bitmap) { setImageBitmap(b); fit() }

    fun fit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
        baseScale = min(width / dw, height / dh)
        m.reset()
        m.postScale(baseScale, baseScale)
        m.postTranslate((width - dw * baseScale) / 2f, (height - dh * baseScale) / 2f)
        imageMatrix = m
    }

    private fun currentScale(): Float { m.getValues(vals); return vals[Matrix.MSCALE_X] }

    private fun fix() {
        val d = drawable ?: return
        m.getValues(vals)
        val s = vals[Matrix.MSCALE_X]; val tx = vals[Matrix.MTRANS_X]; val ty = vals[Matrix.MTRANS_Y]
        val w = d.intrinsicWidth * s; val h = d.intrinsicHeight * s
        var dx = 0f; var dy = 0f
        if (w <= width) dx = (width - w) / 2f - tx else if (tx > 0) dx = -tx else if (tx + w < width) dx = width - (tx + w)
        if (h <= height) dy = (height - h) / 2f - ty else if (ty > 0) dy = -ty else if (ty + h < height) dy = height - (ty + h)
        m.postTranslate(dx, dy)
    }
}

/** Full-screen image viewer: swipe between photos, pinch/double-tap zoom, rotate, details, wallpaper, slideshow. */
class ImageViewerActivity : AppCompatActivity() {
    private var paths: List<String> = emptyList()
    private var index = 0
    private lateinit var zoom: ZoomImageView
    private lateinit var top: LinearLayout
    private lateinit var caption: TextView
    private var bitmap: Bitmap? = null
    private var loadJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private var slideshow = false
    private val slideTick = object : Runnable {
        override fun run() {
            if (!slideshow) return
            go(1)
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = intent.getStringArrayListExtra("paths") ?: emptyList()
        index = intent.getIntExtra("index", 0)
        if (paths.isEmpty()) { finish(); return }

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        zoom = ZoomImageView(this)
        zoom.onSwipe = { dir -> go(dir) }
        zoom.onTap = { top.visibility = if (top.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        root.addView(zoom, FrameLayout.LayoutParams(-1, -1))

        top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setBackgroundColor(0x99000000.toInt())
        top.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 32), UiKit.dp(this, 8), UiKit.dp(this, 6))
        caption = UiKit.text(this, "", 14f)
        caption.maxLines = 1
        caption.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(caption, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(UiKit.iconBtn(this, "⟳") { rotate() })
        top.addView(UiKit.iconBtn(this, "ℹ️") { details() })
        top.addView(UiKit.iconBtn(this, "🖼") { wallpaper() })
        top.addView(UiKit.iconBtn(this, "▶") { toggleSlideshow() })
        top.addView(UiKit.iconBtn(this, "✕") { finish() })
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        loadCurrent()
    }

    override fun onDestroy() { slideshow = false; handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun go(dir: Int) {
        index = (index + dir + paths.size) % paths.size
        loadCurrent()
    }

    private fun loadCurrent() {
        val f = File(paths[index])
        caption.text = "${index + 1}/${paths.size}  ${f.name}"
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val b = withContext(Dispatchers.IO) { decode(f) }
            if (b != null) { bitmap = b; zoom.show(b) } else UiKit.toast(this@ImageViewerActivity, "Cannot decode this image")
        }
    }

    private fun decode(f: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val target = 2400
        var sample = 1
        while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(f.path, opts)
        if (bmp != null) {
            val deg = try {
                when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) { 0f }
            if (deg != 0f) {
                val mx = Matrix(); mx.postRotate(deg)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
            }
        }
        bmp
    } catch (e: Throwable) { null }

    private fun rotate() {
        val b = bitmap ?: return
        val mx = Matrix(); mx.postRotate(90f)
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, mx, true)
        bitmap = r; zoom.show(r)
    }

    private fun details() {
        val f = File(paths[index])
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        MaterialAlertDialogBuilder(this).setTitle(f.name)
            .setMessage("${o.outWidth} × ${o.outHeight}\n${FormatBytes.human(f.length())}\n${f.absolutePath}")
            .setPositiveButton("OK", null).show()
    }

    private fun wallpaper() {
        val b = bitmap ?: return
        UiKit.confirm(this, "Set as wallpaper?", "Use this picture as your wallpaper.", "Set") {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try { WallpaperManager.getInstance(this@ImageViewerActivity).setBitmap(b); true } catch (_: Exception) { false }
                }
                UiKit.toast(this@ImageViewerActivity, if (ok) "Wallpaper set" else "Could not set wallpaper")
            }
        }
    }

    private fun toggleSlideshow() {
        slideshow = !slideshow
        if (slideshow) { top.visibility = View.GONE; handler.postDelayed(slideTick, 3000) }
        UiKit.toast(this, if (slideshow) "Slideshow on (tap to show controls)" else "Slideshow off")
    }
}
