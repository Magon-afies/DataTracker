package com.mycompany.datatracker.cleaner

import android.content.Context
import android.os.SystemClock
import com.mycompany.datatracker.usage.CacheStats

data class CacheTarget(val packageName: String, val cacheBytes: Long)

object CacheBot {
    val SETTINGS_PACKAGES = setOf("com.android.settings", "com.samsung.android.settings")
    private const val MIN_CACHE_BYTES = 256L * 1024L

    @Volatile
    private var machine: CacheBotMachine? = null

    fun get(context: Context): CacheBotMachine {
        val existing = machine
        if (existing != null) return existing
        return synchronized(this) {
            machine ?: CacheBotMachine(
                context.applicationContext.packageName,
                SETTINGS_PACKAGES
            ) { SystemClock.uptimeMillis() }.also { machine = it }
        }
    }

    fun peek(): CacheBotMachine? = machine

    fun targets(context: Context): List<CacheTarget> {
        val app = context.applicationContext
        val pm = app.packageManager
        val stats = CacheStats(app)
        val out = ArrayList<CacheTarget>()
        for (info in pm.getInstalledApplications(0)) {
            val pkg = info.packageName
            if (pkg == app.packageName || pkg in SETTINGS_PACKAGES) continue
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            val cache = stats.forPackage(pkg)?.cache ?: 0L
            if (cache >= MIN_CACHE_BYTES) out.add(CacheTarget(pkg, cache))
        }
        return out.sortedByDescending { it.cacheBytes }
    }

    fun cacheBytes(context: Context, packages: List<String>): Long {
        val stats = CacheStats(context.applicationContext)
        var sum = 0L
        for (pkg in packages) sum += stats.forPackage(pkg)?.cache ?: 0L
        return sum
    }
}
