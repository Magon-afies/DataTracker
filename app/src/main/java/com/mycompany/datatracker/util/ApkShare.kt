package com.mycompany.datatracker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mycompany.datatracker.R
import java.io.File

object ApkShare {

    fun buildIntent(context: Context): Intent? {
        val app = context.applicationContext
        val info = app.applicationInfo
        if (!info.splitSourceDirs.isNullOrEmpty()) return null
        val source = File(info.sourceDir)
        if (!source.isFile) return null

        val dir = File(app.cacheDir, "share")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val name = "DataTracker-" + versionName(app) + ".apk"
        val target = File(dir, name)
        source.copyTo(target, overwrite = true)

        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", target)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, app.getString(R.string.share_app_text))
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "app"
    } catch (_: Exception) {
        "app"
    }
}
