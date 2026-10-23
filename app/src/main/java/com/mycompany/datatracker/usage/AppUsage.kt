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
    val hourBytes = LongArray(24)

    val mobileTotal: Long get() = mobileRx + mobileTx
    val wifiTotal: Long get() = wifiRx + wifiTx
    val total: Long get() = mobileTotal + wifiTotal

    val backgroundPercent: Int
        get() = if (total == 0L) 0 else ((backgroundBytes * 100) / total).toInt()

    /** Hour of day (0–23) with the most traffic, e.g. "21:00". Empty if no usage. */
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
}
