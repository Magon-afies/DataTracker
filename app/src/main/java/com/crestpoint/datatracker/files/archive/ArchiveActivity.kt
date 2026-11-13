package com.crestpoint.datatracker.files.archive

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileJob
import com.crestpoint.datatracker.files.FileJobs
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.ui.FolderPicker
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Browse inside an archive like a folder and extract all or selected entries. */
class ArchiveActivity : AppCompatActivity() {
    private var archive: File? = null
    private var password: String? = null
    private var all: List<ArchiveManager.Entry> = emptyList()
    private var prefix = ""
    private var rows: List<ArchiveManager.Entry> = emptyList()
    private val selected = LinkedHashSet<String>()

    private lateinit var title: TextView
    private lateinit var pathText: TextView
    private lateinit var info: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var list: RecyclerView
    private lateinit var adapter: RowAdapter
    private val listener: (String) -> Unit = { msg -> UiKit.toast(this, msg) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        val pad = UiKit.dp(this, 14)
        root.setPadding(pad, UiKit.dp(this, 36), pad, pad)

        title = UiKit.text(this, "Archive", 20f, bold = true)
        pathText = UiKit.text(this, "/", 12f, R.color.on_surface_muted)
        info = UiKit.text(this, "Opening…", 12f, R.color.on_surface_muted)
        spinner = ProgressBar(this)
        list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter()
        list.adapter = adapter

        val up = UiKit.button(this, "⬆ Up", true) { goUp() }
        val extractAll = UiKit.button(this, "Extract all") { extractAllHere() }
        val extractSel = UiKit.button(this, "Extract selected", true) { extractSelected() }
        val extractTo = UiKit.button(this, "Extract to…", true) { extractTo() }
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(up); bar.addView(extractAll); bar.addView(extractSel); bar.addView(extractTo)
        val hs = android.widget.HorizontalScrollView(this)
        hs.addView(bar)

        root.addView(title)
        root.addView(pathText)
        root.addView(info)
        root.addView(hs)
        root.addView(spinner, UiKit.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(list, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        lifecycleScope.launch { resolveAndOpen() }
    }

    override fun onStart() { super.onStart(); FileJobs.listeners.add(listener) }
    override fun onStop() { super.onStop(); FileJobs.listeners.remove(listener) }

    private suspend fun resolveAndOpen() {
        val f = withContext(Dispatchers.IO) { resolveFile() }
        if (f == null || !f.exists()) {
            UiKit.toast(this, "Could not open this archive"); finish(); return
        }
        archive = f
        title.text = f.name
        if (ArchiveManager.kindOf(f) == null) { UiKit.toast(this, "Unsupported archive type"); finish(); return }
        load()
    }

    private fun resolveFile(): File? {
        intent.getStringExtra("path")?.let { return File(it) }
        val uri: Uri = intent.data ?: return null
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        // content:// → copy to cache so we can read it as a normal file
        var name = "archive.zip"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        val dir = File(cacheDir, "open_archives").also { it.mkdirs() }
        val out = File(dir, name)
        contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } } ?: return null
        return out
    }

