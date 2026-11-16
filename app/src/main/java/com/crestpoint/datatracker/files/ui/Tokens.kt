package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.crestpoint.datatracker.R

/** One place for the app's look: colours, spacing, corner radii and text sizes. */
object Tokens {
    // colours (same values as res/values/colors.xml)
    val BG = 0xFF0F1020.toInt()
    val SURFACE = 0xFF191A2D.toInt()
    val SURFACE_HIGH = 0xFF23243B.toInt()
    val OUTLINE = 0x1AFFFFFF
    val PRIMARY = 0xFF7657F6.toInt()
    val ACCENT = 0xFF26C6DA.toInt()
    val GOOD = 0xFF4CAF50.toInt()
    val WARN = 0xFFFF9800.toInt()
    val BAD = 0xFFF44336.toInt()
    val TEXT = 0xFFF7F5FF.toInt()
    val TEXT_MUTED = 0xFFB8B5C9.toInt()

    // spacing (dp)
    const val SP_XS = 4
    const val SP_S = 8
    const val SP_M = 12
    const val SP_L = 16
    const val SP_XL = 24

    // corner radius (dp)
    const val R_S = 10
    const val R_M = 16
    const val R_L = 24

    // text sizes (sp)
    const val T_CAPTION = 12f
    const val T_BODY = 14f
    const val T_TITLE = 16f
    const val T_HEAD = 20f
    const val T_DISPLAY = 32f

    /** Rounded dark card with a thin light outline. */
    fun cardBg(ctx: Context, radiusDp: Int = R_M): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(UiKit.color(ctx, R.color.surface_elevated))
        g.cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
        g.setStroke(UiKit.dp(ctx, 1), OUTLINE)
        return g
    }

    fun pillBg(ctx: Context, color: Int, radiusDp: Int = R_L): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
        return g
    }
}
