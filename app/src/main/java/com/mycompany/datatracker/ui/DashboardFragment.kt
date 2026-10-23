package com.mycompany.datatracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.data.HistoryStore
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.Dates
import com.mycompany.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val repo by lazy { UsageRepository(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.top_apps)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = AppUsageAdapter()

        view.findViewById<Button>(R.id.btn_grant).setOnClickListener {
            repo.openUsageAccessSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val view = view ?: return
        val granted = repo.hasUsageAccess()
        view.findViewById<View>(R.id.usage_banner).visibility =
            if (granted) View.GONE else View.VISIBLE
        view.findViewById<Button>(R.id.btn_grant).visibility =
            if (granted) View.GONE else View.VISIBLE

        if (!granted) return

        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val today = repo.query(repo.startOfDay(now), now)
            val week = repo.query(now - 7L * 24 * 60 * 60 * 1000, now)
            val month = repo.query(repo.startOfMonth(now), now)

            // History: backfill once a day, then keep today's row current
            val history = HistoryStore(appContext)
            val prefs = Prefs(appContext)
            val todayKey = Dates.day(now)
            if (prefs.lastBackfillDay != todayKey) {
                history.backfill(repo, 30)
                prefs.lastBackfillDay = todayKey
            }
            history.saveDay(todayKey, today)

            val days = Dates.lastDays(14, now)
            val byDay = history.dayTotals(days.first()).associateBy { it.day }
            val mobile = LongArray(days.size) { byDay[days[it]]?.mobile ?: 0L }
            val wifi = LongArray(days.size) { byDay[days[it]]?.wifi ?: 0L }
            val labels = days.map { it.substring(8) }

            val text = { m: Long, w: Long ->
                "Mobile ${FormatBytes.human(m)} · Wi-Fi ${FormatBytes.human(w)}"
            }
            val todayText = text(today.sumOf { it.mobileTotal }, today.sumOf { it.wifiTotal })
            val weekText = text(week.sumOf { it.mobileTotal }, week.sumOf { it.wifiTotal })
            val monthText = text(month.sumOf { it.mobileTotal }, month.sumOf { it.wifiTotal })

            withContext(Dispatchers.Main) {
                view.findViewById<TextView>(R.id.total_today).text = "Today — $todayText"
                view.findViewById<TextView>(R.id.total_week).text = "Last 7 days — $weekText"
                view.findViewById<TextView>(R.id.total_month).text = "This month — $monthText"
                view.findViewById<BarChartView>(R.id.history_chart).setData(labels, mobile, wifi)
                val adapter = view.findViewById<RecyclerView>(R.id.top_apps).adapter
                (adapter as? AppUsageAdapter)?.submit(today.take(5))
            }
        }
    }
}
