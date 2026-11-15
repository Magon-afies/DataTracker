package com.crestpoint.datatracker.files.tools

import android.os.Bundle
import android.os.StatFs
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.FileWalker
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Breaks down what is using your storage and lets you drill into the biggest folders. */
class StorageAnalyzerActivity : AppCompatActivity() {
    private lateinit var box: LinearLayout
    private var dir: File = FilePaths.internalRoot()
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(UiKit.color(this, R.color.surface))
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 36), UiKit.dp(this, 8), UiKit.dp(this, 24))
        sv.addView(box)
        setContentView(sv)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val p = dir.parentFile
                if (dir != FilePaths.internalRoot() && p != null) { dir = p; analyze() } else finish()
            }
        })
        analyze()
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }

    private fun analyze() {
        job?.cancel()
        box.removeAllViews()
        box.addView(UiKit.text(this, "Storage analyzer", 22f, bold = true))
        box.addView(UiKit.text(this, dir.absolutePath, 12f, R.color.on_surface_muted))
        val status = UiKit.text(this, "Scanning…", 13f, R.color.on_surface_muted)
        box.addView(status)
        val target = dir
        job = lifecycleScope.launch {
            val cats = HashMap<FileKind, Long>()
            val children = ArrayList<Pair<File, Long>>()
            var count = 0
            withContext(Dispatchers.IO) {
                val skip = FilePaths.skipForScan(FilePaths.internalRoot())
                val kids = target.listFiles() ?: emptyArray()
                for (k in kids) {
                    if (!isActive) return@withContext
                    var size = 0L
                    if (k.isDirectory) {
                        if (skip(k)) continue
                        FileWalker.walk(k, skip, { !isActive }) { f ->
                            val s = f.length(); size += s; count++
                            val kind = FileKinds.of(f); cats[kind] = (cats[kind] ?: 0L) + s
                        }
                    } else {
                        size = k.length(); count++
                        val kind = FileKinds.of(k); cats[kind] = (cats[kind] ?: 0L) + size
                    }
                    children.add(k to size)
                }
            }
            if (!isActive) return@launch
            val total = children.sumOf { it.second }
            status.text = "$count files · ${FormatBytes.human(total)} here"
            if (target == FilePaths.internalRoot()) {
                val st = StatFs(target.path)
                val used = st.totalBytes - st.availableBytes
                box.addView(UiKit.text(this@StorageAnalyzerActivity,
                    "Phone: ${FormatBytes.human(used)} used of ${FormatBytes.human(st.totalBytes)} · ${FormatBytes.human(st.availableBytes)} free", 13f))
                box.addView(UiKit.progress(this@StorageAnalyzerActivity, used.toFloat() / st.totalBytes))
                val other = used - total
                if (other > 0) box.addView(UiKit.text(this@StorageAnalyzerActivity,
                    "Apps & system data (not browsable): ${FormatBytes.human(other)}", 12f, R.color.on_surface_muted))
            }
            val ctx2 = this@StorageAnalyzerActivity
            val ordered = cats.entries.filter { it.key != FileKind.FOLDER && it.value > 0 }.sortedByDescending { it.value }
            if (ordered.isNotEmpty()) {
                val donut = com.crestpoint.datatracker.files.ui.DonutView(ctx2)
                donut.setData(ordered.map { it.value.toFloat() to com.crestpoint.datatracker.files.ui.Palette.kind(it.key) }, FormatBytes.human(total), "in this folder")
                val dl = LinearLayout.LayoutParams(UiKit.dp(ctx2, 200), UiKit.dp(ctx2, 200))
                dl.gravity = android.view.Gravity.CENTER_HORIZONTAL; dl.topMargin = UiKit.dp(ctx2, 12)
                box.addView(donut, dl)
            }
            ordered.forEach { (k, v) ->
                val row = LinearLayout(ctx2)
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                row.setPadding(UiKit.dp(ctx2, 14), UiKit.dp(ctx2, 7), UiKit.dp(ctx2, 14), UiKit.dp(ctx2, 7))
                row.addView(com.crestpoint.datatracker.files.ui.Design.iconTile(ctx2, k.emoji, com.crestpoint.datatracker.files.ui.Palette.kind(k), 36, 12, 18f))
                val col = LinearLayout(ctx2)
                col.orientation = LinearLayout.VERTICAL
                col.setPadding(UiKit.dp(ctx2, 12), 0, 0, 0)
                col.addView(UiKit.text(ctx2, k.label, 14f, bold = true))
                col.addView(UiKit.text(ctx2, FormatBytes.human(v) + "  ·  " + (if (total > 0) (v * 100 / total).toString() else "0") + "%", 12f, R.color.on_surface_muted))
                row.addView(col)
                box.addView(row)
            }
            box.addView(UiKit.text(this@StorageAnalyzerActivity, "Biggest items here (tap a folder to drill in)", 16f, bold = true)
                .also { it.setPadding(0, UiKit.dp(this@StorageAnalyzerActivity, 14), 0, 0) })
            children.sortedByDescending { it.second }.take(25).forEach { (f, s) ->
                val c = UiKit.card(this@StorageAnalyzerActivity)
                c.addView(UiKit.text(this@StorageAnalyzerActivity, (if (f.isDirectory) "📁 " else "📄 ") + f.name + " · " + FormatBytes.human(s), 14f))
                c.addView(UiKit.progress(this@StorageAnalyzerActivity, if (total > 0) s.toFloat() / total else 0f))
                if (f.isDirectory) c.setOnClickListener { dir = f; analyze() }
                box.addView(c)
            }
        }
    }
}