    private fun load() {
        val f = archive ?: return
        spinner.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { ArchiveManager.list(f, password) }
                all = entries
                val needs = withContext(Dispatchers.IO) { ArchiveManager.needsPassword(f) }
                spinner.visibility = View.GONE
                val files = entries.count { !it.isDir }
                val unpacked = entries.sumOf { it.size.coerceAtLeast(0L) }
                val ratio = if (unpacked > 0) (100 - (f.length() * 100 / unpacked)).coerceIn(0, 100) else 0
                info.text = "$files files · packed ${FormatBytes.human(f.length())} · unpacked ${FormatBytes.human(unpacked)} · saved $ratio%" +
                    (if (needs) " · 🔒 encrypted" else "")
                prefix = ""
                refreshRows()
                if (needs && password.isNullOrEmpty()) askPassword(null)
            } catch (e: ArchiveManager.NeedPassword) {
                spinner.visibility = View.GONE
                askPassword { load() }
            } catch (e: Throwable) {
                spinner.visibility = View.GONE
                val msg = if (e.javaClass.simpleName.contains("V5", true) || (e.message ?: "").contains("RAR5", true))
                    "This RAR uses the newer RAR5 format, which isn't supported yet." else "Could not read archive: ${e.message}"
                info.text = msg
            }
        }
    }

    private fun askPassword(then: (() -> Unit)?) {
        UiKit.prompt(this, "Archive password", password = true) { pw ->
            password = pw
            then?.invoke()
        }
    }

    private fun refreshRows() {
        val map = LinkedHashMap<String, ArchiveManager.Entry>()
        for (e in all) {
            if (!e.path.startsWith(prefix)) continue
            val rest = e.path.substring(prefix.length)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            if (slash < 0) {
                map[rest] = ArchiveManager.Entry(prefix + rest, e.isDir, e.size)
            } else {
                val dn = rest.substring(0, slash)
                if (!map.containsKey(dn)) map[dn] = ArchiveManager.Entry(prefix + dn, true, 0L)
            }
        }
        rows = map.values.sortedWith(compareBy<ArchiveManager.Entry>({ !it.isDir }, { it.path.lowercase() }))
        pathText.text = "/" + prefix
        adapter.notifyDataSetChanged()
    }

    private fun goUp() {
        if (prefix.isEmpty()) { finish(); return }
        val p = prefix.trimEnd('/')
        val i = p.lastIndexOf('/')
        prefix = if (i < 0) "" else p.substring(0, i + 1)
        refreshRows()
    }

    private fun defaultDest(): File {
        val f = archive!!
        val parent = if (f.parentFile?.canWrite() == true) f.parentFile!! else File(com.crestpoint.datatracker.files.FilePaths.internalRoot(), "Download")
        return FileOps.uniqueFile(parent, ArchiveManager.baseName(f))
    }

    private fun startExtract(dest: File, only: Set<String>?) {
        val f = archive ?: return
        FileJobs.submit(this, FileJob.Extract(f, dest, password, only))
        UiKit.toast(this, "Extracting in background…")
    }

    private fun extractAllHere() = startExtract(defaultDest(), null)

    private fun extractSelected() {
        if (selected.isEmpty()) { UiKit.toast(this, "Tick the items to extract first"); return }
        startExtract(defaultDest(), HashSet(selected))
    }

    private fun extractTo() {
        val f = archive ?: return
        FolderPicker.show(this, f.parentFile ?: File("/sdcard")) { dir ->
            startExtract(dir, if (selected.isEmpty()) null else HashSet(selected))
        }
    }

    inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {
        inner class VH(v: View, val check: CheckBox, val name: TextView, val sub: TextView) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val p = UiKit.dp(ctx, 8)
            row.setPadding(p, p, p, p)
            val cb = CheckBox(ctx)
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            val n = UiKit.text(ctx, "", 15f)
            n.maxLines = 1
            n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            val s = UiKit.text(ctx, "", 12f, R.color.on_surface_muted)
            col.addView(n); col.addView(s)
            row.addView(cb)
            row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VH(row, cb, n, s)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val e = rows[position]
            val short = e.path.substringAfterLast('/')
            h.name.text = (if (e.isDir) "📁 " else "📄 ") + short
            h.sub.text = if (e.isDir) "Folder" else FormatBytes.human(e.size.coerceAtLeast(0L))
            h.check.setOnCheckedChangeListener(null)
            h.check.isChecked = e.path in selected
            h.check.setOnCheckedChangeListener { _, on -> if (on) selected.add(e.path) else selected.remove(e.path) }
            h.itemView.setOnClickListener {
                if (e.isDir) { prefix = e.path + "/"; refreshRows() } else h.check.isChecked = !h.check.isChecked
            }
        }
    }
}
