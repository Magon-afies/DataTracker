package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.crestpoint.datatracker.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tiny helpers so the Files suite can build its screens in code (no extra XML to keep in sync). */
object UiKit {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(ctx: Context, res: Int): Int = ContextCompat.getColor(ctx, res)

    fun text(
        ctx: Context, s: CharSequence, sp: Float = 14f,
        colorRes: Int = R.color.on_surface, bold: Boolean = false
    ): TextView {
        val t = TextView(ctx)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color(ctx, colorRes))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun button(ctx: Context, label: String, outlined: Boolean = false, onClick: (View) -> Unit): MaterialButton {
        val b = if (outlined) {
            MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        } else {
            MaterialButton(ctx)
        }
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick(it) }
        return b
    }

    fun iconBtn(ctx: Context, glyph: String, onClick: (View) -> Unit): TextView {
        val t = text(ctx, glyph, 20f)
        val p = dp(ctx, 10)
        t.setPadding(p, p, p, p)
        t.setOnClickListener { onClick(it) }
        return t
    }

    fun rounded(ctx: Context, colorRes: Int, radiusDp: Int = 14): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color(ctx, colorRes))
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        return g
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(ctx, R.color.surface_elevated)
        val p = dp(ctx, 14)
        c.setPadding(p, p, p, p)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
        c.layoutParams = lp
        return c
    }

    fun progress(ctx: Context, fraction: Float): ProgressBar {
        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 1000
        pb.progress = (fraction.coerceIn(0f, 1f) * 1000).toInt()
        pb.progressTintList = ColorStateList.valueOf(color(ctx, R.color.brand_purple))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 8))
        lp.topMargin = dp(ctx, 8)
        pb.layoutParams = lp
        return pb
    }

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

    fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun prompt(
        ctx: Context, title: String, initial: String = "", hint: String = "",
        password: Boolean = false, onOk: (String) -> Unit
    ) {
        val input = EditText(ctx)
        input.setText(initial)
        input.hint = hint
        input.setSingleLine(true)
        if (password) input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setSelection(input.text.length)
        val box = FrameLayout(ctx)
        val p = dp(ctx, 20)
        box.setPadding(p, dp(ctx, 8), p, 0)
        box.addView(input)
        MaterialAlertDialogBuilder(ctx).setTitle(title).setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    fun confirm(ctx: Context, title: String, message: String, yes: String = "OK", onYes: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(yes) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }
}
