package com.crestpoint.datatracker.databot

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.autopsy.CacheCleanerService
import com.crestpoint.datatracker.usage.UsageRepository
import com.crestpoint.datatracker.util.FormatBytes
import java.util.Locale

data class DataCandidate(val pkg: String, val label: String, val mobileBytes: Long, val bgPercent: Int)

class DataBotPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("databot_prefs", Context.MODE_PRIVATE)

    /** "off", "ask" (notify me) or "auto" (run as soon as I open the app). */
    var mode: String
        get() = sp.getString("mode", "off") ?: "off"
        set(v) { sp.edit().putString("mode", v).apply() }
    var thresholdMb: Int
        get() = sp.getInt("threshold_mb", 25)
        set(v) { sp.edit().putInt("threshold_mb", v).apply() }
    var lastAutoRun: Long
        get() = sp.getLong("last_auto", 0L)
        set(v) { sp.edit().putLong("last_auto", v).apply() }

    private fun getSet(key: String, def: String = ""): Set<String> =
        (sp.getString(key, def) ?: def).split(",").filter { it.isNotBlank() }.toSet()
    private fun putSet(key: String, s: Set<String>) { sp.edit().putString(key, s.joinToString(",")).apply() }

    fun protectedApps(): Set<String> = getSet("protected", DEFAULT_PROTECTED.joinToString(","))
    fun setProtected(s: Set<String>) = putSet("protected", s)
    fun restricted(): Set<String> = getSet("restricted")
    fun addRestricted(l: Collection<String>) = putSet("restricted", restricted() + l)
    fun removeRestricted(l: Collection<String>) = putSet("restricted", restricted() - l.toSet())
    var pending: Set<String>
        get() = getSet("pending")
        set(v) = putSet("pending", v)

    companion object {
        val DEFAULT_PROTECTED = listOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.google.android.gm", "com.truecaller"
        )
    }
}

object DataBot {
    private const val CH = "databot"
    private const val NOTIF = 4301
    private const val NOTIF_DONE = 4302
    @Volatile private var machine: DataBotMachine? = null
    private val handler = Handler(Looper.getMainLooper())

    fun machine(ctx: Context): DataBotMachine = synchronized(this) {
        machine ?: DataBotMachine(ctx.applicationContext.packageName) { SystemClock.uptimeMillis() }.also { machine = it }
    }

    fun serviceReady(ctx: Context): Boolean =
        CacheCleanerService.isEnabled(ctx) && CacheCleanerService.instance != null

    private val poll = object : Runnable {
        override fun run() {
            val m = machine ?: return
            m.tick()
            if (m.running) handler.postDelayed(this, 150)
        }
    }

