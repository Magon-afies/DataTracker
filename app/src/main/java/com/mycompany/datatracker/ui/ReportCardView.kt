package com.crestpoint.datatracker.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.usage.UsageRepository
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReportCardView(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private class Grade(val letter: String, val message: String, val color: Int)

    private val gradeText: TextView
    private val descText: TextView
    private var scope: CoroutineScope? = null

    init {
        radius = dp(24).toFloat()
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        setContentPadding(dp(18), dp(18), dp(18), dp(18))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(context).apply {
            text = "Weekly Report Card"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            textSize = 14f
        }
        gradeText = TextView(context).apply {
            text = "..."
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, dp(6), 0, dp(6))
        }
        descText = TextView(context).apply {
            text = "Analyzing your data habits..."
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 14f
        }
        column.addView(title)
        column.addView(gradeText)
        column.addView(descText)
        addView(column)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        val app = context.applicationContext
        s.launch {
            val grade = withContext(Dispatchers.IO) {
                runCatching { computeGrade(app) }.getOrNull()
            }
            if (grade != null) {
                gradeText.text = grade.letter
                gradeText.setTextColor(grade.color)
                descText.text = grade.message
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun computeGrade(app: Context): Grade {
        val muted = ContextCompat.getColor(app, R.color.on_surface_muted)
        val now = System.currentTimeMillis()
        val plan = PlanStore(app).active(now)
            ?: return Grade("--", "Add your data plan in the Alerts tab to get graded.", muted)

        val planDays = ((plan.expiryMs - plan.startMs + DAY_MS - 1L) / DAY_MS).coerceAtLeast(1L)
        val weeklyAllowance =
            plan.sizeBytes.toDouble() / planDays.toDouble() * minOf(7L, planDays).toDouble()

        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val from = maxOf(cal.timeInMillis, plan.startMs)

        val used = UsageRepository(app).mobileUsage(from, now) ?: 0L
        if (used <= 0L) return Grade("N/A", "No mobile data used yet this week.", muted)

        val pct = used.toDouble() / weeklyAllowance.coerceAtLeast(1.0) * 100.0
        val usedText = FormatBytes.human(used)
        return when {
            pct <= 40.0 -> Grade(
                "A+", "Incredible! You used $usedText this week. You are a Data Master.",
                ContextCompat.getColor(app, R.color.health_good)
            )
            pct <= 70.0 -> Grade(
                "B", "Good job. $usedText used. You are on track to survive the week.",
                Color.parseColor("#FFC107")
            )
            pct <= 100.0 -> Grade(
                "C", "Warning! $usedText used. You are getting close to your limit.",
                ContextCompat.getColor(app, R.color.health_warm)
            )
            else -> Grade(
                "F", "Critical! $usedText used. You are burning data way too fast.",
                ContextCompat.getColor(app, R.color.health_bad)
            )
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
