package com.mycompany.datatracker.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mycompany.datatracker.MainActivity
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.AppUsage
import com.mycompany.datatracker.util.Dates
import com.mycompany.datatracker.util.FormatBytes

object Alerts {
    private const val CHANNEL = "data_alerts"
    private const val MB = 1024L * 1024L
    const val TOP_UP_URL = "https://crestpoint.name.ng"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Data alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel("data_warnings", "Data warnings", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("data_critical", "Critical data warnings", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Called with today's per-app usage. Each alert fires at most once per app per day. */
    fun check(context: Context, today: List<AppUsage>) {
        val prefs = Prefs(context)
        if (!prefs.alertsEnabled || !canNotify(context)) return
        ensureChannel(context)

        val dayKey = Dates.day(System.currentTimeMillis())
        prefs.pruneNotified(dayKey)
        val appCap = prefs.appCapMb * MB
        val bgCap = prefs.bgCapMb * MB

        for (a in today) {
            val capKey = "cap_${a.uid}_$dayKey"
            if (appCap > 0 && a.total > appCap && !prefs.wasNotified(capKey)) {
                post(
                    context, a.uid * 2,
                    "${a.label} passed your daily data cap",
                    "Used ${FormatBytes.human(a.total)} today (cap ${prefs.appCapMb} MB)."
                )
                prefs.markNotified(capKey)
            }
            val bgKey = "bg_${a.uid}_$dayKey"
            if (bgCap > 0 && a.backgroundBytes > bgCap && a.backgroundPercent >= 60 &&
                !prefs.wasNotified(bgKey)
            ) {
                post(
                    context, a.uid * 2 + 1,
                    "${a.label} is eating data in the background",
                    "${FormatBytes.human(a.backgroundBytes)} in the background today " +
                        "(${a.backgroundPercent}% of its usage)."
                )
                prefs.markNotified(bgKey)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.mycompany.datatracker.R.drawable.ic_stat_datatracker)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    @SuppressLint("MissingPermission")
    fun postPlanThreshold(context: Context, threshold: Int) {
        ensureChannel(context)
        val (title, body, channel, id) = when (threshold) {
            80 -> Quad("80% of your data used", "Your data is running low. Top up before it finishes.", "data_warnings", 800)
            90 -> Quad("Only 10% of your data left", "Almost gone! Get affordable data now.", "data_warnings", 900)
            else -> Quad("You've used all your data", "You need a top-up. Tap to buy data on Crestpoint.", "data_critical", 1000)
        }
        val openApp = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val topUp = PendingIntent.getBroadcast(
            context, id, Intent(context, TopUpReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(com.mycompany.datatracker.R.drawable.ic_stat_datatracker)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .addAction(com.mycompany.datatracker.R.drawable.ic_stat_datatracker, "Top up now", topUp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private data class Quad(
        val title: String,
        val body: String,
        val channel: String,
        val id: Int
    )
}
