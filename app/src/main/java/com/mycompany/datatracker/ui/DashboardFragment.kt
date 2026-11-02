package com.mycompany.datatracker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.autopsy.AutopsyService
import com.mycompany.datatracker.data.HistoryStore
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.Dates
import com.mycompany.datatracker.util.FormatBytes
import com.mycompany.datatracker.work.Alerts
import com.mycompany.datatracker.work.SnapshotWorker
import com.mycompany.datatracker.work.PlanMonitor
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
        view.findViewById<Button>(R.id.btn_notification_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
        }

        view.findViewById<Button>(R.id.btn_run_autopsy).setOnClickListener {
            AutopsyService.start(requireContext())
            SnapshotWorker.schedule(requireContext())
            Toast.makeText(requireContext(), R.string.autopsy_running, Toast.LENGTH_SHORT).show()
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
        val notificationsOff = !Alerts.canNotify(requireContext())
        view.findViewById<View>(R.id.notification_banner).visibility =
            if (notificationsOff) View.VISIBLE else View.GONE
        view.findViewById<Button>(R.id.btn_notification_settings).visibility =
            if (notificationsOff) View.VISIBLE else View.GONE

        if (!granted) return

        val appContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val planUsage = PlanMonitor.capture(appContext)
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
                val planText = view.findViewById<TextView>(R.id.plan_usage)
                val planCarrier = view.findViewById<TextView>(R.id.plan_carrier)
                val progress = view.findViewById<ProgressBar>(R.id.plan_progress)
                if (planUsage == null) {
                    planText.setText(R.string.plan_usage)
                    planCarrier.setText(R.string.plan_carrier)
                    progress.progress = 0
                } else {
                    planText.text = "${planUsage.percent}% used · ${FormatBytes.human(planUsage.usedBytes)} / " +
                        FormatBytes.human(planUsage.plan.sizeBytes)
                    
                    val plan = planUsage.plan
                    val nairaPerGB = if (plan.sizeBytes > 0) plan.priceNaira / (plan.sizeBytes / 1073741824.0) else 0.0
                    val burnedNaira = plan.priceNaira * (planUsage.usedBytes.toDouble() / plan.sizeBytes)
                    val nairaLine = String.format(java.util.Locale.US, "≈ ₦%.0f burned so far · ₦%.0f/GB", burnedNaira, nairaPerGB)
                    
                    val plan = planUsage.plan
                    val nairaPerGB = if (plan.sizeBytes > 0) plan.priceNaira / (plan.sizeBytes / 1073741824.0) else 0.0
                    val burnedNaira = plan.priceNaira * (planUsage.usedBytes.toDouble() / plan.sizeBytes)
                    val nairaLine = String.format(java.util.Locale.US, "≈ ₦%.0f burned so far · ₦%.0f/GB", burnedNaira, nairaPerGB)
                    planCarrier.text = "${plan.carrier} · expires ${Dates.day(plan.expiryMs)}
$nairaLine"

                    progress.progress = planUsage.percent
                }
                view.findViewById<BarChartView>(R.id.history_chart).setData(labels, mobile, wifi)
                val adapter = view.findViewById<RecyclerView>(R.id.top_apps).adapter
                (adapter as? AppUsageAdapter)?.submit(today.take(5))
            }
        }
    }
}
