package com.crestpoint.datatracker.databot

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.Design
import com.crestpoint.datatracker.files.ui.Glass
import com.crestpoint.datatracker.files.ui.PathIcon
import com.crestpoint.datatracker.files.ui.UiKit

class DataBotActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accent = UiKit.color(this, R.color.brand_purple)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B14.toInt())
            setPadding(UiKit.dp(this, 16), UiKit.dp(this, 32), UiKit.dp(this, 16), UiKit.dp(this, 16))
        }

        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Glass.iconBtn(this, Glass.BACK) { finish() })
            addView(UiKit.text(this, "Background Data Bot", 18f, bold = true), UiKit.lp(0, -2, 1f))
        }
        root.addView(topBar)

        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16))
        }

        // Hero Card
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = Design.card(this@DataBotActivity, 0xFF141526.toInt(), 20)
            setPadding(UiKit.dp(this, 24), UiKit.dp(this, 28), UiKit.dp(this, 24), UiKit.dp(this, 28))
            
            val icon = ImageView(this@DataBotActivity).apply {
                setImageDrawable(PathIcon(Glass.SETTINGS, accent))
                layoutParams = LinearLayout.LayoutParams(UiKit.dp(this@DataBotActivity, 56), UiKit.dp(this@DataBotActivity, 56))
            }
            addView(icon)
            
            addView(UiKit.text(this@DataBotActivity, "Background App Guard", 18f, bold = true).apply {
                setPadding(0, UiKit.dp(this@DataBotActivity, 12), 0, 4); gravity = Gravity.CENTER
            })
            addView(UiKit.text(this@DataBotActivity, "Easily manage and restrict background data consumption app-by-app to stop hidden data drains.", 13f, R.color.on_surface_muted).apply {
                gravity = Gravity.CENTER
            })
        }
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@DataBotActivity, 24)) })

        // Action Buttons for Background Restriction Guidance
        fun addActionCard(title: String, desc: String, onClick: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Design.card(this@DataBotActivity, 0xFF141526.toInt(), 16)
                setPadding(UiKit.dp(this@DataBotActivity, 18), UiKit.dp(this@DataBotActivity, 16), UiKit.dp(this@DataBotActivity, 16), UiKit.dp(this@DataBotActivity, 16))
                setOnClickListener { onClick() }
                Design.ripple(this, 16)
                
                addView(UiKit.text(this@DataBotActivity, title, 15f, bold = true))
                addView(UiKit.text(this@DataBotActivity, desc, 12f, R.color.on_surface_muted).apply {
                    setPadding(0, 4, 0, 0)
                })
            }
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, UiKit.dp(this@DataBotActivity, 12)) })
        }

        addActionCard("🛡️ Restrict Background Data Settings", "Open system data usage settings to toggle background data off for heavy apps one after the other.") {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }

        addActionCard("🔋 Battery & Background Restrictions", "Open battery usage optimization to stop background CPU and data drain.") {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }

        sv.addView(content)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
