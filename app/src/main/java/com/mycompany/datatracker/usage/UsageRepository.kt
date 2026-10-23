package com.mycompany.datatracker.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar

class UsageRepository(private val context: Context) {

    private val nsm = context.getSystemService(NetworkStatsManager::class.java)
    private val pm = context.packageManager

    /** True if the user granted the special "Usage access" permission. */
    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Reads per-app traffic counters kept by Android itself, between two
     * timestamps, using the public NetworkStatsManager API.
     * Each bucket carries: app uid, state (foreground / not), byte counts
     * and wall-clock start/end stamps.
     */
    fun query(startMillis: Long, endMillis: Long): List<AppUsage> {
        val manager = nsm ?: return emptyList()
        if (!hasUsageAccess()) return emptyList()

        val byUid = HashMap<Int, AppUsage>()

        for (type in intArrayOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)) {
            val isMobile = type == ConnectivityManager.TYPE_MOBILE
            var stats: NetworkStats? = null
            try {
                // subscriberId = null -> all mobile subscribers (allowed on Android 10+)
                stats = manager.queryDetails(type, null, startMillis, endMillis)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid < 0) continue  // removed apps / tethering counters
                    val rx = bucket.rxBytes
                    val tx = bucket.txBytes
                    if (rx == 0L && tx == 0L) continue

                    val entry = byUid.getOrPut(uid) {
                        val pkg = packageForUid(uid)
                        AppUsage(uid, labelForUid(uid, pkg), pkg)
                    }

                    if (isMobile) {
                        entry.mobileRx += rx
                        entry.mobileTx += tx
                    } else {
                        entry.wifiRx += rx
                        entry.wifiTx += tx
                    }

                    if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) {
                        entry.foregroundBytes += rx + tx
                    } else {
                        entry.backgroundBytes += rx + tx
                    }

                    // "When it uses the data": bucket midpoint -> hour of day
                    val mid = (bucket.startTimeStamp + bucket.endTimeStamp) / 2
                    entry.hourBytes[hourOfDay(mid)] += rx + tx
                }
            } catch (se: SecurityException) {
                return emptyList()  // usage access revoked mid-query
            } catch (e: Exception) {
                // e.g. no SIM / no mobile subscriber: skip this network type
            } finally {
                try { stats?.close() } catch (e: Exception) {}
            }
        }

        return byUid.values
            .filter { it.total > 0L }
            .sortedByDescending { it.total }
    }

    fun startOfDay(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfMonth(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun hourOfDay(unixMillis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = unixMillis }
            .get(Calendar.HOUR_OF_DAY)

    private fun packageForUid(uid: Int): String? =
        pm.getPackagesForUid(uid)?.firstOrNull()

    private fun labelForUid(uid: Int, pkg: String?): String {
        if (pkg == null) return "System (UID $uid)"
        return try {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
