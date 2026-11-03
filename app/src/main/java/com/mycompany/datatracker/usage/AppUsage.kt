package com.mycompany.datatracker.usage

import java.util.Locale

class AppUsage(
    val uid: Int,
    val label: String,
    val packageName: String?
) {
    var mobileRx = 0L
    var mobileTx = 0L
    var wifiRx = 0L
    var wifiTx = 0L
    var foregroundBytes = 0L
    var backgroundBytes = 0L
    var splitKnown = false
    val hourBytes = LongArray(24)

    val mobileTotal: Long get() = mobileRx + mobileTx
    val wifiTotal: Long get() = wifiRx + wifiTx
    val total: Long get() = mobileTotal + wifiTotal

    /** Share of traffic Android counted while the app was NOT in the foreground; -1 if unknown. */
    val backgroundPercent: Int
        get() {
            val classified = foregroundBytes + backgroundBytes
            return if (!splitKnown || classified <= 0L) -1
            else ((backgroundBytes * 100) / classified).toInt()
        }

    val foregroundPercent: Int
        get() = if (backgroundPercent < 0) -1 else 100 - backgroundPercent

    /** Hour of day (0-23) with the most traffic, e.g. "21:00". Empty if no usage. */
    fun peakHour(): String {
        var best = -1
        var bestValue = -1L
        for (h in 0 until 24) {
            if (hourBytes[h] > bestValue) {
                bestValue = hourBytes[h]
                best = h
            }
        }
        return if (bestValue <= 0L) "" else String.format(Locale.US, "%02d:00", best)
    }

    /** Busiest two-hour window, e.g. "20:00-22:00" (Android stores traffic in 2-hour buckets). */
    fun peakWindow(): String {
        var best = -1
        var bestValue = 0L
        for (h in 0 until 24) {
            val sum = hourBytes[h] + hourBytes[(h + 1) % 24]
            if (sum > bestValue) {
                bestValue = sum
                best = h
            }
        }
        return if (best < 0) "" else String.format(Locale.US, "%02d:00-%02d:00", best, (best + 2) % 24)
    }
}
