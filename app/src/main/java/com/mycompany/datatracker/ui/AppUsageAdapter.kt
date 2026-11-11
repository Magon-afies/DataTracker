package com.crestpoint.datatracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.usage.AppUsage
import com.crestpoint.datatracker.util.FormatBytes

class AppUsageAdapter(
    private val onClick: (AppUsage) -> Unit = {}
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    private val items = mutableListOf<AppUsage>()

    fun submit(list: List<AppUsage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val detail: TextView = view.findViewById(R.id.app_detail)
        val detail2: TextView = view.findViewById(R.id.app_detail2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_usage, parent, false)
        )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        val context = holder.itemView.context

        holder.name.text = app.label
        holder.detail.text = "Mobile ${FormatBytes.human(app.mobileTotal)} · " +
            "Wi-Fi ${FormatBytes.human(app.wifiTotal)}"
        holder.detail2.text = buildString {
            if (app.backgroundPercent >= 0) {
                append("Foreground ${app.foregroundPercent}% · Background ${app.backgroundPercent}%")
            } else {
                append("Foreground/background split not available")
            }
            app.peakWindow().takeIf { it.isNotEmpty() }?.let { append(" · busiest $it") }
        }

        val pkg = app.packageName
        holder.icon.setImageDrawable(
            try {
                if (pkg != null) context.packageManager.getApplicationIcon(pkg)
                else context.getDrawable(android.R.drawable.sym_def_app_icon)
            } catch (e: Exception) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)
            }
        )

        holder.itemView.setOnClickListener { onClick(app) }
    }
}
