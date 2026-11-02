package com.mycompany.datatracker.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.mycompany.datatracker.usage.AppUsage
import com.mycompany.datatracker.util.FormatBytes
import java.util.Locale

class AppTimelineDialog {
    companion object {
        fun show(context: Context, appName: String, hourBytes: LongArray) {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            val title = TextView(context).apply {
                text = "$appName (Last 24h)"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 30)
            }
            layout.addView(title)

            val maxBytes = (hourBytes.maxOrNull() ?: 0L).coerceAtLeast(1L)

            for (i in 0 until 24) {
                val bytes = hourBytes[i]
                if (bytes == 0L) continue

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val timeText = TextView(context).apply {
                    text = String.format(Locale.US, "%02d:00", i)
                    textSize = 14f
                    width = 120
                }
                
                // Simple bar view
                val bar = android.view.View(context).apply {
                    val height = ((bytes.toDouble() / maxBytes.toDouble()) * 50).toInt().coerceAtLeast(10)
                    layoutParams = LinearLayout.LayoutParams(
                        (bytes.toDouble() / maxBytes.toDouble() * 300).toInt().coerceAtLeast(20),
                        height
                    )
                    setBackgroundColor(Color.parseColor("#7C4DFF"))
                }

                val sizeText = TextView(context).apply {
                    text = " " + FormatBytes.human(bytes)
                    textSize = 12f
                    setTextColor(Color.GRAY)
                }

                row.addView(timeText)
                row.addView(bar)
                row.addView(sizeText)
                layout.addView(row)
            }

            if (layout.childCount == 1) {
                val empty = TextView(context).apply {
                    text = "No traffic in the last 24 hours."
                    setTextColor(Color.GRAY)
                }
                layout.addView(empty)
            }

            AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton("Close", null)
                .show()
        }
    }
}
