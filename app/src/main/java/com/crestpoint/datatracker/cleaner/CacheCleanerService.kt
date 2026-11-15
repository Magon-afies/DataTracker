package com.crestpoint.datatracker.cleaner

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CacheCleanerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
