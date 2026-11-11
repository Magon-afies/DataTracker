package com.crestpoint.datatracker.ui

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.usage.UsageRepository
import com.crestpoint.datatracker.work.Alerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupChecklistCard(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private class Row(val mark: TextView, val button: MaterialButton?)

    private val rows = ArrayList<Row>()
    private var scope: CoroutineScope? = null

    init {
        radius = dp(20).toFloat()
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        setContentPadding(dp(18), dp(14), dp(18), dp(14))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(context).apply {
            setText(R.string.setup_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, 0, 0, dp(6))
        }
        column.addView(title)

        rows.add(addRow(column, R.string.setup_usage, R.string.setup_allow) {
            UsageRepository(context).openUsageAccessSettings()
        })
        rows.add(addRow(column, R.string.setup_notifications, R.string.setup_allow) {
            tryStart(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        })
        rows.add(addRow(column, R.string.setup_battery, R.string.setup_fix) {
            if (!tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                tryStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + context.packageName))
                )
            }
        })
        rows.add(addRow(column, R.string.setup_plan, null, null))

        addView(column)
    }

    private fun addRow(
        column: LinearLayout,
        labelRes: Int,
        buttonRes: Int?,
        onClick: (() -> Unit)?
    ): Row {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val mark = TextView(context).apply {
            text = "○"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val label = TextView(context).apply {
            setText(labelRes)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        line.addView(mark)
        line.addView(label)

        var button: MaterialButton? = null
        if (buttonRes != null && onClick != null) {
            button = MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                setText(buttonRes)
                textSize = 12f
                insetTop = 0
                insetBottom = 0
                minimumWidth = 0
                minHeight = dp(36)
                setOnClickListener { onClick() }
            }
            line.addView(button)
        }
        column.addView(line)
        return Row(mark, button)
    }

    private fun tryStart(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        refresh()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refresh()
    }

    private fun refresh() {
        val s = scope ?: return
        val app = context.applicationContext
        s.launch {
            val done = withContext(Dispatchers.Default) {
                booleanArrayOf(
                    runCatching { UsageRepository(app).hasUsageAccess() }.getOrDefault(false),
                    Alerts.canNotify(app),
                    runCatching {
                        app.getSystemService(PowerManager::class.java)
                            ?.isIgnoringBatteryOptimizations(app.packageName) == true
                    }.getOrDefault(false),
                    runCatching { PlanStore(app).active() != null }.getOrDefault(false)
                )
            }
            val good = ContextCompat.getColor(context, R.color.health_good)
            val muted = ContextCompat.getColor(context, R.color.on_surface_muted)
            for (i in rows.indices) {
                val ok = done[i]
                rows[i].mark.text = if (ok) "✓" else "○"
                rows[i].mark.setTextColor(if (ok) good else muted)
                rows[i].button?.visibility = if (ok) View.GONE else View.VISIBLE
            }
            visibility = if (done.all { it }) View.GONE else View.VISIBLE
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
