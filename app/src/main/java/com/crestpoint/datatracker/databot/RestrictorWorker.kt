package com.crestpoint.datatracker.databot

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps apps you already restricted from waking up in the background.
 * Every 30 minutes it stops the background processes of those apps, unless you used one recently.
 * Apps on your protected list are never touched. Needs Usage Access; without it nothing is stopped.
 */
class RestrictorWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext
        val prefs = DataBotPrefs(app)
        if (prefs.mode == "off") return Result.success()
        val targets = prefs.restricted() - prefs.protectedApps() - app.packageName
        if (targets.isEmpty()) return Result.success()

        val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24L * 3_600_000L, now)
        } catch (_: Exception) { null }
        // No usage access means we cannot tell which app you are using, so do nothing.
        if (stats == null || stats.isEmpty()) return Result.success()
        val lastUsed = HashMap<String, Long>()
        for (s in stats) lastUsed[s.packageName] = s.lastTimeUsed

        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var stopped = 0
        for (pkg in targets) {
            if (now - (lastUsed[pkg] ?: 0L) < 10L * 60_000L) continue
            try { am.killBackgroundProcesses(pkg); stopped++ } catch (_: Exception) { }
        }
        app.getSharedPreferences("restrictor_prefs", Context.MODE_PRIVATE).edit()
            .putLong("last_run", now).putInt("last_stopped", stopped).apply()
        return Result.success()
    }

    companion object {
        private const val NAME = "restrictor_keep_asleep"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RestrictorWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
        }
    }
}
