package com.mycompany.datatracker.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.usage.UsageRepository
import com.mycompany.datatracker.util.FormatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReportCardView(context: Context, attrs: AttributeSet? = null) : MaterialCardView(context, attrs) {
    
    private val gradeText: TextView
    private val titleText: TextView
    private val descText: TextView

    init {
        radius = 24f * resources.displayMetrics.density
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        setContentPadding(24, 24, 24, 24)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        titleText = TextView(context).apply {
            text = "Weekly Report Card"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            textSize = 14f
        }
        
        gradeText = TextView(context).apply {
            text = "..."
            textSize = 36f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        
        descText = TextView(context).apply {
            text = "Analyzing your data habits..."
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            textSize = 14f
        }

        layout.addView(titleText)
        layout.addView(gradeText)
        layout.addView(descText)
        addView(layout)

        loadData()
    }

    private fun loadData() {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val repo = UsageRepository(app)
                val prefs = Prefs(app)
                val now = System.currentTimeMillis()
                
                // Start of week (Monday)
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val weekStart = cal.timeInMillis
                
                val usedThisWeek = repo.mobileUsage(weekStart, now) ?: 0L
                val limitBytes = prefs.dailyLimitMb * 7L * 1024L * 1024L
                
                if (usedThisWeek == 0L) return@withContext Triple("N/A", "No data used yet", Color.GRAY)
                
                val pct = (usedThisWeek.toDouble() / limitBytes.toDouble()) * 100
                
                when {
                    pct <= 40.0 -> Triple("A+", "Incredible! You used ${FormatBytes.human(usedThisWeek)} this week. You are a Data Master.", Color.parseColor("#4CAF50"))
                    pct <= 70.0 -> Triple("B", "Good job. ${FormatBytes.human(usedThisWeek)} used. You're on track to survive the week.", Color.parseColor("#FFC107"))
                    pct <= 100.0 -> Triple("C", "Warning! ${FormatBytes.human(usedThisWeek)} used. You are getting close to your limit.", Color.parseColor("#FF9800"))
                    else -> Triple("F", "Critical! ${FormatBytes.human(usedThisWeek)} used. You are burning data way too fast.", Color.parseColor("#F44336"))
                }
            }
            
            gradeText.text = result.first
            gradeText.setTextColor(result.third)
            descText.text = result.second
        }
    }
}
