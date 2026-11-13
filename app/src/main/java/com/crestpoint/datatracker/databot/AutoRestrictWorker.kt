package com.crestpoint.datatracker.databot

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Every few hours: finds apps burning background mobile data and queues them for the bot. */
class AutoRestrictWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext
        val prefs = DataBotPrefs(app)
        if (prefs.mode == "off") return Result.success()
        val c = DataBot.candidates(app)
        prefs.pending = c.map { it.pkg }.toSet()
        if (c.isNotEmpty()) DataBot.notifyPending(app, c)
        return Result.success()
    }

    companion object {
        private const val NAME = "auto_restrict_background_data"
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AutoRestrictWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx.applicationContext)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
        }
    }
}
