package com.mycompany.datatracker.autopsy

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class CacheCleanerService : AccessibilityService() {

    companion object {
        private const val SETTINGS_PKG = "com.android.settings"

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${CacheCleanerService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != SETTINGS_PKG) return
        val root = rootInActiveWindow ?: return
        val storage = findNode(root, listOf("Storage", "Storage & cache", "Storage usage"))
        if (storage != null && storage.isClickable) {
            storage.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        val clear = findNode(root, listOf("Clear cache", "Clear Cache", "CLEAR CACHE"))
        if (clear != null && clear.isClickable) {
            clear.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, needles: List<String>): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/clear_cache_button")
        if (byId.isNotEmpty()) return byId[0]
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            if (t.isNotEmpty() && needles.any { t.contains(it.lowercase(Locale.ROOT)) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    override fun onInterrupt() {}
}
