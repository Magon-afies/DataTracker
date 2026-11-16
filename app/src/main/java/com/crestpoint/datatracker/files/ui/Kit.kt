package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.crestpoint.datatracker.R
import com.google.android.material.button.MaterialButton

/** Ready-made building blocks so every screen looks the same without repeating layout code. */
object Kit {
    class Screen(val root: LinearLayout, val body: LinearLayout)

    /** Dark page with a title bar (optional back button) and a scrolling body. */
    fun screen(ctx: Context, title: String, onBack: (() -> Unit)? = null): Screen {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Tokens.BG)
        root.setPadding(UiKit.dp(ctx, Tokens.SP_M), UiKit.dp(ctx, 28), UiKit.dp(ctx, Tokens.SP_M), UiKit.dp(ctx, Tokens.SP_M))
        val bar = LinearLayout(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        if (onBack != null) bar.addView(UiKit.iconBtn(ctx, "←") { onBack() })
        bar.addView(UiKit.text(ctx, title, Tokens.T_HEAD, bold = true), UiKit.lp(0, -2, 1f))
        root.addView(bar)
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(body)
        root.addView(sv, UiKit.lp(-1, 0, 1f))
        return Screen(root, body)
    }

    fun section(ctx: Context, label: String): TextView {
        val t = UiKit.text(ctx, label.uppercase(), Tokens.T_CAPTION, R.color.on_surface_muted, bold = true)
        t.setPadding(UiKit.dp(ctx, Tokens.SP_XS), UiKit.dp(ctx, Tokens.SP_L), 0, UiKit.dp(ctx, Tokens.SP_S))
        return t
    }

    /** Icon tile + title + optional subtitle, tappable. */
    fun row(ctx: Context, icon: String, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val r = LinearLayout(ctx)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = Tokens.cardBg(ctx, Tokens.R_M)
        val p = UiKit.dp(ctx, Tokens.SP_M)
        r.setPadding(p, p, p, p)
        r.addView(Design.iconTile(ctx, icon, Tokens.PRIMARY, 42))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UiKit.dp(ctx, Tokens.SP_M), 0, 0, 0)
        col.addView(UiKit.text(ctx, title, Tokens.T_BODY, bold = true))
        if (subtitle != null) col.addView(UiKit.text(ctx, subtitle, Tokens.T_CAPTION, R.color.on_surface_muted))
        r.addView(col, UiKit.lp(0, -2, 1f))
        if (onClick != null) { r.setOnClickListener { onClick() }; Design.ripple(r) }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, UiKit.dp(ctx, Tokens.SP_S))
        r.layoutParams = lp
        return r
    }

    /** Big number with a small label under it. */
    fun stat(ctx: Context, label: String, value: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.background = Tokens.cardBg(ctx, Tokens.R_M)
        val p = UiKit.dp(ctx, Tokens.SP_M)
        c.setPadding(p, p, p, p)
        val v = UiKit.text(ctx, value, Tokens.T_HEAD, bold = true)
        v.gravity = Gravity.CENTER
        c.addView(v)
        val l = UiKit.text(ctx, label, Tokens.T_CAPTION, R.color.on_surface_muted)
        l.gravity = Gravity.CENTER
        c.addView(l)
        return c
    }

    fun primary(ctx: Context, label: String, onClick: () -> Unit): MaterialButton = UiKit.button(ctx, label) { onClick() }

    fun empty(ctx: Context, icon: String, message: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(0, UiKit.dp(ctx, 48), 0, UiKit.dp(ctx, 48))
        c.addView(Design.iconTile(ctx, icon, Tokens.TEXT_MUTED, 64, 20, 30f))
        val t = UiKit.text(ctx, message, Tokens.T_BODY, R.color.on_surface_muted)
        t.gravity = Gravity.CENTER
        t.setPadding(0, UiKit.dp(ctx, Tokens.SP_M), 0, 0)
        c.addView(t)
        return c
    }

    fun spacer(ctx: Context, dpH: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, UiKit.dp(ctx, dpH))
        return v
    }
}
