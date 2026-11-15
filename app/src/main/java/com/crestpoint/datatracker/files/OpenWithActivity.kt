package com.crestpoint.datatracker.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.crestpoint.datatracker.files.edit.Editors
import com.crestpoint.datatracker.files.ui.UiKit
import java.io.File
import java.io.FileOutputStream

/** Receives "Open with" / "Edit" requests from other apps and sends them to our own viewers and editors. */
class OpenWithActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data ?: if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) { finish(); return }
        val edit = intent.action == Intent.ACTION_EDIT
        Thread {
            val f = try { resolve(uri) } catch (_: Throwable) { null }
            runOnUiThread {
                if (f == null || !f.isFile) {
                    UiKit.toast(this, "Cannot open this file")
                } else if (FileKinds.of(f) == FileKind.OTHER) {
                    UiKit.toast(this, "This file type is not supported here")
                } else if (edit && Editors.canEdit(f)) {
                    Editors.open(this, f)
                } else {
                    FileOpener.open(this, f)
                }
                finish()
            }
        }.start()
    }

    private fun resolve(uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        if (uri.authority == "com.android.externalstorage.documents") {
            try {
                val p = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                if (p.size == 2 && p[0].equals("primary", true)) {
                    val f = File(Environment.getExternalStorageDirectory(), p[1])
                    if (f.isFile && f.canRead()) return f
                }
            } catch (_: Exception) { }
        }
        try {
            contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val path = c.getString(0)
                    if (path != null) { val f = File(path); if (f.isFile && f.canRead()) return f }
                }
            }
        } catch (_: Exception) { }
        // Fall back: copy into our cache so the viewers can read it
        var name = "file"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        } catch (_: Exception) { }
        name = name.replace("/", "_")
        if (!name.contains('.')) {
            val ext = contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            if (ext != null) name = "$name.$ext"
        }
        val dir = File(cacheDir, "open_with")
        dir.mkdirs()
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 6 * 3_600_000L) it.delete() }
        val out = File(dir, name)
        contentResolver.openInputStream(uri)?.use { i -> FileOutputStream(out).use { o -> i.copyTo(o) } } ?: return null
        return out
    }
}
