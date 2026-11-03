package com.mycompany.datatracker.usage

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
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
     * Reads per-app traffic counters kept by Android itself between two timestamps.
     *
     * queryDetails() gives bytes per time bucket but merges foreground and background
     * traffic (every bucket comes back as STATE_ALL), so it is used for totals and the
     * hour-of-day chart only. The foreground/background split comes from querySummary(),
     * which keeps Android's per-uid state (foreground vs everything else).
     */
    fun query(startMillis: Long, endMillis: Long): List<AppUsage> {
        val manager = nsm ?: return emptyList()
        if (!hasUsageAccess()) return emptyList()

        val byUid = HashMap<Int, AppUsage>()

        for (type in NETWORK_TYPES) {
            val isMobile = type == ConnectivityManager.TYPE_MOBILE
            var stats: NetworkStats? = null
            try {
                stats = manager.queryDetails(type, null, startMillis, endMillis)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid < 0) continue
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

                    spreadOverHours(entry.hourBytes, rx + tx, bucket.startTimeStamp, bucket.endTimeStamp)
                }
            } catch (se: SecurityException) {
                return emptyList()
            } catch (e: Exception) {
            } finally {
                try { stats?.close() } catch (e: Exception) {}
            }
        }

        addForegroundSplit(manager, byUid, startMillis, endMillis)

        return byUid.values
            .filter { it.total > 0L }
            .sortedByDescending { it.total }
    }

    private fun addForegroundSplit(
        manager: NetworkStatsManager,
        byUid: Map<Int, AppUsage>,
        startMillis: Long,
        endMillis: Long
    ) {
        for (type in NETWORK_TYPES) {
            var stats: NetworkStats? = null
            try {
                stats = manager.querySummary(type, null, startMillis, endMillis)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val entry = byUid[bucket.uid] ?: continue
                    val bytes = bucket.rxBytes + bucket.txBytes
                    when (bucket.state) {
                        NetworkStats.Bucket.STATE_FOREGROUND -> {
                            entry.foregroundBytes += bytes
                            entry.splitKnown = true
                        }
                        NetworkStats.Bucket.STATE_DEFAULT -> {
                            entry.backgroundBytes += bytes
                            entry.splitKnown = true
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { stats?.close() } catch (e: Exception) {}
            }
        }
    }

    private fun spreadOverHours(hours: LongArray, bytes: Long, startMs: Long, endMs: Long) {
        val span = (endMs - startMs).coerceAtLeast(1L)
        val count = ((span + HOUR_MS - 1L) / HOUR_MS).toInt().coerceIn(1, 24)
        val first = hourOfDay(startMs)
        val share = bytes / count
        val rest = bytes - share * count
        for (k in 0 until count) {
            hours[(first + k) % 24] += share
        }
        hours[first] += rest
    }

    /** Cumulative mobile bytes for a plan cycle, or null if NetworkStats is unavailable. */
    fun mobileUsage(startMillis: Long, endMillis: Long): Long? {
        if (!hasUsageAccess()) return null
        var stats: NetworkStats? = null
        return try {
            stats = nsm?.querySummary(ConnectivityManager.TYPE_MOBILE, null, startMillis, endMillis)
                ?: return null
            val bucket = NetworkStats.Bucket()
            var total = 0L
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            total
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            try { stats?.close() } catch (_: Exception) {}
        }
    }

    /** Device-wide mobile counter used only as a reboot-safe fallback. */
    fun trafficCounter(): Long {
        val rx = TrafficStats.getMobileRxBytes()
        val tx = TrafficStats.getMobileTxBytes()
        return if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) -1L
        else rx + tx
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

    private companion object {
        const val HOUR_MS = 3_600_000L
        val NETWORK_TYPES = intArrayOf(ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_WIFI)
    }
}
