package com.mycompany.datatracker.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.mycompany.datatracker.R
import java.util.Locale

class BatteryHealthCard(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private val tvVerdict: TextView
    private val tvStats: TextView
    private val tvWarning: TextView
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) render(intent)
        }
    }

    init {
        radius = dp(20).toFloat()
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        setContentPadding(dp(18), dp(18), dp(18), dp(18))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.battery_title)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            textSize = 14f
        }
        tvVerdict = TextView(context).apply {
            text = "…"
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, dp(6), 0, dp(6))
        }
        tvStats = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 14f
        }
        tvWarning = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.health_warm))
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        column.addView(title)
        column.addView(tvVerdict)
        column.addView(tvStats)
        column.addView(tvWarning)
        addView(column)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!registered) {
            val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
            if (sticky != null) render(sticky)
        }
    }

    override fun onDetachedFromWindow() {
        if (registered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            registered = false
        }
        super.onDetachedFromWindow()
    }

    private fun render(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val health = intent.getIntExtra(
            BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN
        )
        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val charge = if (pct >= 0) "$pct%" else "--"
        tvStats.text = String.format(
            Locale.US,
            "Temp: %.1f°C\nVoltage: %d mV\nHealth: %s\nCharge: %s · %s",
            tempC, voltageMv, healthText, charge, if (charging) "Charging" else "Discharging"
        )

        val degraded = health == BatteryManager.BATTERY_HEALTH_OVERHEAT ||
            health == BatteryManager.BATTERY_HEALTH_DEAD || tempC >= 45f
        val warm = !degraded && tempC >= 39f

        when {
            degraded -> {
                tvVerdict.text = "DEGRADED"
                tvVerdict.setTextColor(ContextCompat.getColor(context, R.color.health_bad))
                tvWarning.text = "Battery is overheating or failing. Unplug and close heavy apps."
                tvWarning.visibility = View.VISIBLE
            }
            warm -> {
                tvVerdict.text = "WARM"
                tvVerdict.setTextColor(ContextCompat.getColor(context, R.color.health_warm))
                tvWarning.text = "Battery is warm. Charging may slow down to protect it."
                tvWarning.visibility = View.VISIBLE
            }
            else -> {
                tvVerdict.text = "HEALTHY"
                tvVerdict.setTextColor(ContextCompat.getColor(context, R.color.health_good))
                tvWarning.visibility = View.GONE
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
