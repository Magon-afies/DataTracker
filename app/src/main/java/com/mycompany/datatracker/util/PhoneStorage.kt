package com.crestpoint.datatracker.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageSnapshot(
    val totalBytes: Long,
    val freeBytes: Long,
    val cacheBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

object PhoneStorage {
    fun snapshot(context: Context): StorageSnapshot {
        val stats = StatFs(Environment.getDataDirectory().path)
        val total = stats.totalBytes
        val free = stats.availableBytes
        val cache = directorySize(context.cacheDir) +
            (context.externalCacheDir?.let(::directorySize) ?: 0L)
        return StorageSnapshot(total, free, cache)
    }

    fun clearAppCache(context: Context): Long {
        val before = snapshot(context).cacheBytes
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
        context.externalCacheDir?.deleteRecursively()
        context.externalCacheDir?.mkdirs()
        return before
    }

    private fun directorySize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::directorySize) ?: 0L
    }
}
