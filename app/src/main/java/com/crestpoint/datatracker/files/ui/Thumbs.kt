package com.crestpoint.datatracker.files.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import com.crestpoint.datatracker.files.FileKind
import com.crestpoint.datatracker.files.FileKinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Small in-memory thumbnail loader for images, videos and APK icons. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val scope = CoroutineScope(SupervisorJob() + Executors.newFixedThreadPool(3).asCoroutineDispatcher())

    fun wants(f: File): Boolean {
        val k = FileKinds.of(f)
        return k == FileKind.IMAGE || k == FileKind.VIDEO || k == FileKind.APK
    }

    fun load(ctx: Context, f: File, px: Int, cb: (Bitmap) -> Unit) {
        val key = f.path + "|" + f.lastModified() + "|" + px
        val hit = cache.get(key)
        if (hit != null) { cb(hit); return }
        val app = ctx.applicationContext
        scope.launch {
            val bmp: Bitmap? = try {
                when (FileKinds.of(f)) {
                    FileKind.IMAGE -> ThumbnailUtils.createImageThumbnail(f, Size(px, px), null)
                    FileKind.VIDEO -> ThumbnailUtils.createVideoThumbnail(f, Size(px, px), null)
                    FileKind.APK -> apkIcon(app, f, px)
                    else -> null
                }
            } catch (e: Throwable) { null }
            if (bmp != null) {
                cache.put(key, bmp)
                withContext(Dispatchers.Main) { cb(bmp) }
            }
        }
    }

    private fun apkIcon(ctx: Context, f: File, px: Int): Bitmap? {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(f.absolutePath, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = f.absolutePath
        ai.publicSourceDir = f.absolutePath
        return pm.getApplicationIcon(ai).toBitmap(px, px)
    }
}
