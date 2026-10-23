package com.mycompany.datatracker.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns

/** OCRs the first page of PDFs the user picked. */
class PdfScanner(private val context: Context, private val cache: MediaCache) {

    /** Returns how many new PDFs were added. */
    suspend fun scan(uris: List<Uri>): Int {
        val known = cache.knownDocUris()
        var added = 0
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // not every provider allows persisting; scanning still works now
            }
            if (uri.toString() in known) continue
            val bmp = renderFirstPage(uri)
            val text = Ocr.read(bmp)
            bmp?.recycle()
            cache.putDoc(uri.toString(), displayName(uri), text)
            added++
        }
        return added
    }

    private fun renderFirstPage(uri: Uri): Bitmap? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    if (renderer.pageCount == 0) return null
                    val page = renderer.openPage(0)
                    try {
                        val w = 1080
                        val h = (w.toFloat() / page.width * page.height).toInt()
                            .coerceIn(1, 3000)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) return c.getString(0) ?: "document.pdf"
                }
        } catch (e: Exception) {
            // fall through
        }
        return uri.lastPathSegment ?: "document.pdf"
    }
}
