package com.mycompany.datatracker.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Calendar

class AppHealthRepository(context: Context) {
    private val app = context.applicationContext
    private val usage = UsageRepository(app)
    private val cache = CacheStats(app)
    private val usm: UsageStatsManager? = app.getSystemService(UsageStatsManager::class.java)
    private val pm: PackageManager = app.packageManager

    fun snapshot(now: Long = System.currentTimeMillis()): List<AppHealth> {
        val start = startOfDay(now)
        val usageApps = usage.query(start, now).associateBy { it.packageName }

        val agg = try {
            usm?.queryAndAggregateUsageStats(start, now) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        val out = ArrayList<AppHealth>()
        
        // Scan ALL installed apps, not just ones with network usage today
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (pkgInfo in packages) {
            val isSystem = (pkgInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasUsage = usageApps.containsKey(pkgInfo.packageName)
            
            // Skip system apps unless they actually used data today
            if (isSystem && !hasUsage) continue

            val pkg = pkgInfo.packageName
            val c = cache.forPackage(pkg)
            val u = usageApps[pkg]
            val fg = agg[pkg]
            
            // Skip if it has 0 cache and 0 usage
            if ((c == null || c.total == 0L) && u == null) continue

            val label = pm.getApplicationLabel(pkgInfo).toString()
            out.add(
                AppHealth(
                    uid = pkgInfo.uid,
                    packageName = pkg,
                    label = label,
                    networkBytes = u?.total ?: 0L,
                    foregroundBytes = u?.foregroundBytes ?: 0L,
                    backgroundBytes = u?.backgroundBytes ?: 0L,
                    cacheBytes = c?.cache ?: 0L,
                    dataBytes = c?.data ?: 0L,
                    apkBytes = c?.apk ?: 0L,
                    foregroundTimeMs = fg?.totalTimeInForeground ?: 0L,
                    lastUsedMs = fg?.lastTimeUsed ?: 0L,
                    backgroundSessions = if ((u?.backgroundBytes ?: 0L) > 0L) 1 else 0
                )
            )
        }
        return out.sortedByDescending { it.cacheBytes }
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
