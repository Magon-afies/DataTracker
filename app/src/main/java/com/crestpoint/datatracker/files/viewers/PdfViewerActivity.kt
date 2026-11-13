package com.crestpoint.datatracker.files.viewers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.UiKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Scrollable PDF viewer built on the system PdfRenderer. */
class PdfViewerActivity : AppCompatActivity() {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val renderScope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val lock = Any()
    private lateinit var list: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = File(intent.getStringExtra("path") ?: run { finish(); return })
        try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
        } catch (e: Exception) {
            UiKit.toast(this, "Cannot open this PDF: ${e.message}"); finish(); return
        }
        val pages = renderer!!.pageCount

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        root.setPadding(0, UiKit.dp(this, 32), 0, 0)
        val bar = LinearLayout(this)
        bar.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 12), UiKit.dp(this, 6))
        val title = UiKit.text(this, "${f.name} · $pages pages", 15f, bold = true)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        bar.addView(title, UiKit.lp(0, -2, 1f))
        bar.addView(UiKit.button(this, "Go to page", true) {
            UiKit.prompt(this, "Page (1-$pages)", "", "Page number") { s ->
                val n = s.trim().toIntOrNull()
                if (n != null && n in 1..pages) list.scrollToPosition(n - 1) else UiKit.toast(this, "Invalid page")
            }
        })
        list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = PageAdapter(pages)
        root.addView(bar)
        root.addView(list, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onDestroy() {
        try { renderer?.close(); pfd?.close() } catch (_: Exception) { }
        super.onDestroy()
    }

    inner class PageAdapter(private val count: Int) : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val img: ImageView) : RecyclerView.ViewHolder(img)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context)
            iv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            iv.adjustViewBounds = true
            iv.setBackgroundColor(Color.WHITE)
            iv.setPadding(0, 0, 0, 0)
            return VH(iv)
        }

        override fun getItemCount() = count

        override fun onBindViewHolder(h: VH, position: Int) {
            val width = resources.displayMetrics.widthPixels
            h.img.setImageBitmap(null)
            h.img.minimumHeight = (width * 1.4f).toInt()
            h.img.tag = position
            renderScope.launch {
                val bmp: Bitmap? = try {
                    synchronized(lock) {
                        val r = renderer ?: return@synchronized null
                        val page = r.openPage(position)
                        val w = width
                        val hgt = (w.toFloat() * page.height / page.width).toInt()
                        val b = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        b
                    }
                } catch (e: Throwable) { null }
                if (bmp != null) withContext(Dispatchers.Main) {
                    if (h.img.tag == position) { h.img.minimumHeight = 0; h.img.setImageBitmap(bmp) }
                }
            }
        }
    }
}
