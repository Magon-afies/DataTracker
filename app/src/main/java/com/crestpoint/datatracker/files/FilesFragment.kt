package com.crestpoint.datatracker.files

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.archive.ArchiveManager
import com.crestpoint.datatracker.files.tools.AppManagerActivity
import com.crestpoint.datatracker.files.tools.ScanListActivity
import com.crestpoint.datatracker.files.tools.StorageAnalyzerActivity
import com.crestpoint.datatracker.files.tools.TrashActivity
import com.crestpoint.datatracker.files.ui.Thumbs
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The "Files" tab: explorer, search, selection actions, quick access and the phone-manager tools. */
class FilesFragment : Fragment() {
    private lateinit var prefs: FilesPrefs
    private var current: File? = null
    private var virtualTitle: String? = null
    private var base: List<File> = emptyList()
    private var shownList: List<File> = emptyList()
    private var searchMode = false
    private val selected = LinkedHashSet<String>()

    private lateinit var crumbs: LinearLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var selScroll: HorizontalScrollView
    private lateinit var selBar: LinearLayout
    private lateinit var pasteBar: LinearLayout
    private lateinit var pasteLabel: TextView
    private lateinit var homeScroll: ScrollView
    private lateinit var homeBox: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var permCard: LinearLayout
    private lateinit var spinner: ProgressBar
    private var adapter: FileAdapter? = null
    private var loadJob: Job? = null
    private val jobListener: (String) -> Unit = { msg ->
        if (isAdded) { UiKit.toast(requireContext(), msg); refresh() }
    }
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { onBack() }
    }

    // ------------------------------------------------------------------ UI

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        prefs = FilesPrefs(ctx)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(ctx, R.color.surface))
        root.setPadding(0, UiKit.dp(ctx, 30), 0, 0)

        // header
        val header = LinearLayout(ctx)
        header.gravity = Gravity.CENTER_VERTICAL
        val crumbScroll = HorizontalScrollView(ctx)
        crumbScroll.isHorizontalScrollBarEnabled = false
        crumbs = LinearLayout(ctx)
        crumbs.gravity = Gravity.CENTER_VERTICAL
        crumbScroll.addView(crumbs)
        header.addView(crumbScroll, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiKit.iconBtn(ctx, "🔍") { toggleSearch() })
        header.addView(UiKit.iconBtn(ctx, "⊞") { prefs.grid = !prefs.grid; rebuildAdapter(); display() })
        header.addView(UiKit.iconBtn(ctx, "⇅") { sortMenu(it) })
        header.addView(UiKit.iconBtn(ctx, "＋") { newMenu(it) })
        header.addView(UiKit.iconBtn(ctx, "⋮") { moreMenu(it) })
        root.addView(header)

        // search row
        searchRow = LinearLayout(ctx)
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.visibility = View.GONE
        searchRow.setPadding(UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 10), 0)
        searchInput = EditText(ctx)
        searchInput.hint = "Search by name"
        searchInput.setSingleLine(true)
        searchInput.setTextColor(UiKit.color(ctx, R.color.on_surface))
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!searchMode) filterLocal() }
        })
        searchRow.addView(searchInput, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(UiKit.button(ctx, "Search all storage", true) { searchEverywhere() })
        root.addView(searchRow)

        // selection bar
        selScroll = HorizontalScrollView(ctx)
        selScroll.visibility = View.GONE
        selBar = LinearLayout(ctx)
        selBar.gravity = Gravity.CENTER_VERTICAL
        selScroll.addView(selBar)
        root.addView(selScroll)

        // paste bar
        pasteBar = LinearLayout(ctx)
        pasteBar.gravity = Gravity.CENTER_VERTICAL
        pasteBar.visibility = View.GONE
        pasteBar.setPadding(UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 12), 0)
        pasteLabel = UiKit.text(ctx, "", 13f)
        pasteBar.addView(pasteLabel, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pasteBar.addView(UiKit.button(ctx, "Paste here") { paste() })
        pasteBar.addView(UiKit.button(ctx, "✕", true) { FileClipboard.clear(); updateBars() })
        root.addView(pasteBar)

        // body
        val body = FrameLayout(ctx)
        homeScroll = ScrollView(ctx)
        homeBox = LinearLayout(ctx)
        homeBox.orientation = LinearLayout.VERTICAL
        homeBox.setPadding(0, 0, 0, UiKit.dp(ctx, 24))
        homeScroll.addView(homeBox)
        list = RecyclerView(ctx)
        list.visibility = View.GONE
        emptyText = UiKit.text(ctx, "Nothing here", 14f, R.color.on_surface_muted)
        emptyText.gravity = Gravity.CENTER
        emptyText.visibility = View.GONE
        spinner = ProgressBar(ctx)
        spinner.visibility = View.GONE
        permCard = UiKit.card(ctx)
        permCard.visibility = View.GONE
        permCard.addView(UiKit.text(ctx, "All-files access needed", 16f, bold = true))
        permCard.addView(UiKit.text(ctx, "To browse, move and clean files, allow Data Tracker to manage all files. Nothing leaves your phone.", 13f, R.color.on_surface_muted))
        permCard.addView(UiKit.button(ctx, "Open settings") { openAccessSettings() })
        body.addView(homeScroll, FrameLayout.LayoutParams(-1, -1))
        body.addView(list, FrameLayout.LayoutParams(-1, -1))
        body.addView(emptyText, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        body.addView(spinner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        body.addView(permCard, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        root.addView(body, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        rebuildAdapter()
        current = null
        refresh()
    }

    override fun onStart() { super.onStart(); FileJobs.listeners.add(jobListener) }
    override fun onStop() { super.onStop(); FileJobs.listeners.remove(jobListener) }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroyView() { loadJob?.cancel(); super.onDestroyView() }

    private fun openAccessSettings() {
        val ctx = requireContext()
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName)))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    // ---------------------------------------------------------- navigation

    private fun roots() = FilePaths.roots(requireContext())

    private fun onBack() {
        when {
            selected.isNotEmpty() -> { selected.clear(); display() }
            searchMode || searchRow.visibility == View.VISIBLE -> closeSearch()
            virtualTitle != null -> showHome()
            current != null -> {
                val c = current!!
                val p = c.parentFile
                if (roots().any { it.second.absolutePath == c.absolutePath } || p == null) showHome() else open(p)
            }
        }
    }

    private fun open(dir: File) {
        current = dir; virtualTitle = null; searchMode = false
        selected.clear(); closeSearch(false)
        refresh()
    }

    private fun showHome() {
        current = null; virtualTitle = null; searchMode = false
        selected.clear(); closeSearch(false)
        refresh()
    }

    private fun showVirtual(title: String, files: List<File>, pathMode: Boolean = false) {
        current = null; virtualTitle = title; searchMode = pathMode
        selected.clear()
        base = files; shownList = files
        spinner.visibility = View.GONE
        display()
    }

    private fun refresh() {
        if (!isAdded || view == null) return
        if (!FilePaths.hasAccess(requireContext())) {
            permCard.visibility = View.VISIBLE
            homeScroll.visibility = View.GONE; list.visibility = View.GONE; emptyText.visibility = View.GONE
            updateBars(); return
        }
        permCard.visibility = View.GONE
        val dir = current
        if (dir != null) {
            loadJob?.cancel()
            spinner.visibility = View.VISIBLE
            loadJob = viewLifecycleOwner.lifecycleScope.launch {
                val l = withContext(Dispatchers.IO) { FileOps.list(dir, prefs.showHidden, prefs.sort, prefs.asc) }
                base = l; shownList = l
                spinner.visibility = View.GONE
                filterLocal(false)
                display()
            }
        } else if (virtualTitle != null) {
            display()
        } else {
            buildHome()
            display()
        }
    }

    private fun display() {
        if (!isAdded || view == null) return
        val home = current == null && virtualTitle == null
        homeScroll.visibility = if (home) View.VISIBLE else View.GONE
        list.visibility = if (home) View.GONE else View.VISIBLE
        emptyText.visibility = if (!home && shownList.isEmpty() && spinner.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        adapter?.data = shownList
        adapter?.notifyDataSetChanged()
        buildCrumbs()
        updateBars()
        backCallback.isEnabled = !home || selected.isNotEmpty() || searchRow.visibility == View.VISIBLE
    }

    private fun buildCrumbs() {
        val ctx = requireContext()
        crumbs.removeAllViews()
        fun seg(label: String, onClick: (() -> Unit)?) {
            val t = UiKit.text(ctx, label, 15f, if (onClick != null) R.color.brand_cyan else R.color.on_surface, bold = onClick == null)
            t.setPadding(UiKit.dp(ctx, 8), UiKit.dp(ctx, 12), UiKit.dp(ctx, 4), UiKit.dp(ctx, 12))
            if (onClick != null) t.setOnClickListener { onClick() }
            crumbs.addView(t)
        }
        val dir = current
        if (dir == null) {
            seg(virtualTitle ?: "Files", null)
            return
        }
        seg("Files ›") { showHome() }
        val rootPair = roots().firstOrNull { FileOps.isInside(dir, it.second) }
        if (rootPair == null) { seg(dir.name, null); return }
        val rootDir = rootPair.second
        val parts = dir.absolutePath.removePrefix(rootDir.absolutePath).split("/").filter { it.isNotEmpty() }
        var acc = rootDir
        if (parts.isEmpty()) seg(rootPair.first, null) else seg(rootPair.first + " ›") { open(rootDir) }
        parts.forEachIndexed { i, p ->
            acc = File(acc, p)
            val target = acc
            if (i == parts.lastIndex) seg(p, null) else seg("$p ›") { open(target) }
        }
    }

    // ---------------------------------------------------------------- home

    private fun tiles(items: List<Triple<String, String, () -> Unit>>, perRow: Int = 3) {
        val ctx = requireContext()
        items.chunked(perRow).forEach { chunk ->
            val row = LinearLayout(ctx)
            row.setPadding(UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 8), 0)
            chunk.forEach { (emoji, label, action) ->
                val c = LinearLayout(ctx)
                c.orientation = LinearLayout.VERTICAL
                c.gravity = Gravity.CENTER
                c.background = UiKit.rounded(ctx, R.color.surface_elevated)
                val p = UiKit.dp(ctx, 10)
                c.setPadding(p, p + UiKit.dp(ctx, 4), p, p + UiKit.dp(ctx, 4))
                c.addView(UiKit.text(ctx, emoji, 26f))
                val t = UiKit.text(ctx, label, 12f)
                t.gravity = Gravity.CENTER
                t.maxLines = 2
                c.addView(t)
                c.setOnClickListener { action() }
                val lp = UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4))
                row.addView(c, lp)
            }
            repeat(perRow - chunk.size) { row.addView(View(ctx), UiKit.lp(0, 1, 1f)) }
            homeBox.addView(row)
        }
    }

    private fun section(title: String) {
        val ctx = requireContext()
        val t = UiKit.text(ctx, title, 15f, R.color.on_surface_muted, bold = true)
        t.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 16), 0, UiKit.dp(ctx, 4))
        homeBox.addView(t)
    }

    private fun buildHome() {
        val ctx = requireContext()
        homeBox.removeAllViews()
        // storage cards
        roots().forEach { (name, dir) ->
            val c = UiKit.card(ctx)
            try {
                val st = StatFs(dir.path)
                val used = st.totalBytes - st.availableBytes
                c.addView(UiKit.text(ctx, "💾 $name", 16f, bold = true))
                c.addView(UiKit.text(ctx, "${FormatBytes.human(used)} used of ${FormatBytes.human(st.totalBytes)} · ${FormatBytes.human(st.availableBytes)} free", 12f, R.color.on_surface_muted))
                c.addView(UiKit.progress(ctx, used.toFloat() / st.totalBytes))
            } catch (_: Exception) { c.addView(UiKit.text(ctx, "💾 $name", 16f, bold = true)) }
            c.setOnClickListener { open(dir) }
            homeBox.addView(c)
        }
        // quick access
        val ext = FilePaths.internalRoot()
        val quick = listOf("Download" to "⬇️", "DCIM" to "📷", "Pictures" to "🖼️", "Music" to "🎵", "Movies" to "🎞️", "Documents" to "📄")
            .filter { File(ext, it.first).isDirectory }
            .map { (n, e) -> Triple(e, n, { open(File(ext, n)) }) }
        if (quick.isNotEmpty()) { section("Quick access"); tiles(quick) }

        section("Categories")
        tiles(listOf(
            Triple("🖼️", "Images", { scanKind(FileKind.IMAGE) }),
            Triple("🎞️", "Videos", { scanKind(FileKind.VIDEO) }),
            Triple("🎵", "Audio", { scanKind(FileKind.AUDIO) }),
            Triple("📄", "Documents", { scanKind(FileKind.DOC) }),
            Triple("📦", "Archives", { scanKind(FileKind.ARCHIVE) }),
            Triple("🕘", "Recent", { showVirtual("Recent", prefs.recents().map { File(it) }.filter { it.exists() }) })
        ))

        val marks = prefs.bookmarks().map { File(it) }.filter { it.exists() }
        if (marks.isNotEmpty()) {
            section("Bookmarks (long-press to remove)")
            marks.forEach { f ->
                val t = UiKit.text(ctx, "⭐ " + f.name + "  ·  " + (f.parent ?: ""), 14f)
                t.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 10), UiKit.dp(ctx, 12), UiKit.dp(ctx, 10))
                t.maxLines = 1; t.ellipsize = TextUtils.TruncateAt.MIDDLE
                t.setOnClickListener { if (f.isDirectory) open(f) else FileOpener.open(ctx, f) }
                t.setOnLongClickListener { prefs.removeBookmark(f.path); buildHome(); true }
                homeBox.addView(t)
            }
        }

        section("Phone manager")
        tiles(listOf(
            Triple("📊", "Storage analyzer", { startActivity(Intent(ctx, StorageAnalyzerActivity::class.java)) }),
            Triple("🐘", "Large files", { ScanListActivity.start(ctx, ScanListActivity.Mode.LARGE) }),
            Triple("🕰️", "Old files", { ScanListActivity.start(ctx, ScanListActivity.Mode.OLD) }),
            Triple("👯", "Duplicates", { ScanListActivity.start(ctx, ScanListActivity.Mode.DUPLICATES) }),
            Triple("🧹", "Junk cleaner", { ScanListActivity.start(ctx, ScanListActivity.Mode.JUNK) }),
            Triple("💬", "Chat media", { ScanListActivity.start(ctx, ScanListActivity.Mode.CHAT_MEDIA) }),
            Triple("📱", "APK files", { ScanListActivity.start(ctx, ScanListActivity.Mode.APKS) }),
            Triple("🧩", "App manager", { startActivity(Intent(ctx, AppManagerActivity::class.java)) }),
            Triple("🗑️", "Recycle Bin", { startActivity(Intent(ctx, TrashActivity::class.java)) }),
            Triple("📵", "Background data bot", { startActivity(Intent(ctx, com.crestpoint.datatracker.databot.DataBotActivity::class.java)) })
        ))
    }

    private fun scanKind(kind: FileKind) {
        spinner.visibility = View.VISIBLE
        homeScroll.visibility = View.GONE
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                val out = ArrayList<File>()
                for ((_, r) in roots()) {
                    val skip = FilePaths.skipForScan(r)
                    FileWalker.walk(r, { skip(it) || it.name.startsWith(".") }, { !isActive }) { f ->
                        if (out.size < 5000 && FileKinds.of(f) == kind) out.add(f)
                    }
                }
                out.sortedByDescending { it.lastModified() }
            }
            showVirtual(kind.label, res, true)
        }
    }

    // -------------------------------------------------------------- search

    private fun toggleSearch() {
        if (searchRow.visibility == View.VISIBLE) closeSearch() else {
            searchRow.visibility = View.VISIBLE; searchInput.requestFocus(); display()
        }
    }

    private fun closeSearch(redraw: Boolean = true) {
        searchInput.setText("")
        searchRow.visibility = View.GONE
        if (virtualTitle != null) { virtualTitle = null; searchMode = false }
        else if (searchMode && current != null) searchMode = false
        shownList = base
        if (redraw && isAdded) refresh()
    }

    private fun filterLocal(redraw: Boolean = true) {
        val q = searchInput.text.toString().trim()
        shownList = if (q.isEmpty()) base else base.filter { it.name.contains(q, ignoreCase = true) }
        if (redraw) display()
    }

    private fun searchEverywhere() {
        val q = searchInput.text.toString().trim()
        if (q.isEmpty()) { UiKit.toast(requireContext(), "Type something to search for"); return }
        spinner.visibility = View.VISIBLE
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                val out = ArrayList<File>()
                for ((_, r) in roots()) {
                    val skip = FilePaths.skipForScan(r)
                    FileWalker.walk(r, skip, { !isActive || out.size >= 2000 }, onDir = { d, kids ->
                        kids.forEach { if (it.isDirectory && it.name.contains(q, true) && out.size < 2000) out.add(it) }
                    }) { f -> if (f.name.contains(q, ignoreCase = true) && out.size < 2000) out.add(f) }
                }
                out
            }
            searchRow.visibility = View.VISIBLE
            showVirtual("Results for \"$q\"", res, true)
        }
    }

    // ------------------------------------------------------------- menus

    private fun sortMenu(anchor: View) {
        val m = PopupMenu(requireContext(), anchor)
        SortMode.values().forEach { m.menu.add((if (prefs.sort == it) "✓ " else "") + it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
        m.menu.add(if (prefs.asc) "Order: A→Z (tap to reverse)" else "Order: Z→A (tap to reverse)")
        m.setOnMenuItemClickListener { item ->
            val t = item.title.toString()
            if (t.startsWith("Order")) prefs.asc = !prefs.asc
            else SortMode.values().firstOrNull { t.removePrefix("✓ ").equals(it.name, true) }?.let { prefs.sort = it }
            refresh(); true
        }
        m.show()
    }

    private fun newMenu(anchor: View) {
        val dir = current ?: run { UiKit.toast(requireContext(), "Open a folder first"); return }
        val m = PopupMenu(requireContext(), anchor)
        m.menu.add("New folder"); m.menu.add("New text file")
        m.setOnMenuItemClickListener { item ->
            val folder = item.title == "New folder"
            UiKit.prompt(requireContext(), if (folder) "Folder name" else "File name", if (folder) "" else "note.txt") { n ->
                val name = n.trim()
                if (name.isEmpty() || name.contains('/')) { UiKit.toast(requireContext(), "Invalid name"); return@prompt }
                val f = File(dir, name)
                if (f.exists()) { UiKit.toast(requireContext(), "Already exists"); return@prompt }
                val ok = try { if (folder) f.mkdirs() else f.createNewFile() } catch (_: Exception) { false }
                UiKit.toast(requireContext(), if (ok) "Created" else "Could not create")
                refresh()
            }
            true
        }
        m.show()
    }

    private fun moreMenu(anchor: View) {
        val m = PopupMenu(requireContext(), anchor)
        m.menu.add(if (prefs.showHidden) "✓ Show hidden files" else "Show hidden files")
        m.menu.add("Select all")
        m.menu.add("Bookmark this folder")
        m.menu.add("Recycle Bin")
        m.menu.add("Refresh")
        m.setOnMenuItemClickListener { item ->
            when {
                item.title.toString().endsWith("Show hidden files") -> { prefs.showHidden = !prefs.showHidden; refresh() }
                item.title == "Select all" -> { shownList.forEach { selected.add(it.path) }; display() }
                item.title == "Bookmark this folder" -> current?.let { prefs.addBookmark(it.path); UiKit.toast(requireContext(), "Bookmarked") }
                item.title == "Recycle Bin" -> startActivity(Intent(requireContext(), TrashActivity::class.java))
                else -> refresh()
            }
            true
        }
        m.show()
    }

    // --------------------------------------------------------- selection

    private fun updateBars() {
        if (!isAdded || view == null) return
        val ctx = requireContext()
        selScroll.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        selBar.removeAllViews()
        if (selected.isNotEmpty()) {
            selBar.addView(UiKit.text(ctx, " ${selected.size} selected ", 13f, bold = true))
            fun b(label: String, a: () -> Unit) = selBar.addView(UiKit.button(ctx, label, true) { a() })
            b("Copy") { setClip(false) }
            b("Cut") { setClip(true) }
            b("Delete") { deleteSelected() }
            b("Zip") { zipDialog() }
            b("Share") { FileOpener.share(ctx, selectedFiles()) }
            b("Rename") { renameSelected() }
            b("All") { shownList.forEach { selected.add(it.path) }; display() }
            b("Info") { properties() }
            b("Open with") { selectedFiles().firstOrNull { it.isFile }?.let { FileOpener.external(ctx, it) } }
            b("Cancel") { selected.clear(); display() }
        }
        val clip = FileClipboard.files
        val show = clip.isNotEmpty() && current != null
        pasteBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) pasteLabel.text = "${if (FileClipboard.move) "Move" else "Copy"} ${clip.size} item(s)"
    }

    private fun selectedFiles(): List<File> = selected.map { File(it) }.filter { it.exists() }

    private fun setClip(move: Boolean) {
        FileClipboard.files = selectedFiles(); FileClipboard.move = move
        selected.clear()
        UiKit.toast(requireContext(), "Open the destination folder and tap Paste")
        display()
    }

    private fun paste() {
        val dest = current ?: return
        val files = FileClipboard.files
        if (files.isEmpty()) return
        val conflicts = files.any { File(dest, it.name).exists() }
        val go = { policy: FileOps.Conflict ->
            FileJobs.submit(requireContext(), FileJob.Copy(files, dest, policy, FileClipboard.move))
            if (FileClipboard.move) FileClipboard.clear()
            UiKit.toast(requireContext(), "Working in the background…")
            updateBars()
        }
        if (!conflicts) { go(FileOps.Conflict.KEEP_BOTH); return }
        MaterialAlertDialogBuilder(requireContext()).setTitle("Some items already exist here")
            .setItems(arrayOf("Replace", "Skip existing", "Keep both")) { _, i ->
                go(arrayOf(FileOps.Conflict.REPLACE, FileOps.Conflict.SKIP, FileOps.Conflict.KEEP_BOTH)[i])
            }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteSelected() {
        val files = selectedFiles(); if (files.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext()).setTitle("Delete ${files.size} item(s)?")
            .setMessage("Move to the Recycle Bin, or delete permanently.")
            .setPositiveButton("Recycle Bin") { _, _ -> FileJobs.submit(requireContext(), FileJob.Delete(files, false)); selected.clear(); display() }
            .setNeutralButton("Delete forever") { _, _ -> FileJobs.submit(requireContext(), FileJob.Delete(files, true)); selected.clear(); display() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun renameSelected() {
        val ctx = requireContext()
        val files = selectedFiles()
        if (files.size == 1) {
            val f = files[0]
            UiKit.prompt(ctx, "Rename", f.name) { n ->
                val t = File(f.parentFile, n.trim())
                if (n.isBlank() || n.contains('/')) UiKit.toast(ctx, "Invalid name")
                else if (t.exists()) UiKit.toast(ctx, "That name already exists")
                else UiKit.toast(ctx, if (f.renameTo(t)) "Renamed" else "Could not rename")
                selected.clear(); refresh()
            }
        } else if (files.size > 1) {
            UiKit.prompt(ctx, "Batch rename (### = counter)", "File_###") { pat ->
                var ok = 0
                files.forEachIndexed { i, f ->
                    val run = Regex("#+").find(pat)
                    val num = (i + 1).toString().padStart(run?.value?.length ?: 1, '0')
                    var name = if (run != null) pat.replaceRange(run.range, num) else "$pat$num"
                    if (f.isFile && !name.contains('.') && f.extension.isNotEmpty()) name += "." + f.extension
                    val t = File(f.parentFile, name)
                    if (!t.exists() && f.renameTo(t)) ok++
                }
                UiKit.toast(ctx, "Renamed $ok of ${files.size}")
                selected.clear(); refresh()
            }
        }
    }

    private fun zipDialog() {
        val ctx = requireContext()
        val files = selectedFiles(); if (files.isEmpty()) return
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val p = UiKit.dp(ctx, 20)
        box.setPadding(p, UiKit.dp(ctx, 8), p, 0)
        val name = EditText(ctx); name.setText(if (files.size == 1) files[0].nameWithoutExtension else "Archive"); name.hint = "Archive name"
        val pw = EditText(ctx); pw.hint = "Password (zip only, optional)"; pw.setSingleLine(true)
        pw.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(name); box.addView(pw)
        val formats = arrayOf("zip", "7z", "tar.gz")
        MaterialAlertDialogBuilder(ctx).setTitle("Create archive").setView(box)
            .setItems(formats) { _, i ->
                val fmt = formats[i]
                val dir = current ?: files[0].parentFile ?: return@setItems
                val out = FileOps.uniqueFile(dir, name.text.toString().ifBlank { "Archive" } + "." + fmt)
                FileJobs.submit(ctx, FileJob.Zip(files, out, fmt, pw.text.toString().ifEmpty { null }))
                selected.clear(); display()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun properties() {
        val ctx = requireContext()
        val files = selectedFiles(); if (files.isEmpty()) return
        val f = files[0]
        val tv = UiKit.text(ctx, "", 13f)
        val pad = UiKit.dp(ctx, 20)
        tv.setPadding(pad, pad, pad, 0)
        fun body(size: String) = (if (files.size > 1) "${files.size} items selected\n\n" else "") +
            "Name: ${f.name}\nPath: ${f.absolutePath}\nType: ${FileKinds.of(f).label}\nSize: $size\n" +
            "Modified: ${DateFormat.format("dd MMM yyyy, HH:mm", f.lastModified())}\n" +
            "Readable: ${f.canRead()}  Writable: ${f.canWrite()}"
        tv.text = body("calculating…")
        viewLifecycleOwner.lifecycleScope.launch {
            val total = withContext(Dispatchers.IO) { files.sumOf { FileOps.treeSize(it) } }
            tv.text = body(FormatBytes.human(total))
        }
        val b = MaterialAlertDialogBuilder(ctx).setTitle("Properties").setView(tv).setPositiveButton("OK", null)
        if (f.isFile) b.setNeutralButton("MD5 / SHA-256") { _, _ ->
            viewLifecycleOwner.lifecycleScope.launch {
                val r = withContext(Dispatchers.IO) { "MD5:\n${FileOps.hash(f, "MD5")}\n\nSHA-256:\n${FileOps.hash(f, "SHA-256")}" }
                MaterialAlertDialogBuilder(ctx).setTitle(f.name).setMessage(r).setPositiveButton("OK", null).show()
            }
        }
        b.show()
    }

    // ------------------------------------------------------------- adapter

    private fun rebuildAdapter() {
        val grid = prefs.grid
        adapter = FileAdapter(grid)
        list.layoutManager = if (grid) GridLayoutManager(requireContext(), 3) else LinearLayoutManager(requireContext())
        list.adapter = adapter
    }

    inner class FileAdapter(private val grid: Boolean) : RecyclerView.Adapter<FileAdapter.VH>() {
        var data: List<File> = emptyList()

        inner class VH(
            v: View, val emoji: TextView, val thumb: ImageView, val name: TextView, val sub: TextView?, val check: CheckBox
        ) : RecyclerView.ViewHolder(v) { var bound: String = "" }

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val iconSize = UiKit.dp(ctx, if (grid) 76 else 44)
            val box = FrameLayout(ctx)
            val emoji = UiKit.text(ctx, "📄", if (grid) 34f else 24f)
            emoji.gravity = Gravity.CENTER
            val thumb = ImageView(ctx)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.visibility = View.GONE
            box.addView(emoji, FrameLayout.LayoutParams(-1, -1))
            box.addView(thumb, FrameLayout.LayoutParams(-1, -1))
            val name = UiKit.text(ctx, "", if (grid) 12f else 15f)
            val check = CheckBox(ctx)
            check.isClickable = false
            check.visibility = View.GONE
            val row = LinearLayout(ctx)
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val pad = UiKit.dp(ctx, 8)
            row.setPadding(pad + 4, pad, pad + 4, pad)
            var sub: TextView? = null
            if (grid) {
                row.orientation = LinearLayout.VERTICAL
                row.gravity = Gravity.CENTER_HORIZONTAL
                name.gravity = Gravity.CENTER; name.maxLines = 2; name.ellipsize = TextUtils.TruncateAt.MIDDLE
                row.addView(box, LinearLayout.LayoutParams(iconSize, iconSize))
                row.addView(name, LinearLayout.LayoutParams(-1, -2))
                row.addView(check)
            } else {
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val col = LinearLayout(ctx)
                col.orientation = LinearLayout.VERTICAL
                name.maxLines = 1; name.ellipsize = TextUtils.TruncateAt.MIDDLE
                sub = UiKit.text(ctx, "", 11f, R.color.on_surface_muted)
                sub.maxLines = 1; sub.ellipsize = TextUtils.TruncateAt.MIDDLE
                col.addView(name); col.addView(sub)
                row.addView(check)
                row.addView(box, LinearLayout.LayoutParams(iconSize, iconSize))
                val lp = UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = UiKit.dp(ctx, 12)
                row.addView(col, lp)
            }
            return VH(row, emoji, thumb, name, sub, check)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val ctx = h.itemView.context
            val f = data[position]
            h.bound = f.path
            h.name.text = f.name
            val kind = FileKinds.of(f)
            h.emoji.text = kind.emoji
            h.emoji.visibility = View.VISIBLE
            h.thumb.visibility = View.GONE
            h.thumb.setImageDrawable(null)
            if (Thumbs.wants(f)) {
                Thumbs.load(ctx, f, UiKit.dp(ctx, 96)) { bmp ->
                    if (h.bound == f.path) { h.thumb.setImageBitmap(bmp); h.thumb.visibility = View.VISIBLE; h.emoji.visibility = View.GONE }
                }
            }
            val date = DateFormat.format("dd MMM yyyy, HH:mm", f.lastModified())
            h.sub?.text = when {
                searchMode -> f.parent ?: ""
                f.isDirectory -> date.toString()
                else -> FormatBytes.human(f.length()) + " · " + date
            }
            val sel = f.path in selected
            h.itemView.setBackgroundColor(if (sel) 0x337657F6 else 0)
            h.check.visibility = if (selected.isNotEmpty()) View.VISIBLE else View.GONE
            h.check.isChecked = sel
            h.itemView.setOnClickListener {
                if (selected.isNotEmpty()) toggle(f) else click(f)
            }
            h.itemView.setOnLongClickListener { toggle(f); true }
        }
    }

    private fun toggle(f: File) {
        if (!selected.remove(f.path)) selected.add(f.path)
        display()
    }

    private fun click(f: File) {
        if (f.isDirectory) open(f)
        else FileOpener.open(requireContext(), f, shownList.filter { it.isFile })
    }
}
