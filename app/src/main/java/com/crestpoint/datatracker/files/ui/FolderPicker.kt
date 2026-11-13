package com.crestpoint.datatracker.files.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

object FolderPicker {
    fun show(ctx: Context, start: File, onPick: (File) -> Unit) {
        fun render(dir: File) {
            val dirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
            val labels = ArrayList<String>()
            labels.add("⬆  ..")
            dirs.forEach { labels.add("📁  " + it.name) }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(dir.absolutePath)
                .setItems(labels.toTypedArray()) { _, i ->
                    if (i == 0) render(dir.parentFile ?: dir) else render(dirs[i - 1])
                }
                .setPositiveButton("Select this folder") { _, _ -> onPick(dir) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        render(if (start.isDirectory) start else (start.parentFile ?: start))
    }
}
