package com.crestpoint.datatracker.files.tools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileJob
import com.crestpoint.datatracker.files.FileJobs
import com.crestpoint.datatracker.files.FileKinds
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.FileWalker
import com.crestpoint.datatracker.files.FileOpener
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One screen for: large files, old files, duplicates, junk, APKs and chat-app media. */
class ScanListActivity : AppCompatActivity() {
    enum class Mode(val title: String) {
        LARGE("Large files"), OLD("Old files"), DUPLICATES("Duplicate files"),
        JUNK("Junk cleaner"), APKS("APK files"), CHAT_MEDIA("WhatsApp & Telegram media")
    }

    data class Row(val file: File?, val size: Long, val header: String? = null, val group: Int = 0, val preselect: Boolean = false)

    companion object {
        fun start(ctx: Context, mode: Mode) {
            val i = Intent(ctx, ScanListActivity::class.java).putExtra("mode", mode.name)
            ctx.startActivity(i)
        }
    }

    private lateinit var mode: Mode
    private var rows: List<Row> = emptyList()
    private val checked = HashSet<String>()
    private lateinit var summary: TextView
    private lateinit var adapter: RowAdapter
    private lateinit var deleteBtn: com.google.android.material.button.MaterialButton
    private lateinit var optionBtn: com.google.android.material.button.MaterialButton
    private var scanJob: Job? = null
    private var thresholdMb = 100
    private var days = 180
    private val listener: (String) -> Unit = { msg -> UiKit.toast(this, msg) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = try { Mode.valueOf(intent.getStringExtra("mode") ?: "") } catch (_: Exception) { Mode.LARGE }
        if (mode == Mode.CHAT_MEDIA) days = 90
        if (!FilePaths.hasAccess(this)) { UiKit.toast(this, "Allow file access in the Files tab first"); finish(); return }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        val pad = UiKit.dp(this, 12)
        root.setPadding(pad, UiKit.dp(this, 36), pad, pad)
        root.addView(UiKit.text(this, mode.title, 22f, bold = true))
        summary = UiKit.text(this, "Scanning…", 13f, R.color.on_surface_muted)
        root.addView(summary)

        val bar = LinearLayout(this)
        optionBtn = UiKit.button(this, optionLabel(), true) { chooseOption() }
        if (mode == Mode.LARGE || mode == Mode.OLD || mode == Mode.CHAT_MEDIA) bar.addView(optionBtn)
        bar.addView(UiKit.button(this, "Select all", true) { selectAll(true) })
        bar.addView(UiKit.button(this, "Clear", true) { selectAll(false) })
        if (mode == Mode.DUPLICATES) bar.addView(UiKit.button(this, "Auto-select…", true) { autoSelect() })
        val hs = android.widget.HorizontalScrollView(this)
        hs.addView(bar)
        root.addView(hs)

        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter()
        list.adapter = adapter
        root.addView(list, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        deleteBtn = UiKit.button(this, "Move to Recycle Bin") { deleteChecked() }
        root.addView(deleteBtn)
        setContentView(root)
        scan()
    }

    override fun onStart() { super.onStart(); FileJobs.listeners.add(listener) }
    override fun onStop() { super.onStop(); FileJobs.listeners.remove(listener) }
    override fun onDestroy() { scanJob?.cancel(); super.onDestroy() }

    private fun optionLabel(): String = when (mode) {
        Mode.LARGE -> "Min size: ${if (thresholdMb >= 1024) "1 GB" else "$thresholdMb MB"}"
        Mode.OLD -> "Not changed in: $days days"
        Mode.CHAT_MEDIA -> "Older than: $days days"
        else -> ""
    }

    private fun chooseOption() {
        if (mode == Mode.LARGE) {
            val opts = intArrayOf(50, 100, 250, 500, 1024)
            MaterialAlertDialogBuilder(this).setTitle("Minimum size")
                .setItems(arrayOf("50 MB", "100 MB", "250 MB", "500 MB", "1 GB")) { _, i -> thresholdMb = opts[i]; refreshOption() }.show()
        } else {
            val opts = if (mode == Mode.OLD) intArrayOf(90, 180, 365, 730) else intArrayOf(30, 90, 180, 365)
            MaterialAlertDialogBuilder(this).setTitle("Age")
                .setItems(opts.map { "$it days" }.toTypedArray()) { _, i -> days = opts[i]; refreshOption() }.show()
        }
    }

    private fun refreshOption() { optionBtn.text = optionLabel(); scan() }

    private fun scan() {
        scanJob?.cancel()
        rows = emptyList(); checked.clear(); adapter.notifyDataSetChanged(); updateBar()
        val counter = AtomicInteger()
        scanJob = lifecycleScope.launch {
            val ticker = launch { while (isActive) { summary.text = "Scanning… ${counter.get()} files checked"; delay(300) } }
            val result = withContext(Dispatchers.IO) { runScan(counter) }
            ticker.cancel()
            rows = result
            result.filter { it.file != null && it.preselect }.forEach { checked.add(it.file!!.path) }
            adapter.notifyDataSetChanged()
            val files = result.count { it.file != null }
            summary.text = if (files == 0) "Nothing found 🎉" else "$files item(s) · ${FormatBytes.human(result.sumOf { if (it.file != null) it.size else 0L })}"
            updateBar()
        }
    }

    // ------------------------------------------------------------- scanners

    private fun CoroutineScopeActive(): Boolean = scanJob?.isActive == true

    private fun runScan(counter: AtomicInteger): List<Row> {
        val root = FilePaths.internalRoot()
        val skip = FilePaths.skipForScan(root)
        val cancelled = { !CoroutineScopeActive() }
        return when (mode) {
            Mode.LARGE -> {
                val min = thresholdMb * 1024L * 1024L
                val out = ArrayList<File>()
                FileWalker.walk(root, { skip(it) || it.name.startsWith(".") }, cancelled) { f -> counter.incrementAndGet(); if (f.length() >= min) out.add(f) }
                out.sortedByDescending { it.length() }.take(500).map { Row(it, it.length()) }
            }
            Mode.OLD -> {
                val limit = System.currentTimeMillis() - days * 86_400_000L
                val out = ArrayList<File>()
                FileWalker.walk(root, { skip(it) || it.name.startsWith(".") }, cancelled) { f ->
                    counter.incrementAndGet()
                    if (f.lastModified() < limit && f.length() >= 1024 * 1024L) out.add(f)
                }
                out.sortedByDescending { it.length() }.take(500).map { Row(it, it.length()) }
            }
            Mode.APKS -> {
                val out = ArrayList<File>()
                FileWalker.walk(root, { skip(it) || it.name.startsWith(".") }, cancelled) { f ->
                    counter.incrementAndGet(); if (f.extension.equals("apk", true)) out.add(f)
                }
                out.sortedByDescending { it.length() }.map { Row(it, it.length()) }
            }
            Mode.DUPLICATES -> scanDuplicates(root, skip, counter, cancelled)
            Mode.JUNK -> scanJunk(root, skip, counter, cancelled)
            Mode.CHAT_MEDIA -> scanChat(root, counter, cancelled)
        }
    }

    private fun scanDuplicates(root: File, skip: (File) -> Boolean, counter: AtomicInteger, cancelled: () -> Boolean): List<Row> {
        val bySize = HashMap<Long, MutableList<File>>()
        FileWalker.walk(root, { skip(it) || it.name.startsWith(".") }, cancelled) { f ->
            counter.incrementAndGet()
            val s = f.length()
            if (s >= 1024) bySize.getOrPut(s) { ArrayList() }.add(f)
        }
        val groups = ArrayList<List<File>>()
        for ((_, same) in bySize) {
            if (same.size < 2) continue
            if (!CoroutineScopeActive()) break
            val byPartial = HashMap<String, MutableList<File>>()
            for (f in same) {
                val h = try { FileOps.hash(f, "MD5", 64 * 1024) } catch (_: Exception) { continue }
                byPartial.getOrPut(h) { ArrayList() }.add(f)
            }
            for ((_, p) in byPartial) {
                if (p.size < 2) continue
                val byFull = HashMap<String, MutableList<File>>()
                for (f in p) {
                    val h = try { FileOps.hash(f, "SHA-256") } catch (_: Exception) { continue }
                    byFull.getOrPut(h) { ArrayList() }.add(f)
                }
                byFull.values.filter { it.size >= 2 }.forEach { groups.add(it) }
            }
        }
        groups.sortByDescending { it[0].length() * (it.size - 1) }
        val out = ArrayList<Row>()
        groups.take(300).forEachIndexed { gi, g ->
            val size = g[0].length()
            out.add(Row(null, size * (g.size - 1), "Group ${gi + 1} · ${g.size} copies · ${FormatBytes.human(size)} each", gi))
            g.sortedBy { it.lastModified() }.forEach { out.add(Row(it, size, null, gi)) }
        }
        return out
    }

    private fun scanJunk(root: File, skip: (File) -> Boolean, counter: AtomicInteger, cancelled: () -> Boolean): List<Row> {
        val empties = ArrayList<File>()
        val zero = ArrayList<File>()
        val temp = ArrayList<File>()
        val thumbs = ArrayList<File>()
        val lost = ArrayList<File>()
        val apks = ArrayList<File>()
        val keepDirs = setOf("DCIM", "Download", "Downloads", "Pictures", "Music", "Movies", "Documents", "Alarms",
            "Notifications", "Ringtones", "Podcasts", "Recordings", "Audiobooks")
        val tempExt = setOf("tmp", "temp", "log", "bak", "old", "chk", "dmp")
        FileWalker.walk(root, { skip(it) }, cancelled,
            onDir = { d, kids ->
                if (kids.isEmpty() && d != root && !(d.parentFile == root && d.name in keepDirs)) empties.add(d)
            }
        ) { f ->
            counter.incrementAndGet()
            val parent = f.parentFile?.name ?: ""
            val ext = f.extension.lowercase()
            when {
                parent == ".thumbnails" || parent == "thumbnails" && f.parentFile?.parentFile?.name == "DCIM" -> thumbs.add(f)
                parent == "LOST.DIR" -> lost.add(f)
                f.name == ".nomedia" -> {}
                f.length() == 0L -> zero.add(f)
                ext in tempExt || f.name.startsWith("~$") || f.name.endsWith("~") || f.name.equals("Thumbs.db", true) -> temp.add(f)
                ext == "apk" -> apks.add(f)
            }
        }
        val out = ArrayList<Row>()
        fun section(title: String, files: List<File>, pre: Boolean) {
            if (files.isEmpty()) return
            val total = files.sumOf { FileOps.treeSize(it) }
            out.add(Row(null, total, "$title · ${files.size} · ${FormatBytes.human(total)}"))
            files.take(400).forEach { out.add(Row(it, FileOps.treeSize(it), null, 0, pre)) }
        }
        section("Thumbnail caches", thumbs, true)
        section("Temporary & log files", temp, true)
        section("Empty files (0 bytes)", zero, true)
        section("Empty folders", empties, true)
        section("Recovered fragments (LOST.DIR)", lost, true)
        section("Leftover APK installers (check before deleting)", apks, false)
        return out
    }

    private fun scanChat(root: File, counter: AtomicInteger, cancelled: () -> Boolean): List<Row> {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        val bases = listOf(
            "Android/media/com.whatsapp/WhatsApp/Media", "WhatsApp/Media",
            "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            "Android/media/org.telegram.messenger", "Telegram"
        ).map { File(root, it) }.filter { it.isDirectory }
        val out = ArrayList<Row>()
        var g = 0
        for (b in bases) {
            val subs = b.listFiles()?.filter { it.isDirectory } ?: continue
            for (s in subs.sortedBy { it.name }) {
                val files = ArrayList<File>()
                FileWalker.walk(s, { false }, cancelled) { f ->
                    counter.incrementAndGet()
                    if (f.lastModified() < limit && f.name != ".nomedia") files.add(f)
                }
                if (files.isEmpty()) continue
                val total = files.sumOf { it.length() }
                out.add(Row(null, total, "${b.parentFile?.name ?: ""} › ${s.name} · ${files.size} · ${FormatBytes.human(total)}", g++))
                files.sortedByDescending { it.length() }.take(300).forEach { out.add(Row(it, it.length(), null, g)) }
            }
        }
        return out
    }

    // -------------------------------------------------------------- actions

    private fun selectAll(on: Boolean) {
        checked.clear()
        if (on) rows.forEach { r -> r.file?.let { checked.add(it.path) } }
        adapter.notifyDataSetChanged(); updateBar()
    }

    private fun autoSelect() {
        MaterialAlertDialogBuilder(this).setTitle("In each group, keep…")
            .setItems(arrayOf("Newest copy", "Oldest copy", "Shortest path")) { _, which ->
                checked.clear()
                val byGroup = rows.filter { it.file != null }.groupBy { it.group }
                for ((_, g) in byGroup) {
                    val keep = when (which) {
                        0 -> g.maxByOrNull { it.file!!.lastModified() }
                        1 -> g.minByOrNull { it.file!!.lastModified() }
                        else -> g.minByOrNull { it.file!!.path.length }
                    }
                    g.forEach { if (it !== keep) checked.add(it.file!!.path) }
                }
                adapter.notifyDataSetChanged(); updateBar()
            }.show()
    }

    private fun updateBar() {
        val files = rows.filter { it.file != null && it.file.path in checked }
        val size = files.sumOf { it.size }
        deleteBtn.text = "Move ${files.size} to Recycle Bin (${FormatBytes.human(size)})"
        deleteBtn.isEnabled = files.isNotEmpty()
    }

    private fun deleteChecked() {
        val files = rows.mapNotNull { it.file }.filter { it.path in checked }
        if (files.isEmpty()) return
        UiKit.confirm(this, "Move ${files.size} item(s) to the Recycle Bin?",
            "You can restore them from Files › Recycle Bin until it auto-empties.", "Move") {
            FileJobs.submit(this, FileJob.Delete(files, false))
            val gone = files.map { it.path }.toSet()
            val kept = ArrayList<Row>()
            var pendingHeader: Row? = null
            for (r in rows) {
                if (r.file == null) { pendingHeader = r; continue }
                if (r.file.path in gone) continue
                pendingHeader?.let { kept.add(it); pendingHeader = null }
                kept.add(r)
            }
            rows = kept
            checked.clear()
            adapter.notifyDataSetChanged(); updateBar()
            summary.text = "Moving ${files.size} item(s)…"
        }
    }

    inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class HeaderVH(val t: TextView) : RecyclerView.ViewHolder(t)
        inner class ItemVH(v: View, val cb: CheckBox, val name: TextView, val sub: TextView) : RecyclerView.ViewHolder(v)

        override fun getItemViewType(position: Int) = if (rows[position].file == null) 0 else 1
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            if (viewType == 0) {
                val t = UiKit.text(ctx, "", 14f, R.color.brand_cyan, bold = true)
                t.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                t.setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 14), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4))
                return HeaderVH(t)
            }
            val row = LinearLayout(ctx)
            row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val cb = CheckBox(ctx)
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            val n = UiKit.text(ctx, "", 14f); n.maxLines = 1; n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            val s = UiKit.text(ctx, "", 11f, R.color.on_surface_muted); s.maxLines = 1; s.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            col.addView(n); col.addView(s)
            row.addView(cb)
            row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return ItemVH(row, cb, n, s)
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val r = rows[position]
            if (h is HeaderVH) { h.t.text = r.header; return }
            h as ItemVH
            val f = r.file!!
            h.name.text = (if (f.isDirectory) "📁 " else "") + f.name
            h.sub.text = FormatBytes.human(r.size) + " · " + (f.parent ?: "")
            h.cb.setOnCheckedChangeListener(null)
            h.cb.isChecked = f.path in checked
            h.cb.setOnCheckedChangeListener { _, on -> if (on) checked.add(f.path) else checked.remove(f.path); updateBar() }
            h.itemView.setOnClickListener { h.cb.isChecked = !h.cb.isChecked }
            h.itemView.setOnLongClickListener { if (f.isFile) FileOpener.open(this@ScanListActivity, f); true }
        }
    }
}
