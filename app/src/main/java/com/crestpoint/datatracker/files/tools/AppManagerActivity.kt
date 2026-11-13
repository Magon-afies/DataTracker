package com.crestpoint.datatracker.files.tools

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FileOps
import com.crestpoint.datatracker.files.FilePaths
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Installed apps with size/dates, batch APK backup and batch uninstall. */
class AppManagerActivity : AppCompatActivity() {
    data class AppRow(
        val pkg: String, val label: String, val version: String, val installed: Long,
        val updated: Long, val size: Long, val system: Boolean, val apks: List<File>
    )

    private var all: List<AppRow> = emptyList()
    private var shown: List<AppRow> = emptyList()
    private val checked = LinkedHashSet<String>()
    private var showSystem = false
    private var sortIdx = 0
    private val sortNames = listOf("Size", "Name", "Recently installed", "Recently updated")
    private lateinit var summary: TextView
    private lateinit var adapter: Adapter
    private val uninstallQueue = ArrayDeque<String>()
    private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { nextUninstall() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        root.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 36), UiKit.dp(this, 12), UiKit.dp(this, 12))
        root.addView(UiKit.text(this, "App manager", 22f, bold = true))
        summary = UiKit.text(this, "Loading…", 13f, R.color.on_surface_muted)
        root.addView(summary)
        val bar = LinearLayout(this)
        val sortBtn = UiKit.button(this, "Sort: ${sortNames[0]}", true) { }
        sortBtn.setOnClickListener { sortIdx = (sortIdx + 1) % sortNames.size; sortBtn.text = "Sort: ${sortNames[sortIdx]}"; applyFilter() }
        val sysBtn = UiKit.button(this, "System apps: hidden", true) { }
        sysBtn.setOnClickListener { showSystem = !showSystem; sysBtn.text = "System apps: ${if (showSystem) "shown" else "hidden"}"; applyFilter() }
        bar.addView(sortBtn); bar.addView(sysBtn)
        val hs = android.widget.HorizontalScrollView(this); hs.addView(bar)
        root.addView(hs)
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = Adapter()
        list.adapter = adapter
        root.addView(list, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val actions = LinearLayout(this)
        actions.addView(UiKit.button(this, "Backup APKs") { backup() })
        actions.addView(UiKit.button(this, "Uninstall", true) { uninstall() })
        root.addView(actions)
        setContentView(root)
        load()
    }

    override fun onResume() { super.onResume(); if (all.isNotEmpty()) load() }

    @Suppress("DEPRECATION")
    private fun load() {
        lifecycleScope.launch {
            all = withContext(Dispatchers.IO) {
                val pm = packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { ai ->
                    try {
                        val pi = pm.getPackageInfo(ai.packageName, 0)
                        val apks = ArrayList<File>()
                        apks.add(File(ai.sourceDir))
                        ai.splitSourceDirs?.forEach { apks.add(File(it)) }
                        AppRow(
                            ai.packageName, pm.getApplicationLabel(ai).toString(), pi.versionName ?: "?",
                            pi.firstInstallTime, pi.lastUpdateTime, apks.sumOf { it.length() },
                            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, apks
                        )
                    } catch (_: Exception) { null }
                }
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val base = all.filter { showSystem || !it.system }
        shown = when (sortIdx) {
            0 -> base.sortedByDescending { it.size }
            1 -> base.sortedBy { it.label.lowercase() }
            2 -> base.sortedByDescending { it.installed }
            else -> base.sortedByDescending { it.updated }
        }
        checked.retainAll(shown.map { it.pkg }.toSet())
        summary.text = "${shown.size} apps · ${FormatBytes.human(shown.sumOf { it.size })} of APK files"
        adapter.notifyDataSetChanged()
    }

    private fun backup() {
        val sel = shown.filter { it.pkg in checked }
        if (sel.isEmpty()) { UiKit.toast(this, "Tick the apps to back up"); return }
        lifecycleScope.launch {
            val dir = FilePaths.backupDir()
            val ok = withContext(Dispatchers.IO) {
                dir.mkdirs()
                var n = 0
                for (a in sel) {
                    try {
                        val safe = (a.label + "_" + a.version).replace(Regex("[^A-Za-z0-9._-]+"), "_")
                        val p = FileOps.Progress()
                        if (a.apks.size == 1) {
                            FileOps.copyFile(a.apks[0], FileOps.uniqueFile(dir, "$safe.apk"), p) {}
                        } else {
                            val d = FileOps.uniqueFile(dir, safe); d.mkdirs()
                            a.apks.forEach { FileOps.copyFile(it, File(d, it.name), p) {} }
                        }
                        n++
                    } catch (_: Exception) { }
                }
                n
            }
            UiKit.toast(this@AppManagerActivity, "Backed up $ok app(s) to ${dir.absolutePath}")
        }
    }

    private fun uninstall() {
        val sel = shown.filter { it.pkg in checked && !it.system }
        if (sel.isEmpty()) { UiKit.toast(this, "Tick user apps to uninstall"); return }
        UiKit.confirm(this, "Uninstall ${sel.size} app(s)?", "Android will ask you to confirm each one.", "Continue") {
            uninstallQueue.clear(); sel.forEach { uninstallQueue.addLast(it.pkg) }
            nextUninstall()
        }
    }

    private fun nextUninstall() {
        val p = uninstallQueue.removeFirstOrNull()
        if (p == null) { checked.clear(); load(); return }
        uninstallLauncher.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$p")))
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: LinearLayout, val cb: CheckBox, val icon: ImageView, val n: TextView, val s: TextView) : RecyclerView.ViewHolder(v)
        override fun getItemCount() = shown.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx); row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val cb = CheckBox(ctx)
            val icon = ImageView(ctx); icon.layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, 40), UiKit.dp(ctx, 40))
            val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL; col.setPadding(UiKit.dp(ctx, 10), 0, 0, 0)
            val n = UiKit.text(ctx, "", 14f); val s = UiKit.text(ctx, "", 11f, R.color.on_surface_muted)
            col.addView(n); col.addView(s)
            row.addView(cb); row.addView(icon); row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VH(row, cb, icon, n, s)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val a = shown[position]
            h.n.text = a.label
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            h.s.text = "v${a.version} · ${FormatBytes.human(a.size)} · installed ${df.format(Date(a.installed))}\n${a.pkg}"
            try { h.icon.setImageDrawable(packageManager.getApplicationIcon(a.pkg)) } catch (_: Exception) { h.icon.setImageDrawable(null) }
            h.cb.setOnCheckedChangeListener(null)
            h.cb.isChecked = a.pkg in checked
            h.cb.setOnCheckedChangeListener { _, on -> if (on) checked.add(a.pkg) else checked.remove(a.pkg) }
            h.itemView.setOnClickListener { h.cb.isChecked = !h.cb.isChecked }
            h.itemView.setOnLongClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))); true
            }
        }
    }
}
