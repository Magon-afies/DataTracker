package com.crestpoint.datatracker.autopsy

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.crestpoint.datatracker.cleaner.BotScreen
import com.crestpoint.datatracker.cleaner.CacheBot
import com.crestpoint.datatracker.cleaner.ClearResult
import java.util.Locale

class CacheCleanerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var tickPending = false

    private val pollTask = object : Runnable {
        override fun run() {
            val machine = CacheBot.peek()
            if (machine == null || !machine.running) {
                polling = false
                return
            }
            machine.tick()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val eventTick = Runnable {
        tickPending = false
        CacheBot.peek()?.takeIf { it.running }?.tick()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        CacheBot.get(this).screen = ServiceScreen()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        CacheBot.peek()?.screen = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val machine = CacheBot.peek() ?: return
        if (!machine.running) return
        startPolling()
        if (!tickPending) {
            tickPending = true
            handler.post(eventTick)
        }
    }

    override fun onInterrupt() {}

    fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollTask)
    }

    private inner class ServiceScreen : BotScreen {

        override fun activePackage(): String? = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            null
        }

        override fun clickStorage(): Boolean {
            val root = rootInActiveWindow ?: return false
            val node = find(root, STORAGE_EXACT, STORAGE_CONTAINS) ?: return false
            val target = clickableAncestor(node) ?: return false
            if (!target.isEnabled) return false
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        override fun clearCache(): ClearResult {
            val root = rootInActiveWindow ?: return ClearResult.NOT_FOUND
            var node: AccessibilityNodeInfo? =
                root.findAccessibilityNodeInfosByViewId(CLEAR_CACHE_ID)?.firstOrNull()
            if (node == null) node = find(root, CLEAR_CACHE_TEXTS, CLEAR_CACHE_TEXTS)
            if (node == null) return ClearResult.NOT_FOUND
            val target = clickableAncestor(node) ?: return ClearResult.NOT_FOUND
            if (!target.isEnabled) return ClearResult.DISABLED
            return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                ClearResult.CLICKED
            } else {
                ClearResult.DISABLED
            }
        }

        override fun back() {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        private fun find(
            root: AccessibilityNodeInfo,
            exact: List<String>,
            contains: List<String>
        ): AccessibilityNodeInfo? {
            for (needle in exact) {
                val hits = root.findAccessibilityNodeInfosByText(needle) ?: continue
                val hit = hits.firstOrNull {
                    norm(it.text) == needle || norm(it.contentDescription) == needle
                }
                if (hit != null) return hit
            }
            for (needle in contains) {
                val hits = root.findAccessibilityNodeInfosByText(needle)
                if (hits != null && hits.isNotEmpty()) return hits[0]
            }
            return null
        }

        private fun clickableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var node: AccessibilityNodeInfo? = start
            var hops = 0
            while (node != null && hops < 7) {
                if (node.isClickable) return node
                node = node.parent
                hops++
            }
            return null
        }

        private fun norm(text: CharSequence?): String =
            text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
    }

    companion object {
        private const val POLL_MS = 60L
        private const val CLEAR_CACHE_ID = "com.android.settings:id/clear_cache_button"
        private val STORAGE_EXACT = listOf("storage & cache", "storage and cache", "storage usage", "storage")
        private val STORAGE_CONTAINS = listOf("storage & cache", "storage usage")
        private val CLEAR_CACHE_TEXTS = listOf("clear cache")

        @Volatile
        var instance: CacheCleanerService? = null

        fun kick() {
            instance?.startPolling()
        }

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${CacheCleanerService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
