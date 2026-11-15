package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.crestpoint.datatracker.R

object Design {
    fun ripple(v: View, radiusDp: Int = 12) {
        val ctx = v.context
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
            setColor(Color.WHITE)
        }
        v.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), v.background, mask)
    }

    fun card(ctx: Context, bgCol: Int = 0xFF141526.toInt(), radiusDp: Int = 16): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgCol)
            cornerRadius = UiKit.dp(ctx, radiusDp).toFloat()
            setStroke(UiKit.dp(ctx, 1), 0x22FFFFFF)
        }
    }

    fun chip(ctx: Context, label: String, active: Boolean, onClick: () -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(if (active) UiKit.color(ctx, R.color.brand_purple) else 0xFF1E1F38.toInt())
                cornerRadius = UiKit.dp(ctx, 20).toFloat()
                if (!active) setStroke(UiKit.dp(ctx, 1), 0x33FFFFFF)
            }
            val padH = UiKit.dp(ctx, 16); val padV = UiKit.dp(ctx, 10)
            setPadding(padH, padV, padH, padV)
            setOnClickListener { onClick() }
            ripple(this, 20)
        }
        val t = UiKit.text(ctx, label, 13f, if (active) android.R.color.white else R.color.on_surface_muted)
        t.gravity = Gravity.CENTER
        box.addView(t)
        box.layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
            setMargins(UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 6), 0)
        }
        return box
    }
}
