package com.crestpoint.datatracker.files.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import java.io.File

object Editors {
    fun canEdit(f: File): Boolean {
        if (!f.isFile) return false
        val k = FileKinds.of(f)
        return (k == FileKind.IMAGE && f.extension.lowercase() != "svg") || k == FileKind.VIDEO || k == FileKind.AUDIO
    }

    fun open(ctx: Context, f: File) {
        if (!canEdit(f)) return
        val cls = if (FileKinds.of(f) == FileKind.IMAGE) ImageEditorActivity::class.java else MediaEditorActivity::class.java
        val i = Intent(ctx, cls).putExtra("path", f.path)
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** name_edited.ext, name_edited2.ext ... next to the original. Never overwrites. */
    fun outFile(src: File, ext: String): File {
        val dir = if (src.path.contains("/open_with/"))
            File(android.os.Environment.getExternalStorageDirectory(), "Download").also { it.mkdirs() }
        else (src.parentFile ?: src.absoluteFile.parentFile)
        var n = File(dir, "${src.nameWithoutExtension}_edited.$ext")
        var i = 2
        while (n.exists()) { n = File(dir, "${src.nameWithoutExtension}_edited$i.$ext"); i++ }
        return n
    }
}
