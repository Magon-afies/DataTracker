#!/usr/bin/env python3
import os

proj = os.path.expanduser("~/DataTracker")
src_java = os.path.join(proj, "app/src/main/java/com/crestpoint/datatracker")

print("==> Writing missing stubs and UI extensions...")

# 1. Clean & create necessary package directories
databot_dir = os.path.join(src_java, "databot")
cleaner_dir = os.path.join(src_java, "cleaner")
ui_dir = os.path.join(src_java, "files/ui")

os.makedirs(databot_dir, exist_ok=True)
os.makedirs(cleaner_dir, exist_ok=True)
os.makedirs(ui_dir, exist_ok=True)

# Submodules for databot
for sub in ["autopsy", "usage", "util"]:
    os.makedirs(os.path.join(databot_dir, sub), exist_ok=True)
    with open(os.path.join(databot_dir, sub, "Stub.kt"), "w", encoding="utf-8") as f:
        f.write(f"package com.crestpoint.datatracker.databot.{sub}\n\nclass Stub\n")

# 2. CacheCleanerService stub
with open(os.path.join(cleaner_dir, "CacheCleanerService.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.cleaner

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CacheCleanerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
""")

# 3. DataBot stub
with open(os.path.join(databot_dir, "DataBot.kt"), "w", encoding="utf-8") as f:
    f.write(r"""package com.crestpoint.datatracker.databot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DataBot : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
""")

# 4. Comprehensive UI extensions and helper functions (satisfying prompt, confirm, SheetAction, iconTile, etc.)
extensions_code = r"""package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.crestpoint.datatracker.R

object Glass {
    const val BACK = 1
    const val SETTINGS = 2
    const val CLEANER = 3
    
    fun iconBtn(ctx: Context, icon: Int, size: Int = 40, onClick: () -> Unit): View {
        return Button(ctx).apply {
            text = "⚡"
            setOnClickListener { onClick() }
        }
    }
}

class PathIcon(val type: Int, val color: Int) : android.graphics.drawable.Drawable() {
    override fun draw(canvas: android.graphics.Canvas) {}
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

data class SheetAction(val label: String, val onClick: () -> Unit) {
    constructor(label: String, dummy: String, onClick: () -> Unit) : this(label, onClick)
}

class ActionSheet(private val ctx: Context) {
    var title: String = ""
    private val actions = mutableListOf<SheetAction>()

    fun addAction(label: String, onClick: () -> Unit) {
        actions.add(SheetAction(label, onClick))
    }
    fun addAction(label: String, icon: Any?, onClick: () -> Unit) {
        actions.add(SheetAction(label, onClick))
    }
    fun addAction(action: SheetAction) {
        actions.add(action)
    }

    fun show() {
        val items = actions.map { it.label }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(title.ifEmpty { "Options" })
            .setItems(items) { _, which ->
                actions.getOrNull(which)?.onClick?.invoke()
            }
            .show()
    }
}

fun Context.prompt(title: String, defaultVal: String = "", onResult: (String) -> Unit) {
    val input = android.widget.EditText(this).apply { setText(defaultVal) }
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(title)
        .setView(input)
        .setPositiveButton("OK") { _, _ -> onResult(input.text.toString()) }
        .setNegativeButton("Cancel", null)
        .show()
}

fun Context.confirm(title: String, message: String, onConfirm: () -> Unit) {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Yes") { _, _ -> onConfirm() }
        .setNegativeButton("No", null)
        .show()
}

fun Context.iconTile(iconRes: Int, text: String): View {
    return LinearLayout(this)
}

val android.graphics.drawable.Drawable.kind: Int get() = 0
"""

with open(os.path.join(ui_dir, "Extensions.kt"), "w", encoding="utf-8") as f:
    f.write(extensions_code)

print("==> All stubs and extension helpers written successfully.")
