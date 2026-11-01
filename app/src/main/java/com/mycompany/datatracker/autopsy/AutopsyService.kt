package com.mycompany.datatracker.autopsy

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AutopsyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var screen: ScreenLog? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AutopsyNotifier.ensureChannels(this)
        val n = AutopsyNotifier.serviceNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                AutopsyNotifier.SERVICE_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AutopsyNotifier.SERVICE_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        begin()
        return START_STICKY
    }

    private fun begin() {
        if (job?.isActive == true) return
        val pm = getSystemService(PowerManager::class.java)
        val log = ScreenLog(pm?.isInteractive ?: true, System.currentTimeMillis())
        screen = log

        if (receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    log.set(intent?.action == Intent.ACTION_SCREEN_ON, System.currentTimeMillis())
                    wake.trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        }

        val app = applicationContext
        val detector = AutopsyDetector(
            UsageRepository(app), log, AutopsyPrefs(app), Process.myUid()
        )
        val prefs = Prefs(app)

        job = scope.launch {
            while (isActive) {
                try {
                    if (prefs.alertsEnabled) {
                        detector.tick(System.currentTimeMillis())
                            .forEach { AutopsyNotifier.post(app, it) }
                    }
                } catch (_: Exception) {
                }
                withTimeoutOrNull(AutopsyPrefs.TICK_MS) { wake.receive() }
            }
        }
    }

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, AutopsyService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
