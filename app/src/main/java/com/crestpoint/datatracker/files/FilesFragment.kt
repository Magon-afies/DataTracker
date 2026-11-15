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
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.crestpoint.datatracker.databot.DataBotActivity
import com.crestpoint.datatracker.files.share.WifiShareActivity
import com.crestpoint.datatracker.files.remote.RemoteActivity
import com.crestpoint.datatracker.files.music.MusicActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.crestpoint.datatracker.files.tools.AppManagerActivity
import com.crestpoint.datatracker.files.tools.ScanListActivity
import com.crestpoint.datatracker.files.tools.StorageAnalyzerActivity
import com.crestpoint.datatracker.files.tools.TrashActivity
import com.crestpoint.datatracker.files.ui.ActionSheet
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Icons
import com.crestpoint.datatracker.files.ui.Palette
import com.crestpoint.datatracker.files.ui.RingView
import com.crestpoint.datatracker.files.ui.SheetAction
import com.crestpoint.datatracker.files.ui.Thumbs
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.files.vault.VaultActivity
import com.crestpoint.datatracker.util.FormatBytes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The Files tab: a hero storage ring, colourful categories, a fast explorer and floating actions. */
class FilesFragment : Fragment() {
    private lateinit var prefs: FilesPrefs
    private var current: File? = null
    private var virtualTitle: String? = null
    private var base: List<File> = emptyList()
    private var shownList: List<File> = emptyList()
    private var searchMode = false
    private val selected = LinkedHashSet<String>()

