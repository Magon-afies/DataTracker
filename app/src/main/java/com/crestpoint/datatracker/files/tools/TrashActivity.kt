package com.crestpoint.datatracker.files.tools

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.FilesPrefs
import com.crestpoint.datatracker.files.TrashStore
import com.crestpoint.datatracker.files.ui.UiKit
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashActivity : AppCompatActivity() {
    private var items: List<TrashStore.Entry> = emptyList()
    private val checked = HashSet<String>()
    private lateinit var summary: TextView
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        root.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 36), UiKit.dp(this, 12), UiKit.dp(this, 12))
        root.addView(UiKit.text(this, "Recycle Bin", 22f, bold = true))
        summary = UiKit.text(this, "", 13f, R.color.on_surface_muted)
        root.addView(summary)
        val bar = LinearLayout(this)
        bar.addView(UiKit.button(this, "Restore") { restore() })
        bar.addView(UiKit.button(this, "Delete forever", true) { deleteSel() })
        bar.addView(UiKit.button(this, "Empty all", true) {
            UiKit.confirm(this, "Empty Recycle Bin?", "Everything inside will be permanently deleted.", "Empty") {
                lifecycleScope.launch { withContext(Dispatchers.IO) { TrashStore.emptyAll() }; load() }
            }
        })
        val hs = android.widget.HorizontalScrollView(this); hs.addView(bar)
        root.addView(hs)
        val auto = UiKit.button(this, "Auto-empty after: ${FilesPrefs(this).trashDays} days", true) { }
        auto.setOnClickListener {
            UiKit.prompt(this, "Auto-empty after how many days? (0 = never)", FilesPrefs(this).trashDays.toString()) { s ->
                s.trim().toIntOrNull()?.let { FilesPrefs(this).trashDays = it.coerceIn(0, 365); auto.text = "Auto-empty after: $it days" }
            }
        }
        root.addView(auto)
        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = Adapter()
        list.adapter = adapter
        root.addView(list, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            items = withContext(Dispatchers.IO) { TrashStore.purgeOlderThan(FilesPrefs(this@TrashActivity).trashDays); TrashStore.list() }
            checked.clear()
            summary.text = "${items.size} item(s) · ${FormatBytes.human(items.sumOf { it.size })}"
            adapter.notifyDataSetChanged()
        }
    }

    private fun selected() = items.filter { it.id in checked }

    private fun restore() {
        val sel = selected(); if (sel.isEmpty()) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { sel.count { TrashStore.restore(it) } }
            UiKit.toast(this@TrashActivity, "Restored $ok of ${sel.size}")
            load()
        }
    }

    private fun deleteSel() {
        val sel = selected(); if (sel.isEmpty()) return
        UiKit.confirm(this, "Delete ${sel.size} item(s) forever?", "This cannot be undone.", "Delete") {
            lifecycleScope.launch { withContext(Dispatchers.IO) { sel.forEach { TrashStore.deleteForever(it) } }; load() }
        }
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: LinearLayout, val cb: CheckBox, val n: TextView, val s: TextView) : RecyclerView.ViewHolder(v)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx); row.gravity = Gravity.CENTER_VERTICAL
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val cb = CheckBox(ctx)
            val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
            val n = UiKit.text(ctx, "", 14f); val s = UiKit.text(ctx, "", 11f, R.color.on_surface_muted)
            col.addView(n); col.addView(s)
            row.addView(cb); row.addView(col, UiKit.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VH(row, cb, n, s)
        }
        override fun onBindViewHolder(h: VH, position: Int) {
            val e = items[position]
            h.n.text = e.name
            h.s.text = FormatBytes.human(e.size) + " · from " + (java.io.File(e.original).parent ?: "")
            h.cb.setOnCheckedChangeListener(null)
            h.cb.isChecked = e.id in checked
            h.cb.setOnCheckedChangeListener { _, on -> if (on) checked.add(e.id) else checked.remove(e.id) }
            h.itemView.setOnClickListener { h.cb.isChecked = !h.cb.isChecked }
        }
    }
}
