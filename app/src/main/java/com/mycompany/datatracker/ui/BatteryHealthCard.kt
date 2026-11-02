package com.mycompany.datatracker.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.mycompany.datatracker.R
import java.util.Locale

class BatteryHealthCard(context: Context, attrs: AttributeSet? = null) : MaterialCardView(context, attrs) {
    private val tvStats: TextView
    private val tvVerdict: TextView
    private val tvWarning: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateUI(intent)
            }
        }
    }

    init {
        radius = 20f * resources.displayMetrics.density
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        setContentPadding(18, 18, 18, 18)

        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        
        val title = TextView(context).apply {
            text = "Battery Health"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            textSize = 14f
        }
        
        tvVerdict = TextView(context).apply {
            text = "Analyzing..."
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        
        tvStats = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 14f
        }
        
        tvWarning = TextView(context).apply {
            setTextColor(Color.parseColor("#FF9800"))
            textSize = 14f
            setPadding(0, 8, 0, 0)
            visibility = GONE
        }

        layout.addView(title)
        layout.addView(tvVerdict)
        layout.addView(tvStats)
        layout.addView(tvWarning)
        addView(layout)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDetachedFromWindow()
    }

    private fun updateUI(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = (level.toFloat() / scale.toFloat() * 100).toInt()
        
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            else -> "Unknown"
        }

        tvStats.text = String.format(Locale.US, 
            "Charge: %d%%\nTemp: %.1f°C\nVoltage: %d mV\nStatus: %s · Health: %s", 
            pct, tempC, voltageMv, if (isCharging) "Charging" else "Discharging", healthStr
        )

        // Verdict Logic
        if (health == BatteryManager.BATTERY_HEALTH_OVERHEAT || health == BatteryManager.BATTERY_HEALTH_DEAD || tempC >= 45f) {
            tvVerdict.text = "DEGRADED"
            tvVerdict.setTextColor(Color.parseColor("#F44336")) // Red
            tvWarning.text = "⚠️ Your battery is running dangerously hot. Unplug or close heavy apps."
            tvWarning.visibility = VISIBLE
        } else if (tempC >= 39f) {
            tvVerdict.text = "WARM"
            tvVerdict.setTextColor(Color.parseColor("#FF9800")) // Orange
            tvWarning.text = "⚠️ Battery is warm. If charging, it may slow down to protect itself."
            tvWarning.visibility = VISIBLE
        } else {
            tvVerdict.text = "HEALTHY"
            tvVerdict.setTextColor(Color.parseColor("#4CAF50")) // Green
            tvWarning.visibility = GONE
        }
    }
}
