package com.mycompany.datatracker.work

import android.content.Context
import com.mycompany.datatracker.data.DataPlan
import com.mycompany.datatracker.data.PlanStore
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.UsageRepository

data class PlanUsage(val plan: DataPlan, val usedBytes: Long, val percent: Int)

/** Captures a plan-cycle total and emits each plan threshold once. */
object PlanMonitor {
    const val THRESHOLD_80 = 80
    const val THRESHOLD_90 = 90
    const val THRESHOLD_100 = 100
    private val THRESHOLDS = intArrayOf(THRESHOLD_80, THRESHOLD_90, THRESHOLD_100)

    fun capture(context: Context, notify: Boolean = true): PlanUsage? {
        val app = context.applicationContext
        val store = PlanStore(app)
        val plan = store.active() ?: return null
        val repo = UsageRepository(app)
        val now = System.currentTimeMillis()
        val direct = repo.mobileUsage(plan.startMs, minOf(now, plan.expiryMs))
        val previous = store.latestSnapshot(plan.id)
        val counter = repo.trafficCounter()
        val usage = if (direct != null) {
            direct
        } else if (counter >= 0) {
            val delta = if (previous != null && previous.trafficCounter >= 0 &&
                counter >= previous.trafficCounter
            ) counter - previous.trafficCounter else counter
            (previous?.usageBytes ?: 0L) + delta
        } else {
            previous?.usageBytes ?: 0L
        }
        store.saveSnapshot(plan.id, usage, counter)

        val prefs = Prefs(app)
        val debug = prefs.debugUsagePercent
        val effective = if (debug > 0) plan.sizeBytes * debug / 100L else usage
        val percent = ((effective * 100L) / plan.sizeBytes.coerceAtLeast(1L)).toInt()
            .coerceAtMost(100)
        val result = PlanUsage(plan, effective, percent)
        if (notify && Alerts.canNotify(app)) {
            for (threshold in THRESHOLDS) {
                if (percent >= threshold && store.markThresholdFired(plan.id, threshold)) {
                    Alerts.postPlanThreshold(app, threshold)
                }
            }
        }
        return result
    }
}
