package com.crestpoint.datatracker.util

import android.content.Context
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.Naira
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.usage.AppUsage
import com.crestpoint.datatracker.usage.UsageRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportShare {

    fun build(context: Context, now: Long = System.currentTimeMillis()): String {
        val app = context.applicationContext
        val repo = UsageRepository(app)
        val date = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(now))
        val sb = StringBuilder()
        sb.append("Data Tracker report · ").append(date).append('\n')

        if (!repo.hasUsageAccess()) {
            sb.append("Usage access is not granted yet.\n")
        } else {
            val today = repo.query(repo.startOfDay(now), now)
            val week = repo.query(now - 7L * 24 * 60 * 60 * 1000, now)
            val month = repo.query(repo.startOfMonth(now), now)
            sb.append(totals("Today", today))
            sb.append(totals("Last 7 days", week))
            sb.append(totals("This month", month))

            val store = PlanStore(app)
            val plan = store.active(now)
            if (plan != null) {
                val used = repo.mobileUsage(plan.startMs, minOf(now, plan.expiryMs))
                    ?: store.latestSnapshot(plan.id)?.usageBytes
                    ?: 0L
                val percent = (used * 100L / plan.sizeBytes.coerceAtLeast(1L)).coerceIn(0L, 100L)
                sb.append("Plan: ").append(plan.carrier).append(" · ").append(percent).append("% used (")
                    .append(FormatBytes.human(used)).append(" of ")
                    .append(FormatBytes.human(plan.sizeBytes)).append(") · expires ")
                    .append(Dates.day(plan.expiryMs)).append('\n')
                if (plan.priceNaira > 0.0) {
                    sb.append("≈ ").append(Naira.format(plan.nairaFor(used)))
                        .append(" burned so far · ").append(Naira.format(plan.nairaPerGb))
                        .append("/GB\n")
                }
            }

            val top = today.filter { it.total > 0L }.sortedByDescending { it.total }.take(5)
            if (top.isNotEmpty()) {
                sb.append("Top apps today:\n")
                top.forEachIndexed { index, a ->
                    sb.append(index + 1).append(". ").append(a.label).append(" — ")
                        .append(FormatBytes.human(a.total)).append('\n')
                }
            }
        }

        sb.append('\n')
            .append(app.getString(R.string.brand_credit)).append(" · ")
            .append(app.getString(R.string.brand_product))
        return sb.toString()
    }

    private fun totals(label: String, list: List<AppUsage>): String =
        label + ": Mobile " + FormatBytes.human(list.sumOf { it.mobileTotal }) +
            " · Wi-Fi " + FormatBytes.human(list.sumOf { it.wifiTotal }) + "\n"
}
