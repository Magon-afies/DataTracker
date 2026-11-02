package com.mycompany.datatracker.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size

private class LiveImage(
    val id: Long,
    val name: String,
    val path: String,
    val modified: Long,
    val size: Long
)

/**
 * Walks the gallery, and for every photo not yet in the cache computes the
 * perceptual hash + OCR text and saves it. Safe to stop and resume at any time.
 */
class MediaIndexer(private val context: Context) {

    private val cache = MediaCache(context)

    suspend fun index(shouldStop: () -> Boolean, onProgress: suspend (Int, Int) -> Unit) {
        val live = queryLive()
        cache.pruneMissing(live.map { it.id }.toSet())

        val known = cache.knownModified()
        val todo = live.filter { known[it.id] != it.modified }
        val total = todo.size
        onProgress(0, total)

        var done = 0
        for (img in todo) {
            if (shouldStop()) return

            val bmp = loadBitmap(img.id, 720)
            val hash = bmp?.let { dHash(it) } ?: 0L
            val text = Ocr.read(bmp)
            bmp?.recycle()

            cache.put(
                CachedImage(img.id, img.name, img.path, img.modified, img.size, hash, text)
            )
            done++
            if (done % 5 == 0 || done == total) onProgress(done, total)
        }
    }

    private fun queryLive(): List<LiveImage> {
        val out = ArrayList<LiveImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    LiveImage(
                        c.getLong(0),
                        c.getString(1) ?: "image",
                        c.getString(2) ?: "",
                        c.getLong(3),
                        c.getLong(4)
                    )
                )
            }
        }
        return out
    }

    private fun loadBitmap(imageId: Long, targetSize: Int): Bitmap? {
        val uri: Uri =
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
        return try {
            context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
        } catch (e: Exception) {
            null
        }
    }

    /** 64-bit difference hash: 9x8 grayscale, one bit per left>right comparison. */
    private fun dHash(source: Bitmap): Long {
        val mid = Bitmap.createScaledBitmap(source, 64, 64, true)
        val tiny = Bitmap.createScaledBitmap(mid, 9, 8, true)
        if (mid !== source) mid.recycle()
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p1 = luminance(tiny.getPixel(x, y))
                val p2 = luminance(tiny.getPixel(x + 1, y))
                hash = (hash shl 1) or (if (p1 > p2) 1L else 0L)
            }
        }
        tiny.recycle()
        return hash
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
