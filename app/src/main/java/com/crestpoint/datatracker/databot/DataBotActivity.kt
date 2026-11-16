package com.crestpoint.datatracker.databot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.autopsy.CacheCleanerService
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Control room for the background-data bot: scan, pick apps, restrict/allow, choose how automatic it is. */
class DataBotActivity : AppCompatActivity() {
    companion object {
        /** Used by the Apps tab: restrict one app with the bot if possible, else open Settings by hand. */
        fun restrictOne(ctx: Context, pkg: String, fallback: () -> Unit) {
            if (DataBot.serviceReady(ctx)) {
                ctx.startActivity(
                    Intent(ctx, DataBotActivity::class.java)
                        .putStringArrayListExtra("packages", arrayListOf(pkg))
                        .putExtra("autostart", true)
                )
            } else fallback()
        }
    }

    private lateinit var prefs: DataBotPrefs
    private lateinit var status: TextView
    private lateinit var access: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var list: LinearLayout
    private val boxes = LinkedHashMap<String, CheckBox>()
    private val handler = Handler(Looper.getMainLooper())
    private var countdown: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DataBotPrefs(this)
        val sv = ScrollView(this)
        sv.setBackgroundColor(UiKit.color(this, R.color.surface))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = UiKit.dp(this, 14)
        root.setPadding(pad, UiKit.dp(this, 36), pad, pad)
        sv.addView(root)
        setContentView(sv)

        root.addView(UiKit.text(this, "Background data bot", 22f, bold = true))
        root.addView(UiKit.text(this, "Turns off \"Background data\" for apps that quietly burn your mobile data. " +
            "Restricted apps still work on Wi-Fi and when you open them, but may not get notifications on mobile data.", 12f, R.color.on_surface_muted))

        access = UiKit.text(this, "", 13f)
        root.addView(access)
        root.addView(UiKit.button(this, "Open Accessibility settings", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(UiKit.text(this, "How automatic?", 15f, bold = true).also { it.setPadding(0, UiKit.dp(this, 14), 0, 0) })
        modeRow = LinearLayout(this)
        root.addView(modeRow)
        root.addView(UiKit.text(this,
            "Ask: a notification tells you which apps to restrict (tap to run).\n" +
                "Auto: the bot runs by itself the moment you open Data Tracker. Android doesn't let apps change this setting silently from the background, so this is as automatic as it can safely get.",
            11f, R.color.on_surface_muted))

        val opts = LinearLayout(this)
        opts.addView(UiKit.button(this, "Threshold: ${prefs.thresholdMb} MB / day", true) { }.also { b ->
            b.setOnClickListener {
                val v = intArrayOf(10, 25, 50, 100, 250)
                MaterialAlertDialogBuilder(this).setTitle("Flag apps using more than…")
                    .setItems(v.map { "$it MB of mobile data per day" }.toTypedArray()) { _, i ->
                        prefs.thresholdMb = v[i]; b.text = "Threshold: ${v[i]} MB / day"; scan()
                    }.show()
            }
        })
        opts.addView(UiKit.button(this, "Protected apps", true) { protectedDialog() })
        val hs = android.widget.HorizontalScrollView(this); hs.addView(opts)
        root.addView(hs)

        root.addView(UiKit.text(this, "Apps to restrict", 15f, bold = true).also { it.setPadding(0, UiKit.dp(this, 14), 0, 0) })
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)

        val actions = LinearLayout(this)
        actions.addView(UiKit.button(this, "Scan now", true) { scan() })
        actions.addView(UiKit.button(this, "Restrict selected") { runBot(false) })
        actions.addView(UiKit.button(this, "Allow again", true) { runBot(true) })
        val hs2 = android.widget.HorizontalScrollView(this); hs2.addView(actions)
        root.addView(hs2)

        status = UiKit.text(this, "", 13f, R.color.brand_cyan)
        root.addView(status)
        val bottom = LinearLayout(this)
        bottom.addView(UiKit.button(this, "Cancel run", true) { cancelRun() })
        bottom.addView(UiKit.button(this, "Copy last failure report", true) { copyReport() })
        val hs3 = android.widget.HorizontalScrollView(this); hs3.addView(bottom)
        root.addView(hs3)

        buildModeButtons()
        DataBot.machine(this).onChange = { runOnUiThread { updateStatus() } }

        val pkgs = intent.getStringArrayListExtra("packages")
        if (pkgs != null) {
            showCandidates(pkgs.map { DataCandidate(it, labelOf(it), 0L, -1) }, true)
            if (intent.getBooleanExtra("autostart", false)) countdownStart(pkgs)
        } else {
            scan()
            if (intent.getBooleanExtra("autostart", false)) {
                val p = prefs.pending.toList()
                if (p.isNotEmpty()) countdownStart(p)
            }
        }
    }

