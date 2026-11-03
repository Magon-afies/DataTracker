package com.mycompany.datatracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.usage.AppHealth
import com.mycompany.datatracker.util.FormatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthAdapter(
    private val onOpenSettings: (String) -> Unit,
    private val onKill: (String) -> Unit,
    private val onClearCache: (String) -> Unit,
    private val onRestrict: (String) -> Unit
) : RecyclerView.Adapter<HealthAdapter.VH>() {

    private val items = ArrayList<AppHealth>()
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(list: List<AppHealth>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_health, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = items[position]
        h.label.text = a.label
        h.meta.text = "FG " + (a.foregroundTimeMs / 60_000L) + "m · " +
            "BG " + FormatBytes.human(a.backgroundBytes) + " · " +
            "Net " + FormatBytes.human(a.networkBytes)
        h.drain.text = String.format(
            Locale.US, "Drain score %,d · BG sessions %d", a.drainScore, a.backgroundSessions
        )
        h.storage.text = "Cache " + FormatBytes.human(a.cacheBytes) +
            " · Data " + FormatBytes.human(a.dataBytes) +
            " · APK " + FormatBytes.human(a.apkBytes)
        h.last.text = if (a.lastUsedMs > 0L)
            "Last used " + fmt.format(Date(a.lastUsedMs)) else "Not used today"
        h.btnSettings.setOnClickListener { onOpenSettings(a.packageName) }
        h.btnKill.setOnClickListener { onKill(a.packageName) }
        h.btnClearCache.setOnClickListener { onClearCache(a.packageName) }
        h.btnRestrict.setOnClickListener { onRestrict(a.packageName) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v.findViewById(R.id.health_label)
        val meta: TextView = v.findViewById(R.id.health_meta)
        val drain: TextView = v.findViewById(R.id.health_drain)
        val storage: TextView = v.findViewById(R.id.health_storage)
        val last: TextView = v.findViewById(R.id.health_last)
        val btnSettings: Button = v.findViewById(R.id.health_open_settings)
        val btnKill: Button = v.findViewById(R.id.health_kill)
        val btnClearCache: Button = v.findViewById(R.id.health_clear_cache)
        val btnRestrict: Button = v.findViewById(R.id.health_restrict)
    }
}
