package com.mycompany.datatracker.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.util.ApkShare
import com.mycompany.datatracker.util.ReportShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareCard(context: Context, attrs: AttributeSet? = null) :
    MaterialCardView(context, attrs) {

    private var scope: CoroutineScope? = null

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
            setText(R.string.share_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val reportButton = MaterialButton(context).apply {
            setText(R.string.share_report)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { shareReport() }
        }
        val appButton = MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            setText(R.string.share_app)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(8) }
            setOnClickListener { shareApp() }
        }
        buttons.addView(reportButton)
        buttons.addView(appButton)

        val privacy = TextView(context).apply {
            setText(R.string.share_privacy)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            setPadding(0, dp(10), 0, 0)
        }
        val credit = TextView(context).apply {
            setText(R.string.brand_credit)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.on_surface_muted))
            setPadding(0, dp(4), 0, 0)
        }
        column.addView(title)
        column.addView(buttons)
        column.addView(privacy)
        column.addView(credit)
        addView(column)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun shareReport() {
        val s = scope ?: return
        val app = context.applicationContext
        s.launch {
            val text = withContext(Dispatchers.Default) {
                runCatching { ReportShare.build(app) }.getOrNull()
            }
            if (text == null) {
                Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            launchChooser(send, R.string.share_report)
        }
    }

    private fun shareApp() {
        val s = scope ?: return
        val app = context.applicationContext
        s.launch {
            val send = withContext(Dispatchers.IO) {
                runCatching { ApkShare.buildIntent(app) }.getOrNull()
            }
            if (send == null) {
                Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            launchChooser(send, R.string.share_app)
        }
    }

    private fun launchChooser(send: Intent, titleRes: Int) {
        try {
            context.startActivity(
                Intent.createChooser(send, context.getString(titleRes))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