    fun start(ctx: Context, packages: List<String>, allow: Boolean): Boolean {
        val app = ctx.applicationContext
        if (!serviceReady(app)) return false
        val m = machine(app)
        if (m.running) return true
        m.screen = ServiceDataScreen()
        m.launcher = { pkg ->
            app.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        m.onFinished = { finished(app, m) }
        m.begin(packages, allow)
        handler.removeCallbacks(poll)
        handler.post(poll)
        return true
    }

    private fun finished(app: Context, m: DataBotMachine) {
        val prefs = DataBotPrefs(app)
        if (m.wantAllowed) {
            prefs.removeRestricted(m.changed + m.already)
        } else {
            prefs.addRestricted(m.changed + m.already)
        }
        prefs.pending = prefs.pending - (m.changed + m.already).toSet()
        val text = if (m.error != null) m.error!! else
            "${if (m.wantAllowed) "Allowed" else "Restricted"} ${m.changed.size}" +
                (if (m.already.isNotEmpty()) " · ${m.already.size} already set" else "") +
                (if (m.skipped.isNotEmpty()) " · ${m.skipped.size} skipped" else "")
        post(app, NOTIF_DONE, "Background data bot", text, null)
    }

    // ----------------------------------------------------------- detection

    fun candidates(ctx: Context): List<DataCandidate> {
        val app = ctx.applicationContext
        val prefs = DataBotPrefs(app)
        val pm = app.packageManager
        val now = System.currentTimeMillis()
        val usage = try { UsageRepository(app).query(now - 24L * 3600_000L, now) } catch (_: Throwable) { return emptyList() }
        val skip = prefs.protectedApps() + prefs.restricted() + app.packageName
        val min = prefs.thresholdMb * 1024L * 1024L
        val out = ArrayList<DataCandidate>()
        for (a in usage) {
            val pkg = a.packageName ?: continue
            if (pkg in skip) continue
            val system = try {
                (pm.getApplicationInfo(pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) { true }
            if (system) continue
            if (a.mobileTotal < min) continue
            val bp = a.backgroundPercent
            if (bp in 0..59) continue
            if (bp < 0 && a.mobileTotal < min * 2) continue
            out.add(DataCandidate(pkg, a.label, a.mobileTotal, bp))
        }
        return out.sortedByDescending { it.mobileBytes }
    }

    fun notifyPending(ctx: Context, c: List<DataCandidate>) {
        val i = Intent(ctx, DataBotActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val names = c.take(3).joinToString(", ") { it.label }
        post(ctx, NOTIF, "${c.size} app(s) use background mobile data",
            "$names${if (c.size > 3) "…" else ""} — tap to restrict them", pi)
    }

    private fun post(ctx: Context, id: Int, title: String, text: String, pi: PendingIntent?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "Background data bot", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat_datatracker)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (pi != null) b.setContentIntent(pi)
        nm.notify(id, b.build())
    }

    /** Called when the app is opened: in "auto" mode, start the bot straight away. */
    fun onAppOpened(activity: Activity) {
        val prefs = DataBotPrefs(activity)
        if (prefs.mode != "auto" || prefs.pending.isEmpty()) return
        if (!serviceReady(activity) || machine?.running == true) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastAutoRun < 30 * 60_000L) return
        prefs.lastAutoRun = now
        activity.startActivity(Intent(activity, DataBotActivity::class.java).putExtra("autostart", true))
    }

    // ------------------------------------------------------ screen driver

    private class ServiceDataScreen : DataBotScreen {
        private fun root(): AccessibilityNodeInfo? = try { CacheCleanerService.instance?.rootInActiveWindow } catch (_: Exception) { null }

        override fun activePackage(): String? = try { root()?.packageName?.toString() } catch (_: Exception) { null }

        override fun back() { CacheCleanerService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }

        override fun clickDataEntry(): Boolean {
            val r = root() ?: return false
            val node = findText(r, ENTRY_TEXTS) ?: return false
            val t = clickableAncestor(node) ?: return false
            return t.isEnabled && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        private class Hit(val checkable: AccessibilityNodeInfo, val row: AccessibilityNodeInfo?, val inverse: Boolean)

        private fun findToggle(r: AccessibilityNodeInfo): Hit? {
            for (needle in TOGGLE_TEXTS) {
                val hits = r.findAccessibilityNodeInfosByText(needle) ?: continue
                for (h in hits) {
                    val txt = norm(h.text)
                    if (txt != needle && !txt.startsWith(needle)) continue
                    var n: AccessibilityNodeInfo? = h
                    var hops = 0
                    while (n != null && hops < 6) {
                        val c = checkableIn(n, 0)
                        if (c != null) return Hit(c, clickableAncestor(h), needle.startsWith("restrict"))
                        n = n.parent; hops++
                    }
                }
            }
            return null
        }

        private fun checkableIn(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (n.isCheckable) return n
            if (depth >= 3) return null
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                val f = checkableIn(c, depth + 1)
                if (f != null) return f
            }
            return null
        }

        override fun readToggle(): Toggle {
            val r = root() ?: return Toggle.NOT_FOUND
            val h = findToggle(r) ?: return Toggle.NOT_FOUND
            val on = h.checkable.isChecked
            val allowed = if (h.inverse) !on else on
            return if (allowed) Toggle.ALLOWED else Toggle.BLOCKED
        }

        override fun clickToggle(): Boolean {
            val r = root() ?: return false
            val h = findToggle(r) ?: return false
            val target = h.row?.takeIf { it.isEnabled } ?: h.checkable
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        override fun confirmDialog(): Boolean {
            val r = root() ?: return false
            val b = r.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull() ?: return false
            if (norm(b.text) !in CONFIRM_TEXTS) return false
            return b.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        override fun dump(): String {
            val r = root() ?: return "(no window)"
            val sb = StringBuilder()
            var count = 0
            fun walk(n: AccessibilityNodeInfo?, d: Int) {
                if (n == null || count > 160 || d > 14) return
                val t = n.text?.toString() ?: n.contentDescription?.toString()
                if (!t.isNullOrBlank() || n.isCheckable) {
                    count++
                    sb.append("  ".repeat(d.coerceAtMost(6))).append(t ?: "").append(
                        if (n.isCheckable) "  [switch checked=${n.isChecked}]" else ""
                    ).append("  <").append(n.className?.toString()?.substringAfterLast('.')).append(">\n")
                }
                for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
            }
            sb.append("Window package: ${r.packageName}\n")
            walk(r, 0)
            return sb.toString()
        }

        private fun findText(r: AccessibilityNodeInfo, needles: List<String>): AccessibilityNodeInfo? {
            for (needle in needles) {
                val hits = r.findAccessibilityNodeInfosByText(needle) ?: continue
                val exact = hits.firstOrNull { norm(it.text) == needle }
                if (exact != null) return exact
            }
            for (needle in needles) {
                val hits = r.findAccessibilityNodeInfosByText(needle)
                if (hits != null && hits.isNotEmpty()) return hits[0]
            }
            return null
        }

        private fun clickableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            var n: AccessibilityNodeInfo? = start
            var hops = 0
            while (n != null && hops < 7) { if (n.isClickable) return n; n = n.parent; hops++ }
            return null
        }

        private fun norm(t: CharSequence?): String = t?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""

        companion object {
            val ENTRY_TEXTS = listOf(
                "mobile data & wi-fi", "mobile data and wi-fi", "mobile data usage", "data usage",
                "mobile data", "network usage", "data usage & restrictions"
            )
            val TOGGLE_TEXTS = listOf(
                "background data", "allow background data usage", "background data usage",
                "allow background data", "restrict background data"
            )
            val CONFIRM_TEXTS = setOf("ok", "turn off", "restrict", "yes", "disable", "stop", "confirm")
        }
    }
}

fun humanMb(b: Long): String = FormatBytes.human(b)
