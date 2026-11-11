package com.crestpoint.datatracker.cleaner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.autopsy.CacheCleanerService
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CacheBotActivity : AppCompatActivity() {

    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var button: Button
    private var wakeLock: PowerManager.WakeLock? = null
    private var resumedAt = 0L
    private var packages: List<String> = emptyList()
    private var beforeBytes = 0L
    private var freedText: String? = null
    private var summaryRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()

        val machine = CacheBot.get(this)
        machine.launcher = { pkg ->
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        }
        machine.onChange = { render() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (machine.running) {
                    if (SystemClock.uptimeMillis() - resumedAt < 900L) return
                    machine.cancel()
                } else {
                    finish()
                }
            }
        })

        val requested = intent.getStringArrayExtra(EXTRA_PACKAGES)?.toList()
        if (requested != null && !machine.running && savedInstanceState == null) {
            packages = requested
            lifecycleScope.launch {
                beforeBytes = withContext(Dispatchers.IO) { CacheBot.cacheBytes(applicationContext, requested) }
                acquireWakeLock()
                machine.begin(requested)
                CacheCleanerService.kick()
                machine.launchNextIfIdle()
                render()
            }
        } else {
            packages = requested ?: emptyList()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        resumedAt = SystemClock.uptimeMillis()
        val machine = CacheBot.get(this)
        if (machine.running && !CacheCleanerService.isEnabled(this)) {
            machine.cancel()
        }
        CacheCleanerService.kick()
        machine.launchNextIfIdle()
        render()
    }

    override fun onDestroy() {
        val machine = CacheBot.peek()
        if (machine != null) {
            machine.launcher = null
            machine.onChange = null
            if (isFinishing && machine.running) machine.cancel()
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun render() {
        val machine = CacheBot.get(this)
        val total = machine.total
        progress.max = if (total > 0) total else 1
        progress.progress = machine.done.coerceAtMost(progress.max)
        if (machine.running) {
            val label = machine.current?.let { labelOf(it) } ?: "Starting"
            title.text = "Cleaning caches"
            status.text = "App ${(machine.done + 1).coerceAtMost(total)} of $total: $label\n" +
                "Cleared ${machine.cleared} · skipped ${machine.skipped}\n\n" +
                "Please do not touch the phone while the robot works."
            button.text = getString(R.string.bot_cancel)
            button.setOnClickListener { machine.cancel() }
        } else {
            releaseWakeLock()
            val err = machine.error
            title.text = if (err != null) "Stopped" else "All done"
            val freed = freedText
            status.text = buildString {
                append("Cleared ${machine.cleared} app(s) · skipped ${machine.skipped}")
                if (freed != null) append("\n$freed")
                if (err != null) append("\n\n$err")
            }
            button.text = getString(R.string.bot_close)
            button.setOnClickListener { finish() }
            if (machine.finished && !summaryRequested && packages.isNotEmpty()) {
                summaryRequested = true
                lifecycleScope.launch {
                    val after = withContext(Dispatchers.IO) { CacheBot.cacheBytes(applicationContext, packages) }
                    val freedBytes = (beforeBytes - after).coerceAtLeast(0L)
                    freedText = "Freed about " + FormatBytes.human(freedBytes)
                    render()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "DataTracker:cachebot").also {
            it.acquire(10L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun buildUi() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(ContextCompat.getColor(this@CacheBotActivity, R.color.surface))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        title = TextView(this).apply {
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@CacheBotActivity, R.color.on_surface))
            text = getString(R.string.bot_title)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad; bottomMargin = pad }
        }
        status = TextView(this).apply {
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@CacheBotActivity, R.color.on_surface_muted))
        }
        button = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad }
        }
        root.addView(title)
        root.addView(progress)
        root.addView(status)
        root.addView(button)
        setContentView(root)
    }

    companion object {
        private const val EXTRA_PACKAGES = "packages"

        fun start(context: Context, packages: List<String>) {
            context.startActivity(
                Intent(context, CacheBotActivity::class.java)
                    .putExtra(EXTRA_PACKAGES, packages.toTypedArray())
            )
        }
    }
}
