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
        "▶" to R.drawable.ic_s_play,
        "⏸" to R.drawable.ic_s_pause,
        "⏮" to R.drawable.ic_s_prev,
        "⏭" to R.drawable.ic_s_next,
        "🔀" to R.drawable.ic_s_shuffle,
        "🔁" to R.drawable.ic_s_repeat,
        "←" to R.drawable.ic_s_back,
        "ℹ" to R.drawable.ic_s_info,
        "⬇" to R.drawable.ic_s_download,
        "📥" to R.drawable.ic_s_download,
        "⬆" to R.drawable.ic_s_upload,
        "🗑" to R.drawable.ic_s_trash,
        "✖" to R.drawable.ic_s_close,
        "➕" to R.drawable.ic_s_plus,
        "🎬" to R.drawable.ic_s_video,
        "🎶" to R.drawable.ic_s_audio,
        "📂" to R.drawable.ic_s_folder,
        "✎" to R.drawable.ic_s_edit,
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

    fun has(key: String): Boolean { val k = key.replace("\uFE0F", ""); return map.containsKey(k) || extra.containsKey(k) }

    private val extra: Map<String, String> = mapOf(
        "🔒" to "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
        "👁" to "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        "⚠" to "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z",
        "💾" to "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z",
        "✏" to "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        "🗂" to "M10,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
        "☑" to "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"
    ).let { m -> m + mapOf("🔓" to m.getValue("🔒")) }

    private fun drawableFor(tv: TextView, k: String, color: Int): android.graphics.drawable.Drawable? {
        val res = map[k]
        if (res != null) return ContextCompat.getDrawable(tv.context, res)?.mutate()?.also { it.setTint(color) }
        val path = extra[k] ?: return null
        return PathIcon(path, color)
    }

    fun set(tv: TextView, key: String, tint: Int? = null) {
        val k = key.replace("\uFE0F", "")
        val d = drawableFor(tv, k, tint ?: tv.currentTextColor)
        if (d == null) { tv.text = key; return }
        val s = (tv.textSize * 1.15f).toInt().coerceAtLeast(8)
        d.setBounds(0, 0, s, s)
        val sp = SpannableString(" ")
        sp.setSpan(ImageSpan(d, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = sp
    }

    /** "⬇️  Download" becomes [icon] Download. Labels that do not start with a known icon are left alone. */
    fun setLabel(tv: TextView, label: String, tint: Int? = null) {
        val i = label.indexOf(' ')
        if (i <= 0) return
        val k = label.substring(0, i).replace("\uFE0F", "")
        if (!has(k)) return
        val d = drawableFor(tv, k, tint ?: tv.currentTextColor) ?: return
        val s = (tv.textSize * 1.15f).toInt().coerceAtLeast(8)
        d.setBounds(0, 0, s, s)
        val sb = android.text.SpannableStringBuilder(" ")
        sb.setSpan(ImageSpan(d, ImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("  ").append(label.substring(i).trim())
        tv.text = sb
    }
}
