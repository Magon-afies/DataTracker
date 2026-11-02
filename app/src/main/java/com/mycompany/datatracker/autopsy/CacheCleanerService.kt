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
        private var isProcessing = false
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
            isProcessing = false
            processNext(context.applicationContext)
        }

        private fun processNext(context: Context) {
            if (queue.isEmpty()) return
            val pkg = queue.removeFirst()
            isProcessing = false
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.android.settings" || isProcessing) return
        isProcessing = true

        // Wait 1 full second for the Settings screen to load
        handler.postDelayed({
            val root = rootInActiveWindow
            if (root == null) {
                isProcessing = false
                return@postDelayed
            }

            // 1. Look for the "Storage" button
            val storageNode = findNode(root, listOf("Storage", "Storage & cache", "Storage usage"))
            if (storageNode != null) {
                clickNode(storageNode)
                // Wait 1 second for the Storage page to open, then clear cache
                handler.postDelayed({
                    val root2 = rootInActiveWindow
                    if (root2 != null) {
                        val clearNode = findNode(root2, listOf("Clear cache", "Clear Cache", "CLEAR CACHE"))
                        if (clearNode != null) {
                            clickNode(clearNode)
                        }
                    }
                    // Wait 1.5 seconds for the clear to finish, then go Back and next app
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.postDelayed({
                            processNext(applicationContext)
                        }, 800)
                    }, 1500)
                }, 1000)
            } else {
                // If Storage is not found, try scrolling
                scrollDown(root)
                isProcessing = false
            }
        }, 1000)
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        else node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun scrollDown(root: AccessibilityNodeInfo) {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isScrollable) {
                n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, needles: List<String>): AccessibilityNodeInfo? {
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
