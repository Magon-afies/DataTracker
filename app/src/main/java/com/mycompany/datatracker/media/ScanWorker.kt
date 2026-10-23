package com.mycompany.datatracker.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Background photo indexing. Results are saved per photo, so if Android stops the
 * worker (long scans), WorkManager restarts it and it continues where it left off.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            MediaIndexer(applicationContext).index({ isStopped }) { done, total ->
                setProgress(workDataOf("done" to done, "total" to total))
            }
            Result.success()
        } catch (e: SecurityException) {
            Result.failure()
        }
    }

    companion object {
        const val NAME = "media_scan"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ScanWorker>().build()
            )
        }
    }
}
