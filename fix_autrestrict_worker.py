#!/usr/bin/env python3
import os

proj = os.path.expanduser("~/DataTracker")
src_java = os.path.join(proj, "app/src/main/java/com/crestpoint/datatracker")
databot_dir = os.path.join(src_java, "databot")
os.makedirs(databot_dir, exist_ok=True)

worker_code = r"""package com.crestpoint.datatracker.databot

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AutoRestrictWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            // Safe stub or implementation for auto-restriction background task
            val ctx = applicationContext
            val prefs = ctx.getSharedPreferences("databot_prefs", Context.MODE_PRIVATE)
            val candidates = prefs.getStringSet("restricted_packages", emptySet()) ?: emptySet()
            for (pkg in candidates) {
                // Perform restriction logic safely if needed
            }
        } catch (_: Exception) {
            return Result.failure()
        }
        return Result.success()
    }
}
"""

with open(os.path.join(databot_dir, "AutoRestrictWorker.kt"), "w", encoding="utf-8") as f:
    f.write(worker_code)

print("==> AutoRestrictWorker.kt successfully updated.")
