package com.mycompany.datatracker.usage

data class AppHealth(
    val uid: Int,
    val packageName: String,
    val label: String,
    val networkBytes: Long,
    val foregroundBytes: Long,
    val backgroundBytes: Long,
    val cacheBytes: Long,
    val dataBytes: Long,
    val apkBytes: Long,
    val foregroundTimeMs: Long,
    val lastUsedMs: Long,
    val backgroundSessions: Int = 0
) {
    val storageBytes: Long get() = cacheBytes + dataBytes + apkBytes

    /** Battery proxy: 1 pt per foreground minute + 1 pt per background MB. */
    val batteryScore: Double
        get() = foregroundTimeMs / 60_000.0 + backgroundBytes / 1_048_576.0

    val drainScore: Long
        get() = foregroundTimeMs + backgroundBytes + (backgroundSessions * 5)
}