    override fun onResume() { super.onResume(); updateStatus() }
    override fun onDestroy() { countdown?.let { handler.removeCallbacks(it) }; super.onDestroy() }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun countdownStart(pkgs: List<String>) {
        status.text = "Starting in 3 seconds… (tap Cancel run to stop)"
        val r = Runnable { startBot(pkgs, false) }
        countdown = r
        handler.postDelayed(r, 3000)
    }

    private fun cancelRun() {
        countdown?.let { handler.removeCallbacks(it) }; countdown = null
        val m = DataBot.machine(this)
        if (m.running) m.cancel()
        status.text = "Cancelled"
    }

    private fun buildModeButtons() {
        modeRow.removeAllViews()
        for ((key, label) in listOf("off" to "Off", "ask" to "Ask me", "auto" to "Auto")) {
            val sel = prefs.mode == key
            modeRow.addView(UiKit.button(this, (if (sel) "● " else "") + label, !sel) {
                prefs.mode = key
                if (key == "off") AutoRestrictWorker.cancel(this) else AutoRestrictWorker.schedule(this)
                buildModeButtons()
            })
        }
    }

    private fun scan() {
        status.text = "Scanning the last 24 hours of usage…"
        lifecycleScope.launch {
            val c = withContext(Dispatchers.IO) { DataBot.candidates(this@DataBotActivity) }
            showCandidates(c, true)
            status.text = if (c.isEmpty()) "No apps are over the threshold 🎉 (needs Usage access)" else "${c.size} app(s) found"
            prefs.pending = c.map { it.pkg }.toSet()
        }
    }

    private fun showCandidates(c: List<DataCandidate>, checked: Boolean) {
        list.removeAllViews(); boxes.clear()
        for (a in c) {
            val cb = CheckBox(this)
            cb.isChecked = checked
            cb.setTextColor(UiKit.color(this, R.color.on_surface))
            cb.text = a.label + if (a.mobileBytes > 0)
                "\n${humanMb(a.mobileBytes)} mobile" + (if (a.bgPercent >= 0) " · ${a.bgPercent}% in background" else "") else ""
            list.addView(cb)
            boxes[a.pkg] = cb
        }
    }

    private fun selectedPackages(): List<String> = boxes.filter { it.value.isChecked }.keys.toList()

    private fun runBot(allow: Boolean) {
        val sel = if (allow && boxes.isEmpty()) DataBotPrefs(this).restricted().toList() else selectedPackages()
        if (sel.isEmpty()) { UiKit.toast(this, "Nothing selected"); return }
        startBot(sel, allow)
    }

    private fun startBot(pkgs: List<String>, allow: Boolean) {
        countdown = null
        if (!DataBot.start(this, pkgs, allow)) {
            MaterialAlertDialogBuilder(this).setTitle("Turn on the helper first")
                .setMessage("Open Accessibility settings, choose Data Tracker, and switch it on. It's the same helper the cache cleaner uses.")
                .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
        } else updateStatus()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        val on = DataBot.serviceReady(this)
        access.text = if (on) "✅ Accessibility helper is ON" else "⚠️ Accessibility helper is OFF — needed for the bot"
        val m = DataBot.machine(this)
        if (m.running) {
            status.text = "Working on ${m.current?.let { labelOf(it) } ?: "…"}  (${m.done}/${m.total}) — please don't touch the screen"
        } else if (m.finished && m.total > 0) {
            status.text = (m.error ?: "${if (m.wantAllowed) "Allowed" else "Restricted"} ${m.changed.size}") +
                (if (m.already.isNotEmpty()) " · ${m.already.size} already set" else "") +
                (if (m.skipped.isNotEmpty()) " · ${m.skipped.size} skipped (see failure report)" else "")
        }
    }

    private fun protectedDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val prot = prefs.protectedApps().toMutableSet()
        val names = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].packageName in prot }
        MaterialAlertDialogBuilder(this).setTitle("Never restrict these apps")
            .setMultiChoiceItems(names, checked) { _, i, on -> if (on) prot.add(apps[i].packageName) else prot.remove(apps[i].packageName) }
            .setPositiveButton("Save") { _, _ -> prefs.setProtected(prot); scan() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun copyReport() {
        val d = DataBot.machine(this).lastDump
        if (d.isBlank()) { UiKit.toast(this, "No failures recorded yet"); return }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("databot", d))
        UiKit.toast(this, "Copied — paste it to Claude so the bot can learn your phone's wording")
    }
}
