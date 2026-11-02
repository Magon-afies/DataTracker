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

    companion object {
        private var cachedList: List<AppHealth>? = null
        private var lastCacheTime = 0L
        private const val CACHE_TTL = 3 * 60_000L
    }

    fun snapshot(now: Long = System.currentTimeMillis(), forceRefresh: Boolean = false): List<AppHealth> {
        if (!forceRefresh && cachedList != null && (now - lastCacheTime) < CACHE_TTL) {
            return cachedList!!
        }

        val start = startOfDay(now)
        val usageApps = usage.query(start, now).associateBy { it.packageName }
        val agg = try {
            usm?.queryAndAggregateUsageStats(start, now) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        val out = ArrayList<AppHealth>()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        for (pkgInfo in packages) {
            val isSystem = (pkgInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val hasUsage = usageApps.containsKey(pkgInfo.packageName)
            if (isSystem && !hasUsage) continue

            val pkg = pkgInfo.packageName
            val c = cache.forPackage(pkg)
            val u = usageApps[pkg]
            val fg = agg[pkg]
            
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
        
        val sorted = out.sortedByDescending { it.cacheBytes }
        cachedList = sorted
        lastCacheTime = now
        return sorted
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
