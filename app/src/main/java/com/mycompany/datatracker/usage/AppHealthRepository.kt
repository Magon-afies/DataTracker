package com.crestpoint.datatracker.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

class AppHealthRepository(context: Context) {
    private val app = context.applicationContext
    private val usage = UsageRepository(app)
    private val cache = CacheStats(app)
    private val usm: UsageStatsManager? =
        app.getSystemService(UsageStatsManager::class.java)

    fun snapshot(now: Long = System.currentTimeMillis()): List<AppHealth> {
        val start = startOfDay(now)
        val usageApps = usage.query(start, now)
        if (usageApps.isEmpty()) return emptyList()

        val agg = try {
            usm?.queryAndAggregateUsageStats(start, now) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        val out = ArrayList<AppHealth>()
        for (a in usageApps) {
            val pkg = a.packageName ?: continue
            val c = cache.forPackage(pkg)
            val fg = agg[pkg]
            out.add(
                AppHealth(
                    uid = a.uid,
                    packageName = pkg,
                    label = a.label,
                    networkBytes = a.total,
                    foregroundBytes = a.foregroundBytes,
                    backgroundBytes = a.backgroundBytes,
                    cacheBytes = c?.cache ?: 0L,
                    dataBytes = c?.data ?: 0L,
                    apkBytes = c?.apk ?: 0L,
                    foregroundTimeMs = fg?.totalTimeInForeground ?: 0L,
                    lastUsedMs = fg?.lastTimeUsed ?: 0L,
                    backgroundSessions = if (a.backgroundBytes > 0L) 1 else 0
                )
            )
        }
        return out.sortedByDescending { it.drainScore }
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