    private lateinit var titleText: TextView
    private lateinit var crumbScroll: HorizontalScrollView
    private lateinit var crumbs: LinearLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var homeScroll: ScrollView
    private lateinit var homeBox: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var emptyBox: LinearLayout
    private lateinit var permCard: LinearLayout
    private lateinit var spinner: ProgressBar
    private lateinit var fab: TextView
    private lateinit var floatBar: LinearLayout
    private lateinit var pasteBar: LinearLayout
    private lateinit var pasteLabel: TextView
    private var adapter: FileAdapter? = null
    private var loadJob: Job? = null
    private var lastKey: String? = null
    private val tabs = arrayListOf<String?>(null)
    private var tabIndex = 0
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var tabRow: LinearLayout
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
        val frame = FrameLayout(ctx)
        frame.setBackgroundColor(UiKit.color(ctx, R.color.surface))

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, UiKit.dp(ctx, 30), 0, 0)

        val header = LinearLayout(ctx)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 6), UiKit.dp(ctx, 6), 0)
        titleText = UiKit.text(ctx, "Files", 28f, bold = true)
        titleText.maxLines = 1
        titleText.ellipsize = TextUtils.TruncateAt.END
        header.addView(titleText, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(UiKit.iconBtn(ctx, "🔍") { toggleSearch() })
        header.addView(UiKit.iconBtn(ctx, "⇅") { sortMenu() })
        header.addView(UiKit.iconBtn(ctx, "⊞") { prefs.grid = !prefs.grid; rebuildAdapter(); display() })
        header.addView(UiKit.iconBtn(ctx, "⋮") { moreMenu() })
        col.addView(header)

        tabScroll = HorizontalScrollView(ctx)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabRow = LinearLayout(ctx)
        tabRow.gravity = Gravity.CENTER_VERTICAL
        tabRow.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 2), UiKit.dp(ctx, 14), UiKit.dp(ctx, 2))
        tabScroll.addView(tabRow)
        col.addView(tabScroll)

        crumbScroll = HorizontalScrollView(ctx)
        crumbScroll.isHorizontalScrollBarEnabled = false
        crumbScroll.visibility = View.GONE
        crumbs = LinearLayout(ctx)
        crumbs.gravity = Gravity.CENTER_VERTICAL
        crumbs.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 4), UiKit.dp(ctx, 14), UiKit.dp(ctx, 8))
        crumbScroll.addView(crumbs)
        col.addView(crumbScroll)

        searchRow = LinearLayout(ctx)
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.visibility = View.GONE
        searchRow.setPadding(UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 14), UiKit.dp(ctx, 6))
        searchInput = EditText(ctx)
        searchInput.hint = "Search files…"
        searchInput.setHintTextColor(UiKit.color(ctx, R.color.on_surface_muted))
        searchInput.setSingleLine(true)
        searchInput.setTextColor(UiKit.color(ctx, R.color.on_surface))
        searchInput.background = UiKit.rounded(ctx, R.color.surface_elevated, 22)
        searchInput.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 10), UiKit.dp(ctx, 16), UiKit.dp(ctx, 10))
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { if (!searchMode) filterLocal() }
        })
        searchRow.addView(searchInput, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(Design.chip(ctx, "All storage", true) { searchEverywhere() })
        col.addView(searchRow)

        val body = FrameLayout(ctx)
        homeScroll = ScrollView(ctx)
        homeScroll.isVerticalScrollBarEnabled = false
        homeBox = LinearLayout(ctx)
        homeBox.orientation = LinearLayout.VERTICAL
        homeBox.setPadding(0, 0, 0, UiKit.dp(ctx, 30))
        homeScroll.addView(homeBox)
        list = RecyclerView(ctx)
        list.visibility = View.GONE
        list.clipToPadding = false
        list.setPadding(0, 0, 0, UiKit.dp(ctx, 120))
        emptyBox = LinearLayout(ctx)
        emptyBox.orientation = LinearLayout.VERTICAL
        emptyBox.gravity = Gravity.CENTER
        emptyBox.visibility = View.GONE
        emptyBox.addView(UiKit.text(ctx, "🗂️", 54f))
        emptyBox.addView(UiKit.text(ctx, "Nothing here", 16f, bold = true).also { it.setPadding(0, UiKit.dp(ctx, 8), 0, 0) })
        emptyBox.addView(UiKit.text(ctx, "Tap ＋ to add a folder or file", 12f, R.color.on_surface_muted))
        spinner = ProgressBar(ctx)
        spinner.visibility = View.GONE
        permCard = UiKit.card(ctx)
        permCard.visibility = View.GONE
        permCard.addView(UiKit.text(ctx, "🔓 Allow file access", 18f, bold = true))
        permCard.addView(UiKit.text(ctx, "To browse, move and clean files, allow Data Tracker to manage all files. Everything stays on your phone.", 13f, R.color.on_surface_muted)
            .also { it.setPadding(0, UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 8)) })
        permCard.addView(UiKit.button(ctx, "Open settings") { openAccessSettings() })
        body.addView(homeScroll, FrameLayout.LayoutParams(-1, -1))
        body.addView(list, FrameLayout.LayoutParams(-1, -1))
        body.addView(emptyBox, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        body.addView(spinner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        body.addView(permCard, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        col.addView(body, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))

        // floating action button
        fab = Design.circleButton(ctx, "＋", 58, R.color.brand_purple) { fabMenu() }
        val flp = FrameLayout.LayoutParams(UiKit.dp(ctx, 58), UiKit.dp(ctx, 58), Gravity.BOTTOM or Gravity.END)
        flp.setMargins(0, 0, UiKit.dp(ctx, 18), UiKit.dp(ctx, 18))
        fab.visibility = View.GONE
        frame.addView(fab, flp)

        // paste pill
        pasteBar = LinearLayout(ctx)
        pasteBar.gravity = Gravity.CENTER_VERTICAL
        pasteBar.background = UiKit.rounded(ctx, R.color.surface_elevated, 28)
        pasteBar.elevation = UiKit.dp(ctx, 10).toFloat()
        pasteBar.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 6), UiKit.dp(ctx, 8), UiKit.dp(ctx, 6))
        pasteLabel = UiKit.text(ctx, "", 13f, bold = true)
        pasteBar.addView(pasteLabel, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pasteBar.addView(Design.chip(ctx, "Paste here", true) { paste() })
        pasteBar.addView(Design.chip(ctx, "✕", false) { FileClipboard.clear(); updateBars() })
        pasteBar.visibility = View.GONE
        val plp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        plp.setMargins(UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 90), UiKit.dp(ctx, 22))
        frame.addView(pasteBar, plp)

        // floating selection bar
        floatBar = LinearLayout(ctx)
        floatBar.gravity = Gravity.CENTER_VERTICAL
        floatBar.background = UiKit.rounded(ctx, R.color.surface_elevated, 28)
        floatBar.elevation = UiKit.dp(ctx, 14).toFloat()
        floatBar.setPadding(UiKit.dp(ctx, 8), UiKit.dp(ctx, 6), UiKit.dp(ctx, 8), UiKit.dp(ctx, 6))
        floatBar.visibility = View.GONE
        val blp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        blp.setMargins(UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 12), UiKit.dp(ctx, 16))
        frame.addView(floatBar, blp)
        return frame
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        rebuildAdapter()
        current = null
        refresh()
    }

    override fun onStart() { super.onStart(); FileJobs.listeners.add(jobListener); updateShortcuts() }
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

    // ---------------------------------------------------------------- tabs

    private fun tabLabel(i: Int): String {
        val p = tabs[i]
        return "${i + 1} · " + (if (p == null) "Home" else File(p).name.ifEmpty { "Storage" })
    }

    private fun buildTabs() {
        val ctx = requireContext()
        tabRow.removeAllViews()
        tabScroll.visibility = if (tabs.size > 1 || current != null) View.VISIBLE else View.GONE
        tabs.indices.forEach { i ->
            val c = Design.chip(ctx, tabLabel(i), i == tabIndex) { switchTab(i) }
            if (tabs.size > 1) c.setOnLongClickListener { closeTab(i); true }
            tabRow.addView(c)
        }
        tabRow.addView(Design.chip(ctx, "＋ Tab", false) { addTab() })
    }

    private fun addTab() {
        if (tabs.size >= 6) { UiKit.toast(requireContext(), "Up to 6 tabs"); return }
        tabs.add(current?.path)
        switchTab(tabs.size - 1)
        UiKit.toast(requireContext(), "New tab. Long-press a tab to close it.")
    }

    private fun closeTab(i: Int) {
        if (tabs.size <= 1) return
        tabs.removeAt(i)
        tabIndex = tabIndex.coerceAtMost(tabs.size - 1)
        val p = tabs[tabIndex]
        selected.clear()
        if (p != null) open(File(p)) else showHome()
    }

    private fun switchTab(i: Int) {
        if (i == tabIndex || i >= tabs.size) return
        tabs[tabIndex] = current?.path
        tabIndex = i
        selected.clear()
        val p = tabs[i]
        if (p != null && File(p).isDirectory) open(File(p)) else showHome()
    }

    private fun updateShortcuts() {
        val ctx = requireContext()
        try {
            fun sc(id: String, label: String, i: Intent) = ShortcutInfoCompat.Builder(ctx, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_stat_datatracker))
                .setIntent(i.setAction(Intent.ACTION_VIEW)).build()
            ShortcutManagerCompat.setDynamicShortcuts(ctx, listOf(
                sc("junk", "Clean junk", Intent(ctx, ScanListActivity::class.java).putExtra("mode", "JUNK")),
                sc("vault", "Vault", Intent(ctx, VaultActivity::class.java)),
                sc("share", "Wi-Fi share", Intent(ctx, WifiShareActivity::class.java)),
                sc("databot", "Background data bot", Intent(ctx, DataBotActivity::class.java))
            ))
        } catch (_: Exception) { }
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
            homeScroll.visibility = View.GONE; list.visibility = View.GONE; emptyBox.visibility = View.GONE
            fab.visibility = View.GONE
            return
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
        emptyBox.visibility = if (!home && shownList.isEmpty() && spinner.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        adapter?.data = shownList
        adapter?.notifyDataSetChanged()
        val key = current?.path ?: virtualTitle
        if (!home && key != lastKey) { list.alpha = 0.2f; list.animate().alpha(1f).setDuration(180).start() }
        lastKey = key
        if (tabIndex < tabs.size) tabs[tabIndex] = current?.path
        buildTabs()
        buildCrumbs()
        updateBars()
        backCallback.isEnabled = !home || selected.isNotEmpty() || searchRow.visibility == View.VISIBLE
    }

    private fun buildCrumbs() {
        val ctx = requireContext()
        crumbs.removeAllViews()
        val dir = current
        titleText.text = when {
            dir != null -> roots().firstOrNull { it.second.absolutePath == dir.absolutePath }?.first ?: dir.name
            virtualTitle != null -> virtualTitle
            else -> "Files"
        }
        if (dir == null) { crumbScroll.visibility = View.GONE; return }
        crumbScroll.visibility = View.VISIBLE
        crumbs.addView(Design.chip(ctx, "🏠", false) { showHome() })
        val rootPair = roots().firstOrNull { FileOps.isInside(dir, it.second) } ?: return
        val rootDir = rootPair.second
        val parts = dir.absolutePath.removePrefix(rootDir.absolutePath).split("/").filter { it.isNotEmpty() }
        crumbs.addView(Design.chip(ctx, rootPair.first, parts.isEmpty()) { open(rootDir) })
        var acc = rootDir
        parts.forEachIndexed { i, p ->
            acc = File(acc, p)
            val target = acc
            crumbs.addView(Design.chip(ctx, p, i == parts.lastIndex) { open(target) })
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    // ---------------------------------------------------------------- home

    private fun section(title: String) {
        val ctx = requireContext()
        val t = UiKit.text(ctx, title, 13f, R.color.on_surface_muted, bold = true)
        t.setPadding(UiKit.dp(ctx, 20), UiKit.dp(ctx, 20), 0, UiKit.dp(ctx, 8))
        homeBox.addView(t)
    }

    private fun gridRows(cells: List<View>, perRow: Int) {
        val ctx = requireContext()
        cells.chunked(perRow).forEach { chunk ->
            val row = LinearLayout(ctx)
            row.setPadding(UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 10), 0)
            chunk.forEach { v ->
                val lp = UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(UiKit.dp(ctx, 5), UiKit.dp(ctx, 5), UiKit.dp(ctx, 5), UiKit.dp(ctx, 5))
                row.addView(v, lp)
            }
            repeat(perRow - chunk.size) { row.addView(View(ctx), UiKit.lp(0, 1, 1f)) }
            homeBox.addView(row)
        }
    }

    private fun catTile(emoji: String, label: String, color: Int, onClick: () -> Unit): View {
        val ctx = requireContext()
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.background = UiKit.rounded(ctx, R.color.surface_elevated, 20)
        c.setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 14), UiKit.dp(ctx, 6), UiKit.dp(ctx, 12))
        val tile = Design.iconTile(ctx, emoji, color, 48, 16, 24f)
        c.addView(tile)
        c.addView(UiKit.text(ctx, label, 12f).also { it.setPadding(0, UiKit.dp(ctx, 8), 0, 0); it.gravity = Gravity.CENTER; it.maxLines = 1 })
        c.setOnClickListener { onClick() }
        Design.ripple(c)
        return c
    }

    private fun toolCard(emoji: String, title: String, sub: String, color: Int, onClick: () -> Unit): View {
        val ctx = requireContext()
        val c = LinearLayout(ctx)
        c.gravity = Gravity.CENTER_VERTICAL
        c.background = UiKit.rounded(ctx, R.color.surface_elevated, 20)
        c.setPadding(UiKit.dp(ctx, 12), UiKit.dp(ctx, 12), UiKit.dp(ctx, 10), UiKit.dp(ctx, 12))
        c.addView(Design.iconTile(ctx, emoji, color, 42, 14, 21f))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(ctx, 10), 0, 0, 0)
        col.addView(UiKit.text(ctx, title, 14f, bold = true).also { it.maxLines = 1; it.ellipsize = TextUtils.TruncateAt.END })
        col.addView(UiKit.text(ctx, sub, 11f, R.color.on_surface_muted).also { it.maxLines = 2 })
        c.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.setOnClickListener { onClick() }
        Design.ripple(c)
        return c
    }

    private fun buildHome() {
        val ctx = requireContext()
        homeBox.removeAllViews()

        // hero storage card
        val rts = roots()
        val first = rts.first()
        val hero = LinearLayout(ctx)
        hero.gravity = Gravity.CENTER_VERTICAL
        val g = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(UiKit.color(ctx, R.color.brand_purple_dark), UiKit.color(ctx, R.color.surface_elevated))
        )
        g.cornerRadius = UiKit.dp(ctx, 26).toFloat()
        hero.background = g
        hero.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 18), UiKit.dp(ctx, 18), UiKit.dp(ctx, 18))
        val hlp = LinearLayout.LayoutParams(-1, -2)
        hlp.setMargins(UiKit.dp(ctx, 14), UiKit.dp(ctx, 8), UiKit.dp(ctx, 14), UiKit.dp(ctx, 4))
        val ring = RingView(ctx)
        hero.addView(ring, LinearLayout.LayoutParams(UiKit.dp(ctx, 112), UiKit.dp(ctx, 112)))
        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        info.setPadding(UiKit.dp(ctx, 16), 0, 0, 0)
        try {
            val st = StatFs(first.second.path)
            val used = st.totalBytes - st.availableBytes
            val frac = used.toFloat() / st.totalBytes
            ring.center = "${(frac * 100).toInt()}%"; ring.sub = "used"
            ring.animateTo(frac)
            info.addView(UiKit.text(ctx, first.first, 17f, bold = true))
            info.addView(UiKit.text(ctx, "${FormatBytes.human(st.availableBytes)} free of ${FormatBytes.human(st.totalBytes)}", 12f, R.color.on_surface_muted))
        } catch (_: Exception) { info.addView(UiKit.text(ctx, first.first, 17f, bold = true)) }
        val chips = LinearLayout(ctx)
        chips.setPadding(0, UiKit.dp(ctx, 10), 0, 0)
        chips.addView(Design.chip(ctx, "Browse", true) { open(first.second) })
        chips.addView(Design.chip(ctx, "Clean", false) { ScanListActivity.start(ctx, ScanListActivity.Mode.JUNK) })
        info.addView(chips)
        hero.addView(info, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        homeBox.addView(hero, hlp)

        val tipBox = LinearLayout(ctx)
        tipBox.orientation = LinearLayout.VERTICAL
        homeBox.addView(tipBox)
        viewLifecycleOwner.lifecycleScope.launch {
            val dl = File(FilePaths.internalRoot(), "Download")
            val info = withContext(Dispatchers.IO) {
                val trash = try { TrashStore.totalSize() } catch (_: Exception) { 0L }
                val dlSize = if (dl.isDirectory) FileOps.treeSize(dl) else 0L
                val apks = dl.listFiles()?.count { it.isFile && it.extension.equals("apk", true) } ?: 0
                Triple(trash, dlSize, apks)
            }
            if (!isAdded || tipBox.parent == null) return@launch
            val tips = ArrayList<Triple<String, String, () -> Unit>>()
            if (info.first > 20L * 1024 * 1024) tips.add(Triple("🗑️", "Recycle Bin holds ${FormatBytes.human(info.first)} · tap to review", { startActivity(Intent(ctx, TrashActivity::class.java)) }))
            if (info.third > 0) tips.add(Triple("📱", "${info.third} APK installer(s) in Downloads · tap to review", { ScanListActivity.start(ctx, ScanListActivity.Mode.APKS) }))
            if (info.second > 500L * 1024 * 1024) tips.add(Triple("⬇️", "Downloads folder is ${FormatBytes.human(info.second)} · tap to open", { open(dl) }))
            if (tips.isNotEmpty()) {
                val c = UiKit.card(ctx)
                c.addView(UiKit.text(ctx, "💡 Smart tips", 14f, bold = true))
                tips.forEach { (e, t, a) ->
                    val tv = UiKit.text(ctx, "$e  $t", 13f, R.color.brand_cyan)
                    tv.setPadding(0, UiKit.dp(ctx, 8), 0, 0)
                    tv.setOnClickListener { a() }
                    c.addView(tv)
                }
                tipBox.addView(c)
            }
        }

        rts.drop(1).forEach { (name, dir) ->
            val c = UiKit.card(ctx)
            try {
                val st = StatFs(dir.path)
                val used = st.totalBytes - st.availableBytes
                c.addView(UiKit.text(ctx, "💾 $name", 15f, bold = true))
                c.addView(UiKit.text(ctx, "${FormatBytes.human(used)} used of ${FormatBytes.human(st.totalBytes)}", 12f, R.color.on_surface_muted))
                c.addView(UiKit.progress(ctx, used.toFloat() / st.totalBytes))
            } catch (_: Exception) { c.addView(UiKit.text(ctx, "💾 $name", 15f, bold = true)) }
            c.setOnClickListener { open(dir) }
            homeBox.addView(c)
        }

        // quick access pills
        val ext = FilePaths.internalRoot()
        val quick = listOf("Download" to "⬇️", "DCIM" to "📷", "Pictures" to "🖼️", "Music" to "🎵", "Movies" to "🎞️", "Documents" to "📄")
            .filter { File(ext, it.first).isDirectory }
        if (quick.isNotEmpty()) {
            section("QUICK ACCESS")
            val hs = HorizontalScrollView(ctx)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(ctx)
            row.setPadding(UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 14), 0)
            quick.forEach { (n, e) -> row.addView(Design.chip(ctx, "$e  $n", false) { open(File(ext, n)) }) }
            hs.addView(row)
            homeBox.addView(hs)
        }

        section("CATEGORIES")
        gridRows(listOf(
            catTile("🖼️", "Images", Palette.kind(FileKind.IMAGE)) { scanKind(FileKind.IMAGE) },
            catTile("🎞️", "Videos", Palette.kind(FileKind.VIDEO)) { scanKind(FileKind.VIDEO) },
            catTile("🎵", "Audio", Palette.kind(FileKind.AUDIO)) { scanKind(FileKind.AUDIO) },
            catTile("📄", "Documents", Palette.kind(FileKind.DOC)) { scanKind(FileKind.DOC) },
            catTile("📦", "Archives", Palette.kind(FileKind.ARCHIVE)) { scanKind(FileKind.ARCHIVE) },
            catTile("🕘", "Recent", 0xFF9FA8DA.toInt()) { showVirtual("Recent", prefs.recents().map { File(it) }.filter { it.exists() }) }
        ), 3)

        val marks = prefs.bookmarks().map { File(it) }.filter { it.exists() }
        if (marks.isNotEmpty()) {
            section("BOOKMARKS · long-press to remove")
            val row = HorizontalScrollView(ctx)
            row.isHorizontalScrollBarEnabled = false
            val inner = LinearLayout(ctx)
            inner.setPadding(UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 14), 0)
            marks.forEach { f ->
                val c = Design.chip(ctx, "⭐ " + f.name, false) { if (f.isDirectory) open(f) else FileOpener.open(ctx, f) }
                c.setOnLongClickListener { prefs.removeBookmark(f.path); buildHome(); true }
                inner.addView(c)
            }
            row.addView(inner)
            homeBox.addView(row)
        }

        section("PHONE MANAGER")
        gridRows(listOf(
            toolCard("🧹", "Junk cleaner", "Free up space safely", 0xFFFF8A65.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.JUNK) },
            toolCard("👯", "Duplicates", "Find identical copies", 0xFF64B5F6.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.DUPLICATES) },
            toolCard("🐘", "Large files", "Biggest space hogs", 0xFFFFB74D.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.LARGE) },
            toolCard("🕰️", "Old files", "Untouched for months", 0xFF9FA8DA.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.OLD) },
            toolCard("📊", "Analyzer", "See what's using storage", 0xFF4DB6AC.toInt()) { startActivity(Intent(ctx, StorageAnalyzerActivity::class.java)) },
            toolCard("💬", "Chat media", "WhatsApp & Telegram", 0xFF81C784.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.CHAT_MEDIA) },
            toolCard("🧩", "App manager", "Backup & uninstall", 0xFFBA68C8.toInt()) { startActivity(Intent(ctx, AppManagerActivity::class.java)) },
            toolCard("📵", "Data bot", "Stop background data", 0xFF26C6DA.toInt()) { startActivity(Intent(ctx, DataBotActivity::class.java)) },
            toolCard("🔐", "Vault", "Locked & encrypted", 0xFF7657F6.toInt()) { startActivity(Intent(ctx, VaultActivity::class.java)) },
            toolCard("📡", "Wi-Fi share", "Use files from your PC", 0xFF4FC3F7.toInt()) { startActivity(Intent(ctx, WifiShareActivity::class.java)) },
            toolCard("🌐", "Remote (FTP)", "Servers, NAS, hosting", 0xFF4DD0E1.toInt()) { startActivity(Intent(ctx, RemoteActivity::class.java)) },
            toolCard("🎧", "Music player", "Plays in background", 0xFFF06292.toInt()) { startActivity(Intent(ctx, MusicActivity::class.java)) },
            toolCard("📱", "APK files", "Installers on this phone", 0xFFAED581.toInt()) { ScanListActivity.start(ctx, ScanListActivity.Mode.APKS) },
            toolCard("🗑️", "Recycle Bin", "Restore deleted files", 0xFF90A4AE.toInt()) { startActivity(Intent(ctx, TrashActivity::class.java)) }
        ), 2)
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
                    FileWalker.walk(r, skip, { !isActive || out.size >= 2000 }, onDir = { _, kids ->
                        kids.forEach { if (it.isDirectory && it.name.contains(q, true) && out.size < 2000) out.add(it) }
                    }) { f -> if (f.name.contains(q, ignoreCase = true) && out.size < 2000) out.add(f) }
                }
                out
            }
            searchRow.visibility = View.VISIBLE
            showVirtual("Results: $q", res, true)
        }
    }

    // ------------------------------------------------------------- menus

    private fun sortMenu() {
        val ctx = requireContext()
        val acts = ArrayList<SheetAction>()
        SortMode.values().forEach { m ->
            val label = m.name.lowercase().replaceFirstChar { it.uppercase() }
            acts.add(SheetAction(if (prefs.sort == m) "✅" else "▫️", "Sort by $label") { prefs.sort = m; refresh() })
        }
        acts.add(SheetAction("↕️", if (prefs.asc) "Order: A → Z (tap to reverse)" else "Order: Z → A (tap to reverse)") { prefs.asc = !prefs.asc; refresh() })
        ActionSheet.show(ctx, "Sort", acts)
    }

    private fun fabMenu() {
        val ctx = requireContext()
        val dir = current ?: return
        val acts = arrayListOf(
            SheetAction("📁", "New folder") { createItem(dir, true) },
            SheetAction("📝", "New text file") { createItem(dir, false) }
        )
        ActionSheet.show(ctx, "Create in ${dir.name}", acts)
    }

    private fun createItem(dir: File, folder: Boolean) {
        val ctx = requireContext()
        UiKit.prompt(ctx, if (folder) "Folder name" else "File name", if (folder) "" else "note.txt") { n ->
            val name = n.trim()
            if (name.isEmpty() || name.contains('/')) { UiKit.toast(ctx, "Invalid name"); return@prompt }
            val f = File(dir, name)
            if (f.exists()) { UiKit.toast(ctx, "Already exists"); return@prompt }
            val ok = try { if (folder) f.mkdirs() else f.createNewFile() } catch (_: Exception) { false }
            UiKit.toast(ctx, if (ok) "Created" else "Could not create")
            refresh()
        }
    }

    private fun moreMenu() {
        val ctx = requireContext()
        val acts = arrayListOf(
            SheetAction(if (prefs.showHidden) "✅" else "👁️", "Show hidden files") { prefs.showHidden = !prefs.showHidden; refresh() },
            SheetAction("☑️", "Select all") { shownList.forEach { selected.add(it.path) }; display() },
            SheetAction("🗑️", "Recycle Bin") { startActivity(Intent(ctx, TrashActivity::class.java)) },
            SheetAction("🔄", "Refresh") { refresh() }
        )
        current?.let { c -> acts.add(1, SheetAction("⭐", "Bookmark this folder") { prefs.addBookmark(c.path); UiKit.toast(ctx, "Bookmarked") }) }
        ActionSheet.show(ctx, null, acts)
    }

    // --------------------------------------------------------- selection

    private fun updateBars() {
        if (!isAdded || view == null) return
        val ctx = requireContext()
        val home = current == null && virtualTitle == null
        val sel = selected.isNotEmpty()
        floatBar.visibility = if (sel) View.VISIBLE else View.GONE
        fab.visibility = if (!sel && current != null && FileClipboard.files.isEmpty()) View.VISIBLE else View.GONE
        floatBar.removeAllViews()
        if (sel) {
            floatBar.addView(Design.chip(ctx, "✕  ${selected.size}", true) { selected.clear(); display() })
            fun act(emoji: String, label: String, run: () -> Unit) {
                val c = LinearLayout(ctx)
                c.orientation = LinearLayout.VERTICAL
                c.gravity = Gravity.CENTER
                c.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 4), UiKit.dp(ctx, 10), UiKit.dp(ctx, 4))
                c.addView(UiKit.text(ctx, emoji, 20f).also { it.gravity = Gravity.CENTER; Icons.set(it, emoji) })
                c.addView(UiKit.text(ctx, label, 10f, R.color.on_surface_muted).also { it.gravity = Gravity.CENTER })
                c.setOnClickListener { run() }
                Design.ripple(c)
                floatBar.addView(c, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            act("📋", "Copy") { setClip(false) }
            act("✂️", "Cut") { setClip(true) }
            act("🗑️", "Delete") { deleteSelected() }
            act("📤", "Share") { FileOpener.share(ctx, selectedFiles()) }
            act("⋯", "More") { selectionMenu() }
        }
        val clip = FileClipboard.files
        val show = clip.isNotEmpty() && current != null && !sel && !home
        pasteBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) pasteLabel.text = "${if (FileClipboard.move) "Move" else "Copy"} ${clip.size} item(s)"
    }

    private fun selectionMenu() {
        val ctx = requireContext()
        val files = selectedFiles()
        val acts = arrayListOf(
            SheetAction("✏️", "Rename") { renameSelected() },
            SheetAction("📦", "Compress…") { zipDialog() },
            SheetAction("ℹ️", "Properties & checksums") { properties() },
            SheetAction("🔐", "Move to Vault") { VaultActivity.importFiles(ctx, files) },
            SheetAction("☑️", "Select all") { shownList.forEach { selected.add(it.path) }; display() }
        )
        if (files.any { it.isFile }) acts.add(2, SheetAction("🔗", "Open with…") { files.firstOrNull { it.isFile }?.let { FileOpener.external(ctx, it) } })
        val others = tabs.withIndex().filter { (i, p) -> i != tabIndex && p != null && File(p).isDirectory }
        others.forEach { (i, p) ->
            val dest = File(p!!)
            acts.add(0, SheetAction("📋", "Copy to tab ${i + 1} (${dest.name})") {
                FileJobs.submit(ctx, FileJob.Copy(files, dest, FileOps.Conflict.KEEP_BOTH, false)); selected.clear(); display()
            })
            acts.add(1, SheetAction("✂️", "Move to tab ${i + 1} (${dest.name})") {
                FileJobs.submit(ctx, FileJob.Copy(files, dest, FileOps.Conflict.KEEP_BOTH, true)); selected.clear(); display()
            })
        }
        ActionSheet.show(ctx, "${selected.size} selected", acts)
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
            v: View, val tile: FrameLayout, val emoji: TextView, val thumb: ImageView, val name: TextView, val sub: TextView?
        ) : RecyclerView.ViewHolder(v) { var bound: String = "" }

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val tile = Design.iconTile(ctx, "📄", Palette.kind(FileKind.OTHER), if (grid) 84 else 48, if (grid) 20 else 15, if (grid) 34f else 24f)
            val emoji = tile.tag as TextView
            val thumb = ImageView(ctx)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.visibility = View.GONE
            tile.addView(thumb, FrameLayout.LayoutParams(-1, -1))
            val name = UiKit.text(ctx, "", if (grid) 12f else 15f)
            val row = LinearLayout(ctx)
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val pad = UiKit.dp(ctx, 8)
            row.setPadding(UiKit.dp(ctx, 14), pad, UiKit.dp(ctx, 14), pad)
            var sub: TextView? = null
            if (grid) {
                row.orientation = LinearLayout.VERTICAL
                row.gravity = Gravity.CENTER_HORIZONTAL
                name.gravity = Gravity.CENTER; name.maxLines = 2; name.ellipsize = TextUtils.TruncateAt.MIDDLE
                name.setPadding(0, UiKit.dp(ctx, 4), 0, 0)
                row.addView(tile)
                row.addView(name, LinearLayout.LayoutParams(-1, -2))
            } else {
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val col = LinearLayout(ctx)
                col.orientation = LinearLayout.VERTICAL
                name.maxLines = 1; name.ellipsize = TextUtils.TruncateAt.MIDDLE
                sub = UiKit.text(ctx, "", 12f, R.color.on_surface_muted)
                sub.maxLines = 1; sub.ellipsize = TextUtils.TruncateAt.MIDDLE
                col.addView(name); col.addView(sub)
                row.addView(tile)
                val lp = UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = UiKit.dp(ctx, 14)
                row.addView(col, lp)
            }
            Design.ripple(row)
            return VH(row, tile, emoji, thumb, name, sub)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val ctx = h.itemView.context
            val f = data[position]
            h.bound = f.path
            h.name.text = f.name
            val kind = FileKinds.of(f)
            val sel = f.path in selected
            val color = if (sel) UiKit.color(ctx, R.color.brand_purple) else Palette.kind(kind)
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(if (sel) color else Palette.withAlpha(color, 0x33))
            bg.cornerRadius = UiKit.dp(ctx, if (grid) 20 else 15).toFloat()
            h.tile.background = bg
            Icons.set(h.emoji, if (sel) "✓" else kind.emoji, if (sel) 0xFFFFFFFF.toInt() else color)
            h.emoji.visibility = View.VISIBLE
            h.thumb.visibility = View.GONE
            h.thumb.setImageDrawable(null)
            if (!sel && Thumbs.wants(f)) {
                Thumbs.load(ctx, f, UiKit.dp(ctx, 120)) { bmp ->
                    if (h.bound == f.path) { h.thumb.setImageBitmap(bmp); h.thumb.visibility = View.VISIBLE; h.emoji.visibility = View.GONE }
                }
            }
            val date = DateFormat.format("dd MMM yyyy", f.lastModified())
            h.sub?.text = when {
                searchMode -> f.parent ?: ""
                f.isDirectory -> "Folder · $date"
                else -> FormatBytes.human(f.length()) + " · " + date
            }
            h.itemView.setBackgroundColor(if (sel) 0x227657F6 else 0)
            h.itemView.setOnClickListener { if (selected.isNotEmpty()) toggle(f) else click(f) }
            h.itemView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                toggle(f); true
            }
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
