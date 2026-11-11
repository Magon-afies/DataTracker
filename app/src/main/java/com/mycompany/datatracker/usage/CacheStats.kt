package com.crestpoint.datatracker.usage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.UserHandle
import android.os.storage.StorageManager

class CacheStats(context: Context) {
    private val ssm: StorageStatsManager? =
        context.applicationContext.getSystemService(StorageStatsManager::class.java)

    data class Sizes(val cache: Long, val data: Long, val apk: Long) {
        val total: Long get() = cache + data + apk
    }

    fun forPackage(pkg: String): Sizes? {
        val mgr = ssm ?: return null
        return try {
            val s = mgr.queryStatsForPackage(
                StorageManager.UUID_DEFAULT, pkg, UserHandle.getUserHandleForUid(0)
            )
            Sizes(s.cacheBytes, s.dataBytes, s.appBytes)
        } catch (_: Exception) {
            null
        }
    }
}
