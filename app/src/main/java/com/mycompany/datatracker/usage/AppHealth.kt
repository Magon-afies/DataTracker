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
    val backgroundSessions: Int
) {
    val storageBytes: Long get() = cacheBytes + dataBytes + apkBytes
    val batteryScore: Double
        get() = (foregroundTimeMs / 60_000.0) + (backgroundBytes / 1_048_576.0) + (backgroundSessions * 5.0)
}
