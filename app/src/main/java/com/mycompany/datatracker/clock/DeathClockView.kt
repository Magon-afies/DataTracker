package com.crestpoint.datatracker.clock

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.Naira
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.util.FormatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeathClockView(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private val countdown: TextView
    private val detail: TextView
    private val normalColor = ContextCompat.getColor(context, R.color.on_surface)
    private val urgentColor = 0xFFB00020.toInt()
    private val dateFormat = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
    private var scope: CoroutineScope? = null
    private var state: DeathClockState? = null
    private var nairaPerByte: Double = 0.0

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
            text = "Data Death Clock"
            setTextColor(normalColor)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        countdown = TextView(context).apply {
            text = "--h --m --s"
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(normalColor)
            setPadding(0, dp(8), 0, 0)
        }
        detail = TextView(context).apply {
            text = "Calculating…"
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            setPadding(0, dp(6), 0, 0)
        }
        column.addView(title)
        column.addView(countdown)
        column.addView(detail)
        addView(column)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        val app = context.applicationContext
        s.launch {
            var lastCompute = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastCompute >= REFRESH_MS) {
                    lastCompute = now
                    val (next, price) = withContext(Dispatchers.Default) {
                        val computed = runCatching { DeathClock.compute(app, now) }.getOrNull()
                        val perByte = runCatching { PlanStore(app).active(now)?.nairaPerByte ?: 0.0 }
                            .getOrDefault(0.0)
                        computed to perByte
                    }
                    if (next != null) {
                        state = next
                        nairaPerByte = price
                    }
                }
                render(System.currentTimeMillis())
                delay(1000L - System.currentTimeMillis() % 1000L)
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun render(now: Long) {
        val s = state
        if (s == null) {
            countdown.text = "--h --m --s"
            countdown.setTextColor(normalColor)
            detail.text = "Calculating…"
            return
        }
        when (s.kind) {
            DeathClockState.Kind.NO_PLAN -> {
                countdown.text = "--h --m --s"
                countdown.setTextColor(normalColor)
                detail.text = "Set a data plan in Alerts to start the clock."
            }
            DeathClockState.Kind.DEAD -> {
                countdown.text = format(0L)
                countdown.setTextColor(urgentColor)
                detail.text = "Your data balance is exhausted."
            }
            DeathClockState.Kind.IDLE -> {
                countdown.text = "--h --m --s"
                countdown.setTextColor(normalColor)
                detail.text = "${FormatBytes.human(s.remainingBytes)} left · no data burned yet."
            }
            DeathClockState.Kind.COUNTDOWN -> {
                val left = (s.deathMs - now).coerceAtLeast(0L)
                countdown.text = format(left)
                countdown.setTextColor(if (left < 6L * 3_600_000L) urgentColor else normalColor)
                val nairaHour = if (nairaPerByte > 0.0) {
                    " (≈ ${Naira.format(s.bytesPerHour * nairaPerByte)}/hour)"
                } else ""
                val line = "Zero at ${dateFormat.format(Date(s.deathMs))} · " +
                    "${FormatBytes.human(s.remainingBytes)} left · " +
                    "burning ${FormatBytes.human(s.bytesPerHour.toLong())}/h$nairaHour"
                detail.text = if (s.outlastsPlan) {
                    line + "\nPlan expires ${dateFormat.format(Date(s.expiryMs))} first — at this pace it will last."
                } else line
            }
        }
    }

    private fun format(ms: Long): String {
        val t = ms / 1000L
        val d = t / 86400L
        val h = (t % 86400L) / 3600L
        val m = (t % 3600L) / 60L
        val sec = t % 60L
        return if (d > 0L) {
            String.format(Locale.US, "%dd %02dh %02dm %02ds", d, h, m, sec)
        } else {
            String.format(Locale.US, "%02dh %02dm %02ds", h, m, sec)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REFRESH_MS = 60_000L
    }
}
