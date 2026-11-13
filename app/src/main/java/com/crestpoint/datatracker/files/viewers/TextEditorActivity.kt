package com.crestpoint.datatracker.files.viewers

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.files.ui.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/** Simple text / code viewer + editor with find & replace. Big files open read-only (first 2 MB). */
class TextEditorActivity : AppCompatActivity() {
    private lateinit var file: File
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var charset: Charset = Charsets.UTF_8
    private var readOnly = false
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra("path") ?: run { finish(); return })

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UiKit.color(this, R.color.surface))
        root.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 36), UiKit.dp(this, 10), UiKit.dp(this, 8))

        val title = UiKit.text(this, file.name, 18f, bold = true)
        status = UiKit.text(this, "Loading…", 12f, R.color.on_surface_muted)
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val save = UiKit.button(this, "Save") { save() }
        val find = UiKit.button(this, "Find / replace", true) { findDialog() }
        bar.addView(save); bar.addView(find)

        editor = EditText(this)
        editor.typeface = Typeface.MONOSPACE
        editor.textSize = 13f
        editor.gravity = Gravity.TOP or Gravity.START
        editor.setBackgroundColor(UiKit.color(this, R.color.surface_elevated))
        editor.setTextColor(UiKit.color(this, R.color.on_surface))
        editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.addView(editor, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(title); root.addView(status); root.addView(bar)
        root.addView(sv, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dirty) { finish(); return }
                MaterialAlertDialogBuilder(this@TextEditorActivity).setTitle("Unsaved changes")
                    .setPositiveButton("Save & close") { _, _ -> if (save()) finish() }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Keep editing", null).show()
            }
        })

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { load() }
            editor.setText(text)
            editor.isEnabled = true
            editor.isFocusable = !readOnly
            editor.showSoftInputOnFocus = !readOnly
            status.text = (if (readOnly) "Read-only (file larger than 2 MB, showing the first 2 MB)" else "Editable") +
                " · ${charset.name()}"
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { dirty = true }
            })
        }
    }

    private fun load(): String {
        val max = 2 * 1024 * 1024
        readOnly = file.length() > max
        val bytes = file.inputStream().use { ins ->
            val buf = ByteArray(minOf(file.length(), max.toLong()).toInt())
            var off = 0
            while (off < buf.size) { val n = ins.read(buf, off, buf.size - off); if (n < 0) break; off += n }
            buf.copyOf(off)
        }
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            charset = Charsets.UTF_8
            return String(bytes, 3, bytes.size - 3, charset)
        }
        if (bytes.size >= 2 && ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))) {
            charset = Charsets.UTF_16
            return String(bytes, charset)
        }
        charset = Charsets.UTF_8
        return String(bytes, charset)
    }

    private fun save(): Boolean {
        if (readOnly) { UiKit.toast(this, "This file is open read-only"); return false }
        return try {
            file.writeText(editor.text.toString(), charset)
            dirty = false
            UiKit.toast(this, "Saved")
            true
        } catch (e: Exception) {
            UiKit.toast(this, "Could not save: ${e.message}")
            false
        }
    }

    private fun findDialog() {
        val ctx = this
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val p = UiKit.dp(ctx, 20)
        box.setPadding(p, UiKit.dp(ctx, 8), p, 0)
        val q = EditText(ctx); q.hint = "Find"; q.setSingleLine(true)
        val r = EditText(ctx); r.hint = "Replace with"; r.setSingleLine(true)
        box.addView(q); box.addView(r)
        MaterialAlertDialogBuilder(ctx).setTitle("Find / replace").setView(box)
            .setPositiveButton("Find next") { _, _ ->
                val text = editor.text.toString()
                val needle = q.text.toString()
                if (needle.isEmpty()) return@setPositiveButton
                var i = text.indexOf(needle, editor.selectionEnd, ignoreCase = true)
                if (i < 0) i = text.indexOf(needle, 0, ignoreCase = true)
                if (i < 0) UiKit.toast(ctx, "Not found") else { editor.requestFocus(); editor.setSelection(i, i + needle.length) }
            }
            .setNeutralButton("Replace all") { _, _ ->
                val needle = q.text.toString()
                if (needle.isEmpty() || readOnly) return@setNeutralButton
                val n = Regex(Regex.escape(needle), RegexOption.IGNORE_CASE).findAll(editor.text.toString()).count()
                editor.setText(editor.text.toString().replace(needle, r.text.toString(), ignoreCase = true))
                UiKit.toast(ctx, "Replaced $n")
            }
            .setNegativeButton("Close", null).show()
    }
}
