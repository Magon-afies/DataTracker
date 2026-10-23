package com.mycompany.datatracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mycompany.datatracker.data.HistoryStore
import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.Dates
import java.util.concurrent.TimeUnit

/** Runs about every 30 min: saves today's snapshot to history and checks alert limits. */
class SnapshotWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = UsageRepository(applicationContext)
        if (!repo.hasUsageAccess()) return Result.success()

        val history = HistoryStore(applicationContext)
        val now = System.currentTimeMillis()
        val todayStart = repo.startOfDay(now)

        val today = repo.query(todayStart, now)
        history.saveDay(Dates.day(todayStart), today)

        // Re-save yesterday so its final numbers are captured after midnight
        val yesterdayStart = repo.startOfDay(todayStart - 1)
        val yesterday = repo.query(yesterdayStart, todayStart)
        if (yesterday.isNotEmpty()) history.saveDay(Dates.day(yesterdayStart), yesterday)

        Alerts.check(applicationContext, today)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "usage_snapshot", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
