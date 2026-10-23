package com.mycompany.datatracker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mycompany.datatracker.R
import com.mycompany.datatracker.usage.AppUsage
import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsFragment : Fragment(R.layout.fragment_apps) {

    private val repo by lazy { UsageRepository(requireContext()) }
    private lateinit var adapter: AppUsageAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.apps_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppUsageAdapter { showDetails(it) }
        list.adapter = adapter

        view.findViewById<ChipGroup>(R.id.range_chips)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                load(rangeStart(checkedIds.firstOrNull()))
            }

        load(rangeStart(R.id.chip_week))
    }

    private fun rangeStart(chipId: Int?): Long {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        return when (chipId) {
            R.id.chip_today -> repo.startOfDay(now)
            R.id.chip_month -> now - 30 * day
            else -> now - 7 * day
        }
    }

    private fun load(start: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val result = repo.query(start, System.currentTimeMillis())
            withContext(Dispatchers.Main) { adapter.submit(result) }
        }
    }

    /** Per-app details: totals, hourly chart, and shortcuts to the system data controls. */
    private fun showDetails(app: AppUsage) {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()

        val summary = TextView(ctx).apply {
            text = "Mobile ${FormatBytes.human(app.mobileTotal)} · " +
                "Wi-Fi ${FormatBytes.human(app.wifiTotal)}\n" +
                "Foreground ${FormatBytes.human(app.foregroundBytes)} · " +
                "Background ${FormatBytes.human(app.backgroundBytes)} " +
                "(${app.backgroundPercent}%)"
        }
        val chartTitle = TextView(ctx).apply {
            text = "Data by hour of day"
            setPadding(0, (16 * d).toInt(), 0, (4 * d).toInt())
        }
        val chart = BarChartView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (150 * d).toInt()
            )
            setData(List(24) { "%02d".format(it) }, app.hourBytes.copyOf())
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(summary)
            addView(chartTitle)
            addView(chart)
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(app.label)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)

        val pkg = app.packageName
        if (pkg != null) {
            builder.setNeutralButton(R.string.restrict_data) { _, _ -> openDataSettings(pkg) }
            builder.setNegativeButton(R.string.app_info) { _, _ -> openAppInfo(pkg) }
        }
        builder.show()
    }

    private fun openDataSettings(pkg: String) {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        } catch (e: Exception) {
            openAppInfo(pkg)
        }
    }

    private fun openAppInfo(pkg: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            // nothing to open on this device
        }
    }
}
