package com.mycompany.datatracker.autopsy

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class CacheCleanerService : AccessibilityService() {

    companion object {
        private val queue = ArrayDeque<String>()
        private var state = 0 // 0 = Wait for Storage, 1 = Wait for Clear Cache
        private val handler = Handler(Looper.getMainLooper())

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${CacheCleanerService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun startCleaning(context: Context, packages: List<String>) {
            if (packages.isEmpty()) return
            queue.clear()
            queue.addAll(packages)
            state = 0
            processNext(context.applicationContext)
        }

        private fun processNext(context: Context) {
            if (queue.isEmpty()) return
            val pkg = queue.removeFirst()
            state = 0
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.android.settings") return
        val root = rootInActiveWindow ?: return

        when (state) {
            0 -> {
                val node = findNode(root, listOf("Storage", "Storage & cache", "Storage usage"))
                if (node != null && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    state = 1
                }
            }
            1 -> {
                val node = findNode(root, listOf("Clear cache", "Clear Cache", "CLEAR CACHE"))
                if (node != null && node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    state = 0
                    handler.postDelayed({
                        processNext(applicationContext)
                    }, 1500)
                }
            }
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, needles: List<String>): AccessibilityNodeInfo? {
        val byId = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/clear_cache_button")
        if (byId.isNotEmpty()) return byId[0]
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            val t = n.text?.toString()?.lowercase(Locale.ROOT) ?: ""
            if (t.isNotEmpty() && needles.any { t.contains(it.lowercase(Locale.ROOT)) }) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    override fun onInterrupt() {}
}
