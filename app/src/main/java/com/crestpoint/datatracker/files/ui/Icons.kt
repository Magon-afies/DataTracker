package com.crestpoint.datatracker.files.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.crestpoint.datatracker.R

/** Shows a crisp vector icon in place of an old emoji. Unknown keys fall back to plain text. */
object Icons {
    private val map: Map<String, Int> = mapOf(
        "📁" to R.drawable.ic_s_folder,
        "🖼" to R.drawable.ic_s_image,
        "📷" to R.drawable.ic_s_image,
        "🎞" to R.drawable.ic_s_video,
        "🎵" to R.drawable.ic_s_audio,
        "🎧" to R.drawable.ic_s_audio,
        "📄" to R.drawable.ic_s_doc,
        "📝" to R.drawable.ic_s_code,
        "📦" to R.drawable.ic_s_archive,
        "📱" to R.drawable.ic_s_apk,
        "📎" to R.drawable.ic_s_file,
        "🔍" to R.drawable.ic_s_search,
        "⇅" to R.drawable.ic_s_sort,
        "⊞" to R.drawable.ic_s_grid,
        "⋮" to R.drawable.ic_s_more,
        "⋯" to R.drawable.ic_s_more,
        "🧹" to R.drawable.ic_s_broom,
        "📋" to R.drawable.ic_s_copy,
        "👯" to R.drawable.ic_s_copy,
        "🔗" to R.drawable.ic_s_link,
        "🔄" to R.drawable.ic_s_refresh,
        "⟳" to R.drawable.ic_s_refresh,
        "✕" to R.drawable.ic_s_close,
        "✓" to R.drawable.ic_s_check,
        "✅" to R.drawable.ic_s_checkc,
        "🏠" to R.drawable.ic_s_home,
        "⭐" to R.drawable.ic_s_star,
        "📤" to R.drawable.ic_s_share,
        "📊" to R.drawable.ic_s_chart,
        "🔐" to R.drawable.ic_s_lock,
        "🌐" to R.drawable.ic_s_globe,
        "📡" to R.drawable.ic_s_globe,
        "💬" to R.drawable.ic_s_chat,
        "🕘" to R.drawable.ic_s_history,
        "🧩" to R.drawable.ic_s_puzzle,
        "＋" to R.drawable.ic_s_plus,
    )

    fun has(key: String) = map.containsKey(key.replace("\uFE0F", ""))

    fun set(tv: TextView, key: String, tint: Int? = null) {
        val res = map[key.replace("\uFE0F", "")]
        val d = res?.let { ContextCompat.getDrawable(tv.context, it)?.mutate() }
        if (d == null) { tv.text = key; return }
        val s = (tv.textSize * 1.15f).toInt().coerceAtLeast(8)
        d.setBounds(0, 0, s, s)
        d.setTint(tint ?: tv.currentTextColor)
        val sp = SpannableString(" ")
        sp.setSpan(ImageSpan(d, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = sp
    }
}
