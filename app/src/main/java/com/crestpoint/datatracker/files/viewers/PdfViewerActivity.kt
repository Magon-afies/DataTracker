package com.crestpoint.datatracker.files.viewers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.UiKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Wraps content and turns pinch / double-tap into a zoom callback without stealing scrolls. */
class PinchFrame(ctx: Context, private val onZoom: (Float) -> Unit, private val onDouble: () -> Unit) : FrameLayout(ctx) {
    private var pending = 1f
    private val scale = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { pending *= d.scaleFactor; return true }
        override fun onScaleEnd(d: ScaleGestureDetector) { val p = pending; pending = 1f; if (p > 1.08f || p < 0.92f) onZoom(p) }
    })
    private val taps = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean { onDouble(); return true }
    })
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scale.onTouchEvent(ev); taps.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}

/** PDF viewer: pinch / double-tap zoom, page slider, night mode, remembers your page. */
class PdfViewerActivity : AppCompatActivity() {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val renderScope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val lock = Any()
    private lateinit var list: RecyclerView
    private lateinit var lm: LinearLayoutManager
    private lateinit var adapter: PageAdapter
    private lateinit var indicator: TextView
    private lateinit var slider: SeekBar
    private lateinit var file: File
    private lateinit var prefs: FilesPrefs
    private var pages = 0
    private var zoom = 1f
    private var night = false
    private var ratio = 1.41f
    private val screenW get() = resources.displayMetrics.widthPixels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra("path") ?: run { finish(); return })
        prefs = FilesPrefs(this)
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
        } catch (e: SecurityException) {
            UiKit.toast(this, "This PDF is password-protected. Opening it in another app…")
            FileOpener.external(this, file); finish(); return
        } catch (e: Exception) {
            UiKit.toast(this, "Cannot open this PDF: ${e.message}"); finish(); return
        }
        pages = renderer!!.pageCount
        try { synchronized(lock) { renderer!!.openPage(0).use { ratio = it.height.toFloat() / it.width } } } catch (_: Exception) { }

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF2A2A33.toInt())

        lm = LinearLayoutManager(this)
        list = RecyclerView(this)
        list.layoutManager = lm
        adapter = PageAdapter()
        list.adapter = adapter
        list.setItemViewCacheSize(2)
        list.setPadding(0, UiKit.dp(this, 64), 0, UiKit.dp(this, 76))
        list.clipToPadding = false
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(list, FrameLayout.LayoutParams(screenW, -1))
        val pinch = PinchFrame(this, { f -> setZoom(zoom * f, hs) }, { setZoom(if (zoom > 1.2f) 1f else 2f, hs) })
        pinch.addView(hs, FrameLayout.LayoutParams(-1, -1))
        root.addView(pinch, FrameLayout.LayoutParams(-1, -1))

        // top bar
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setBackgroundColor(0xEE191A2D.toInt())
        top.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 26), UiKit.dp(this, 6), UiKit.dp(this, 6))
        top.addView(Design.icon(this, R.drawable.ic_fx_back, 44) { finish() })
        val t = UiKit.text(this, file.name, 15f, bold = true)
        t.maxLines = 1; t.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        top.addView(t, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Design.icon(this, R.drawable.ic_fx_zoom_out, 44) { setZoom(zoom / 1.3f, hs) })
        top.addView(Design.icon(this, R.drawable.ic_fx_zoom_in, 44) { setZoom(zoom * 1.3f, hs) })
        top.addView(Design.icon(this, R.drawable.ic_fx_dark, 44) { night = !night; adapter.notifyDataSetChanged() })
        top.addView(Design.icon(this, R.drawable.ic_fx_share, 44) { FileOpener.share(this, listOf(file)) })
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // bottom: page chip + slider
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.gravity = Gravity.CENTER_HORIZONTAL
        bottom.setBackgroundColor(0xCC191A2D.toInt())
        bottom.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 8), UiKit.dp(this, 18), UiKit.dp(this, 14))
        indicator = UiKit.text(this, "1 / $pages", 13f, bold = true)
        slider = SeekBar(this)
        slider.max = (pages - 1).coerceAtLeast(0)
        slider.progressTintList = android.content.res.ColorStateList.valueOf(UiKit.color(this, R.color.brand_purple))
        slider.thumbTintList = android.content.res.ColorStateList.valueOf(UiKit.color(this, R.color.brand_purple))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) lm.scrollToPositionWithOffset(p, 0) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        bottom.addView(indicator)
        bottom.addView(slider, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val p = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
                indicator.text = "${p + 1} / $pages"
                if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE || true) slider.progress = p
            }
        })
        val last = prefs.mediaPos(file.path)
        if (last in 1 until pages) list.post { lm.scrollToPositionWithOffset(last, 0) }
    }

    private fun setZoom(z: Float, hs: HorizontalScrollView) {
        val nz = z.coerceIn(1f, 2.5f)
        if (nz == zoom) return
        val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
        zoom = nz
        val lp = list.layoutParams
        lp.width = (screenW * zoom).toInt()
        list.layoutParams = lp
        adapter.notifyDataSetChanged()
        list.post { lm.scrollToPositionWithOffset(first, 0); if (zoom == 1f) hs.scrollTo(0, 0) }
    }

    override fun onPause() {
        super.onPause()
        prefs.setMediaPos(file.path, lm.findFirstVisibleItemPosition().coerceAtLeast(0))
    }

    override fun onDestroy() {
        try { renderer?.close(); pfd?.close() } catch (_: Exception) { }
        super.onDestroy()
    }

    inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val img: ImageView) : RecyclerView.ViewHolder(img)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context)
            iv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            iv.adjustViewBounds = true
            val lp = iv.layoutParams as RecyclerView.LayoutParams
            lp.setMargins(UiKit.dp(parent.context, 6), UiKit.dp(parent.context, 5), UiKit.dp(parent.context, 6), UiKit.dp(parent.context, 5))
            return VH(iv)
        }

        override fun getItemCount() = pages

        override fun onBindViewHolder(h: VH, position: Int) {
            val width = ((screenW * zoom).toInt() - UiKit.dp(this@PdfViewerActivity, 12)).coerceAtMost(2800)
            h.img.setImageBitmap(null)
            h.img.setBackgroundColor(if (night) 0xFF111111.toInt() else Color.WHITE)
            h.img.colorFilter = if (night) ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))) else null
            h.img.minimumHeight = (width * ratio).toInt()
            h.img.tag = position
            renderScope.launch {
                val bmp: Bitmap? = try {
                    synchronized(lock) {
                        val r = renderer ?: return@synchronized null
                        r.openPage(position).use { page ->
                            val hgt = (width.toFloat() * page.height / page.width).toInt()
                            val b = Bitmap.createBitmap(width, hgt, Bitmap.Config.ARGB_8888)
                            b.eraseColor(Color.WHITE)
                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            b
                        }
                    }
                } catch (e: Throwable) { null }
                if (bmp != null) withContext(Dispatchers.Main) {
                    if (h.img.tag == position) { h.img.minimumHeight = 0; h.img.setImageBitmap(bmp) }
                }
            }
        }
    }
}
